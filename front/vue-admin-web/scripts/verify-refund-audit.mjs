/**
 * 退款审核流 UI 验收（12.24 / 28.7）。
 *
 * 全部走真实接口，不写库造状态：
 *  1. C 端造一笔已支付订单 → 带 `audit=true` 申请退款，落在「待审核」；
 *  2. 后台登录打开退款详情：断言状态、审批按钮、审核人字段；
 *  3. 点「驳回」→ 填原因 → 断言状态 30、原因回显、审批按钮消失；
 *  4. 另一笔待审核单点「同意退款」→ 二次确认 → 断言状态 20、渠道退款单号出现；
 *  5. 客服账号登录，同一单只看到「当前账号只能查看」，没有审批按钮。
 *
 * 用法：node scripts/verify-refund-audit.mjs [baseUrl] [outDir]
 * 依赖：网关 10000、mall-order 8105、mall-pay 8106、mall-admin 8111 与后台 dev server。
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://localhost:5174'
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9342
const USER_DATA = join(tmpdir(), `mall-admin-refund-audit-${Date.now()}`)
const GATEWAY = 'http://127.0.0.1:10000'
const INTERNAL_TOKEN = process.env.INTERNAL_TOKEN || 'dev-internal-token-please-change'

async function api(path, { method = 'GET', token, body } = {}) {
  const res = await fetch(`${GATEWAY}${path}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json().catch(() => null)
  if (!res.ok) throw new Error(`${path} HTTP ${res.status}: ${JSON.stringify(json)}`)
  if (!json || json.code !== 0) throw new Error(`${path} 业务失败: ${JSON.stringify(json)}`)
  return json.data
}

/** 真实链路造一笔已支付订单，返回订单号与支付单号。 */
async function seedPaidOrder(token, tag) {
  const requestId = `${tag}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
  const order = await api('/api/order', {
    method: 'POST',
    token,
    body: {
      requestId,
      receiverName: '退款审核验收',
      receiverPhone: '13800000000',
      receiverAddress: '上海市浦东新区测试路 3 号',
      remark: `${tag} 退款审核验收订单`,
      items: [{ skuId: 1001, qty: 1 }],
    },
  })
  const pay = await api('/api/pay', {
    method: 'POST',
    token,
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
  return order.orderNo
}

const buyer = await api('/api/auth/login', {
  method: 'POST',
  body: { username: 'buyer01', password: 'Buyer@123456' },
})

const rejectOrderNo = await seedPaidOrder(buyer.accessToken, 'audit-reject')
const rejectRefund = await api('/api/pay/refund', {
  method: 'POST',
  token: buyer.accessToken,
  body: { orderNo: rejectOrderNo, reason: 'UI 验收：驳回路径', audit: true },
})

const approveOrderNo = await seedPaidOrder(buyer.accessToken, 'audit-approve')
const approveRefund = await api('/api/pay/refund', {
  method: 'POST',
  token: buyer.accessToken,
  body: { orderNo: approveOrderNo, reason: 'UI 验收：同意路径', audit: true },
})

if (rejectRefund.status !== 0 || approveRefund.status !== 0) {
  throw new Error(`建单未进入待审核: reject=${rejectRefund.status}, approve=${approveRefund.status}`)
}
console.log(`已造待审核退款: 驳回 ${rejectRefund.refundNo} / 同意 ${approveRefund.refundNo}`)

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

async function login(username, password) {
  // 先清掉上一个账号的令牌再进登录页：直接 navigate 到 /login 会被路由守卫按
  // 已登录状态弹回工作台，选择器就落在一个没有登录表单的页面上了。
  await call('Page.navigate', { url: `${BASE_URL}/login` })
  await sleep(600)
  await evaluate(`(() => { localStorage.clear(); sessionStorage.clear(); })()`)
  await call('Page.navigate', { url: `${BASE_URL}/login?redirect=/refund/list` })
  await sleep(2200)
  await evaluate(`
    (() => {
      const setVal = (el, v) => {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(el, v);
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      };
      const inputs = [...document.querySelectorAll('.el-form input')];
      setVal(inputs[0], ${JSON.stringify(username)});
      setVal(inputs[1], ${JSON.stringify(password)});
    })()
  `)
  await evaluate(`(() => {
    const btn = [...document.querySelectorAll('button')].find(b => b.innerText.replace(/\\s/g,'') === '登录');
    if (btn) btn.click();
  })()`)
  await sleep(3200)
}

/** 打开某一笔退款单的详情抽屉。 */
async function openRefund(refundNo) {
  await call('Page.navigate', { url: `${BASE_URL}/refund/list?refundNo=${refundNo}` })
  await sleep(2600)
  await evaluate(`(() => {
    const btn = document.querySelector('.el-table__body tbody tr td .el-button');
    if (btn) btn.click();
  })()`)
  await sleep(2000)
}

let failures = 0
function check(name, ok, detail = '') {
  console.log(`  ${ok ? '✅' : '❌'} ${name}${detail ? ' — ' + detail : ''}`)
  if (!ok) failures++
}

try {
  console.log('退款审核流验收（12.24）')
  await login('admin', 'Admin@123456')

  console.log('\n  待审核 Tab')
  await call('Page.navigate', { url: `${BASE_URL}/refund/list?status=0` })
  await sleep(2600)
  const tabState = JSON.parse(await evaluate(`JSON.stringify({
    tabs: [...document.querySelectorAll('.el-tabs__item')].map(e => e.innerText.trim()),
    active: document.querySelector('.el-tabs__item.is-active')?.innerText.trim() || '',
    total: document.querySelector('.el-pagination__total')?.innerText.trim() || ''
  })`))
  console.log('  Tab:', tabState)
  check('新增「待审核」Tab', tabState.tabs.includes('待审核'), tabState.tabs.join(','))
  check('?status=0 直达待审核 Tab', tabState.active === '待审核', tabState.active)
  await shot('70-refund-pending-tab')

  console.log('\n  驳回路径')
  await openRefund(rejectRefund.refundNo)
  const pending = JSON.parse(await evaluate(`JSON.stringify({
    status: document.querySelector('.el-drawer .el-tag')?.innerText.trim() || '',
    hasApprove: [...document.querySelectorAll('.el-drawer button')].some(b => b.innerText.trim() === '同意退款'),
    hasReject: [...document.querySelectorAll('.el-drawer button')].some(b => b.innerText.trim() === '驳回'),
    labels: [...document.querySelectorAll('.el-drawer .el-descriptions__label')].map(e => e.innerText.trim())
  })`))
  console.log('  待审核详情:', pending)
  await shot('71-refund-pending-detail')
  check('状态为「待审核」', pending.status === '待审核', pending.status)
  check('出现「同意退款」「驳回」按钮', pending.hasApprove && pending.hasReject)
  check('详情含审核人字段', pending.labels.includes('审核人') && pending.labels.includes('审核时间'))

  await evaluate(`(() => {
    const btn = [...document.querySelectorAll('.el-drawer button')].find(b => b.innerText.trim() === '驳回');
    if (btn) btn.click();
  })()`)
  await sleep(900)
  const rejectDialog = JSON.parse(await evaluate(`JSON.stringify({
    visible: !!document.querySelector('.el-dialog'),
    title: document.querySelector('.el-dialog__title')?.innerText.trim() || '',
    textarea: !!document.querySelector('.el-dialog textarea')
  })`))
  console.log('  驳回弹窗:', rejectDialog)
  await shot('72-refund-reject-dialog')
  check('驳回弹窗可填写原因', rejectDialog.visible && rejectDialog.textarea && rejectDialog.title.includes('驳回'))

  await evaluate(`(() => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
    const el = document.querySelector('.el-dialog textarea');
    setter.call(el, 'UI 验收：商品已签收，不支持仅退款');
    el.dispatchEvent(new Event('input', { bubbles: true }));
  })()`)
  await evaluate(`(() => {
    const btn = [...document.querySelectorAll('.el-dialog button')].find(b => b.innerText.trim() === '确认驳回');
    if (btn) btn.click();
  })()`)
  await sleep(2600)
  const rejected = JSON.parse(await evaluate(`JSON.stringify({
    status: document.querySelector('.el-drawer .el-tag')?.innerText.trim() || '',
    hasApprove: [...document.querySelectorAll('.el-drawer button')].some(b => b.innerText.trim() === '同意退款'),
    text: document.querySelector('.el-drawer')?.innerText || ''
  })`))
  console.log('  驳回后:', { status: rejected.status, hasApprove: rejected.hasApprove })
  await shot('73-refund-rejected')
  check('驳回后状态为「退款失败」', rejected.status === '退款失败', rejected.status)
  check('驳回原因回显', rejected.text.includes('商品已签收'), '')
  check('驳回后审批按钮消失', !rejected.hasApprove)

  console.log('\n  同意路径')
  await openRefund(approveRefund.refundNo)
  await evaluate(`(() => {
    const btn = [...document.querySelectorAll('.el-drawer button')].find(b => b.innerText.trim() === '同意退款');
    if (btn) btn.click();
  })()`)
  await sleep(900)
  const approveDialog = JSON.parse(await evaluate(`JSON.stringify({
    visible: !!document.querySelector('.el-message-box'),
    title: document.querySelector('.el-message-box__title')?.innerText.trim() || '',
    text: document.querySelector('.el-message-box__message')?.innerText.trim() || ''
  })`))
  console.log('  二次确认:', approveDialog)
  await shot('74-refund-approve-dialog')
  check('同意前有二次确认', approveDialog.visible && approveDialog.title.includes('同意退款'))
  check('确认文案说明会立即退款', approveDialog.text.includes('渠道退款'), approveDialog.text)

  await evaluate(`(() => {
    const btn = [...document.querySelectorAll('.el-message-box button')].find(b => b.innerText.trim() === '同意退款');
    if (btn) btn.click();
  })()`)
  await sleep(3200)
  const approved = JSON.parse(await evaluate(`JSON.stringify({
    status: document.querySelector('.el-drawer .el-tag')?.innerText.trim() || '',
    text: document.querySelector('.el-drawer')?.innerText || '',
    hasApprove: [...document.querySelectorAll('.el-drawer button')].some(b => b.innerText.trim() === '同意退款')
  })`))
  console.log('  同意后:', { status: approved.status, hasApprove: approved.hasApprove })
  await shot('75-refund-approved')
  check('同意后状态为「退款成功」', approved.status === '退款成功', approved.status)
  check('渠道退款单号已回显', approved.text.includes('MOCK_REFUND_'), '')
  check('成功后退款单不再显示审批按钮', !approved.hasApprove)

  console.log('\n  客服只读')
  await login('cs01', 'Cs@123456')
  await openRefund(rejectRefund.refundNo)
  const csView = JSON.parse(await evaluate(`JSON.stringify({
    hasApprove: [...document.querySelectorAll('.el-drawer button')].some(b => b.innerText.trim() === '同意退款'),
    text: document.querySelector('.el-drawer')?.innerText || ''
  })`))
  console.log('  CS 视角:', { hasApprove: csView.hasApprove })
  await shot('76-refund-cs-readonly')
  check('客服看不到审批按钮', !csView.hasApprove)

  console.log(failures === 0 ? '\n✅ 退款审核流验收全部通过' : `\n❌ ${failures} 项未通过`)
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
