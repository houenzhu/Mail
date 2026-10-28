<script setup>
/**
 * 我的商品 —— 对齐千牛「商品 → 我的商品」页（计划文档 8.1 的样板页）。
 *
 * 结构：状态 Tab（带角标）→ 筛选栏 → 批量操作 → 表格 → 分页
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import MoneyRange from '@/components/common/MoneyRange.vue'
import ProductStatusTag from '@/components/common/ProductStatusTag.vue'
import { PRODUCT_STATUS, PRODUCT_STATUS_TABS } from '@/constants/dict'
import { deleteProduct, listBrands, listCategories, pageProducts, updateProductStatus } from '@/api/product'
import { usePermission } from '@/composables/usePermission'
import { useTable } from '@/composables/useTable'
import { formatDateTime, formatNumber } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const { hasRole } = usePermission()

/** 低库存阈值：仅用于列表高亮，不是业务规则 */
const LOW_STOCK_THRESHOLD = 50

const activeTab = ref('all')
const selectedRows = ref([])
const categories = ref([])
const brands = ref([])
const actionLoadingId = ref(null)
const tableRef = ref()

const filters = reactive({
  keyword: '',
  productId: '',
  categoryId: undefined,
  brandId: undefined,
})

const initialQuery = computed(() => {
  const tab = PRODUCT_STATUS_TABS.find((item) => item.key === activeTab.value)
  return {
    keyword: filters.keyword || undefined,
    productId: filters.productId || undefined,
    categoryId: filters.categoryId,
    brandId: filters.brandId,
    status: tab?.status,
    deleted: tab?.deleted ?? 0,
    pageSize: 10,
  }
})

const { query, list, total, loading, statusCounts, load, search, reset, changePage, changePageSize } =
  useTable(pageProducts, initialQuery.value, { pageSize: 10 })

/** 切换 Tab：重置到第一页并重查 */
watch(activeTab, () => {
  const tab = PRODUCT_STATUS_TABS.find((item) => item.key === activeTab.value)
  query.status = tab?.status
  query.deleted = tab?.deleted ?? 0
  search()
})

onMounted(async () => {
  // 顶部搜索框跳转过来时带上 keyword
  if (typeof route.query.keyword === 'string' && route.query.keyword) {
    filters.keyword = route.query.keyword
    query.keyword = route.query.keyword
    load()
  }
  try {
    const [cats, brs] = await Promise.all([listCategories(), listBrands()])
    categories.value = cats ?? []
    brands.value = brs ?? []
  } catch {
    // 字典拉取失败不影响列表主流程
  }
})

/**
 * Tab 角标：statusCounts 由后端一次返回（BFF 的 AdminProductPage.statusCounts），
 * 因此不会为每个 Tab 各发一次 count 请求。字段名以后端契约为准。
 */
function tabCount(key) {
  const counts = statusCounts.value || {}
  return counts[key] ?? 0
}

function onSearch() {
  router.replace({ query: filters.keyword ? { keyword: filters.keyword } : {} })
  Object.assign(query, {
    keyword: filters.keyword || undefined,
    productId: filters.productId || undefined,
    categoryId: filters.categoryId,
    brandId: filters.brandId,
  })
  search()
}

function onReset() {
  filters.keyword = ''
  filters.productId = ''
  filters.categoryId = undefined
  filters.brandId = undefined
  router.replace({ query: {} })
  Object.assign(query, {
    keyword: undefined,
    productId: undefined,
    categoryId: undefined,
    brandId: undefined,
  })
  search()
}

function clearSelection() {
  tableRef.value?.clearSelection()
  selectedRows.value = []
}

/* ---------------- 行操作 ---------------- */

