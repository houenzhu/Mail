/**
 * 演示数据（仅 mock 模式使用）。
 *
 * ⚠️ 数据与 `backend/SCA-Mail/sql/04_seed_data.sql` 的**真实种子保持一致**
 * （3 个商品 / 5 个 SKU / 库存 100·50·200·0·0）。
 * 刻意不编造一套"好看的"商品：演示数据与真机不一致最误导人——
 * 切到真实接口后行数、库存、售罄状态全变，会让人误判成接口有问题。
 *
 * 响应形状对齐 BFF（mall-admin）的 AdminProductPage：
 * { total, pageNo, pageSize, records, statusCounts:{all,on,off,deleted} }
 */

/** 分类（对齐 sql/04_seed_data.sql） */
export const MOCK_CATEGORIES = [
  { id: 1, name: '数码配件', parentId: 0, level: 1, sortNo: 1, status: 1 },
  { id: 2, name: '服饰', parentId: 0, level: 1, sortNo: 2, status: 1 },
  { id: 3, name: '电脑外设', parentId: 0, level: 1, sortNo: 3, status: 1 },
]

/** 品牌（对齐 sql/04_seed_data.sql：种子品牌为 0 号/无品牌） */
export const MOCK_BRANDS = [
  { id: 1, name: '通用品牌', sortNo: 1, status: 1 },
]

/**
 * 商品（SPU）+ 库存聚合视图。
 *
 * 字段与后端 `AdminProductRow` 一一对应（mall-admin），包括 categoryName / brandName 这类
 * 由 BFF 跨服务补齐的字段——真实实现里它们来自 mall-product，前端不再自行拼装。
 */
export const MOCK_PRODUCTS = [
  {
    id: 1,
    name: '无线降噪耳机 Pro',
    subtitle: '40小时续航 / 主动降噪',
    categoryId: 1,
    categoryName: '数码配件',
    brandId: 0,
    brandName: null,
    mainImage: null,
    priceMin: 499.0,
    priceMax: 519.0,
    // SKU 1001(93) + 1002(49)
    stock: 142,
    sales: 0,
    sales30d: null,
    skuCount: 2,
    status: 1,
    deleted: 0,
    createdAt: '2026-09-14 10:00:00',
    updatedAt: '2026-09-19 11:30:00',
  },
  {
    id: 2,
    name: '纯棉圆领T恤',
    subtitle: '夏季新款 / 透气亲肤',
    categoryId: 2,
    categoryName: '服饰',
    brandId: 0,
    brandName: null,
    mainImage: null,
    priceMin: 79.0,
    priceMax: 79.0,
    // SKU 1003(199) + 1004(0)
    stock: 199,
    sales: 0,
    sales30d: null,
    skuCount: 2,
    status: 1,
    deleted: 0,
    createdAt: '2026-09-14 10:00:00',
    updatedAt: '2026-09-19 11:30:00',
  },
  {
    id: 3,
    name: '机械键盘 87键',
    subtitle: '库存为 0，用于验证售罄',
    categoryId: 3,
    categoryName: '电脑外设',
    brandId: 0,
    brandName: null,
    mainImage: null,
    priceMin: 299.0,
    priceMax: 299.0,
    // SKU 1005 库存 0 → 列表应显示"售罄"
    stock: 0,
    sales: 0,
    sales30d: null,
    skuCount: 1,
    status: 1,
    deleted: 0,
    createdAt: '2026-09-14 10:00:00',
    updatedAt: '2026-09-19 11:30:00',
  },
]

/** 单个商品的 SKU（与种子一致：商品 1 两个 SKU、商品 2 两个、商品 3 一个） */
export const MOCK_SKUS = {
  1: [
    { id: 1001, productId: 1, skuCode: 'SKU-1001', name: '默认规格', price: 499.0, marketPrice: 599.0, status: 1 },
    { id: 1002, productId: 1, skuCode: 'SKU-1002', name: '套装版', price: 519.0, marketPrice: 619.0, status: 1 },
  ],
  2: [
    { id: 1003, productId: 2, skuCode: 'SKU-1003', name: '默认规格', price: 79.0, marketPrice: 99.0, status: 1 },
    { id: 1004, productId: 2, skuCode: 'SKU-1004', name: '加大码', price: 79.0, marketPrice: 99.0, status: 1 },
  ],
  3: [
    { id: 1005, productId: 3, skuCode: 'SKU-1005', name: '默认规格', price: 299.0, marketPrice: 359.0, status: 1 },
  ],
}

export function mockProductSkus(productId) {
  return MOCK_SKUS[Number(productId)] ?? []
}

/** 库存（对齐 mall_inventory 种子：1001=100-7、1002=50-1、1003=200-1、1004=0、1005=0） */
export const MOCK_INVENTORY = {
  1001: { skuId: 1001, total: 100, locked: 7, available: 93 },
  1002: { skuId: 1002, total: 50, locked: 1, available: 49 },
  1003: { skuId: 1003, total: 200, locked: 1, available: 199 },
  1004: { skuId: 1004, total: 0, locked: 0, available: 0 },
  1005: { skuId: 1005, total: 0, locked: 0, available: 0 },
}
