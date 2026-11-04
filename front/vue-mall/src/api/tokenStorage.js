const ACCESS_KEY = 'mall_buyer_access_token'
const REFRESH_KEY = 'mall_buyer_refresh_token'

function read(key) {
  try {
    return localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

function write(key, value) {
  try {
    if (value) localStorage.setItem(key, value)
    else localStorage.removeItem(key)
  } catch {
    // 隐私模式或存储配额异常时，退化为仅内存态
  }
}

export function getAccessToken() {
  return read(ACCESS_KEY)
}

export function getRefreshToken() {
  return read(REFRESH_KEY)
}

export function setTokens({ accessToken, refreshToken } = {}) {
  if (accessToken) write(ACCESS_KEY, accessToken)
  if (refreshToken) write(REFRESH_KEY, refreshToken)
}

export function clearTokens() {
  write(ACCESS_KEY, '')
  write(REFRESH_KEY, '')
}

/** 其他标签页登录、刷新或注销后同步内存态（storage 事件只在其他标签页触发） */
export function onTokenStorageChange(handler) {
  if (typeof window === 'undefined') return () => {}
  const listener = (event) => {
    if (event.storageArea !== localStorage) return
    if (event.key !== null && event.key !== ACCESS_KEY && event.key !== REFRESH_KEY) return
    handler({ accessToken: getAccessToken(), refreshToken: getRefreshToken() })
  }
  window.addEventListener('storage', listener)
  return () => window.removeEventListener('storage', listener)
}

export const TOKEN_KEYS = { access: ACCESS_KEY, refresh: REFRESH_KEY }
