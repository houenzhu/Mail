import { request } from './client'

/** 收货地址簿（UC-USER-02）；归属由后端从网关身份头解析，前端不传 userId。 */

export function fetchAddresses(options = {}) {
  return request('/user/address', options)
}

export function createAddress(payload, options = {}) {
  return request('/user/address', { method: 'POST', body: payload, ...options })
}

export function updateAddress(id, payload, options = {}) {
  return request(`/user/address/${encodeURIComponent(id)}`, { method: 'PUT', body: payload, ...options })
}

export function deleteAddress(id, options = {}) {
  return request(`/user/address/${encodeURIComponent(id)}`, { method: 'DELETE', ...options })
}

export function setDefaultAddress(id, options = {}) {
  return request(`/user/address/${encodeURIComponent(id)}/default`, { method: 'PUT', ...options })
}
