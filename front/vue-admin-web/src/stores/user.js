import { defineStore } from 'pinia'

import * as authApi from '@/api/auth'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/api/request'
import { useTagsStore } from '@/stores/tags'

/** 可进入后台的角色（对齐需求文档 16.1：客服 / 运营 / 管理员） */
const STAFF_ROLES = ['OPERATOR', 'CS', 'ADMIN']

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: getAccessToken(),
    refreshToken: getRefreshToken(),
    profile: null,
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken),
    roles: (state) => state.profile?.roles ?? [],
    /** 是否具备后台访问权限 */
    isStaff() {
      return this.roles.some((role) => STAFF_ROLES.includes(role))
    },
    displayName: (state) => state.profile?.nickname || state.profile?.username || '未登录',
  },

  actions: {
    /**
     * 是否拥有任一角色（用于菜单 / 按钮级鉴权）。
     * 未声明 roles（undefined/null）视为「不限制」，对任意 staff 可见
     * —— 对齐 constants/menu.js 的约定「roles 为空表示所有 staff 可见」。
     * （注意不能用 `!flat.length` 判断：仅传 undefined 时 flat 长度为 1，
     *   必须先把 nullish 过滤掉，否则未声明角色的菜单项会被全部隐藏。）
     */
    hasRole(...roles) {
      const flat = roles.flat().filter((role) => role != null)
      if (!flat.length) return true
      return this.roles.some((role) => flat.includes(role))
    },

    /**
     * 登录。后端返回 roles，非 staff 直接拒绝进入后台
     * （前端拦截只是体验优化，真正的边界在后端，见 G-01）。
     */
    async login({ username, password }) {
      const data = await authApi.login({ username, password })
      const isStaff = (data.roles ?? []).some((role) => STAFF_ROLES.includes(role))
      if (!isStaff) {
        throw new Error('该账号无后台访问权限（需要 运营 / 客服 / 管理员 角色）')
      }
      this.applyToken(data)
      this.profile = {
        userId: data.userId,
        username: data.username,
        nickname: data.nickname,
        roles: data.roles ?? [],
      }
      return data
    },

    /**
     * 刷新页面后用已有令牌恢复用户信息。
     * 失败时清空登录态——避免出现"有令牌但没有用户信息"的半登录状态。
     * 静默请求：恢复失败只是"该重新登录了"，不该在登录页再弹一次错误。
     */
    async restore() {
      if (!this.accessToken && !getAccessToken()) return
      this.accessToken = getAccessToken()
      this.refreshToken = getRefreshToken()
      try {
        const info = await authApi.me({ silent: true })
        this.profile = info
      } catch {
        this.reset()
      }
    },

    async logout() {
      try {
        await authApi.logout()
      } catch {
        // 注销失败也要清本地态，否则用户被卡在"看起来还登录着"的状态
      } finally {
        this.reset()
      }
    },

    applyToken(data) {
      this.accessToken = data.accessToken
      this.refreshToken = data.refreshToken
      setTokens(data.accessToken, data.refreshToken)
    },

    /** 令牌已过期且无法刷新（由 request.js 回调） */
    reset() {
      this.accessToken = null
      this.refreshToken = null
      this.profile = null
      clearTokens()
      // 退出登录/会话失效后清空页签，避免下次登录残留上一个账号的标签
      useTagsStore().$reset()
    },
  },
})
