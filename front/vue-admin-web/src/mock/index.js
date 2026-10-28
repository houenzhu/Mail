/**
 * mock 层：提供可选的部分离线演示数据。当前真实管理接口已实现一部分，开发默认关闭 mock。
 *
 * 设计要点：
 *  - mock 开启时，认证接口也由本地演示账号处理，不会请求真实 mall-auth。
 *  - 覆盖登录、部分商品列表/新增/上下架/删除和字典读取场景；没有覆盖的接口会显式失败。
 *  - 返回体一律是后端同形的 `Result{code,message,data}`，因此 request.js 的解包逻辑
 *    在 mock 与真实模式下走的是同一条路径（不会出现"mock 通过、真机失败"）。
 */

import {
  MOCK_BRANDS,
  MOCK_CATEGORIES,
  MOCK_INVENTORY,
  MOCK_PRODUCTS,
  MOCK_SKUS,
} from './data'

const USE_MOCK = String(import.meta.env.VITE_USE_MOCK ?? 'true') === 'true'

export function getUseMock() {
  return USE_MOCK
}

/** 演示账号（对应 sql/07_seed_user_auth.sql 的种子账号与口令） */
const DEMO_ACCOUNTS = {
  admin: { password: 'Admin@123456', userId: 10001, nickname: '超级管理员', roles: ['ADMIN'] },
  operator01: { password: 'Operator@123456', userId: 10003, nickname: '运营小张', roles: ['OPERATOR'] },
  buyer01: { password: 'Buyer@123456', userId: 10002, nickname: '买家小李', roles: ['USER'] },
}

const TOKEN_PREFIX = 'mock-access-token'
const REFRESH_PREFIX = 'mock-refresh-token'

/**
 * 令牌 → 用户名 的映射。
 *
 * ⚠️ 不要从令牌字符串里"解析"用户名：不透明令牌按定义就是不可解析的
 * （真实 Sa-Token 令牌是 UUID，更解析不出东西）。用一张映射表模拟服务端会话，
 * 才和真实语义一致，也不会因为用户名/前缀里出现 `-` 而解析错。
 */
const TOKEN_OWNER_KEY = 'mall_admin_mock_token_owner'

function readTokenOwnerMap() {
  try {
    return JSON.parse(localStorage.getItem(TOKEN_OWNER_KEY) || '{}')
  } catch {
    return {}
  }
}

function rememberToken(accessToken, refreshToken, username) {
  const map = readTokenOwnerMap()
  if (accessToken) map[accessToken] = username
  if (refreshToken) map[refreshToken] = username
  // 只保留最近 50 条，避免 localStorage 无限增长
  const keys = Object.keys(map)
  if (keys.length > 50) {
    keys.slice(0, keys.length - 50).forEach((key) => delete map[key])
  }
  localStorage.setItem(TOKEN_OWNER_KEY, JSON.stringify(map))
}

function resolveUsername(token) {
  if (!token) return ''
  return readTokenOwnerMap()[token] || ''
}

function ok(data, message = '成功') {
  return { code: 0, message, data }
}

function fail(code, message) {
  return { code, message, data: null }
}

/* ---------------- 入口 ---------------- */

