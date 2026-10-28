import http from './request'

/**
 * 认证接口 —— 由 mall-auth(8101) 提供，**已真实落地**。
 * 契约见需求文档 26.4：登录 / 刷新 / 注销 / 当前用户。
 *
 * 注意：refresh 在 request.js 的单飞刷新里直接调用，这里只导出给特殊场景使用。
 */

/**
 * 登录：签发 accessToken(300s) + refreshToken(7d) + roles。
 *
 * `skipErrorToast`：登录失败的文案由登录页统一呈现（它会展示后端 message，
 * 如 31004 账号或密码错误 / 20003 无权限），避免请求层与页面各弹一次造成重复提示。
 */
export function login(data) {
  return http.post('/auth/login', data, { skipAuth: true, skipErrorToast: true })
}

/** 刷新令牌（一次一换） */
export function refresh(refreshToken) {
  return http.post('/auth/refresh', { refreshToken }, { skipAuth: true, skipErrorToast: true })
}

/** 注销：后端语义是**全端立即下线**（ADR-026）。失败不弹提示，由调用方决定文案。 */
export function logout() {
  return http.post('/auth/logout', null, { skipErrorToast: true })
}

/**
 * 当前登录用户（roles 用于后台准入与菜单过滤）。
 * @param {object} [options]
 * @param {boolean} [options.silent] 静默模式：失败不弹提示。
 *   用于刷新页面后的会话恢复——失败时应安静地跳登录页，
 *   而不是在登录页上再弹一次错误（否则会和登录时的提示重复）。
 */
export function me({ silent = false } = {}) {
  return http.get('/auth/me', { skipErrorToast: silent })
}
