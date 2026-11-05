import { request } from './client'
import { formatPriceRange, formatSales, truncate } from '@/utils/format'
import { inferImageKey, ratioForImageKey, resolveImage } from '@/utils/media'
import { canBuySku } from '@/utils/spec'

function toProductView(raw = {}) {
  const name = String(raw.name || '')
  const imageKey = inferImageKey(name, raw.categoryId)
  const id = raw.id
  return {
    id,
    title: name,
    subtitle: truncate(raw.subtitle || '', 46),
    image: resolveImage(raw.mainImage, imageKey),
    imageKey,
    imageRatio: ratioForImageKey(imageKey),
    priceRange: formatPriceRange(raw.priceMin, raw.priceMax),
    priceMin: Number(raw.priceMin) || 0,
    priceMax: Number(raw.priceMax) || Number(raw.priceMin) || 0,
    salesText: formatSales(raw.sales),
    sales: Number(raw.sales) || 0,
    tag: raw.sales >= 500 ? '热卖' : '',
    detailPath: `/product/${encodeURIComponent(id)}`,
  }
}

function toDetailView(data = {}) {
  const product = data.product || {}
  const imageKey = inferImageKey(product.name, product.categoryId)
  const skus = (Array.isArray(data.skus) ? data.skus : []).map((sku) => ({
    ...sku,
    id: Number(sku.id),
    productId: Number(sku.productId) || Number(product.id),
    price: Number(sku.price) || 0,
    marketPrice: Number(sku.marketPrice) || 0,
    available: sku.available === null || sku.available === undefined
      ? null
      : Number(sku.available),
    status: Number(sku.status),
  }))
  const saleable = skus.filter(canBuySku)
  const lowPrice = saleable.length
    ? Math.min(...saleable.map((sku) => sku.price))
    : Number(product.priceMin) || 0
  const highPrice = saleable.length
    ? Math.max(...saleable.map((sku) => sku.price))
    : Number(product.priceMax) || lowPrice
  return {
    product: {
      ...product,
      id: Number(product.id),
      title: String(product.name || ''),
      subtitle: String(product.subtitle || ''),
      image: resolveImage(product.mainImage, imageKey),
      imageKey,
      imageRatio: ratioForImageKey(imageKey),
      status: Number(product.status),
      sales: Number(product.sales) || 0,
      salesText: formatSales(product.sales),
      priceMin: Number(product.priceMin) || 0,
      priceMax: Number(product.priceMax) || Number(product.priceMin) || 0,
    },
    skus,
    lowPrice,
    highPrice,
    totalAvailable: saleable.reduce(
      (sum, sku) => sum + (sku.available === null ? 0 : sku.available),
      0,
    ),
    hasUnknownStock: saleable.some((sku) => sku.available === null),
  }
}

export async function fetchProductPage({ categoryId, keyword, pageNo = 1, pageSize = 12 } = {}, options = {}) {
  const data = await request('/product/page', {
    query: {
      categoryId,
      keyword,
      pageNo,
      pageSize,
      status: 1,
    },
    ...options,
  })

  const list = Array.isArray(data?.list) ? data.list : []
  return {
    total: Number(data?.total) || 0,
    pageNo: Number(data?.pageNo) || pageNo,
    pageSize: Number(data?.pageSize) || pageSize,
    list: list.filter((item) => item?.status === 1).map(toProductView),
  }
}

export async function fetchCategories(options = {}) {
  const data = await request('/product/category/list', options)
  return Array.isArray(data) ? data : []
}

export async function fetchProductDetail(productId, options = {}) {
  const data = await request(`/product/${encodeURIComponent(productId)}/detail`, options)
  return toDetailView(data)
}

export { toProductView, toDetailView }