async function onToggleStatus(row) {
  const next = Number(row.status) === PRODUCT_STATUS.ON ? PRODUCT_STATUS.OFF : PRODUCT_STATUS.ON
  const verb = next === PRODUCT_STATUS.ON ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确认${verb}商品「${row.name}」？`, `${verb}确认`, {
      type: 'warning',
      confirmButtonText: `确认${verb}`,
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  actionLoadingId.value = row.id
  try {
    await updateProductStatus(row.id, next)
    ElMessage.success(`${verb}成功`)
    row.status = next
    load()
  } catch (e) {
    // 34002 等业务失败文案由 request.js 统一提示
    if (!e?.code) ElMessage.error(`${verb}失败`)
  } finally {
    actionLoadingId.value = null
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除后商品将不在列表与 C 端展示（逻辑删除，可恢复）。确认删除「${row.name}」？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  actionLoadingId.value = row.id
  try {
    await deleteProduct(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    ElMessage.error('删除失败')
  } finally {
    actionLoadingId.value = null
  }
}

function onEdit(row) {
  router.push({ name: 'product-edit', params: { id: row.id } })
}

function onCopyId(row) {
  const text = String(row.id)
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(text).then(
      () => ElMessage.success(`已复制商品ID：${text}`),
      () => ElMessage.warning(`商品ID：${text}`),
    )
  } else {
    ElMessage.info(`商品ID：${text}`)
  }
}

/* ---------------- 批量操作 ---------------- */

async function onBatchStatus(next) {
  const rows = selectedRows.value.filter((row) => Number(row.status) !== next)
  if (!rows.length) {
    ElMessage.warning('所选商品均已处于该状态')
    return
  }
  const verb = next === PRODUCT_STATUS.ON ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确认批量${verb} ${rows.length} 个商品？`, `批量${verb}`, {
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await Promise.all(rows.map((row) => updateProductStatus(row.id, next)))
    ElMessage.success(`已${verb} ${rows.length} 个商品`)
    clearSelection()
    load()
  } catch {
    ElMessage.error(`批量${verb}失败`)
  }
}
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-toolbar">
        <h2 class="page-title">
          我的商品
          <span class="page-subtitle">共 {{ total }} 件</span>
        </h2>
        <el-button
          v-if="hasRole('OPERATOR', 'ADMIN')"
          type="primary"
          @click="router.push({ name: 'product-create' })"
        >
          <el-icon><Plus /></el-icon>发布商品
        </el-button>
      </div>

      <!-- 状态 Tab（对齐千牛：全部 / 出售中 / 仓库中 / 已删除，带数量角标） -->
      <el-tabs v-model="activeTab" class="status-tabs">
        <el-tab-pane v-for="tab in PRODUCT_STATUS_TABS" :key="tab.key" :name="tab.key">
          <template #label>
            <span class="tab-label">
              {{ tab.label }}
              <span class="tab-count">{{ tabCount(tab.key) }}</span>
            </span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-form :inline="true" class="filter-form" @submit.prevent>
          <el-form-item label="商品名称">
            <el-input
              v-model="filters.keyword"
              placeholder="名称 / 卖点"
              clearable
              style="width: 190px"
              @keyup.enter="onSearch"
            />
          </el-form-item>
          <el-form-item label="商品ID">
            <el-input
              v-model="filters.productId"
              placeholder="精确/模糊均可"
              clearable
              style="width: 140px"
              @keyup.enter="onSearch"
            />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="filters.categoryId" placeholder="全部分类" clearable style="width: 140px">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="品牌">
            <el-select v-model="filters.brandId" placeholder="全部品牌" clearable style="width: 140px">
              <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
            </el-select>
          </el-form-item>
          <el-form-item class="filter-actions">
            <el-button type="primary" @click="onSearch">
              <el-icon><Search /></el-icon>搜索
            </el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 批量操作 -->
      <div class="batch-bar">
        <span class="sel-count">
          已选 <b>{{ selectedRows.length }}</b> 件
        </span>
        <el-button
          v-if="selectedRows.length"
          link
          type="primary"
          size="small"
          @click="clearSelection"
        >
          清空
        </el-button>

        <span class="batch-divider" />

        <el-button
          size="small"
          :disabled="!selectedRows.length || !hasRole('OPERATOR', 'ADMIN')"
          @click="onBatchStatus(PRODUCT_STATUS.ON)"
        >
          批量上架
        </el-button>
        <el-button
          size="small"
          :disabled="!selectedRows.length || !hasRole('OPERATOR', 'ADMIN')"
          @click="onBatchStatus(PRODUCT_STATUS.OFF)"
        >
          批量下架
        </el-button>
        <el-button size="small" :disabled="true">批量改库存（待建）</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="list"
        row-key="id"
        border
        class="goods-table"
        @selection-change="(rows) => (selectedRows = rows)"
      >
        <el-table-column type="selection" width="44" :selectable="(row) => row.deleted === 0" />

        <el-table-column label="商品" min-width="320">
          <template #default="{ row }">
            <div class="goods-cell">
              <img v-if="row.mainImage" class="goods-thumb" :src="row.mainImage" :alt="row.name" />
              <div v-else class="goods-thumb-fallback">{{ row.name.slice(0, 1) }}</div>
              <div class="goods-meta">
                <div class="goods-name" :title="row.name">{{ row.name }}</div>
                <div class="goods-sub">
                  ID:{{ row.id }}
                  <span v-if="row.skuCount > 1">· {{ row.skuCount }} 个规格</span>
                  <span v-if="row.categoryName">· {{ row.categoryName }}</span>
                  <span v-if="row.brandName">· {{ row.brandName }}</span>
                </div>
                <div class="goods-sub">
                  <ProductStatusTag :status="row.status" :deleted="row.deleted" />
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="价格" width="140" align="right">
          <template #default="{ row }">
            <MoneyRange :min="row.priceMin" :max="row.priceMax" />
          </template>
        </el-table-column>

        <el-table-column label="库存" width="100" align="right" sortable :sort-by="(row) => row.stock">
          <template #default="{ row }">
            <span v-if="row.stock === 0" class="stock-out">售罄</span>
            <span v-else :class="{ 'stock-low': row.stock < LOW_STOCK_THRESHOLD }">
              {{ formatNumber(row.stock) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="累计销量" width="110" align="right" sortable :sort-by="(row) => row.sales">
          <template #default="{ row }">
            <div>{{ formatNumber(row.sales) }}</div>
            <div class="text-muted text-xs">30日 {{ formatNumber(row.sales30d) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="text-xs text-muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">
            <span class="text-xs text-muted">{{ formatDateTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <template v-if="row.deleted === 0">
                <el-button
                  link
                  type="primary"
                  size="small"
                  :disabled="!hasRole('OPERATOR', 'ADMIN')"
                  @click="onEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  link
                  type="primary"
                  size="small"
                  :loading="actionLoadingId === row.id"
                  :disabled="!hasRole('OPERATOR', 'ADMIN')"
                  @click="onToggleStatus(row)"
                >
                  {{ row.status === PRODUCT_STATUS.ON ? '下架' : '上架' }}
                </el-button>
                <el-dropdown trigger="click">
                  <el-button link type="primary" size="small">
                    更多<el-icon><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="onCopyId(row)">复制商品ID</el-dropdown-item>
                      <el-dropdown-item
                        divided
                        :disabled="!hasRole('OPERATOR', 'ADMIN')"
                        @click="onDelete(row)"
                      >
                        删除商品
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
              <span v-else class="text-muted text-xs">已删除</span>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="没有符合条件的商品" :image-size="80" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pager">
        <el-pagination
          :current-page="query.pageNo"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="changePage"
          @size-change="changePageSize"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 状态 Tab ---------- */
.status-tabs {
  margin-bottom: 4px;
}

.status-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: var(--border-color-light);
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-count {
  min-width: 18px;
  height: 16px;
  padding: 0 5px;
  border-radius: 8px;
  background: var(--border-color-light);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 500;
  line-height: 16px;
  text-align: center;
}

.status-tabs :deep(.el-tabs__item.is-active) .tab-count {
  background: var(--brand-primary-light);
  color: var(--brand-primary);
}

/* ---------- 筛选栏 ---------- */
.filter-bar {
  padding: 12px 12px 2px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--border-color-light);
  border-radius: var(--card-radius);
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.filter-actions {
  margin-left: auto;
}

/* ---------- 批量操作条 ---------- */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 10px;
  min-height: 24px;
}

.sel-count {
  font-size: 12px;
  color: var(--text-regular);
}

.sel-count b {
  color: var(--brand-primary);
  font-weight: 600;
}

.batch-divider {
  width: 1px;
  height: 14px;
  background: var(--border-color);
  margin: 0 2px;
}

/* ---------- 表格 ---------- */
.goods-table {
  width: 100%;
}

.stock-out {
  color: var(--c-danger);
  font-weight: 600;
}

.stock-low {
  color: var(--c-warning);
  font-weight: 600;
}

/* ---------- 分页 ---------- */
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>