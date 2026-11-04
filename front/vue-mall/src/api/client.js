const DEFAULT_TIMEOUT = 6000
const BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

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

export async function request(path, { query, timeout = DEFAULT_TIMEOUT, signal } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  if (signal) {
    signal.addEventListener('abort', () => controller.abort(), { once: true })
  }

  try {
    const response = await fetch(buildUrl(path, query), {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    })

    if (!response.ok) {
      throw new ApiError(`请求失败（HTTP ${response.status}）`, { status: response.status })
    }

    const payload = await response.json()
    if (payload && typeof payload === 'object' && 'success' in payload) {
      if (payload.success === false || payload.code !== 0) {
        throw new ApiError(payload.message || '业务处理失败', {
          code: payload.code,
          payload,
        })
      }
      return payload.data
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
