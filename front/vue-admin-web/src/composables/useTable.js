import { reactive, ref } from 'vue'

/**
 * 列表页通用逻辑：查询条件 + 分页 + 加载态 + 关键统计。
 *
 * 用法：
 *   const { query, list, total, loading, statusCounts, load, search, reset, changePage } =
 *     useTable(pageProducts, { status: undefined, deleted: 0 }, { pageSize: 10 })
 *
 * 约定：
 *  - `fetcher(query)` 需返回 `{ total, records, statusCounts? }`
 *  - 查询条件变化后调用 `search()` 会重置到第 1 页（避免"翻到第 3 页再筛选出空列表"）
 */
export function useTable(fetcher, initialQuery = {}, options = {}) {
  const { pageSize = 10, immediate = true } = options

  const query = reactive({
    pageNo: 1,
    pageSize,
    ...initialQuery,
  })

  const list = ref([])
  const total = ref(0)
  const loading = ref(false)
  const statusCounts = ref({})

  async function load() {
    loading.value = true
    try {
      const data = await fetcher({ ...query })
      list.value = data?.records ?? []
      total.value = data?.total ?? 0
      if (data?.statusCounts) statusCounts.value = data.statusCounts
    } catch {
      // 错误提示已由 request.js 统一处理，这里只保证列表不残留旧数据
      list.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  /** 条件变化：回到第 1 页再查 */
  function search() {
    query.pageNo = 1
    return load()
  }

  /** 重置条件：保留 initialQuery 的默认值 */
  function reset() {
    Object.keys(query).forEach((key) => {
      if (key !== 'pageNo' && key !== 'pageSize') delete query[key]
    })
    Object.assign(query, initialQuery, { pageNo: 1, pageSize: query.pageSize })
    return load()
  }

  function changePage(pageNo) {
    query.pageNo = pageNo
    return load()
  }

  function changePageSize(size) {
    query.pageSize = size
    query.pageNo = 1
    return load()
  }

  if (immediate) load()

  return {
    query,
    list,
    total,
    loading,
    statusCounts,
    load,
    search,
    reset,
    changePage,
    changePageSize,
  }
}
