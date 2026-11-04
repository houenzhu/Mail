<script setup>
/**
 * 订单管理 —— 后台全量订单（M4 第一个切片：查询）。
 *
 * 结构：状态 Tab → 筛选栏 → 表格 → 分页 → 详情抽屉（含状态流转时间线）
 *
 * 数据口径说明：
 *  - 列表来自 `POST /api/admin/order/page`，BFF 聚合后一次返回件数/数量；
 *  - 详情 `GET /api/admin/order/{orderNo}` 返回明细与状态时间线；
 *  - 发货是唯一写操作（仅 OPERATOR/ADMIN 可见），已支付订单可填写物流信息后发货；
 *    重复提交同一单号幂等，改单号由后端拒绝。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getOrderDetail, pageOrders, shipOrder } from '@/api/order'
import { ORDER_STATUS_TABS, PAY_TYPE_DICT, dictLabel } from '@/constants/dict'
import { useTable } from '@/composables/useTable'
import { usePermission } from '@/composables/usePermission'
import { formatDateTime, formatMoney, formatNumber } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const { hasRole } = usePermission()

const activeTab = ref('all')
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const shipVisible = ref(false)
const shipLoading = ref(false)
const shipForm = reactive({ shippingCompany: '', trackingNo: '' })

const filters = reactive({
  orderNo: '',
  userId: '',
  dateRange: [],
})

/** 把日期选择器的两个值转成后端约定的 yyyy-MM-dd */
function toDateText(value) {
  if (!value) return undefined
  return String(value).slice(0, 10)
}

const initialQuery = computed(() => {
  const tab = ORDER_STATUS_TABS.find((item) => item.key === activeTab.value)
  return {
    orderNo: filters.orderNo || undefined,
    userId: filters.userId || undefined,
    status: tab?.status,
    createdAtStart: toDateText(filters.dateRange?.[0]),
    createdAtEnd: toDateText(filters.dateRange?.[1]),
    pageSize: 10,
  }
})

/**
 * useTable 的通用约定是 `{ total, records }`，而后端统一分页结构 `PageResult` 用的是 `list`
 * （见 mall-common 的 PageResult）。这里做一次适配，保持后端契约统一，
 * 而不是为了前端改后端 DTO 的字段名。
 */
async function fetchOrders(params) {
  const data = await pageOrders(params)
  return {
    total: data?.total ?? 0,
    records: data?.list ?? [],
  }
}

const { query, list, total, loading, load, search, changePage, changePageSize } =
  useTable(fetchOrders, initialQuery.value, { pageSize: 10 })

watch(activeTab, () => {
  const tab = ORDER_STATUS_TABS.find((item) => item.key === activeTab.value)
  query.status = tab?.status
  search()
})

onMounted(() => {
  if (typeof route.query.orderNo === 'string' && route.query.orderNo) {
    filters.orderNo = route.query.orderNo
    query.orderNo = route.query.orderNo
    load()
  }
})

function onSearch() {
  router.replace({ query: filters.orderNo ? { orderNo: filters.orderNo } : {} })
  Object.assign(query, {
    orderNo: filters.orderNo || undefined,
    userId: filters.userId || undefined,
    createdAtStart: toDateText(filters.dateRange?.[0]),
    createdAtEnd: toDateText(filters.dateRange?.[1]),
  })
  search()
}