export async function mockRequest({ url, method, data, params }) {
  const path = normalize(url)
  const verb = (method || 'GET').toUpperCase()

  await delay()

  // ---- 认证类：mock 模式下也走本地（真实模式由 VITE_USE_MOCK=false 控制）----
  if (path === '/auth/login' && verb === 'POST') return mockLogin(data)
  if (path === '/auth/refresh' && verb === 'POST') return mockRefresh(data)
  if (path === '/auth/logout' && verb === 'POST') return ok(null, '已退出登录')
  if (path === '/auth/me' && verb === 'GET') return mockMe()

  // ---- 管理端：只提供部分演示接口；页面当前使用 POST 分页，本 mock 仍是旧 GET 契约 ----
  if (path === '/admin/product/page' && verb === 'GET') return mockProductPage(params)
  if (path === '/admin/product' && verb === 'POST') return mockCreateProduct(data)
  if (path === '/admin/category/list' && verb === 'GET') return ok(MOCK_CATEGORIES)
  if (path === '/admin/brand/list' && verb === 'GET') return ok(MOCK_BRANDS)
  if (path === '/admin/product/sku' && verb === 'GET') {
    return ok(MOCK_SKUS[Number(params?.productId)] ?? [])
  }
  if (path === '/admin/inventory/batch' && verb === 'POST') {
    const ids = data?.skuIds ?? []
    const inventory = Object.fromEntries(ids.map((id) => [id, MOCK_INVENTORY[id]]).filter(([, v]) => v))
    return ok(inventory)
  }

  const statusMatch = path.match(/^\/admin\/product\/(\d+)\/status$/)
  if (statusMatch && verb === 'PUT') {
    return mockUpdateStatus(Number(statusMatch[1]), params?.status)
  }

  const removeMatch = path.match(/^\/admin\/product\/(\d+)$/)
  if (removeMatch && verb === 'DELETE') {
    return mockDeleteProduct(Number(removeMatch[1]))
  }

  // 未覆盖的管理端接口：明确报错而不是静默返回空，避免"看起来成功了"
  if (path.startsWith('/admin/')) {
    return fail(50001, `管理端接口尚未实现：${verb} ${path}`)
  }

  return fail(50001, `未覆盖的请求：${verb} ${path}`)
}

/* ---------------- 认证 ---------------- */

function mockLogin({ username, password }) {
  const account = DEMO_ACCOUNTS[username]
  if (!account || account.password !== password) {
    return fail(31004, '账号或密码错误')
  }
  if (!account.roles.some((role) => ['OPERATOR', 'CS', 'ADMIN'].includes(role))) {
    return fail(20003, '该账号无后台访问权限')
  }
  const stamp = Date.now()
  const accessToken = `${TOKEN_PREFIX}-${stamp}`
  const refreshToken = `${REFRESH_PREFIX}-${stamp}`
  rememberToken(accessToken, refreshToken, username)
  return ok({
    userId: account.userId,
    username,
    nickname: account.nickname,
    roles: account.roles,
    tokenType: 'Bearer',
    accessToken,
    expiresIn: 300,
    refreshToken,
    refreshExpiresIn: 604800,
  })
}

function mockRefresh({ refreshToken }) {
  const username = resolveUsername(refreshToken)
  const account = DEMO_ACCOUNTS[username]
  if (!account) return fail(20002, '登录已过期，请重新登录')
  const stamp = Date.now()
  const newAccess = `${TOKEN_PREFIX}-${stamp}`
  const newRefresh = `${REFRESH_PREFIX}-${stamp}`
  // 一次一换：刷新令牌每次都变（对齐 21.9.3 的旋转语义）
  rememberToken(newAccess, newRefresh, username)
  return ok({
    userId: account.userId,
    username,
    nickname: account.nickname,
    roles: account.roles,
    tokenType: 'Bearer',
    accessToken: newAccess,
    expiresIn: 300,
    refreshToken: newRefresh,
    refreshExpiresIn: 604800,
  })
}

function mockMe() {
  const username = currentUsername()
  const account = DEMO_ACCOUNTS[username]
  if (!account) return fail(20001, '令牌无效或已失效')
  return ok({
    userId: account.userId,
    username,
    nickname: account.nickname,
    phone: '138****0000',
    roles: account.roles,
    status: 1,
    lastLoginAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
  })
}

function currentUsername() {
  const raw = localStorage.getItem('mall_admin_access_token') || ''
  // 调试脚本可能给令牌加了失效后缀，先去掉再查表
  const token = raw.replace('__expired__', '')
  return resolveUsername(token)
}

/* ---------------- 管理端：商品 ---------------- */

