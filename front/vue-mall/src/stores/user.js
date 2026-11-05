import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import { useCartStore } from '@/stores/cart'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  onTokenStorageChange,
  setTokens,
} from '@/api/tokenStorage'

/** 买家端用户态：令牌持久化 + 当前用户资料 + 登录 / 注册 / 注销 / 会话恢复 */
export const useUserStore = defineStore('user', () => {
  const accessToken = ref(getAccessToken())
  const refreshToken = ref(getRefreshToken())
  const profile = ref(null)
  const restoring = ref(false)
  const restored = ref(false)
  const unauthorizedMessage = ref('')

  const isLoggedIn = computed(() => Boolean(accessToken.value))
  const displayName = computed(
    () => profile.value?.nickname || profile.value?.username || '亲爱的用户',
  )
  const maskedPhone = computed(() => profile.value?.phone || '')
  const roles = computed(() => profile.value?.roles ?? [])

  function syncTokenState() {
    accessToken.value = getAccessToken()
    refreshToken.value = getRefreshToken()
  }

  function applyLogin(data) {
    setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken })
    syncTokenState()
    profile.value = {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
      roles: data.roles ?? [],
    }
    restored.value = true
  }

  async function login(credentials) {
    const data = await authApi.login(credentials)
    applyLogin(data)
    return data
  }

  async function register(payload) {
    return authApi.register(payload)
  }

  /** 刷新页面后恢复登录态；失败即视为未登录，不留下“有令牌没资料”的半登录状态 */
  async function restore() {
    if (!getAccessToken()) {
      reset()
      restored.value = true
      return null
    }
    restoring.value = true
    try {
      const info = await authApi.fetchCurrentUser()
      profile.value = info
      restored.value = true
      syncTokenState()
      return info
    } catch {
      reset()
      restored.value = true
      return null
    } finally {
      restoring.value = false
    }
  }

  /** 登录响应不含手机号等资料：进入账户页后补拉一次，失败时保留已有昵称，不误踢登录态 */
  async function hydrateProfile() {
    if (!isLoggedIn.value) return null
    try {
      const info = await authApi.fetchCurrentUser()
      profile.value = info
      syncTokenState()
      return info
    } catch {
      return profile.value
    }
  }

  async function ensureProfile() {
    if (profile.value || !isLoggedIn.value) return profile.value
    return restore()
  }

  async function logout() {
    const cartStore = useCartStore()
    try {
      if (accessToken.value) await authApi.logout()
    } catch {
      // 注销失败也要清本地态，避免用户卡在“看起来还登录着”
    } finally {
      reset()
      cartStore.reset()
    }
  }

  function reset() {
    useCartStore().reset()
    clearTokens()
    accessToken.value = ''
    refreshToken.value = ''
    profile.value = null
  }

  function handleUnauthorized(message) {
    reset()
    useCartStore().reset()
    unauthorizedMessage.value = message || '登录已过期，请重新登录'
  }

  function clearUnauthorizedMessage() {
    unauthorizedMessage.value = ''
  }

  onTokenStorageChange(({ accessToken: nextAccess, refreshToken: nextRefresh }) => {
    accessToken.value = nextAccess
    refreshToken.value = nextRefresh
    if (!nextAccess) {
      profile.value = null
      useCartStore().reset()
    }
  })

  return {
    accessToken,
    refreshToken,
    profile,
    restoring,
    restored,
    isLoggedIn,
    displayName,
    maskedPhone,
    roles,
    unauthorizedMessage,
    login,
    register,
    restore,
    hydrateProfile,
    ensureProfile,
    logout,
    reset,
    handleUnauthorized,
    clearUnauthorizedMessage,
  }
})