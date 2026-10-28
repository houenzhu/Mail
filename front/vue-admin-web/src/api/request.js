import axios from 'axios'

import { getUseMock } from '@/mock'

/**
 * 统一请求层。
 *
 * 三条硬约束（来自后端语义，见计划文档 6.2）：
 *  1. accessToken 固定 300s 且**不滑动续期**（需求文档 21.8.7 坑 4）→ 过期必须走 /api/auth/refresh；
 *  2. 刷新令牌**一次一换**，并发刷新或重放旧令牌会被后端判为重放并**强制全端下线**
 *     → 刷新必须串行单飞（single-flight），绝不能同时发两个 refresh；
 *  3. 后端 `Result{code,message,data}` 与 HTTP 状态码分离（鉴权失败是 HTTP 401 + code=20001/20002，
 *     业务失败常是 HTTP 200 + code=3xxxx）→ **不能只看 HTTP 状态码**。
 *
 * 错误提示约定（避免同一个错误弹两次）：
 *  - 默认：业务失败由本层统一 toast（`skipErrorToast` 未设置时）。
 *  - 调用方若自己会展示更精确的文案（如登录页要按 31004/20003 分支），
 *    必须传 `{ skipErrorToast: true }`，否则会出现"请求层 + 页面"两次提示。
 */

const ACCESS_KEY = 'mall_admin_access_token'
const REFRESH_KEY = 'mall_admin_refresh_token'

