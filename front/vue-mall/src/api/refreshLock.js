/**
 * 买家端跨标签页刷新互斥锁。
 *
 * 后端刷新令牌一次一换，旧令牌重放会被判为泄露并强制全端下线。
 * 多标签共享 localStorage 但各自持有内存态，必须保证同一时刻只有一个标签真正刷新。
 */

const LOCK_NAME = 'mall_buyer_token_refresh'
const LEASE_KEY = 'mall_buyer_token_refresh_lock'
const LEASE_MS = 20000
const POLL_MS = 50
const ACQUIRE_TIMEOUT_MS = 15000

const TAB_ID =
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function readLease() {
  try {
    return JSON.parse(localStorage.getItem(LEASE_KEY) || 'null')
  } catch {
    return null
  }
}

function tryAcquireLease() {
  const now = Date.now()
  const lease = readLease()
  if (lease && lease.owner !== TAB_ID && lease.expiresAt > now) return false

  localStorage.setItem(LEASE_KEY, JSON.stringify({ owner: TAB_ID, expiresAt: now + LEASE_MS }))
  return readLease()?.owner === TAB_ID
}

function releaseLease() {
  if (readLease()?.owner === TAB_ID) localStorage.removeItem(LEASE_KEY)
}

async function withLeaseLock(task) {
  const deadline = Date.now() + ACQUIRE_TIMEOUT_MS
  while (Date.now() < deadline) {
    if (tryAcquireLease()) {
      try {
        return await task()
      } finally {
        releaseLease()
      }
    }
    await sleep(POLL_MS)
  }
  throw new Error('REFRESH_LOCK_TIMEOUT')
}

/** 优先使用 Web Locks；不支持时退化为带过期时间与归属校验的 localStorage 租约。 */
export function withRefreshLock(task) {
  if (typeof navigator !== 'undefined' && navigator.locks?.request) {
    return navigator.locks.request(LOCK_NAME, { mode: 'exclusive' }, task)
  }
  return withLeaseLock(task)
}