<script setup>
/** 库存管理：按商品展示汇总库存，调整时选择具体 SKU 并写入库存流水。 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { adjustStock, getInventory } from '@/api/inventory'
import { listSkus, pageProducts } from '@/api/product'
import { usePermission } from '@/composables/usePermission'
import { formatDateTime, formatNumber } from '@/utils/format'

const { hasRole } = usePermission()
const LOW_STOCK_THRESHOLD = 50
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const adjustVisible = ref(false)
const adjustLoading = ref(false)
const skuLoading = ref(false)
const skuOptions = ref([])
const selectedProduct = ref(null)
const currentStock = ref(null)
const query = reactive({ keyword: '', onlyLow: false, pageNo: 1, pageSize: 10 })
const form = reactive({ skuId: null, delta: null, remark: '' })

async function load() {
  loading.value = true
  try {
    const data = await pageProducts({ keyword: query.keyword || undefined, pageNo: query.pageNo, pageSize: query.pageSize, deleted: 0 })
    let records = data?.records ?? []
    if (query.onlyLow) records = records.filter((item) => Number(item.stock) < LOW_STOCK_THRESHOLD)
    rows.value = records
    total.value = data?.total ?? 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() { query.pageNo = 1; load() }

async function onAdjust(row) {
  selectedProduct.value = row
  form.skuId = null
  form.delta = null
  form.remark = ''
  currentStock.value = null
  adjustVisible.value = true
  skuLoading.value = true
  try {
    skuOptions.value = await listSkus(row.id) ?? []
    if (skuOptions.value.length) {
      form.skuId = skuOptions.value[0].id
      await loadCurrentStock()
    }
  } catch {
    skuOptions.value = []
  } finally {
    skuLoading.value = false
  }
}

async function loadCurrentStock() {
  if (!form.skuId) return
  try {
    const data = await getInventory(form.skuId)
    currentStock.value = data?.available ?? null
  } catch {
    currentStock.value = null
  }
}

async function submitAdjust() {
  if (!form.skuId || !form.delta || Number(form.delta) === 0) {
    ElMessage.warning('请选择 SKU 并填写非 0 的调整数量')
    return
  }
  adjustLoading.value = true
  try {
    const data = await adjustStock({
      requestId: `inv-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      skuId: form.skuId,
      delta: Number(form.delta),
      remark: form.remark || undefined,
    })
    ElMessage.success(`库存调整成功，可售库存：${formatNumber(data?.remaining)}`)
    adjustVisible.value = false
    await load()
  } finally {
    adjustLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-toolbar">
        <div><h2 class="page-title">库存管理</h2><p class="page-subtitle">调整 SKU 库存并自动记录变动流水</p></div>
      </div>
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="商品名称"><el-input v-model="query.keyword" placeholder="按商品名称搜索" clearable style="width: 200px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item><el-checkbox v-model="query.onlyLow" @change="onSearch">只看低库存（&lt; {{ LOW_STOCK_THRESHOLD }}）</el-checkbox></el-form-item>
        <el-form-item><el-button type="primary" @click="onSearch">搜索</el-button><el-button @click="((query.keyword = ''), (query.onlyLow = false), onSearch())">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="rows" border stripe row-key="id">
        <el-table-column label="商品" min-width="280"><template #default="{ row }"><div class="goods-cell"><img v-if="row.mainImage" class="goods-thumb" :src="row.mainImage" :alt="row.name" /><div v-else class="goods-thumb-fallback">{{ row.name.slice(0, 1) }}</div><div class="goods-meta"><div class="goods-name" :title="row.name">{{ row.name }}</div><div class="goods-sub">ID:{{ row.id }} · {{ row.skuCount }} 个 SKU</div></div></div></template></el-table-column>
        <el-table-column label="可售库存" width="120" align="right"><template #default="{ row }"><span v-if="row.stock === 0" class="stock-out">0</span><span v-else-if="row.stock < LOW_STOCK_THRESHOLD" class="stock-low">{{ formatNumber(row.stock) }}</span><span v-else>{{ formatNumber(row.stock) }}</span></template></el-table-column>
        <el-table-column label="库存状态" width="110" align="center"><template #default="{ row }"><el-tag v-if="row.stock === 0" type="danger" size="small">售罄</el-tag><el-tag v-else-if="row.stock < LOW_STOCK_THRESHOLD" type="warning" size="small">低库存</el-tag><el-tag v-else type="success" size="small">充足</el-tag></template></el-table-column>
        <el-table-column label="累计销量" width="110" align="right"><template #default="{ row }">{{ formatNumber(row.sales) }}</template></el-table-column>
        <el-table-column label="最近变更" width="160"><template #default="{ row }"><span class="text-xs">{{ formatDateTime(row.updatedAt) }}</span></template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" size="small" :disabled="!hasRole('OPERATOR')" @click="onAdjust(row)">调整</el-button></template></el-table-column>
        <template #empty><el-empty description="没有符合条件的库存记录" :image-size="80" /></template>
      </el-table>
      <div class="pager"><el-pagination :current-page="query.pageNo" :page-size="query.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" background @current-change="((p) => ((query.pageNo = p), load()))" @size-change="((s) => ((query.pageSize = s), (query.pageNo = 1), load()))" /></div>
    </div>

    <el-dialog v-model="adjustVisible" title="调整库存" width="520px" destroy-on-close>
      <el-alert v-if="selectedProduct" :title="selectedProduct.name" :description="`商品 ID：${selectedProduct.id}`" type="info" :closable="false" show-icon />
      <el-skeleton v-if="skuLoading" :rows="3" animated />
      <el-empty v-else-if="!skuOptions.length" description="该商品暂无可调整的 SKU" />
      <el-form v-else label-width="96px" class="adjust-form">
        <el-form-item label="SKU"><el-select v-model="form.skuId" style="width: 100%" @change="loadCurrentStock"><el-option v-for="sku in skuOptions" :key="sku.id" :label="`${sku.skuCode} · ${sku.name}`" :value="sku.id" /></el-select></el-form-item>
        <el-form-item label="当前可售"><span>{{ currentStock === null ? '-' : formatNumber(currentStock) }}</span></el-form-item>
        <el-form-item label="调整数量"><el-input-number v-model="form.delta" :precision="0" :step="1" controls-position="right" style="width: 100%" /><div class="form-help">正数调增，负数调减；调减后不能低于 0 或已锁定库存。</div></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="255" show-word-limit :rows="3" placeholder="填写调整原因，便于流水追溯" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="adjustVisible = false">取消</el-button><el-button type="primary" :loading="adjustLoading" :disabled="!skuOptions.length" @click="submitAdjust">确认调整</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-form { padding: 10px 12px 0; background: #fafafa; border: 1px solid var(--border-color-light); border-radius: 4px; }
.filter-form :deep(.el-form-item) { margin-bottom: 10px; }
.stock-out { color: var(--c-danger); font-weight: 600; }
.stock-low { color: var(--c-warning); font-weight: 600; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
.adjust-form { margin-top: 18px; }
.form-help { color: var(--text-color-secondary); font-size: 12px; line-height: 1.5; margin-top: 5px; }
</style>