function mockProductPage(params = {}) {
  const {
    keyword,
    productId,
    categoryId,
    brandId,
    status,
    deleted = 0,
    pageNo = 1,
    pageSize = 10,
  } = params

  let list = MOCK_PRODUCTS.filter((item) => Number(item.deleted) === Number(deleted))

  if (status !== undefined && status !== null && status !== '') {
    list = list.filter((item) => Number(item.status) === Number(status))
  }
  if (categoryId) {
    list = list.filter((item) => Number(item.categoryId) === Number(categoryId))
  }
  if (brandId) {
    list = list.filter((item) => Number(item.brandId) === Number(brandId))
  }
  if (productId) {
    list = list.filter((item) => String(item.id).includes(String(productId)))
  }
  if (keyword) {
    const kw = String(keyword).toLowerCase()
    list = list.filter(
      (item) =>
        item.name.toLowerCase().includes(kw) ||
        (item.subtitle || '').toLowerCase().includes(kw),
    )
  }

  const total = list.length
  const page = Number(pageNo) || 1
  const size = Number(pageSize) || 10
  const records = list.slice((page - 1) * size, page * size)

  // 响应形状严格对齐后端 BFF 的 AdminProductPage（mall-admin 的 AdminProductPage record）：
  // { total, pageNo, pageSize, records, statusCounts: { all, on, off, deleted } }
  // 这样从 mock 切到真实接口时，页面代码一行都不用改。
  return ok({
    total,
    pageNo: page,
    pageSize: size,
    records,
    statusCounts: countByStatus(),
  })
}

/** Tab 上的角标数字（全部 / 出售中 / 仓库中 / 已删除） */
function countByStatus() {
  const alive = MOCK_PRODUCTS.filter((item) => Number(item.deleted) === 0)
  return {
    all: alive.length,
    on: alive.filter((item) => Number(item.status) === 1).length,
    off: alive.filter((item) => Number(item.status) === 0).length,
    deleted: MOCK_PRODUCTS.filter((item) => Number(item.deleted) === 1).length,
  }
}

function mockUpdateStatus(productId, status) {
  const product = MOCK_PRODUCTS.find((item) => item.id === productId)
  if (!product) return fail(32001, '商品不存在')
  product.status = Number(status)
  product.updatedAt = nowText()
  return ok(null, Number(status) === 1 ? '上架成功' : '下架成功')
}

/**
 * 新增商品（POST /admin/product）。
 * 刻意保留后端的两条真实约束，便于前端提前对接错误分支：
 *  - SKU 编码唯一 → 32004
 *  - 库存初始化失败 → 整单失败（这里用 50001 模拟下游不可用）
 */
function mockCreateProduct(payload = {}) {
  const { name, categoryId, skus = [] } = payload
  if (!name || !categoryId) return fail(10002, '必填字段缺失：商品名称与分类')
  if (!skus.length) return fail(10002, '至少需要一个 SKU')

  const existingCodes = new Set()
  Object.values(MOCK_SKUS).forEach((list) => list.forEach((sku) => existingCodes.add(sku.skuCode)))
  const duplicated = skus.find((sku) => existingCodes.has(sku.skuCode))
  if (duplicated) return fail(32004, `SKU 编码已存在：${duplicated.skuCode}`)

  const prices = skus.map((sku) => Number(sku.price) || 0)
  const nextId = Math.max(...MOCK_PRODUCTS.map((p) => p.id)) + 1
  const category = MOCK_CATEGORIES.find((c) => c.id === Number(categoryId))

  MOCK_PRODUCTS.unshift({
    id: nextId,
    name,
    subtitle: payload.subtitle || '',
    categoryId: Number(categoryId),
    categoryName: category?.name || null,
    brandId: Number(payload.brandId) || 0,
    brandName: null,
    mainImage: payload.mainImage || null,
    priceMin: Math.min(...prices),
    priceMax: Math.max(...prices),
    stock: skus.reduce((sum, sku) => sum + (Number(sku.stock) || 0), 0),
    sales: 0,
    sales30d: null,
    skuCount: skus.length,
    status: 0,
    deleted: 0,
    createdAt: nowText(),
    updatedAt: nowText(),
  })

  return ok(nextId, '发布成功（新商品默认为仓库中，需手动上架）')
}

function mockDeleteProduct(productId) {
  const product = MOCK_PRODUCTS.find((item) => item.id === productId)
  if (!product) return fail(32001, '商品不存在')
  product.deleted = 1
  product.updatedAt = nowText()
  return ok(null, '已删除')
}

/* ---------------- 工具 ---------------- */

function normalize(url) {
  return String(url || '').replace(/^\/api/, '')
}

function delay(ms = 180) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function nowText() {
  return new Date().toISOString().slice(0, 19).replace('T', ' ')
}
