/**
 * UC-ORDER-06 后台验收：代客确认收货（已发货 → 已完成）。
 *
 * 完整走真实链路，不用 SQL 造状态：
 *  1. 通过 C 端接口造一笔「已支付」订单（建单 → 支付 → 内部查单）；
 *  2. 后台发货（`/api/admin/order/{orderNo}/ship`），订单变 30；
 *  3. 打开后台订单详情，断言出现「确认收货」按钮与物流信息；
 *  4. 点击 → 二次确认 → 断言状态变「已完成」、完成时间回显、时间线新增；
 *  5. 再次打开详情，断言「确认收货」按钮已消失（终态不再给动作）。
 *
 * 用法：node scripts/verify-confirm.mjs [baseUrl] [outDir]
 * 依赖：网关 10000、mall-order 8105、mall-pay 8106、mall-admin 8111 与后台 dev server。
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://localhost:5174'
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9340
const USER_DATA = join(tmpdir(), `mall-admin-confirm-${Date.now()}`)
const GATEWAY = 'http://127.0.0.1:10000'
const INTERNAL_TOKEN = process.env.INTERNAL_TOKEN || 'dev-internal-token-please-change'

/* ---------------- 先造一笔已支付并发货的订单 ---------------- */

async function api(path, { method = 'GET', token, body, headers = {} } = {}) {
  const res = await fetch(`${GATEWAY}${path}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json().catch(() => null)
  if (!res.ok) throw new Error(`${path} HTTP ${res.status}: ${JSON.stringify(json)}`)
  if (!json || json.code !== 0) throw new Error(`${path} 业务失败: ${JSON.stringify(json)}`)
  return json.data
}

const buyerLogin = await api('/api/auth/login', {
  method: 'POST',
  body: { username: 'buyer01', password: 'Buyer@123456' },
})
const requestId = `confirm-ui-${Date.now()}`
const order = await api('/api/order', {
  method: 'POST',
  token: buyerLogin.accessToken,
  body: {
    requestId,
    receiverName: '确认收货验收',
    receiverPhone: '13800000000',
    receiverAddress: '上海市浦东新区测试路 2 号',
    remark: '确认收货 UI 验收订单',
    items: [{ skuId: 1001, qty: 1 }],
  },
})
const pay = await api('/api/pay', {
  method: 'POST',
  token: buyerLogin.accessToken,
  body: { orderNo: order.orderNo, payType: 1, requestId: `pay-${requestId}` },
})
const syncRes = await fetch(`http://127.0.0.1:8106/api/pay/internal/sync/${pay.paymentNo}`, {
  method: 'POST',
  headers: { 'X-Gateway-Token': INTERNAL_TOKEN },
})
const syncJson = await syncRes.json()
if (!syncRes.ok || syncJson.code !== 0) {
  throw new Error(`支付查单失败: HTTP ${syncRes.status} ${JSON.stringify(syncJson)}`)
}

const adminLogin = await api('/api/auth/login', {
  method: 'POST',
  body: { username: 'admin', password: 'Admin@123456' },
})
await api(`/api/admin/order/${order.orderNo}/ship`, {
  method: 'POST',
  token: adminLogin.accessToken,
  body: { shippingCompany: '顺丰速运', trackingNo: `SF-CONFIRM-${Date.now()}` },
})
console.log(`已造单并发货: ${order.orderNo}`)

/* ---------------- 无头浏览器 ---------------- */

const CHROME = [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
].find((p) => existsSync(p))

if (!CHROME) {
  console.error('未找到 Chrome/Edge')
  process.exit(2)
}

mkdirSync(OUT_DIR, { recursive: true })

const chrome = spawn(
  CHROME,
  ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${USER_DATA}`,
   '--no-first-run', '--no-default-browser-check', 'about:blank'],
  { stdio: 'ignore' },
)
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

let ws
for (let i = 0; i < 50; i++) {
  try {
    const res = await fetch(`http://127.0.0.1:${PORT}/json/version`)
    const j = await res.json()
    if (j.webSocketDebuggerUrl) { ws = new WebSocket(j.webSocketDebuggerUrl); break }
  } catch {}
  await sleep(200)
}
await new Promise((r) => ws.addEventListener('open', r))

let id = 0
const pend = new Map()
ws.addEventListener('message', (e) => {
  const m = JSON.parse(e.data)
  if (m.id && pend.has(m.id)) {
    const { res, rej } = pend.get(m.id); pend.delete(m.id)
    m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result)
  }
})
const send = (method, params = {}, sessionId) =>
  new Promise((res, rej) => {
    const i = ++id; pend.set(i, { res, rej })
    ws.send(JSON.stringify({ id: i, method, params, ...(sessionId ? { sessionId } : {}) }))
  })

const { targetId } = await send('Target.createTarget', { url: 'about:blank' })
const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true })
const call = (m, p) => send(m, p, sessionId)
await call('Page.enable')
await call('Runtime.enable')

