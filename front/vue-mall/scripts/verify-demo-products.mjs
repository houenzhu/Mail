/**
 * 买家端演示商品验收：验证首页演示卡片、旧详情链接重定向与真实详情页。
 * 用法：node scripts/verify-demo-products.mjs [baseUrl] [outDir]
 *
 * 前置：vite dev 已启动（5175），网关与商品 / 库存服务已启动，且已执行 15_seed_demo_products.sql。
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = (process.argv[2] || 'http://127.0.0.1:5175').replace(/\/$/, '')
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9341
const USER_DATA = join(tmpdir(), `mall-demo-verify-${Date.now()}`)
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

  console.log('1) 首页演示卡片进入真实详情 ...')
await viewport(1440, 900)
await goto('/')
await waitFor("!!document.querySelector('.recommend .product-card')")
const demoCards = JSON.parse(await evaluate(`JSON.stringify([...document.querySelectorAll('.recommend .product-card')].map((n) => n.getAttribute('href')))`))
record(
  '首页猜你喜欢卡片全部指向 /product/:id',
  demoCards.length > 0 && demoCards.every((href) => /^\/product\/\d+$/.test(href || '')),
  JSON.stringify(demoCards.slice(0, 6)),
)

await goto('/product/105')
await waitFor("!!document.querySelector('.detail-info__title')")
const camera = JSON.parse(await evaluate(`JSON.stringify({
  path: location.pathname,
  title: document.querySelector('.detail-info__title')?.innerText || '',
  price: document.querySelector('.detail-price-panel__price')?.innerText || '',
  skus: document.querySelectorAll('.sku-option').length,
  error: document.querySelector('.detail-error')?.innerText || ''
})`))
record(
  '演示商品 105 展示真实相机详情',
  camera.path === '/product/105' && camera.title.includes('复古微单') && camera.price.includes('¥') && camera.skus >= 2 && !camera.error,
  JSON.stringify(camera),
)
await layout('桌面相机详情页', ['.detail-gallery', '.detail-info', '.detail-actions'])
await screenshot('product-demo-camera-desktop')

console.log('2) 旧功能预告链接兼容重定向 ...')
await goto('/feature/product-detail?id=105')
await waitFor("location.pathname === '/product/105'")
const legacy = await path()
record('旧链接重定向到真实详情并保留商品', legacy === '/product/105', legacy)

await goto('/feature/product-detail')
await waitFor("location.pathname === '/search'")
const legacyEmpty = await path()
record('旧链接缺少 id 时回到商品列表', legacyEmpty.startsWith('/search'), legacyEmpty)

console.log('3) 全部演示商品可打开详情 ...')
const ids = [101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113]
const failures = []
for (const id of ids) {
  await goto(`/product/${id}`)
  const ok = await waitFor("!!document.querySelector('.detail-info__title')", 7000)
  const state = JSON.parse(await evaluate(`JSON.stringify({
    title: document.querySelector('.detail-info__title')?.innerText || '',
    error: document.querySelector('.detail-error')?.innerText || '',
    skus: document.querySelectorAll('.sku-option').length
  })`))
  if (!ok || state.error || !state.title || state.skus < 1) failures.push({ id, ...state })
}
record('13 个演示商品详情全部可打开', failures.length === 0, JSON.stringify(failures))

console.log('4) 移动端演示详情布局 ...')
await viewport(390, 844)
await goto('/product/105')
await waitFor("!!document.querySelector('.detail-info__title')")
await layout('移动端相机详情页', ['.detail-gallery', '.detail-info', '.detail-actions'])
await screenshot('product-demo-camera-mobile')

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
