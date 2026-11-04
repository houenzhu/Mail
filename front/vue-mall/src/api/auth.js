import { rawRequest, request } from './client'
import { withRefreshLock } from './refreshLock'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens,
} from './tokenStorage'

/** 刷新令牌一次一换：并发刷新会被后端判定为重放并强制全端下线 */
let refreshing = null
let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

function notifyUnauthorized(message) {
  clearTokens()
  unauthorizedHandler?.(message || '登录已过期，请重新登录')
}

/**
 * 单飞 + 跨标签互斥刷新。
 * - 同一标签内并发请求共享同一个 Promise；
 * - 跨标签通过 Web Locks（不支持时用 localStorage 租约）串行；
 * - 拿到锁后先比对访问令牌：若其他标签已经刷新过，直接复用新令牌，绝不重放旧刷新令牌。
 */
export function refreshSession(staleAccessToken = '') {
  if (refreshing) return refreshing

  const task = withRefreshLock(async () => {
    const currentAccessToken = getAccessToken()
    if (staleAccessToken && currentAccessToken && currentAccessToken !== staleAccessToken) {
      return currentAccessToken
    }

    const refreshToken = getRefreshToken()
    if (!refreshToken) throw new Error('NO_REFRESH_TOKEN')

    const data = await rawRequest('/auth/refresh', {
      method: 'POST',
      body: { refreshToken },
    })
    if (!data?.accessToken || !data?.refreshToken) throw new Error('REFRESH_FAILED')

    setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken })
    return data.accessToken
  })

  refreshing = task.finally(() => {
    refreshing = null
  })
  return refreshing
}

export function getAuthToken() {
  return getAccessToken()
}

export async function login({ username, password }) {
  const data = await rawRequest('/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!data?.accessToken || !data?.refreshToken) {
    throw new Error('登录响应缺少令牌')
  }
  setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken })
  return data
}

export async function register({ username, password, phone, nickname }) {
  return rawRequest('/auth/register', {
    method: 'POST',
    body: { username, password, phone, nickname },
  })
}

export async function fetchCurrentUser() {
  return request('/auth/me')
}

export async function logout() {
  try {
    await request('/auth/logout', { method: 'POST' })
  } finally {
    clearTokens()
  }
}

export { clearTokens, notifyUnauthorized }