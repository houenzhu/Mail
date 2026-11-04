/**
 * 跨标签页刷新互斥锁。
 *
 * 后端刷新令牌一次一换，重放旧令牌会被判为泄露并**强制全端下线**。
 * 同一浏览器打开多个后台标签页时，它们共享 localStorage，但各自持有内存态；
 * 若同时过期并各自发起 refresh，第二个请求必然携带已旋转的旧令牌 → 全员被踢。
 *
 * 因此刷新必须进入跨标签互斥区：同一时刻只有一个标签真正请求 `/auth/refresh`，
 * 其余标签等锁释放后直接复用 localStorage 里的新令牌，不再发请求。
 *
 * 优先使用 Web Locks API（浏览器原生跨标签互斥，Chrome/Edge 均支持）；
 * 不支持时退化为带过期时间与归属校验的 localStorage 租约锁。
 */

const LOCK_NAME = 'mall_admin_token_refresh'
const LEASE_KEY = 'mall_admin_token_refresh_lock'
const LEASE_MS = 20000
const POLL_MS = 50
const ACQUIRE_TIMEOUT_MS = 15000

/** 每个标签页一个随机身份，用于租约锁的归属校验（避免误删别人的锁） */
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
  // 回读确认：两个标签同时写入时，只有最后一个写入者继续持有锁
  return readLease()?.owner === TAB_ID
}

function releaseLease() {
  if (readLease()?.owner === TAB_ID) {
    localStorage.removeItem(LEASE_KEY)
  }
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

/**
 * 在跨标签互斥区内执行刷新动作。
 * 回调内部应重新读取令牌，确认没有别的标签已经完成刷新。
 */
export function withRefreshLock(task) {
  if (typeof navigator !== 'undefined' && navigator.locks?.request) {
    return navigator.locks.request(LOCK_NAME, { mode: 'exclusive' }, task)
  }
  return withLeaseLock(task)
}
