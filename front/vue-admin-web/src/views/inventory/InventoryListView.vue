<script setup>
/**
 * 库存管理。
 *
 * 数据来源：mock 下由商品聚合视图充当（真实实现需 mall-admin 聚合 mall-product + mall-inventory，
 * 因为两者是**独立的库**，禁止跨库 JOIN —— 见计划文档 8.2 / G-06）。
 * 「调整库存」按钮已置灰并说明原因：后端缺少写流水接口（G-07 / UC-ADMIN-01）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { pageProducts } from '@/api/product'
import { usePermission } from '@/composables/usePermission'
import { formatDateTime, formatNumber } from '@/utils/format'

const { hasRole } = usePermission()

/** 低库存阈值（仅用于列表高亮，非业务规则） */
const LOW_STOCK_THRESHOLD = 50

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({
  keyword: '',
  onlyLow: false,
  pageNo: 1,
  pageSize: 10,
})

async function load() {
  loading.value = true
  try {
    const data = await pageProducts({
      keyword: query.keyword || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      deleted: 0,
    })
    let records = data?.records ?? []
    if (query.onlyLow) {
      records = records.filter((item) => Number(item.stock) < LOW_STOCK_THRESHOLD)
    }
    rows.value = records
    total.value = data?.total ?? 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onSearch() {
  query.pageNo = 1
  load()
}

function onAdjust() {
  ElMessage.warning('调整库存接口尚未实现（需写 inventory_record 流水，见计划文档 G-07）')
}
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-toolbar">
        <h2 class="page-title">库存管理</h2>
        <el-tooltip content="后端缺少写流水的调整接口（G-07）" placement="bottom">
          <span>
            <el-button type="primary" disabled :icon="'Plus'" @click="onAdjust">
              调整库存（待建）
            </el-button>
          </span>
        </el-tooltip>
      </div>

      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="商品名称">
          <el-input
            v-model="query.keyword"
            placeholder="按商品名称搜索"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.onlyLow" @change="onSearch">
            只看低库存（&lt; {{ LOW_STOCK_THRESHOLD }}）
          </el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="((query.keyword = ''), (query.onlyLow = false), onSearch())">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe row-key="id">
        <el-table-column label="商品" min-width="280">
          <template #default="{ row }">
            <div class="goods-cell">
              <img v-if="row.mainImage" class="goods-thumb" :src="row.mainImage" :alt="row.name" />
              <div v-else class="goods-thumb-fallback">{{ row.name.slice(0, 1) }}</div>
              <div class="goods-meta">
                <div class="goods-name" :title="row.name">{{ row.name }}</div>
                <div class="goods-sub">ID:{{ row.id }} · {{ row.skuCount }} 个 SKU</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="可售库存" width="120" align="right">
          <template #default="{ row }">
            <span v-if="row.stock === 0" class="stock-out">0</span>
            <span v-else-if="row.stock < LOW_STOCK_THRESHOLD" class="stock-low">
              {{ formatNumber(row.stock) }}
            </span>
            <span v-else>{{ formatNumber(row.stock) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="库存状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.stock === 0" type="danger" size="small">售罄</el-tag>
            <el-tag v-else-if="row.stock < LOW_STOCK_THRESHOLD" type="warning" size="small">低库存</el-tag>
            <el-tag v-else type="success" size="small">充足</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="累计销量" width="110" align="right">
          <template #default="{ row }">{{ formatNumber(row.sales) }}</template>
        </el-table-column>

        <el-table-column label="最近变更" width="160">
          <template #default="{ row }">
            <span class="text-xs">{{ formatDateTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right">
          <template #default>
            <el-button link type="primary" size="small" disabled>调整</el-button>
            <el-button link type="primary" size="small" disabled>流水</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="没有符合条件的库存记录" :image-size="80" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNo"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="((p) => ((query.pageNo = p), load()))"
          @size-change="((s) => ((query.pageSize = s), (query.pageNo = 1), load()))"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-form {
  padding: 10px 12px 0;
  background: #fafafa;
  border: 1px solid var(--border-color-light);
  border-radius: 4px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.stock-out {
  color: var(--c-danger);
  font-weight: 600;
}

.stock-low {
  color: var(--c-warning);
  font-weight: 600;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
