/**
 * 买家端购物车验收：无头 Chrome 实际渲染并操作商品详情 / SKU / 购物车。
 * 用法：node scripts/verify-cart.mjs [baseUrl] [outDir]
 *
 * 前置：vite dev 已启动（5175），网关与商品、库存、购物车服务已启动。
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = (process.argv[2] || 'http://127.0.0.1:5175').replace(/\/$/, '')
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9338
const USER_DATA = join(tmpdir(), `mall-cart-verify-${Date.now()}`)
const ACCESS_KEY = 'mall_buyer_access_token'
const REFRESH_KEY = 'mall_buyer_refresh_token'
let PRODUCT_ID = 1

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
      const text = msg.params.entry.text || ''
      if (expectedAuthFailure && /401|Unauthorized/i.test(text)) return
      consoleErrors.push('控制台错误: ' + text)
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

  async function waitFor(expression, timeout = 9000) {
    const deadline = Date.now() + timeout
    while (Date.now() < deadline) {
      if (await evaluate(expression)) return true
      await sleep(120)
    }
    return false
  }

  async function viewport(width, height) {
    await call('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: width < 600,
    })
  }

  async function goto(path) {
    await call('Page.navigate', { url: `${BASE_URL}${path}` })
    await sleep(1000)
  }

  async function screenshot(name) {
    const { data } = await call('Page.captureScreenshot', { format: 'png' })
    const file = join(OUT_DIR, `${name}.png`)
    writeFileSync(file, Buffer.from(data, 'base64'))
    console.log('  截图:', file)
  }

  async function fill(selector, value) {
    await evaluate(`(() => {
      const input = document.querySelector(${JSON.stringify(selector)})
      if (!input) throw new Error('未找到输入框: ${selector}')
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
      setter.call(input, ${JSON.stringify(value)})
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    })()`)
  }

  async function click(selector) {
    await evaluate(`(() => {
      const node = document.querySelector(${JSON.stringify(selector)})
      if (!node) throw new Error('未找到元素: ${selector}')
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
        width: window.innerWidth,
        overflow: document.documentElement.scrollWidth > window.innerWidth + 1,
        bodyWidth: document.body.scrollWidth,
        pageHeight: document.documentElement.scrollHeight,
        found: ${JSON.stringify(selectors)}.map((selector) => !!document.querySelector(selector))
      })`),
    )
    record(
      `${name} 无横向溢出且结构完整`,
      !data.overflow && data.pageHeight > 300 && data.found.every(Boolean),
      JSON.stringify(data),
    )
    return data
  }

  async function cartTotal() {
    return evaluate("document.querySelector('.cart-summary__total strong')?.innerText || ''")
  }

  console.log('1) 首页商品卡片进入详情 ...')
  await viewport(1440, 900)
  await goto('/')
  await waitFor("!!document.querySelector('.recommend .product-card')")
  await click('.recommend .product-card')
  await waitFor("location.pathname.startsWith('/product/')")
  PRODUCT_ID = await evaluate("Number(location.pathname.split('/').pop())")
  await waitFor("!!document.querySelector('.detail-info__title')")
  const detailState = JSON.parse(
    await evaluate(`JSON.stringify({
      path: location.pathname,
      title: document.querySelector('.detail-info__title')?.innerText || '',
      price: document.querySelector('.detail-price-panel__price')?.innerText || '',
      skus: document.querySelectorAll('.sku-option').length,
      offShelf: !!document.querySelector('.detail-error')
    })`),
  )
  record(
    '商品卡片直达详情并展示真实商品',
    detailState.path === `/product/${PRODUCT_ID}` &&
      detailState.title.length > 0 &&
      detailState.price.includes('¥') &&
      detailState.skus >= 2 &&
      !detailState.offShelf,
    JSON.stringify(detailState),
  )
  await layout('桌面商品详情页', ['.detail-gallery', '.detail-info', '.detail-actions'])
  await screenshot('cart-detail-desktop')

  console.log('2) SKU 联动与库存上限 ...')
  const activeBefore = await evaluate("document.querySelector('.sku-option.is-active')?.innerText || ''")
  const options = await evaluate(
    "[...document.querySelectorAll('.sku-option:not(.is-unavailable)')].map((n) => n.innerText.trim())",
  )
  if (options.length > 1) {
    await evaluate(`(() => {
      const node = [...document.querySelectorAll('.sku-option:not(.is-unavailable)')]
        .find((item) => !item.classList.contains('is-active'))
      if (node) node.click()
      return true
    })()`)
    await sleep(400)
  }
  const skuState = JSON.parse(
    await evaluate(`JSON.stringify({
      active: document.querySelector('.sku-option.is-active')?.innerText || '',
      price: document.querySelector('.detail-price-panel__price')?.innerText || '',
      summary: document.querySelector('.sku-summary')?.innerText || '',
      quantityMax: document.querySelector('.detail-quantity')?.innerText || ''
    })`),
  )
  record(
    '规格切换联动价格与库存提示',
    skuState.active.length > 0 &&
      skuState.price.includes('¥') &&
      /[：:]/.test(skuState.summary) &&
      skuState.quantityMax.includes('最多可购'),
    JSON.stringify({ activeBefore, ...skuState }),
  )

  const soldOut = JSON.parse(await evaluate("JSON.stringify({disabled: [...document.querySelectorAll('.sku-option')].filter((n) => n.disabled).map((n) => n.innerText.trim())})"))
  record('售罄规格禁用选择', soldOut.disabled.length >= 1, JSON.stringify(soldOut))

  console.log('3) 未登录加购跳登录并保留 redirect ...')
  expectedAuthFailure = true
  await click('.detail-actions__buy')
  await waitFor("location.pathname === '/login'")
  expectedAuthFailure = false
  const loginPath = await path()
  record(
    '未登录加购跳转登录并带 redirect',
    loginPath.startsWith('/login') && loginPath.includes(`redirect=/product/${PRODUCT_ID}`),
    loginPath,
  )

  console.log('4) 登录后返回详情并加购 ...')
  await waitFor("!!document.querySelector('#login-username')")
  await fill('#login-username', 'buyer01')
  await fill('#login-password', 'Buyer@123456')
  await click('button[type="submit"]')
  await waitFor(`location.pathname === '/product/${PRODUCT_ID}'`)
  await waitFor("!!document.querySelector('.detail-info__title')")
  await evaluate(`(() => {
    const input = document.querySelector('.quantity-stepper__input')
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
    setter.call(input, '2')
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('blur', { bubbles: true }))
    return true
  })()`)
  await click('.detail-actions__buy')
  await waitFor("document.querySelector('.app-toast')?.innerText.includes('已加入购物车')")
  await sleep(300)
  const addState = JSON.parse(
    await evaluate(`JSON.stringify({
      toast: document.querySelector('.app-toast')?.innerText || '',
      badge: document.querySelector('.header-action__badge')?.innerText || '',
      path: location.pathname
    })`),
  )
  record(
    '登录后加购成功，页头角标同步',
    addState.toast.includes('已加入购物车') && Number(addState.badge) >= 2,
    JSON.stringify(addState),
  )
  await screenshot('cart-detail-added')

  await call('Page.reload', { ignoreCache: true })
  await waitFor("Number(document.querySelector('.header-action__badge')?.innerText || 0) >= 2")
  const persistedBadge = await evaluate("document.querySelector('.header-action__badge')?.innerText || ''")
  record('刷新后页头购物车角标从后端恢复', Number(persistedBadge) >= 2, 'badge=' + persistedBadge)

  console.log('5) 购物车页面与金额 ...')
  await click('.header-action--cart')
  await waitFor("location.pathname === '/cart'")
  await waitFor("!!document.querySelector('.cart-table')")
  const cartState = JSON.parse(
    await evaluate(`JSON.stringify({
      rows: document.querySelectorAll('.cart-row').length,
      total: document.querySelector('.cart-summary__total strong')?.innerText || '',
      count: document.querySelector('.cart-summary__count strong')?.innerText || '',
      allSelected: !!document.querySelector('.cart-summary__all input')?.checked,
      badge: document.querySelector('.header-action__badge')?.innerText || ''
    })`),
  )
  record(
    '购物车展示真实行、勾选金额与页头角标',
    cartState.rows >= 1 && Number(cartState.count) >= 2 && cartState.total.includes('¥'),
    JSON.stringify(cartState),
  )
  await layout('桌面购物车', ['.cart-table', '.cart-summary'])
  await screenshot('cart-cart-desktop')

  console.log('6) 数量修改与取消/全选 ...')
  const firstRow = '.cart-row'
  const beforeTotal = await cartTotal()
  await click(`${firstRow} .quantity-stepper__button:last-child`)
  await waitFor(`(document.querySelector('.cart-summary__total strong')?.innerText || '') !== ${JSON.stringify(beforeTotal)}`, 6000)
  const afterTotal = await cartTotal()
  record('增加数量后合计金额更新', afterTotal !== beforeTotal, `${beforeTotal} -> ${afterTotal}`)

  await click('.cart-summary__checkmark')
  await waitFor("!document.querySelector('.cart-summary__all input')?.checked")
  await sleep(700)
  const unselectedTotal = await cartTotal()
  const submitText = await evaluate("document.querySelector('.cart-summary__submit')?.innerText || ''")
  record(
    '取消全选后金额归零且结算禁用',
    unselectedTotal.includes('0') && submitText.includes('请选择商品'),
    `${unselectedTotal} / ${submitText}`,
  )

  await click('.cart-summary__checkmark')
  await waitFor("!!document.querySelector('.cart-summary__all input')?.checked")
  await sleep(700)

  console.log('7) 结算预览 ...')
  await click('.cart-summary__submit')
  await waitFor("!!document.querySelector('.checkout-sheet')")
  const checkoutState = JSON.parse(
    await evaluate(`JSON.stringify({
      items: document.querySelectorAll('.checkout-item').length,
      amount: document.querySelector('.checkout-total__amount dd')?.innerText || '',
      note: document.querySelector('.checkout-note')?.innerText || '',
      dialog: document.querySelector('.checkout-sheet')?.getAttribute('role')
    })`),
  )
  record(
    '结算预览展示勾选商品与应付金额',
    checkoutState.items >= 1 &&
      checkoutState.amount.includes('¥') &&
      checkoutState.dialog === 'dialog' &&
      checkoutState.note.includes('下一阶段'),
    JSON.stringify(checkoutState),
  )
  await screenshot('cart-checkout-sheet')
  await click('.checkout-sheet__close')
  await waitFor("!document.querySelector('.checkout-sheet')")

  console.log('8) 移动端详情与购物车（有商品）...')
  await viewport(390, 844)
  await goto(`/product/${PRODUCT_ID}`)
  await waitFor("!!document.querySelector('.detail-info__title')")
  await layout('移动端商品详情页', ['.detail-gallery', '.detail-info', '.detail-actions'])
  await screenshot('cart-detail-mobile')

  await goto('/cart')
  await waitFor("!!document.querySelector('.cart-table')")
  await layout('移动端购物车', ['.cart-table', '.cart-summary'])
  const fixed = JSON.parse(await evaluate("JSON.stringify({position: getComputedStyle(document.querySelector('.cart-summary')).position, bottom: getComputedStyle(document.querySelector('.cart-summary')).bottom})"))
  record('移动端购物车结算栏固定可操作', fixed.position === 'fixed', JSON.stringify(fixed))
  await screenshot('cart-cart-mobile')

  console.log('9) 删除商品并同步角标 ...')
  await viewport(1440, 900)
  await goto('/cart')
  await waitFor("!!document.querySelector('.cart-table')")
  const rowsBefore = await evaluate("document.querySelectorAll('.cart-row').length")
  await click(`${firstRow} .cart-row__remove`)
  await waitFor(`document.querySelectorAll('.cart-row').length < ${rowsBefore}`, 6000)
  await sleep(400)
  const afterRemove = JSON.parse(
    await evaluate(`JSON.stringify({
      rows: document.querySelectorAll('.cart-row').length,
      badge: document.querySelector('.header-action__badge')?.innerText || '',
      empty: !!document.querySelector('.cart-empty')
    })`),
  )
  record(
    '删除商品后列表与页头角标同步',
    afterRemove.rows < rowsBefore,
    JSON.stringify(afterRemove),
  )
  const uniqueErrors = [...new Set(consoleErrors)]
  record('全程无控制台异常', uniqueErrors.length === 0, uniqueErrors.slice(0, 4).join(' | '))

  const failed = results.filter((item) => !item.passed)
  console.log(`\n结果：${results.length - failed.length}/${results.length} 通过`)
  if (failed.length) {
    console.log('失败项：')
    failed.forEach((item) => console.log(`- ${item.name}: ${item.detail}`))
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