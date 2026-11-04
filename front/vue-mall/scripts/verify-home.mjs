/**
 * 首页 UI 验收：无头 Chrome 实际渲染并截图。
 * 用法：node scripts/verify-home.mjs [baseUrl] [outDir]
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://localhost:5175'
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9334
const USER_DATA = join(tmpdir(), `mall-home-verify-${Date.now()}`)

const CHROME_CANDIDATES = [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
]

mkdirSync(OUT_DIR, { recursive: true })
const chromePath = CHROME_CANDIDATES.find((p) => existsSync(p))
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

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function getWsUrl() {
  for (let i = 0; i < 60; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json/version`)
      const json = await res.json()
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
      const { resolve, reject } = pending.get(msg.id)
      pending.delete(msg.id)
      msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
    }
  })
  return (method, params = {}, sessionId) =>
    new Promise((resolve, reject) => {
      const id = nextId++
      pending.set(id, { resolve, reject })
      ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }))
    })
}

const errors = []

async function main() {
  const ws = new WebSocket(await getWsUrl())
  await new Promise((res, rej) => {
    ws.addEventListener('open', res)
    ws.addEventListener('error', rej)
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
      errors.push(
        'JS 异常: ' +
          (msg.params?.exceptionDetails?.exception?.description ||
            msg.params?.exceptionDetails?.text),
      )
    }
    if (msg.method === 'Log.entryAdded' && msg.params?.entry?.level === 'error') {
      errors.push('控制台错误: ' + msg.params.entry.text)
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

  async function viewport(width, height) {
    await call('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: width < 600,
    })
  }

  async function goto(url) {
    await call('Page.navigate', { url })
    await sleep(2500)
  }

  async function screenshot(name) {
    const { data } = await call('Page.captureScreenshot', { format: 'png' })
    const file = join(OUT_DIR, `${name}.png`)
    writeFileSync(file, Buffer.from(data, 'base64'))
    console.log('  截图:', file)
  }

  console.log('1) 桌面端首页 ...')
  await viewport(1440, 900)
  await goto(BASE_URL)
  const desktop = await evaluate(`JSON.stringify({
    cards: document.querySelectorAll('.product-card').length,
    slides: document.querySelectorAll('.hero-slide').length,
    navItems: document.querySelectorAll('.category-nav__link').length,
    notice: document.querySelector('.recommend__degraded')?.innerText || '',
    overflow: document.body.scrollWidth > window.innerWidth + 1
  })`)
  console.log('   渲染状态:', desktop)
  await screenshot('home-desktop')

  console.log('2) 页头搜索提交 ...')
  await evaluate(`(() => {
    const input = document.querySelector('.site-header__search input');
    const form = input.closest('form');
    input.value = '耳机';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    form.requestSubmit();
    return 'ok';
  })()`)
  await sleep(2200)
  const searchState = await evaluate(`JSON.stringify({
    path: location.pathname + location.search,
    title: document.querySelector('.search-title')?.innerText,
    cards: document.querySelectorAll('.product-card').length
  })`)
  console.log('   搜索状态:', searchState)
  await screenshot('search')

  console.log('3) 功能预告页 ...')
  await goto(`${BASE_URL}/feature/login`)
  await screenshot('feature-login')

  console.log('4) 移动端首页 ...')
  await viewport(390, 844)
  await goto(BASE_URL)
  const mobile = await evaluate(`JSON.stringify({
    cards: document.querySelectorAll('.product-card').length,
    overflow: document.body.scrollWidth > window.innerWidth + 1,
    width: window.innerWidth,
    scrollWidth: document.body.scrollWidth
  })`)
  console.log('   移动端状态:', mobile)
  await screenshot('home-mobile')

  ws.close()

  if (errors.length) {
    console.log('\n⚠️ 捕获到前端错误:')
    errors.forEach((e) => console.log('   - ' + e))
  } else {
    console.log('\n✅ 未捕获到 JS 异常或控制台错误')
  }
}

try {
  await main()
} catch (error) {
  console.error('验证失败:', error.message)
  console.log('已捕获的错误:', errors)
  process.exitCode = 1
} finally {
  chrome.kill()
  await sleep(300)
  try {
    rmSync(USER_DATA, { recursive: true, force: true })
  } catch {
    // 忽略清理失败
  }
}
