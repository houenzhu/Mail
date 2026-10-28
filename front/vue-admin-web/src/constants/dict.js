/**
 * 状态字典 —— 与《商场微服务项目需求文档》16.5「状态字典（枚举取值总表）」逐条对齐。
 *
 * 约定（对齐文档 16.5）：状态列只存 code，label 与颜色只用于展示；
 * 新增取值必须同时更新需求文档 16.5 与后端枚举类，前端不得自造 label。
 */

/** 商品状态：product.status（0 下架 / 1 上架）+ deleted 逻辑删除 */
export const PRODUCT_STATUS = {
  OFF: 0,
  ON: 1,
}

export const PRODUCT_STATUS_DICT = [
  { value: PRODUCT_STATUS.ON, label: '出售中', tag: 'success' },
  { value: PRODUCT_STATUS.OFF, label: '仓库中', tag: 'info' },
]

/**
 * 商品列表状态 Tab。
 * ⚠️ 千牛截图中还有「草稿 / 回收站 / 违规」三态，我们的 product 表没有对应字段
 * （见计划文档 2.1），因此收敛为 4 个 Tab；deleted 用独立 Tab 表达逻辑删除。
 */
export const PRODUCT_STATUS_TABS = [
  { key: 'all', label: '全部', status: undefined, deleted: 0 },
  { key: 'on', label: '出售中', status: PRODUCT_STATUS.ON, deleted: 0 },
  { key: 'off', label: '仓库中', status: PRODUCT_STATUS.OFF, deleted: 0 },
  { key: 'deleted', label: '已删除', status: undefined, deleted: 1 },
]

/** 订单状态：orders.status（需求文档 16.5） */
export const ORDER_STATUS = {
  PENDING_PAY: 10,
  PAID: 20,
  SHIPPED: 30,
  FINISHED: 40,
  CANCELLED: 50,
  REFUNDING: 60,
  REFUNDED: 70,
}

export const ORDER_STATUS_DICT = [
  { value: 10, label: '待支付', tag: 'warning' },
  { value: 20, label: '已支付', tag: 'success' },
  { value: 30, label: '已发货', tag: 'primary' },
  { value: 40, label: '已完成', tag: 'info' },
  { value: 50, label: '已取消', tag: 'info' },
  { value: 60, label: '退款中', tag: 'danger' },
  { value: 70, label: '已退款', tag: 'danger' },
]

export const ORDER_STATUS_TABS = [
  { key: 'all', label: '全部', status: undefined },
  { key: '10', label: '待支付', status: 10 },
  { key: '20', label: '已支付', status: 20 },
  { key: '30', label: '已发货', status: 30 },
  { key: '40', label: '已完成', status: 40 },
  { key: '50', label: '已取消', status: 50 },
  { key: '60', label: '退款中', status: 60 },
  { key: '70', label: '已退款', status: 70 },
]

/** 支付方式：orders.pay_type（BR-18） */
export const PAY_TYPE_DICT = [
  { value: 0, label: '未支付' },
  { value: 1, label: '微信' },
  { value: 2, label: '支付宝' },
  { value: 3, label: '余额' },
]

/** 库存流水类型：inventory_record.type */
export const INVENTORY_RECORD_TYPE_DICT = [
  { value: 1, label: '扣减', tag: 'danger' },
  { value: 2, label: '回滚', tag: 'success' },
  { value: 3, label: '初始化', tag: 'info' },
  { value: 4, label: '调整', tag: 'warning' },
]

/** 退款状态：refund.status */
export const REFUND_STATUS_DICT = [
  { value: 10, label: '待处理', tag: 'warning' },
  { value: 20, label: '处理中', tag: 'primary' },
  { value: 30, label: '失败', tag: 'danger' },
  { value: 50, label: '已退款', tag: 'success' },
]

/** 支付单状态：payment.status */
export const PAYMENT_STATUS_DICT = [
  { value: 10, label: '待支付', tag: 'warning' },
  { value: 20, label: '支付中', tag: 'primary' },
  { value: 30, label: '支付失败', tag: 'danger' },
  { value: 50, label: '已支付', tag: 'success' },
  { value: 60, label: '已关闭', tag: 'info' },
]

/** 通用：按 value 取字典项 */
export function findDict(dict, value) {
  return dict.find((item) => item.value === value)
}

/** 通用：按 value 取 label（找不到时回退显示原值，避免出现空白） */
export function dictLabel(dict, value) {
  const hit = findDict(dict, value)
  return hit ? hit.label : value === undefined || value === null ? '-' : String(value)
}

/** 通用：按 value 取 Element Plus tag 类型 */
export function dictTag(dict, value) {
  const hit = findDict(dict, value)
  return hit && hit.tag ? hit.tag : 'info'
}
