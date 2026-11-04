const DEFAULT_TIMEOUT = 8000
const BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

/** 业务错误码，对齐后端 ResultCode（见需求文档 18.3） */
export const CODE = {
  SUCCESS: 0,
  PARAM_ERROR: 10001,
  PARAM_INVALID: 10003,
  UNAUTHORIZED: 20001,
  TOKEN_EXPIRED: 20002,
  FORBIDDEN: 20003,
  RATE_LIMITED: 40001,
  PHONE_REGISTERED: 31002,
  USERNAME_REGISTERED: 31003,
  PASSWORD_ERROR: 31004,
  ACCOUNT_LOCKED: 31005,
  ACCOUNT_DISABLED: 31006,
}

export class ApiError extends Error {
  constructor(message, { status = 0, code = -1, payload = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.payload = payload
  }
}

export function buildUrl(path, query = {}) {
  const normalized = path.startsWith('/') ? path : `/${path}`
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    params.set(key, String(value))
  })
  const search = params.toString()
  return `${BASE_URL}${normalized}${search ? `?${search}` : ''}`
}

/**
 * 原始请求：不注入令牌、不刷新，供认证模块和刷新单飞使用。
 * 返回解包后的 data；业务失败统一抛 ApiError，调用方按 code 分支。
 */
export async function rawRequest(path, {
  method = 'GET',
  query,
  body,
  timeout = DEFAULT_TIMEOUT,
  signal,
  headers = {},
} = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  if (signal) signal.addEventListener('abort', () => controller.abort(), { once: true })

  try {
    const response = await fetch(buildUrl(path, query), {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    })

    let payload = null
    try {
      payload = await response.json()
    } catch {
      payload = null
    }

    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.success === false || payload.code !== 0) {
        throw new ApiError(payload.message || '业务处理失败', {
          status: response.status,
          code: payload.code,
          payload,
        })
      }
      return payload.data
    }

    if (!response.ok) {
      throw new ApiError(`请求失败（HTTP ${response.status}）`, { status: response.status })
    }
    return payload
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error?.name === 'AbortError') {
      throw new ApiError('请求超时，请稍后重试', { code: 'TIMEOUT' })
    }
    throw new ApiError('网络异常，请检查连接', { code: 'NETWORK' })
  } finally {
    clearTimeout(timer)
  }
}

/* 认证模块在运行时注册，避免 client 与 auth 循环依赖 */
let authBridge = {
  getAccessToken: () => '',
  refresh: null,
  onUnauthorized: null,
}

export function configureAuthBridge(config = {}) {
  authBridge = { ...authBridge, ...config }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * 带登录态的请求：
 * - 自动注入 Bearer 令牌；
 * - 401 / 20001 / 20002 时走单飞刷新并重放一次；
 * - 刷新令牌一次一换，并发刷新会被后端判为重放并强制全端下线，因此这里严格串行。
 */
export async function request(path, options = {}) {
  const {
    method = 'GET',
    query,
    body,
    timeout = DEFAULT_TIMEOUT,
    signal,
    skipAuth = false,
    retried = false,
  } = options

  const token = skipAuth ? '' : authBridge.getAccessToken()
  const headers = token ? { Authorization: `Bearer ${token}` } : {}

  try {
    return await rawRequest(path, { method, query, body, timeout, signal, headers })
  } catch (error) {
    if (!(error instanceof ApiError)) throw error
    const expired = error.status === 401
      || error.code === CODE.UNAUTHORIZED
      || error.code === CODE.TOKEN_EXPIRED
    if (skipAuth || retried || !expired) throw error

    let nextToken = ''
    try {
      nextToken = await authBridge.refresh?.(token)
    } catch {
      authBridge.onUnauthorized?.(error.message)
      throw error
    }
    if (!nextToken) {
      authBridge.onUnauthorized?.(error.message)
      throw error
    }
    return request(path, { ...options, retried: true })
  }
}

export { sleep }
