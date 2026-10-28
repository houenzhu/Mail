import http from './request'

/**
 * 商品管理接口 —— 由 mall-admin(8111) 的 BFF 提供（对外统一 `/api/admin/**`）。
 *
 * 契约与后端一一对应：
 *  - 分页用 POST + body（筛选条件多且大多可选，body 比一长串 query 更清晰）；
 *  - 上下架 / 删除的角色校验在 mall-product 的 `@RequireStaff(OPERATOR)`，
 *    BFF 只透传身份头，因此这里无需前端再做角色判断。
 */

/**
 * 管理端商品分页。
 * @param {object} params 支持 keyword / productId / categoryId / brandId / status / deleted / pageNo / pageSize
 * @returns {Promise<{total,pageNo,pageSize,records,statusCounts}>}
 */
export function pageProducts(params) {
  return http.post('/admin/product/page', params)
}

/** 商品 SKU 列表（回显用） */
export function listSkus(productId) {
  return http.get('/admin/product/sku', { params: { productId } })
}

/**
 * 商品详情（编辑回显）：商品主体 + 真实 SKU 列表。
 * @returns {Promise<{product, skus}>}
 */
export function getProductDetail(productId) {
  return http.get(`/admin/product/${productId}`)
}

/**
 * 新增商品。
 * @param {object} payload {name,subtitle,categoryId,brandId,mainImage,detail,status,skus[]}
 *   skus[]: {skuId?, skuCode, name, price, marketPrice, stock}（新增时 skuId 省略）
 * @returns {Promise<number>} 新商品 ID
 */
export function createProduct(payload) {
  return http.post('/admin/product', payload)
}

/** 编辑商品：SKU 差量提交（带 skuId 的更新、不带的新增、未出现的被软删） */
export function updateProduct(productId, payload) {
  return http.put(`/admin/product/${productId}`, payload)
}

/** 上下架：status 0 下架 / 1 上架 */
export function updateProductStatus(productId, status) {
  return http.put(`/admin/product/${productId}/status`, null, { params: { status } })
}

/** 逻辑删除 */
export function deleteProduct(productId) {
  return http.delete(`/admin/product/${productId}`)
}

/** 分类列表（发布商品的必填下拉） */
export function listCategories() {
  return http.get('/admin/category/list')
}

/** 品牌列表 */
export function listBrands() {
  return http.get('/admin/brand/list')
}

/** 新增分类（parentId 省略或 0 表示顶级；层级由后端按父分类推导） */
export function createCategory(payload) {
  return http.post('/admin/category', payload)
}

/** 编辑分类 */
export function updateCategory(categoryId, payload) {
  return http.put(`/admin/category/${categoryId}`, payload)
}

/** 删除分类（有子分类或商品引用时后端拒绝，message 可直接展示） */
export function deleteCategory(categoryId) {
  return http.delete(`/admin/category/${categoryId}`)
}

/** 新增品牌 */
export function createBrand(payload) {
  return http.post('/admin/brand', payload)
}

/** 编辑品牌 */
export function updateBrand(brandId, payload) {
  return http.put(`/admin/brand/${brandId}`, payload)
}

/** 删除品牌（有商品引用时后端拒绝） */
export function deleteBrand(brandId) {
  return http.delete(`/admin/brand/${brandId}`)
}
