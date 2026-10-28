<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { findMenuTrail } from '@/constants/menu'
import { __expireAccessTokenForDebug } from '@/api/request'
import { getUseMock } from '@/mock'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const breadcrumbs = computed(() => findMenuTrail(route.path))
const useMock = getUseMock()
const keyword = ref('')

function onSearch() {
  if (!keyword.value.trim()) return
  router.push({ name: 'product-list', query: { keyword: keyword.value.trim() } })
}

async function onLogout() {
  await ElMessageBox.confirm(
    '退出登录会同时下线所有设备上的会话（后端语义为全端立即生效），确认退出？',
    '退出登录',
    { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' },
  )
    .then(async () => {
      await userStore.logout()
      ElMessage.success('已退出登录')
      router.replace({ name: 'login' })
    })
    .catch(() => {})
}

/**
 * 调试用：把本地 accessToken 改坏，触发 401 → 单飞刷新 → 重放。
 * 用来验证「令牌过期无感刷新」这条链路（M1 验收项）。
 */
function onSimulateExpire() {
  __expireAccessTokenForDebug()
  ElMessage.info('已模拟令牌失效，请在页面上做一个操作（如刷新列表）以验证无感刷新')
}
</script>

<template>
  <header class="topbar">
    <div class="topbar-left">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="(item, i) in breadcrumbs" :key="i">
          <span :class="{ 'crumb-current': i === breadcrumbs.length - 1 }">{{ item.title }}</span>
        </el-breadcrumb-item>
      </el-breadcrumb>
      <el-tag v-if="useMock" size="small" type="warning" effect="light" class="mock-tag">
        演示数据 · 后端 /api/admin 未就绪
      </el-tag>
    </div>

    <div class="topbar-right">
      <el-input
        v-model="keyword"
        placeholder="搜索商品名称"
        clearable
        class="search-input"
        @keyup.enter="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <div class="icon-actions">
        <el-tooltip content="模拟令牌过期，验证无感刷新" placement="bottom">
          <el-button text circle class="icon-btn" @click="onSimulateExpire">
            <el-icon><Timer /></el-icon>
          </el-button>
        </el-tooltip>

        <el-badge :value="3" :offset="[-2, 3]">
          <el-button text circle class="icon-btn"><el-icon><Bell /></el-icon></el-button>
        </el-badge>
      </div>

      <span class="divider" />

      <el-dropdown trigger="click">
        <span class="user-area">
          <el-avatar :size="26" class="avatar">{{ userStore.displayName.slice(0, 1) }}</el-avatar>
          <span class="user-meta">
            <span class="user-name">{{ userStore.displayName }}</span>
            <span class="user-role">{{ userStore.roles.join('/') || '未分配角色' }}</span>
          </span>
          <el-icon class="caret"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>账号：{{ userStore.profile?.username || '-' }}</el-dropdown-item>
            <el-dropdown-item disabled>角色：{{ userStore.roles.join('/') || '-' }}</el-dropdown-item>
            <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  height: var(--header-height);
  flex: 0 0 var(--header-height);
  background: var(--bg-white);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  gap: 12px;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.topbar-left :deep(.el-breadcrumb__inner) {
  color: var(--text-secondary);
  font-weight: 400;
}

.crumb-current {
  color: var(--text-primary);
  font-weight: 600;
}

.mock-tag {
  flex: 0 0 auto;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 搜索：圆角胶囊，弱边框 */
.search-input {
  width: 210px;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 16px;
  background: var(--bg-hover);
  box-shadow: none;
  padding-left: 12px;
}

.search-input :deep(.el-input__wrapper:hover) {
  background: #f0f1f3;
}

.search-input :deep(.el-input__wrapper.is-focus) {
  background: var(--bg-white);
  box-shadow: 0 0 0 1px var(--brand-primary) inset;
}

.icon-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.icon-btn {
  color: var(--text-regular);
  font-size: 16px;
}

.icon-btn:hover {
  color: var(--brand-primary);
  background: var(--bg-hover);
}

.divider {
  width: 1px;
  height: 20px;
  background: var(--border-color);
  margin: 0 2px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 3px 8px 3px 3px;
  border-radius: 18px;
  outline: none;
  transition: background-color var(--transition-fast);
}

.user-area:hover {
  background: var(--bg-hover);
}

.avatar {
  background: linear-gradient(135deg, var(--brand-primary), #ff8b3d);
  font-size: 12px;
  flex: 0 0 auto;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  color: var(--text-primary);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 11px;
  color: var(--text-secondary);
}

.caret {
  color: var(--text-secondary);
  font-size: 12px;
}
</style>