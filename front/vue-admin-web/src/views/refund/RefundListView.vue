<script setup>
/**
 * 退款处理 —— 后台退款单全量视图（M4 第二切片）。
 *
 * 结构：状态 Tab → 筛选栏 → 表格 → 分页 → 详情抽屉
 *
 * 边界说明（BR-13 / 28.7 / 12.24）：
 *  - 本阶段只支持「已支付且未发货」的全额退款，退款申请仍走 C 端 `POST /api/pay/refund`；
 *  - 待审核（0）的退款单由运营/管理员在详情里「同意退款」或「驳回」：
 *    同意 → 10 → 渠道退款 → 20；驳回 → 30，释放幂等键允许买家重新申请。
 *  - 客服（CS）可以查看但不能审批，按钮按角色隐藏；真正的边界在服务端
 *    `@RequireStaff(OPERATOR)`，前端隐藏只是体验。
 *  - 只有 0 显示审批按钮：10/20/30 再点一次没有业务意义，只会产生一次 35006。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { approveRefund, getRefundDetail, pageRefunds, rejectRefund } from '@/api/refund'
import {
  REFUND_OPERATOR_DICT,
  REFUND_STATUS_DICT,
  REFUND_STATUS_TABS,
  dictLabel,
  dictTag,
} from '@/constants/dict'
import { formatDateTime, formatMoney, formatNumber } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'

const route = useRoute()
const { hasRole } = usePermission()

const activeTab = ref('all')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const approving = ref(false)
const rejectVisible = ref(false)
const rejectLoading = ref(false)
const rejectReason = ref('')

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

/** 只有待审核单需要审批，且只有运营/管理员能批钱（ADMIN 覆盖 OPERATOR）。 */
const canAudit = computed(
  () => hasRole('OPERATOR') && detail.value?.refund?.status === 0,
)

/** 同意退款：二次确认后再调渠道；成功后刷新列表与抽屉。 */
async function submitApprove() {
  if (!detail.value?.refund) return
  const refundNo = detail.value.refund.refundNo
  try {
    await ElMessageBox.confirm(
      `确认同意 ${formatMoney(detail.value.refund.amount)} 的全额退款？确认后将立即请求渠道退款。`,
      '同意退款',
      { type: 'warning', confirmButtonText: '同意退款', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  approving.value = true
  try {
    detail.value = await approveRefund(refundNo)
    ElMessage.success(
      detail.value?.refund?.status === 20 ? '退款已完成' : '退款已受理，正在等待渠道结果',
    )
    load()
  } catch {
    // 错误提示由 request.js 统一处理；保持抽屉打开便于看到最新状态
    try { detail.value = await getRefundDetail(refundNo) } catch { /* 忽略回读失败 */ }
  } finally {
    approving.value = false
  }
}

function openReject() {
  rejectReason.value = ''
  rejectVisible.value = true
}

/** 驳回：原因会展示给买家，不能空提交（后端有默认文案但审计价值低）。 */
async function submitReject() {
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写驳回原因，买家会看到这段说明')
    return
  }
  rejectLoading.value = true
  try {
    detail.value = await rejectRefund(detail.value.refund.refundNo, reason)
    rejectVisible.value = false
    ElMessage.success('已驳回退款申请')
    load()
  } catch {
    // 错误提示由 request.js 统一处理；保持弹窗打开便于修正
  } finally {
    rejectLoading.value = false
  }
}

watch(activeTab, search)

onMounted(() => {
  // 看板待办用 `?status=10` 直达退款中 Tab；合法值才生效，非法值回退到全部。
  let tabChanged = false
  if (typeof route.query.status === 'string' && route.query.status) {
    const tab = REFUND_STATUS_TABS.find((item) => item.key === route.query.status)
    if (tab && tab.key !== activeTab.value) {
      activeTab.value = tab.key
      tabChanged = true
    }
  }
  if (typeof route.query.refundNo === 'string' && route.query.refundNo) {
    query.refundNo = route.query.refundNo
  }
  // Tab 变化会触发 watch → search() 加载；避免重复请求。
  if (!tabChanged) load()
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
          共 {{ formatNumber(total) }} 笔退款单 · 仅支持「已支付未发货」全额退款
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
            <el-descriptions-item label="审核人">
              {{ detail.refund.auditedBy || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="审核人 ID">
              {{ detail.refund.auditedById ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="审核时间" :span="2">
              {{ formatDateTime(detail.refund.auditedAt) }}
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
            v-if="detail.refund.status === 0"
            class="tip"
            type="warning"
            :closable="false"
            show-icon
            title="等待审核"
            description="同意后立即发起渠道退款；驳回会释放幂等键，买家可重新申请。"
          />
          <el-alert
            v-else-if="detail.refund.status === 30"
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

          <div v-if="canAudit" class="audit-bar">
            <el-button type="primary" :loading="approving" @click="submitApprove">
              同意退款
            </el-button>
            <el-button type="danger" plain @click="openReject">驳回</el-button>
          </div>
          <el-alert
            v-else-if="detail.refund.status === 0"
            class="tip"
            type="info"
            :closable="false"
            show-icon
            title="当前账号只能查看"
            description="退款审批限定运营/管理员，客服可查看详情但无法批准资金动作。"
          />
        </template>
        <el-empty v-else-if="!detailLoading" description="未获取到退款详情" />
      </div>
    </el-drawer>

    <el-dialog v-model="rejectVisible" title="驳回退款申请" width="420px">
      <el-form label-position="top">
        <el-form-item label="驳回原因">
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="3"
            maxlength="128"
            show-word-limit
            placeholder="填写给买家看的原因，例如：商品已签收，不支持仅退款"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectLoading" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
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

.audit-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.text-danger {
  color: var(--el-color-danger);
}
</style>
