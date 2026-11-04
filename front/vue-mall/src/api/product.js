import { request } from './client'
import { formatPriceRange, formatSales, truncate } from '@/utils/format'
import { inferImageKey, ratioForImageKey, resolveImage } from '@/utils/media'

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
    tag: raw.sales >= 500 ? '热卖' : '',
    detailPath: `/feature/product-detail?id=${encodeURIComponent(id)}`,
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
  return data
}

export { toProductView }
