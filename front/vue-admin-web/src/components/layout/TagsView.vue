<script setup>
/**
 * 多标签页签（TagsView）——对齐千牛「页面页签」：打开过的页面在此排队，
 * 可快速切换 / 单个关闭 / 关闭其他 / 关闭全部。
 *
 * 主页（工作台）为固定页签，不可关闭。
 */
import { computed, onBeforeUnmount, onMounted, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { tagKey, useTagsStore } from '@/stores/tags'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsStore()

const visited = computed(() => tagsStore.visited)
const currentKey = computed(() => tagKey(route))

watch(
  () => route.fullPath,
  () => tagsStore.addTag(route),
  { immediate: true },
)

function isActive(tag) {
  return tag.key === currentKey.value
}

function goTo(tag) {
  if (!isActive(tag)) router.push(tag.path)
}

/**
 * 关闭页签。若关的是当前页，跳到相邻页签；没有相邻页则回工作台。
 * 注意在移除前先算出目标，避免 splice 后索引错位。
 */
function closeTag(key) {
  const list = visited.value
  const index = list.findIndex((tag) => tag.key === key)
  if (index === -1) return
  const rest = list.filter((tag) => tag.key !== key)
  const wasActive = key === currentKey.value
  tagsStore.removeTag(key)
  if (!wasActive) return

  const target = rest[index] || rest[index - 1]
  if (target) {
    router.push(target.path)
  } else {
    router.push('/workbench')
  }
}

/* ---------------- 右键菜单 ---------------- */

const contextMenu = reactive({ visible: false, left: 0, top: 0, key: '' })

function openContextMenu(event, tag) {
  contextMenu.left = event.clientX
  contextMenu.top = event.clientY
  contextMenu.key = tag.key
  contextMenu.visible = true
}

function closeContextMenu() {
  contextMenu.visible = false
}

onMounted(() => {
  document.addEventListener('click', closeContextMenu)
  window.addEventListener('resize', closeContextMenu)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', closeContextMenu)
  window.removeEventListener('resize', closeContextMenu)
})

function onCloseOthers() {
  tagsStore.closeOthers(contextMenu.key)
  const keep = visited.value.find((tag) => tag.key === contextMenu.key)
  if (keep) router.push(keep.path)
}

function onCloseAll() {
  tagsStore.closeAll()
  router.push('/workbench')
}

/** 顶部下拉：关闭其他（保留当前页） */
function onCloseOthersForCurrent() {
  tagsStore.closeOthers(currentKey.value)
  const keep = visited.value.find((tag) => tag.key === currentKey.value)
  if (keep) router.push(keep.path)
}

function onTagsCommand(command) {
  if (command === 'others') onCloseOthersForCurrent()
  else onCloseAll()
}

const canCloseCurrent = computed(() => {
  const tag = visited.value.find((item) => item.key === contextMenu.key)
  return tag && !tag.affix
})
</script>

<template>
  <div class="tags-view">
    <el-scrollbar class="tags-scroll">
      <div class="tags-wrap">
        <div
          v-for="tag in visited"
          :key="tag.key"
          class="tag"
          :class="{ 'is-active': isActive(tag) }"
          @click="goTo(tag)"
          @contextmenu.prevent="openContextMenu($event, tag)"
        >
          <span v-if="isActive(tag)" class="dot" />
          <span class="tag-title">{{ tag.title }}</span>
          <el-icon v-if="!tag.affix" class="tag-close" @click.stop="closeTag(tag.key)">
            <Close />
          </el-icon>
        </div>
      </div>
    </el-scrollbar>

    <el-dropdown trigger="click" class="tags-actions" @command="onTagsCommand">
      <el-button text size="small" class="actions-btn">
        <el-icon><MoreFilled /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="others">关闭其他</el-dropdown-item>
          <el-dropdown-item command="all">关闭全部</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <teleport to="body">
      <ul
        v-if="contextMenu.visible"
        class="tag-context-menu"
        :style="{ left: contextMenu.left + 'px', top: contextMenu.top + 'px' }"
        @click.stop
      >
        <li v-if="canCloseCurrent" @click="closeTag(contextMenu.key); closeContextMenu()">关闭当前</li>
        <li @click="onCloseOthers(); closeContextMenu()">关闭其他</li>
        <li @click="onCloseAll(); closeContextMenu()">关闭全部</li>
      </ul>
    </teleport>
  </div>
</template>

<style scoped>
.tags-view {
  height: var(--tabbar-height);
  flex: 0 0 var(--tabbar-height);
  background: var(--bg-white);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
}

.tags-scroll {
  flex: 1 1 auto;
  min-width: 0;
}

.tags-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 0;
  white-space: nowrap;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 8px 0 10px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--bg-white);
  color: var(--text-regular);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: color var(--transition-fast), border-color var(--transition-fast),
    background-color var(--transition-fast);
}

.tag:hover {
  color: var(--brand-primary);
  border-color: var(--brand-primary-light);
  background: var(--brand-primary-lighter);
}

.tag.is-active {
  color: var(--brand-primary);
  border-color: var(--brand-primary-light);
  background: var(--menu-active-bg);
  font-weight: 600;
}

.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--brand-primary);
}

.tag-title {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-close {
  font-size: 12px;
  border-radius: 50%;
  color: var(--text-secondary);
}

.tag-close:hover {
  color: #fff;
  background: var(--c-info);
}

.tags-actions {
  flex: 0 0 auto;
}

.actions-btn {
  color: var(--text-secondary);
}
</style>

<style>
/* 右键菜单 teleport 到 body，需非 scoped */
.tag-context-menu {
  position: fixed;
  z-index: 4000;
  margin: 0;
  padding: 4px;
  list-style: none;
  min-width: 108px;
  background: var(--bg-white);
  border: 1px solid var(--border-color-light);
  border-radius: var(--card-radius);
  box-shadow: var(--shadow-pop);
}

.tag-context-menu li {
  padding: 6px 12px;
  font-size: 12px;
  color: var(--text-regular);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.tag-context-menu li:hover {
  color: var(--brand-primary);
  background: var(--bg-hover);
}
</style>