/** 业务错误码（对齐需求文档 18.3 ResultCode） */
export const CODE = {
  SUCCESS: 0,
  UNAUTHORIZED: 20001,
  TOKEN_EXPIRED: 20002,
  FORBIDDEN: 20003,
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

/* ---------------- 令牌存储 ---------------- */

export function getAccessToken() {
  return localStorage.getItem(ACCESS_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

export function setTokens(accessToken, refreshToken) {
  if (accessToken) localStorage.setItem(ACCESS_KEY, accessToken)
  if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

/* ---------------- 登录态失效回调（由 store 注册，避免循环依赖） ---------------- */

let onUnauthorized = null

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

/* ---------------- 单飞刷新 ---------------- */

let refreshing = null

/**
 * 刷新令牌。同一时刻只允许一个刷新在飞，其余请求等待同一 Promise 的结果。
 * 这是硬要求：并发 refresh 会触发后端「重放检测 → 强制下线」。
 */
function refreshTokens() {
  if (refreshing) return refreshing

  const refreshToken = getRefreshToken()
  if (!refreshToken) return Promise.reject(new Error('NO_REFRESH_TOKEN'))

  refreshing = http
    .post(
      '/auth/refresh',
      { refreshToken },
      { skipAuth: true, skipErrorToast: true, _isRefresh: true },
    )
    .then((res) => {
      const body = res.data
      if (!body || body.code !== CODE.SUCCESS || !body.data) {
        throw new Error(body?.message || 'REFRESH_FAILED')
      }
      setTokens(body.data.accessToken, body.data.refreshToken)
      return body.data.accessToken
    })
    .finally(() => {
      refreshing = null
    })

  return refreshing
}

/** 供调试用：模拟 accessToken 过期 */
export function __expireAccessTokenForDebug() {
  const token = getAccessToken()
  if (token) localStorage.setItem(ACCESS_KEY, `${token}__expired__`)
}

/* ---------------- 请求拦截：注入令牌 ---------------- */

http.interceptors.request.use(
  (config) => {
    // mock 模式：交给 mock 层处理，不发真实请求
    if (getUseMock()) {
      config.adapter = mockAdapter
    }

    if (!config.skipAuth) {
      const token = getAccessToken()
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    }
    return config
  },
  (error) => Promise.reject(error),
)

/* ---------------- mock 适配器 ---------------- */

async function mockAdapter(config) {
  const { mockRequest } = await import('@/mock')
  const body = await mockRequest({
    url: config.url,
    method: (config.method || 'get').toUpperCase(),
    data: parseBody(config.data),
    params: config.params,
  })
  return { data: body, status: 200, statusText: 'OK', headers: {}, config }
}

function parseBody(data) {
  if (!data) return undefined
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return data
    }
  }
  return data
}

/* ---------------- 响应拦截：解包 + 401 刷新重放 ---------------- */

http.interceptors.response.use(
  async (response) => {
    const body = response.data

    // 非 Result 结构（理论上不该出现）直接返回，避免误判
    if (!body || typeof body !== 'object' || !('code' in body)) {
      return body
    }

    if (body.code === CODE.SUCCESS) {
      return body.data
    }

    // 登录态问题 → 刷新后重放原请求
    if (body.code === CODE.UNAUTHORIZED || body.code === CODE.TOKEN_EXPIRED) {
      return handleUnauthorized(response.config, body)
    }

    // 业务失败：提示由调用方或全局决定
    if (!response.config.skipErrorToast) {
      notifyError(body.message || `请求失败（${body.code}）`)
    }
    return Promise.reject(new BizError(body.code, body.message))
  },
  async (error) => {
    const response = error.response

    // HTTP 401：网关鉴权失败（Sa-Token 会话无效/过期）
    if (response && response.status === 401 && !error.config?._isRefresh) {
      return handleUnauthorized(error.config, response.data)
    }

    if (!axios.isCancel(error) && !error.config?.skipErrorToast) {
      notifyError(resolveHttpError(error))
    }
    return Promise.reject(error)
  },
)

async function handleUnauthorized(config, body) {
  // 刷新请求自身返回登录态错误（HTTP 200 + 20001/20002）→ 说明刷新令牌也已失效。
  // ⚠️ 这里必须直接失败返回，绝不能再走下面的"刷新"分支：refreshTokens() 是单飞的，
  //   会返回"正在飞的自己"，await 自己 → Promise 永不 settle → 路由守卫卡死 → 白屏。
  //   HTTP 401 那条路径已在响应错误拦截器里用 _isRefresh 提前排除，这里补上 200 路径。
  //   清态与跳转交给发起刷新的调用方统一处理，避免重复弹提示。
  if (config?._isRefresh) {
    return Promise.reject(new BizError(body?.code ?? CODE.UNAUTHORIZED, body?.message))
  }

  if (!config || config._retried) {
    failLogin(body?.message)
    return Promise.reject(new BizError(body?.code ?? CODE.UNAUTHORIZED, body?.message))
  }

  // 刷新令牌本身失败（或会话过期）→ 直接登出，不再重试
  if (!getRefreshToken()) {
    failLogin(body?.message)
    return Promise.reject(new BizError(body?.code ?? CODE.UNAUTHORIZED, body?.message))
  }

  try {
    const newToken = await refreshTokens()
    const retryConfig = { ...config, _retried: true }
    retryConfig.headers = { ...(config.headers || {}), Authorization: `Bearer ${newToken}` }
    return http.request(retryConfig)
  } catch (e) {
    failLogin('登录已过期，请重新登录')
    return Promise.reject(e)
  }
}

function failLogin(message) {
  clearTokens()
  if (onUnauthorized) onUnauthorized(message)
}

/** HTTP 层错误 → 可读文案（5xxxx 下游依赖类在后端有专门错误码，这里只兜底网络类错误） */
function resolveHttpError(error) {
  if (error.code === 'ECONNABORTED') return '请求超时，请稍后重试'
  const status = error.response?.status
  if (status === 403) return '无访问权限'
  if (status === 404) return '接口不存在（可能后端尚未实现）'
  if (status >= 500) return '服务异常，请稍后重试'
  if (!error.response) return '网络异常，请检查后端网关是否已启动'
  return error.message || '请求失败'
}

/** 全局提示（Element Plus 按需引入，避免在这里 import 整个库） */
let errorNotifier = null

export function setErrorNotifier(fn) {
  errorNotifier = fn
}

function notifyError(message) {
  if (errorNotifier) errorNotifier(message)
}

export class BizError extends Error {
  constructor(code, message) {
    super(message || `业务失败（${code}）`)
    this.name = 'BizError'
    this.code = code
  }
}

export default http