function onReset() {
  filters.orderNo = ''
  filters.userId = ''
  filters.dateRange = []
  router.replace({ query: {} })
  Object.assign(query, {
    orderNo: undefined,
    userId: undefined,
    createdAtStart: undefined,
    createdAtEnd: undefined,
  })
  search()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getOrderDetail(row.orderNo)
  } catch {
    // 错误提示由 request.js 统一处理
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

/** 只有已支付（20）可以发货；已发货展示物流信息，其余状态不显示按钮。 */
const canShip = computed(() =>
  hasRole('OPERATOR') && detail.value?.order?.status === 20)

function openShip() {
  shipForm.shippingCompany = ''
  shipForm.trackingNo = ''
  shipVisible.value = true
}

async function submitShip() {
  const company = shipForm.shippingCompany.trim()
  const tracking = shipForm.trackingNo.trim()
  if (!company || !tracking) {
    ElMessage.warning('请填写物流公司与物流单号')
    return
  }
  shipLoading.value = true
  try {
    const orderNo = detail.value.order.orderNo
    detail.value = await shipOrder(orderNo, {
      shippingCompany: company,
      trackingNo: tracking,
    })
    shipVisible.value = false
    ElMessage.success('发货成功')
    load()
  } catch {
    // 错误提示由 request.js 统一处理；保持弹窗打开便于修正
  } finally {
    shipLoading.value = false
  }
}


/** 列表行的状态标签颜色：复用字典里的 tag 定义 */
const STATUS_TAG = {
  10: 'warning',
  20: 'success',
  30: 'primary',
  40: 'info',
  50: 'info',
  60: 'danger',
  70: 'danger',
}

function tagOf(status) {
  return STATUS_TAG[status] ?? 'info'
}
</script>

<template>
  <div class="page">
    <div class="page-card">
      <el-tabs v-model="activeTab" class="page-tabs">
        <el-tab-pane
          v-for="tab in ORDER_STATUS_TABS"
          :key="tab.key"
          :label="tab.label"
          :name="tab.key"
        />
      </el-tabs>

      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="订单号">
          <el-input
            v-model="filters.orderNo"
            placeholder="精确匹配订单号"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="买家 ID">
          <el-input
            v-model="filters.userId"
            placeholder="用户 ID"
            clearable
            style="width: 140px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="下单时间">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="page-card">
      <div class="table-head">
        <span class="text-muted text-xs">
          共 {{ formatNumber(total) }} 笔订单
        </span>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column label="订单号" min-width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              {{ row.orderNo }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="买家 ID" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagOf(row.status)" effect="plain" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商品" width="100">
          <template #default="{ row }">
            {{ row.itemCount }} 种 / {{ row.totalQty }} 件
          </template>
        </el-table-column>
        <el-table-column label="应付金额" width="130" align="right">
          <template #default="{ row }">
            <span class="money">{{ formatMoney(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" width="100">
          <template #default="{ row }">
            {{ dictLabel(PAY_TYPE_DICT, row.payType) }}
          </template>
        </el-table-column>
        <el-table-column label="收货人" min-width="150">
          <template #default="{ row }">
            <div>{{ row.receiverName || '-' }}</div>
            <div class="text-muted text-xs">{{ row.receiverPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的订单" />
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

    <el-drawer v-model="detailVisible" title="订单详情" size="560px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="订单号" :span="2">
              {{ detail.order.orderNo }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="tagOf(detail.order.status)" effect="plain" size="small">
                {{ detail.order.statusLabel }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="买家 ID">{{ detail.order.userId }}</el-descriptions-item>
            <el-descriptions-item label="商品总额">
              {{ formatMoney(detail.order.totalAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="优惠金额">
              {{ formatMoney(detail.order.discountAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="应付金额">
              <span class="money">{{ formatMoney(detail.order.payAmount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="支付方式">
              {{ dictLabel(PAY_TYPE_DICT, detail.order.payType) }}
            </el-descriptions-item>
            <el-descriptions-item label="收货人">{{ detail.order.receiverName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.order.receiverPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">
              {{ detail.order.receiverAddress || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="买家备注" :span="2">
              {{ detail.order.remark || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="下单时间">
              {{ formatDateTime(detail.order.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="支付时间">
              {{ formatDateTime(detail.order.paidAt) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.order.cancelledAt" label="取消时间">
              {{ formatDateTime(detail.order.cancelledAt) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.order.cancelReason" label="取消原因">
              {{ detail.order.cancelReason }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.order.shippedAt" label="物流公司">
              {{ detail.order.shippingCompany || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.order.shippedAt" label="物流单号">
              {{ detail.order.trackingNo || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.order.shippedAt" label="发货时间">
              {{ formatDateTime(detail.order.shippedAt) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.order.shippedBy" label="发货人">
              {{ detail.order.shippedBy }}
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="canShip" class="ship-bar">
            <el-button type="primary" @click="openShip">发货</el-button>
          </div>

          <h4 class="section-title">商品明细</h4>
          <el-table :data="detail.items" size="small" border>
            <el-table-column prop="productName" label="商品" min-width="140" />
            <el-table-column prop="skuSpec" label="规格" width="100" />
            <el-table-column label="单价" width="100" align="right">
              <template #default="{ row }">{{ formatMoney(row.price) }}</template>
            </el-table-column>
            <el-table-column prop="qty" label="数量" width="70" align="center" />
            <el-table-column label="小计" width="110" align="right">
              <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
          </el-table>

          <h4 class="section-title">状态流转</h4>
          <el-timeline v-if="detail.logs?.length">
            <el-timeline-item
              v-for="(log, index) in detail.logs"
              :key="index"
              :timestamp="formatDateTime(log.createdAt)"
              placement="top"
            >
              <div class="timeline-title">
                {{ log.fromStatusLabel || '-' }} → {{ log.toStatusLabel || '-' }}
                <el-tag size="small" effect="plain" class="ml-6">{{ log.operator }}</el-tag>
              </div>
              <div v-if="log.remark" class="text-muted text-xs">{{ log.remark }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无状态记录" :image-size="60" />
        </template>
        <el-empty v-else-if="!detailLoading" description="未获取到订单详情" />
      </div>
    </el-drawer>

    <el-dialog v-model="shipVisible" title="订单发货" width="420px">
      <el-form label-width="88px" @submit.prevent>
        <el-form-item label="物流公司" required>
          <el-input v-model="shipForm.shippingCompany" maxlength="64" placeholder="如：顺丰速运" />
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="shipForm.trackingNo" maxlength="64" placeholder="快递单号" @keyup.enter="submitShip" />
        </el-form-item>
      </el-form>
      <p class="text-muted text-xs">
        发货后订单进入「已发货」，物流单号不可修改；重复提交同一单号不会重复发货。
      </p>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipLoading" @click="submitShip">确认发货</el-button>
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

.section-title {
  margin: 18px 0 10px;
  font-size: 14px;
  font-weight: 600;
}

.timeline-title {
  font-size: 13px;
}

.ml-6 {
  margin-left: 6px;
}

.ship-bar {
  margin-top: 12px;
  text-align: right;
}
</style>


