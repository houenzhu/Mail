import { request } from './client'

/** C 端购物车接口；用户身份由后端从网关身份头解析，前端不传 userId。 */

export function fetchCart(options = {}) {
  return request('/cart', options)
}

export function addToCart({ skuId, quantity }, options = {}) {
  return request('/cart', {
    method: 'POST',
    body: { skuId, quantity },
    ...options,
  })
}

export function updateCart(items, options = {}) {
  return request('/cart', {
    method: 'PUT',
    body: { items },
    ...options,
  })
}

export function selectCart({ skuIds = [], selected }, options = {}) {
  return request('/cart/selection', {
    method: 'PUT',
    body: { skuIds, selected },
    ...options,
  })
}

export function checkoutCart(options = {}) {
  return request('/cart/checkout', {
    method: 'POST',
    ...options,
  })
}

export function removePurchasedCartItems(skuIds, options = {}) {
  return request('/cart/purchased/remove', {
    method: 'POST',
    body: { skuIds },
    ...options,
  })
}

export function removeCartItem(skuId, options = {}) {
  return request(`/cart/${encodeURIComponent(skuId)}`, {
    method: 'DELETE',
    ...options,
  })
}