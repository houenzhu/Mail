<script setup>
import { onMounted, reactive, ref } from 'vue'

import { pageInventoryRecords } from '@/api/inventory'
import { INVENTORY_RECORD_TYPE_DICT, dictLabel, dictTag } from '@/constants/dict'
import { formatDateTime, formatNumber } from '@/utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ skuId: '', type: undefined, pageNo: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const data = await pageInventoryRecords({
      skuId: query.skuId ? Number(query.skuId) : undefined,
      type: query.type,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    rows.value = data?.records ?? []
    total.value = data?.total ?? 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNo = 1
  load()
}

function reset() {
  query.skuId = ''
  query.type = undefined
  search()
}

function statusLabel(status) {
  return ({ 0: '处理中', 1: '成功', 2: '失败' })[status] ?? status
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-toolbar">
        <div>
          <h2 class="page-title">库存流水</h2>
          <p class="page-subtitle">追溯扣减、回滚和手工调整记录</p>
        </div>
      </div>

      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="SKU ID">
          <el-input v-model="query.skuId" clearable placeholder="精确筛选 SKU" style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="流水类型">
          <el-select v-model="query.type" clearable placeholder="全部类型" style="width: 150px">
            <el-option v-for="item in INVENTORY_RECORD_TYPE_DICT" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe row-key="id">
        <el-table-column prop="id" label="流水 ID" min-width="170" />
        <el-table-column label="SKU" min-width="220">
          <template #default="{ row }">
            <div>{{ row.skuCode || '-' }}</div>
            <div class="text-xs">{{ row.skuName || '-' }}（{{ row.skuId }}）</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }"><el-tag :type="dictTag(INVENTORY_RECORD_TYPE_DICT, row.type)" size="small">{{ dictLabel(INVENTORY_RECORD_TYPE_DICT, row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="right"><template #default="{ row }">{{ formatNumber(row.qty) }}</template></el-table-column>
        <el-table-column label="状态" width="90" align="center"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
        <el-table-column prop="orderNo" label="请求号/订单号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作人" width="140"><template #default="{ row }">{{ row.operatorName || row.operatorId || '-' }}</template></el-table-column>
        <el-table-column label="时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <template #empty><el-empty description="暂无库存流水" :image-size="80" /></template>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNo"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="((page) => ((query.pageNo = page), load()))"
          @size-change="((size) => ((query.pageSize = size), (query.pageNo = 1), load()))"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-form { padding: 10px 12px 0; background: #fafafa; border: 1px solid var(--border-color-light); border-radius: 4px; }
.filter-form :deep(.el-form-item) { margin-bottom: 10px; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
.text-xs { color: var(--text-color-secondary); font-size: 12px; margin-top: 3px; }
</style>