const evaluate = async (expr) => {
  const r = await call('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text)
  return r.result.value
}

async function shot(name) {
  const { data } = await call('Page.captureScreenshot', { format: 'png' })
  writeFileSync(join(OUT_DIR, `${name}.png`), Buffer.from(data, 'base64'))
}

async function fillForm(username, password) {
  return evaluate(`
    (() => {
      const setVal = (el, v) => {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(el, v);
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      };
      const inputs = [...document.querySelectorAll('.el-form input')];
      if (inputs.length < 2) return 'inputs-not-found:' + inputs.length;
      setVal(inputs[0], ${JSON.stringify(username)});
      setVal(inputs[1], ${JSON.stringify(password)});
      return 'filled';
    })()
  `)
}

let failures = 0
function check(name, ok, detail = '') {
  console.log(`  ${ok ? '✅' : '❌'} ${name}${detail ? ' — ' + detail : ''}`)
  if (!ok) failures++
}

async function openOrderDetail(orderNo) {
  await call('Page.navigate', { url: `${BASE_URL}/order/list?orderNo=${orderNo}` })
  await sleep(2800)
  await evaluate(`(() => {
    const btn = document.querySelector('.el-table__body tbody tr td .el-button');
    if (btn) btn.click();
  })()`)
  await sleep(2000)
}

try {
  console.log('代客确认收货验收（UC-ORDER-06）')
  await call('Page.navigate', { url: `${BASE_URL}/login?redirect=/order/list` })
  await sleep(2000)
  console.log('  登录:', await fillForm('admin', 'Admin@123456'))
  await evaluate(`(() => {
    const btn = [...document.querySelectorAll('button')].find(b => b.innerText.replace(/\\s/g,'') === '登录');
    if (btn) btn.click();
  })()`)
  await sleep(3000)

  await openOrderDetail(order.orderNo)
  const before = JSON.parse(await evaluate(`JSON.stringify({
    status: document.querySelector('.el-drawer .el-tag')?.innerText.trim() || '',
    hasConfirm: [...document.querySelectorAll('.el-drawer button')].some(b => b.innerText.trim() === '确认收货'),
    labels: [...document.querySelectorAll('.el-drawer .el-descriptions__label')].map(e => e.innerText.trim()),
    timeline: document.querySelectorAll('.el-drawer .el-timeline-item').length
  })`))
  console.log('  详情:', before)
  await shot('60-order-shipped-detail')
  check('发货后详情状态为「已发货」', before.status === '已发货', before.status)
  check('出现「确认收货」按钮', before.hasConfirm)
  check('物流信息回显', before.labels.includes('物流公司') && before.labels.includes('物流单号'))
  check('时间线包含发货记录', before.timeline >= 3, `${before.timeline} 条`)

  await evaluate(`(() => {
    const btn = [...document.querySelectorAll('.el-drawer button')].find(b => b.innerText.trim() === '确认收货');
    if (btn) btn.click();
  })()`)
  await sleep(900)
  const dialog = JSON.parse(await evaluate(`JSON.stringify({
    visible: !!document.querySelector('.el-message-box'),
    title: document.querySelector('.el-message-box__title')?.innerText.trim() || '',
    text: document.querySelector('.el-message-box__message')?.innerText.trim() || ''
  })`))
  console.log('  二次确认:', dialog)
  await shot('61-confirm-dialog')
  check('点击后弹出二次确认', dialog.visible && dialog.title.includes('代客确认收货'), dialog.title)
  check('确认文案说明不可逆', dialog.text.includes('已完成'), dialog.text)

  await evaluate(`(() => {
    const btn = [...document.querySelectorAll('.el-message-box button')].find(b => b.innerText.trim() === '确认收货');
    if (btn) btn.click();
  })()`)
  await sleep(3000)
  const after = JSON.parse(await evaluate(`JSON.stringify({
    status: document.querySelector('.el-drawer .el-tag')?.innerText.trim() || '',
    hasConfirm: [...document.querySelectorAll('.el-drawer button')].some(b => b.innerText.trim() === '确认收货'),
    labels: [...document.querySelectorAll('.el-drawer .el-descriptions__label')].map(e => e.innerText.trim()),
    timeline: document.querySelectorAll('.el-drawer .el-timeline-item').length,
    operatorTags: [...document.querySelectorAll('.el-drawer .el-timeline-item .el-tag')].map(e => e.innerText.trim())
  })`))
  console.log('  确认后:', after)
  await shot('62-order-completed')
  check('状态变为「已完成」', after.status === '已完成', after.status)
  check('完成时间已回显', after.labels.includes('完成时间'), after.labels.join('/'))
  check('时间线新增确认收货记录', after.timeline > before.timeline, `${before.timeline} → ${after.timeline}`)
  check('确认记录操作人为后台', after.operatorTags.includes('admin'), after.operatorTags.join(','))
  check('终态不再显示确认按钮', !after.hasConfirm)

  console.log('\n  重新进入详情验证终态')
  await openOrderDetail(order.orderNo)
  const reopen = JSON.parse(await evaluate(`JSON.stringify({
    status: document.querySelector('.el-drawer .el-tag')?.innerText.trim() || '',
    hasConfirm: [...document.querySelectorAll('.el-drawer button')].some(b => b.innerText.trim() === '确认收货')
  })`))
  await shot('63-order-completed-reopen')
  check('重新打开仍为已完成', reopen.status === '已完成', reopen.status)
  check('重新打开不显示确认按钮', !reopen.hasConfirm)

  console.log(failures === 0 ? '\n✅ 代客确认收货验收全部通过' : `\n❌ ${failures} 项未通过`)
} catch (e) {
  console.error('验收脚本异常:', e)
  failures++
} finally {
  try { await call('Browser.close') } catch {}
  try { ws.close() } catch {}
  try { chrome.kill() } catch {}
  try { rmSync(USER_DATA, { recursive: true, force: true }) } catch {}
  process.exit(failures === 0 ? 0 : 1)
}
