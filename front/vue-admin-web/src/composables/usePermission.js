import { computed } from 'vue'

import { useUserStore } from '@/stores/user'

/**
 * 角色鉴权（前端侧）。
 *
 * ⚠️ 前端鉴权只决定"显不显示"，后端 `@RequireStaff` 才决定"能不能做"
 * （见计划文档 6.4 / G-01）。不要把隐藏按钮当成安全措施。
 */
export function usePermission() {
  const userStore = useUserStore()

  function hasRole(...roles) {
    return userStore.hasRole(...roles)
  }

  return {
    roles: computed(() => userStore.roles),
    isStaff: computed(() => userStore.isStaff),
    hasRole,
  }
}
