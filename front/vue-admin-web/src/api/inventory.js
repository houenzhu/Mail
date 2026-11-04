import http from './request'

/** 查询单个 SKU 库存，供调整弹窗回显。 */
export function getInventory(skuId) {
  return http.get(`/admin/inventory/sku/${skuId}`)
}

/** 管理端调整库存，delta 为正数调增、负数调减。 */
export function adjustStock(payload) {
  return http.post('/admin/inventory/adjust', payload)
}

/** 库存流水分页。 */
export function pageInventoryRecords(payload) {
  return http.post('/admin/inventory/record/page', payload)
}
