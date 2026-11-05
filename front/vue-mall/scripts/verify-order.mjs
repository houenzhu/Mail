/**
 * 买家端订单闭环验收：结算下单 → 收银台 Mock 支付 → 订单详情 → 订单列表。
 * 用法：node scripts/verify-order.mjs [baseUrl] [outDir]
 *
 * 前置：vite dev 已启动（5175），网关与 auth/user/product/inventory/cart/order/pay 已启动。
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = (process.argv[2] || 'http://127.0.0.1:5175').replace(/\/$/, '')
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9340
const USER_DATA = join(tmpdir(), `mall-order-verify-${Date.now()}`)
const ACCESS_KEY = 'mall_buyer_access_token'
const REFRESH_KEY = 'mall_buyer_refresh_token'
let PRODUCT_ID = 113

const CHROME_CANDIDATES = [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
]

mkdirSync(OUT_DIR, { recursive: true })
const chromePath = CHROME_CANDIDATES.find((path) => existsSync(path))
if (!chromePath) {
  console.error('未找到 Chrome/Edge，跳过 UI 验证')
  process.exit(2)
}

const chrome = spawn(
  chromePath,
  [
    '--headless=new',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${USER_DATA}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-extensions',
    '--hide-scrollbars',
    'about:blank',
  ],
  { stdio: 'ignore' },
)

const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

async function getWsUrl() {
  for (let i = 0; i < 60; i++) {
    try {
      const response = await fetch(`http://127.0.0.1:${PORT}/json/version`)
      const json = await response.json()
      if (json.webSocketDebuggerUrl) return json.webSocketDebuggerUrl
    } catch {
      // 等待浏览器启动
    }
    await sleep(200)
  }
  throw new Error('Chrome 调试端口未就绪')
}

function createClient(ws) {
  let nextId = 1
  const pending = new Map()
  ws.addEventListener('message', (event) => {
    const msg = JSON.parse(event.data)
    if (msg.id && pending.has(msg.id)) {
      const { resolve: done, reject } = pending.get(msg.id)
      pending.delete(msg.id)
      msg.error ? reject(new Error(JSON.stringify(msg.error))) : done(msg.result)
    }
  })
  return (method, params = {}, sessionId) =>
    new Promise((done, reject) => {
      const id = nextId++
      pending.set(id, { resolve: done, reject })
      ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }))
    })
}

const results = []
const consoleErrors = []
let expectedAuthFailure = false

function record(name, passed, detail = '') {
  results.push({ name, passed, detail })
  console.log(`  ${passed ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

async function main() {
  const ws = new WebSocket(await getWsUrl())
  await new Promise((done, reject) => {
    ws.addEventListener('open', done)
    ws.addEventListener('error', reject)
  })
  const send = createClient(ws)
  const { targetId } = await send('Target.createTarget', { url: 'about:blank' })
  const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true })
  const call = (method, params) => send(method, params, sessionId)

  await call('Page.enable')
  await call('Runtime.enable')
  await call('Log.enable')

  ws.addEventListener('message', (event) => {
    const msg = JSON.parse(event.data)
    if (msg.method === 'Runtime.exceptionThrown') {
      consoleErrors.push(
        'JS 异常: ' +
          (msg.params?.exceptionDetails?.exception?.description ||
            msg.params?.exceptionDetails?.text),
      )
    }
    if (msg.method === 'Log.entryAdded' && msg.params?.entry?.level === 'error') {
      consoleErrors.push('控制台错误: ' + (msg.params.entry.text || ''))
    }
  })

  async function evaluate(expression) {
    const { result, exceptionDetails } = await call('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true,
    })
    if (exceptionDetails) throw new Error(exceptionDetails.text)
    return result.value
  }

  async function waitFor(expression, timeout = 12000) {
    const deadline = Date.now() + timeout
    while (Date.now() < deadline) {
      if (await evaluate(expression)) return true
      await sleep(150)
    }
    return false
  }

  async function goto(path) {
    await call('Page.navigate', { url: BASE_URL + path })
    await sleep(900)
  }

  async function screenshot(name) {
    const { data } = await call('Page.captureScreenshot', { format: 'png' })
    const file = join(OUT_DIR, name + '.png')
    writeFileSync(file, Buffer.from(data, 'base64'))
    console.log('  截图:', file)
  }

  async function fill(selector, value) {
    await evaluate(`(() => {
      const input = document.querySelector(` + JSON.stringify(selector) + `)
      if (!input) throw new Error('未找到输入框')
      const proto = input instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype
      const setter = Object.getOwnPropertyDescriptor(proto, 'value').set
      setter.call(input, ` + JSON.stringify(value) + `)
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    })()`)
  }

  async function click(selector) {
    await evaluate(`(() => {
      const node = document.querySelector(` + JSON.stringify(selector) + `)
      if (!node) throw new Error('未找到元素')
      node.click()
      return true
    })()`)
  }

  async function clickText(selector, text) {
    await evaluate(`(() => {
      const node = [...document.querySelectorAll(` + JSON.stringify(selector) + `)]
        .find((item) => item.innerText.trim() === ` + JSON.stringify(text) + `)
      if (!node) throw new Error('未找到按钮')
      node.click()
      return true
    })()`)
  }

  async function path() {
    return evaluate('location.pathname + location.search')
  }

  async function layout(name, selectors = []) {
    const data = JSON.parse(
      await evaluate(`JSON.stringify({
        overflow: document.documentElement.scrollWidth > window.innerWidth + 1,
        pageHeight: document.documentElement.scrollHeight,
        found: ` + JSON.stringify(selectors) + `.map((selector) => !!document.querySelector(selector))
      })`),
    )
    record(
      name + ' 无横向溢出且结构完整',
      !data.overflow && data.pageHeight > 300 && data.found.every(Boolean),
      JSON.stringify(data),
    )
  }

  async function login(username = 'buyer01', password = 'Buyer@123456') {
    await goto('/login')
    await waitFor("!!document.querySelector('#login-username')")
    await fill('#login-username', username)
    await fill('#login-password', password)
    await click('button[type="submit"]')
    await waitFor("location.pathname !== '/login'", 15000)
    await sleep(600)
  }

  await call('Emulation.setDeviceMetricsOverride', {
    width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
  })

  console.log('1) 登录 ...')
  await login()
  record('登录成功', !(await path()).startsWith('/login'), await path())

  console.log('2) 清空购物车并加购 ...')
  const cart = await evaluate(`(async () => {
    const token = localStorage.getItem('mall_buyer_access_token')
    const headers = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }
    const current = await (await fetch('/api/cart', { headers })).json()
    for (const item of current.data?.items || []) {
      await fetch('/api/cart/' + item.skuId, { method: 'DELETE', headers })
    }
    const detail = await (await fetch('/api/product/' + ${PRODUCT_ID} + '/detail', { headers })).json()
    const sku = (detail.data?.skus || []).find((item) => item.status === 1 && (item.available === null || item.available > 0))
    if (!sku) return -2
    const added = await (await fetch('/api/cart', {
      method: 'POST', headers,
      body: JSON.stringify({ skuId: sku.id, quantity: 2 })
    })).json()
    return added.code
  })()`)
  record('购物车准备成功', cart === 0, 'code=' + cart)

  console.log('3) 购物车进入结算 ...')
  await goto('/cart')
  await waitFor("!!document.querySelector('.cart-summary__submit')")
  await click('.cart-summary__submit')
  await waitFor("location.pathname === '/checkout'")
  await waitFor("!!document.querySelector('.summary__submit')")
  const checkout = JSON.parse(await evaluate(`JSON.stringify({
    amount: document.querySelector('.summary__total dd')?.innerText || '',
    items: document.querySelectorAll('.item-list .item').length,
    address: document.querySelector('.address-card__detail')?.innerText || ''
  })`))
  record(
    '结算页加载地址与商品',
    checkout.items >= 1 && checkout.amount.includes('¥') && checkout.address.length > 0,
    JSON.stringify(checkout),
  )
  await layout('桌面结算页', ['.checkout-layout', '.summary'])

  console.log('4) 提交订单 ...')
  await click('.summary__submit')
  await waitFor("location.pathname.startsWith('/order/')", 20000)
  await waitFor("!!document.querySelector('.status-hero')", 15000)
  const orderPath = await path()
  const orderNo = orderPath.split('/')[2].split('?')[0]
  const detail = JSON.parse(await evaluate(`JSON.stringify({
    status: document.querySelector('.status-hero__label')?.innerText || '',
    countdown: document.querySelector('.status-hero__hint')?.innerText || '',
    receiver: document.querySelector('.receiver__who')?.innerText || '',
    address: document.querySelector('.receiver__address')?.innerText || '',
    items: document.querySelectorAll('.item-list .item').length,
    amount: document.querySelector('.amount-list__total dd')?.innerText || '',
    actions: [...document.querySelectorAll('.status-hero__actions button, .status-hero__actions a')].map((n) => n.innerText.trim())
  })`))
  record(
    '下单后跳转详情且状态为待支付',
    detail.status.includes('待支付') &&
      detail.items >= 1 &&
      detail.amount.includes('¥') &&
      detail.actions.includes('去支付') &&
      detail.actions.includes('取消订单'),
    JSON.stringify({ orderNo, ...detail }),
  )
  record('详情展示收货信息与倒计时', detail.receiver.includes('张三') && /\d{2}:\d{2}/.test(detail.countdown), detail.countdown)
  await layout('桌面订单详情', ['.status-hero', '.detail-content', '.detail-aside'])
  await screenshot('order-detail-unpaid')

  console.log('5) 进入收银台并模拟支付 ...')
  await clickText('.status-hero__actions button, .status-hero__actions a', '去支付')
  await waitFor("location.pathname.startsWith('/pay/')", 15000)
  await waitFor("!!document.querySelector('.pay-panel')", 15000)
  const pay = JSON.parse(await evaluate(`JSON.stringify({
    amount: document.querySelector('.pay-panel__amount')?.innerText || '',
    badge: document.querySelector('.pay-panel__badge')?.innerText || '',
    mockNote: !!document.querySelector('.mock-note'),
    info: document.querySelector('.pay-info')?.innerText || '',
    actions: [...document.querySelectorAll('.pay-actions button, .pay-actions a')].map((n) => n.innerText.trim())
  })`))
  record(
    '收银台展示金额与 Mock 通道',
    pay.amount.includes('¥') && pay.badge.includes('模拟') && pay.mockNote && pay.info.includes('支付单号'),
    JSON.stringify(pay),
  )
  record('Mock 通道提供「模拟支付成功」按钮', pay.actions.includes('模拟支付成功'), pay.actions.join(' / '))
  await layout('桌面收银台', ['.pay-panel', '.pay-aside'])
  await screenshot('order-pay')

  await clickText('.pay-actions button', '模拟支付成功')
  await waitFor("location.pathname.startsWith('/order/') && location.search.includes('paid=1')", 20000)
  await waitFor("(document.querySelector('.status-hero__label')?.innerText || '').includes('已支付')", 15000)
  const paid = JSON.parse(await evaluate(`JSON.stringify({
    status: document.querySelector('.status-hero__label')?.innerText || '',
    hint: document.querySelector('.status-hero__hint')?.innerText || '',
    actions: [...document.querySelectorAll('.status-hero__actions button, .status-hero__actions a')].map((n) => n.innerText.trim()),
    timeline: [...document.querySelectorAll('.timeline__title')].map((n) => n.innerText.trim())
  })`))
  record(
    '模拟支付后订单变为已支付',
    paid.status.includes('已支付') && !paid.actions.includes('去支付') && paid.timeline.length >= 2,
    JSON.stringify(paid),
  )
  await screenshot('order-detail-paid')

  console.log('6) 我的订单列表 ...')
  await goto('/order/list')
  await waitFor("!!document.querySelector('.order-card')", 15000)
  const list = JSON.parse(await evaluate(`JSON.stringify({
    cards: document.querySelectorAll('.order-card').length,
    tabs: [...document.querySelectorAll('.order-tabs__item')].map((n) => n.innerText.trim()),
    firstNo: document.querySelector('.order-card__no')?.innerText || '',
    firstStatus: document.querySelector('.order-card__status')?.innerText || ''
  })`))
  record(
    '订单列表展示卡片、状态与 Tab',
    list.cards >= 1 && list.tabs.length === 5 && list.firstNo.includes(orderNo),
    JSON.stringify(list),
  )
  await layout('桌面订单列表', ['.order-list', '.order-tabs'])
  await screenshot('order-list')

  console.log('7) 状态 Tab 筛选 ...')
  await clickText('.order-tabs__item', '待收货')
  await waitFor("location.search.includes('status=30')", 8000)
  await sleep(1500)
  const filtered = JSON.parse(await evaluate(`JSON.stringify({
    active: document.querySelector('.order-tabs__item.is-active')?.innerText || '',
    query: location.search,
    statuses: [...document.querySelectorAll('.order-card__status')].map((n) => n.innerText.trim())
  })`))
  record(
    '状态 Tab 写入 query 且只展示匹配状态',
    filtered.active === '待收货' &&
      filtered.query.includes('status=30') &&
      filtered.statuses.every((text) => text.includes('已发货')),
    JSON.stringify(filtered),
  )

  console.log('8) 移动端订单详情与列表 ...')
  await call('Emulation.setDeviceMetricsOverride', {
    width: 390, height: 844, deviceScaleFactor: 1, mobile: true,
  })
  await goto('/order/' + orderNo)
  await waitFor("!!document.querySelector('.status-hero')", 15000)
  await layout('移动端订单详情', ['.status-hero', '.detail-content'])
  await screenshot('order-detail-mobile')
  await goto('/order/list')
  await waitFor("!!document.querySelector('.order-card')", 15000)
  await layout('移动端订单列表', ['.order-list'])
  await screenshot('order-list-mobile')

  const uniqueErrors = [...new Set(consoleErrors)]
  record('全程无控制台异常', uniqueErrors.length === 0, uniqueErrors.slice(0, 4).join(' | '))

  const failed = results.filter((item) => !item.passed)
  console.log('\n结果：' + (results.length - failed.length) + '/' + results.length + ' 通过')
  if (failed.length) {
    console.log('失败项：')
    failed.forEach((item) => console.log('- ' + item.name + ': ' + item.detail))
  }

  ws.close()
  chrome.kill()
  process.exitCode = failed.length ? 1 : 0
}

main().catch((error) => {
  console.error(error)
  chrome.kill()
  process.exitCode = 1
})
