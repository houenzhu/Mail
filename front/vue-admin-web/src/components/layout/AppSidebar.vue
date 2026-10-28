<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { MENU_TREE } from '@/constants/menu'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

/** 当前激活的一级菜单 key（用于高亮分组） */
const activeGroupKey = computed(() => {
  const group = MENU_TREE.find((item) => {
    if (item.path === route.path) return true
    return item.children?.some((child) => route.path.startsWith(child.path))
  })
  return group?.key
})

const collapsed = computed(() => appStore.sidebarCollapsed)

/** 按角色过滤菜单（前端只控制可见性，真正的边界在后端） */
const visibleMenu = computed(() =>
  MENU_TREE.map((group) => {
    if (!userStore.hasRole(group.roles)) return null
    const children = group.children?.filter((child) => userStore.hasRole(child.roles))
    return { ...group, children }
  }).filter(Boolean),
)
</script>

<template>
  <aside class="sidebar" :class="{ 'is-collapsed': collapsed }">
    <div class="brand">
      <span class="brand-mark">M</span>
      <div v-show="!collapsed" class="brand-text">
        <div class="brand-name">商场管理后台</div>
        <div class="brand-sub">SELLER CONSOLE</div>
      </div>
    </div>

    <el-scrollbar class="menu-scroll">
      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        unique-opened
        router
        class="side-menu"
      >
        <template v-for="group in visibleMenu" :key="group.key">
          <!-- 单项（工作台） -->
          <el-menu-item v-if="!group.children" :index="group.path" :disabled="group.disabled">
            <el-icon><component :is="group.icon" /></el-icon>
            <template #title>{{ group.title }}</template>
          </el-menu-item>

          <!-- 分组（两级） -->
          <el-sub-menu v-else :index="group.key" :disabled="group.disabled">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>
            <el-menu-item
              v-for="child in group.children"
              :key="child.key"
              :index="child.path"
              :disabled="child.disabled"
            >
              {{ child.title }}
              <el-tag v-if="child.disabled" size="small" type="info" class="soon-tag">待建</el-tag>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-scrollbar>

    <div class="sidebar-foot">
      <el-button text class="collapse-btn" @click="appStore.toggleSidebar()">
        <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
        <span v-show="!collapsed" class="collapse-text">收起侧栏</span>
      </el-button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  flex: 0 0 var(--sidebar-width);
  height: 100%;
  background: var(--bg-white);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-base), flex-basis var(--transition-base);
}

.sidebar.is-collapsed {
  width: var(--sidebar-width-collapsed);
  flex-basis: var(--sidebar-width-collapsed);
}

/* ---------- 品牌区 ---------- */
.brand {
  height: var(--header-height);
  flex: 0 0 var(--header-height);
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 14px;
  border-bottom: 1px solid var(--border-color-light);
  overflow: hidden;
}

.sidebar.is-collapsed .brand {
  justify-content: center;
  padding: 0;
}

.brand-mark {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 6px;
  background: linear-gradient(135deg, var(--brand-primary), #ff8b3d);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(255, 80, 0, 0.28);
}

.brand-text {
  min-width: 0;
}

.brand-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-sub {
  font-size: 10px;
  letter-spacing: 0.06em;
  color: var(--text-placeholder);
  line-height: 14px;
}

/* ---------- 菜单 ---------- */
.menu-scroll {
  flex: 1 1 auto;
  min-height: 0;
  padding-top: 6px;
}

.side-menu {
  border-right: none;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--text-regular);
  --el-menu-active-color: var(--brand-primary);
  --el-menu-hover-bg-color: var(--bg-hover);
  --el-menu-item-height: var(--menu-item-height);
  --el-menu-sub-item-height: 36px;
  --el-menu-base-level-padding: 14px;
  --el-menu-level-padding: 16px;
}

.side-menu:not(.el-menu--collapse) {
  width: 100%;
}

/* 一级菜单：稍重的字重与图标 */
.side-menu :deep(> .el-menu-item),
.side-menu :deep(> .el-sub-menu > .el-sub-menu__title) {
  position: relative;
  font-size: 13px;
  margin: 2px 6px;
  border-radius: 4px;
  height: var(--menu-item-height);
  line-height: var(--menu-item-height);
}

.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  position: relative;
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

/* 悬停：浅底 + 主色文字 */
.side-menu :deep(.el-menu-item:not(.is-disabled):hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  background-color: var(--bg-hover);
  color: var(--brand-primary);
}

/* 二级菜单项：缩进 + 更小字号 */
.side-menu :deep(.el-menu--inline .el-menu-item) {
  font-size: 12.5px;
  margin: 1px 6px;
  border-radius: 4px;
  height: 34px;
  line-height: 34px;
  color: var(--text-secondary);
}

/* 选中态：浅橙底 + 左侧强调条 + 主色字 */
.side-menu :deep(.el-menu-item.is-active) {
  background-color: var(--menu-active-bg);
  color: var(--brand-primary);
  font-weight: 600;
}

.side-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: var(--menu-active-bar);
  height: 16px;
  border-radius: 0 2px 2px 0;
  background: var(--brand-primary);
}

/* 分组标题在有子项选中时保持主色 */
.side-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--brand-primary);
  font-weight: 600;
}

/* 图标与文字对齐 */
.side-menu :deep(.el-menu-item .el-icon),
.side-menu :deep(.el-sub-menu__title .el-icon) {
  font-size: 16px;
  width: 18px;
  margin-right: 8px;
}

.soon-tag {
  margin-left: 6px;
  transform: scale(0.85);
}

/* 折叠态：去掉圆角与边距，保持居中 */
.sidebar.is-collapsed .side-menu :deep(.el-menu-item),
.sidebar.is-collapsed .side-menu :deep(.el-sub-menu__title) {
  margin: 2px 0;
  border-radius: 0;
}

/* ---------- 底部收起栏 ---------- */
.sidebar-foot {
  flex: 0 0 38px;
  border-top: 1px solid var(--border-color-light);
  display: flex;
  align-items: center;
}

.collapse-btn {
  width: 100%;
  height: 100%;
  color: var(--text-secondary);
  gap: 6px;
  border-radius: 0;
}

.collapse-btn:hover {
  color: var(--brand-primary);
  background: var(--bg-hover);
}

.collapse-text {
  font-size: 12px;
}
</style>