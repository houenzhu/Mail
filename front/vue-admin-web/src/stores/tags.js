import { defineStore } from 'pinia'

/**
 * 多标签页签（对齐千牛顶部「已打开页面」页签）。
 *
 * 标签 key 规则：普通页用 route.name；带动态参数的页（如 product/edit/:id）
 * 追加参数值，避免「编辑 A、编辑 B」被折叠成同一个页签。
 * 同一 key 重复访问只更新路径，不新增页签（即筛选条件变化不会堆积页签）。
 */
const AFFIX_TAGS = ['workbench']

export function tagKey(route) {
  const id = route.params?.id
  return id ? `${route.name}:${id}` : String(route.name)
}

export const useTagsStore = defineStore('tags', {
  state: () => ({
    /** @type {{ key: string, name: string, path: string, title: string, affix: boolean }[]} */
    visited: [],
  }),

  actions: {
    addTag(route) {
      if (!route.name || route.meta?.public) return
      const key = tagKey(route)
      const existing = this.visited.find((tag) => tag.key === key)
      if (existing) {
        // 同一页面只保留一个页签，路径取最新（例如搜索关键词变化）
        existing.path = route.fullPath
        existing.title = route.meta?.title || existing.title
        return
      }
      this.visited.push({
        key,
        name: route.name,
        path: route.fullPath,
        title: route.meta?.title || String(route.name),
        affix: AFFIX_TAGS.includes(route.name),
      })
    },

    removeTag(key) {
      const index = this.visited.findIndex((tag) => tag.key === key)
      if (index === -1) return
      if (this.visited[index].affix) return
      this.visited.splice(index, 1)
    },

    /** 关闭除当前页签外的其它页签（固定页签保留） */
    closeOthers(key) {
      this.visited = this.visited.filter((tag) => tag.affix || tag.key === key)
    },

    /** 关闭全部（固定页签保留） */
    closeAll() {
      this.visited = this.visited.filter((tag) => tag.affix)
    },
  },
})