<script setup>
/**
 * 退款处理 —— 后台退款单全量视图（M4 第二切片）。
 *
 * 结构：状态 Tab → 筛选栏 → 表格 → 分页 → 详情抽屉
 *
 * 边界说明（BR-13 / 28.7）：
 *  - 本阶段只支持「已支付且未发货」的全额退款，退款申请仍走 C 端 `POST /api/pay/refund`；
 *  - 后台这里只做查询与审计，不提供"同意/驳回"按钮——退款审核流属第四阶段，
 *    放一个假按钮比不放更糟。
 *  - 退款失败（30）的 `active_key` 已被置空，买家可重新申请，因此失败单不是终态。
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getRefundDetail, pageRefunds } from '@/api/refund'
import {
  REFUND_OPERATOR_DICT,
  REFUND_STATUS_DICT,
  REFUND_STATUS_TABS,
  dictLabel,
  dictTag,
} from '@/constants/dict'
import { formatDateTime, formatMoney, formatNumber } from '@/utils/format'

const route = useRoute()

const activeTab = ref('all')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const query = reactive({
  refundNo: '',
  orderNo: '',
  userId: '',
  createdAtStart: '',
  createdAtEnd: '',
  pageNo: 1,
  pageSize: 10,
})

function toDateText(value) {
  if (!value) return undefined
  return String(value).slice(0, 10)
}

async function load() {
  loading.value = true
  try {
    const tab = REFUND_STATUS_TABS.find((item) => item.key === activeTab.value)
    const data = await pageRefunds({
      refundNo: query.refundNo || undefined,
      orderNo: query.orderNo || undefined,
      userId: query.userId || undefined,
      status: tab?.status,
      createdAtStart: toDateText(query.createdAtStart),
      createdAtEnd: toDateText(query.createdAtEnd),
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    rows.value = data?.list ?? []
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
  query.refundNo = ''
  query.orderNo = ''
  query.userId = ''
  query.createdAtStart = ''
  query.createdAtEnd = ''
  search()
}

function changePage(page) {
  query.pageNo = page
  load()
}

function changePageSize(size) {
  query.pageSize = size
  query.pageNo = 1
  load()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getRefundDetail(row.refundNo)
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

watch(activeTab, search)

onMounted(() => {
  if (typeof route.query.refundNo === 'string' && route.query.refundNo) {
    query.refundNo = route.query.refundNo
  }
  load()
})
</script>

<template>
  <div class="page">
    <div class="page-card">
      <el-tabs v-model="activeTab" class="page-tabs">
        <el-tab-pane
          v-for="tab in REFUND_STATUS_TABS"
          :key="tab.key"
          :label="tab.label"
          :name="tab.key"
        />
      </el-tabs>

      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="退款单号">
          <el-input
            v-model="query.refundNo"
            placeholder="精确匹配退款单号"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input
            v-model="query.orderNo"
            placeholder="精确匹配订单号"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="买家 ID">
          <el-input
            v-model="query.userId"
            placeholder="用户 ID"
            clearable
            style="width: 130px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="申请时间">
          <el-date-picker
            v-model="query.createdAtStart"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="开始日期"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="至">
          <el-date-picker
            v-model="query.createdAtEnd"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="结束日期"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-card">
      <div class="table-head">
        <span class="text-muted text-xs">
          共 {{ formatNumber(total) }} 笔退款单 · 本阶段仅支持「已支付未发货」全额退款
        </span>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column label="退款单号" min-width="190">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              {{ row.refundNo }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="userId" label="买家 ID" width="110" />
        <el-table-column label="退款金额" width="120" align="right">
          <template #default="{ row }">
            <span class="money">{{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="dictTag(REFUND_STATUS_DICT, row.status)" effect="plain" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起方" width="100">
          <template #default="{ row }">
            {{ dictLabel(REFUND_OPERATOR_DICT, row.operator) }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="退款原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的退款单" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNo"
          :page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="changePage"
          @size-change="changePageSize"
        />
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="退款详情" size="520px">
      <div v-loading="detailLoading">
        <template v-if="detail?.refund">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="退款单号" :span="2">
              {{ detail.refund.refundNo }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag
                :type="dictTag(REFUND_STATUS_DICT, detail.refund.status)"
                effect="plain"
                size="small"
              >
                {{ detail.refund.statusLabel }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="退款金额">
              <span class="money">{{ formatMoney(detail.refund.amount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="订单号" :span="2">
              {{ detail.refund.orderNo }}
            </el-descriptions-item>
            <el-descriptions-item label="支付单号" :span="2">
              {{ detail.refund.paymentNo }}
            </el-descriptions-item>
            <el-descriptions-item label="买家 ID">{{ detail.refund.userId }}</el-descriptions-item>
            <el-descriptions-item label="发起方">
              {{ dictLabel(REFUND_OPERATOR_DICT, detail.refund.operator) }}
            </el-descriptions-item>
            <el-descriptions-item label="操作人 ID">
              {{ detail.refund.operatorId ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="渠道退款单号">
              {{ detail.refund.channelRefundNo || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="退款原因" :span="2">
              {{ detail.refund.reason || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.refund.failReason" label="失败原因" :span="2">
              <span class="text-danger">{{ detail.refund.failReason }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="申请时间">
              {{ formatDateTime(detail.refund.appliedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="完成时间">
              {{ formatDateTime(detail.refund.finishedAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="detail.refund.status === 30"
            class="tip"
            type="warning"
            :closable="false"
            show-icon
            title="退款失败后买家可重新申请"
            description="失败单的幂等键已释放，同一支付单允许再建一张退款单。"
          />
          <el-alert
            v-else-if="detail.refund.status === 20"
            class="tip"
            type="success"
            :closable="false"
            show-icon
            title="退款已完成"
            description="支付单已置为「已退款」，订单已流转到「已退款」。"
          />
        </template>
        <el-empty v-else-if="!detailLoading" description="未获取到退款详情" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.money {
  font-weight: 600;
  color: var(--el-color-danger);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.tip {
  margin-top: 14px;
}

.text-danger {
  color: var(--el-color-danger);
}
</style>
