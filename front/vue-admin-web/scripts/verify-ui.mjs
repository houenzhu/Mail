/**
 * 一次性验收脚本：用 Chrome DevTools Protocol 打开后台界面并截图。
 *
 * 目的：这是 UI 交付物，构建通过 ≠ 界面能看。用无头 Chrome 真正渲染一遍，
 * 确认登录页与商品列表页（含 Vue 接管后的 DOM）都正常。
 *
 * 用法：node scripts/verify-ui.mjs [baseUrl] [outDir]
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://localhost:5174'
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9333
const USER_DATA = join(tmpdir(), `mall-admin-verify-${Date.now()}`)

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
  for (let i = 0; i < 50; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json/version`)
      const json = await res.json()
      if (json.webSocketDebuggerUrl) return json.webSocketDebuggerUrl
    } catch {
      // 还没起来
    }
    await sleep(200)
  }
  throw new Error('Chrome 调试端口未就绪')
}

/** 极简 CDP 客户端 */
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
  const wsUrl = await getWsUrl()
  const ws = new WebSocket(wsUrl)
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
  await call('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 900,
    deviceScaleFactor: 1,
    mobile: false,
  })

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

  async function goto(url) {
    await call('Page.navigate', { url })
    await sleep(1800)
  }

  async function screenshot(name, fullPage = false) {
    const args = { format: 'png', captureBeyondViewport: fullPage }
    const { data } = await call('Page.captureScreenshot', args)
    const file = join(OUT_DIR, `${name}.png`)
    writeFileSync(file, Buffer.from(data, 'base64'))
    console.log('  截图:', file)
    return file
  }

  async function evaluate(expression) {
    const { result, exceptionDetails } = await call('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true,
    })
    if (exceptionDetails) throw new Error(exceptionDetails.text)
    return result.value
  }

  console.log('1) 打开登录页 ...')
  await goto(`${BASE_URL}/login`)
  const loginText = await evaluate('document.body.innerText')
  console.log('   页面文本片段:', JSON.stringify(loginText.slice(0, 60)))
  await screenshot('01-login')

  console.log('2) 注入演示令牌并进入商品列表 ...')
  // mock 模式下令牌 → 用户名的映射由 mock 层维护（不透明令牌不可解析），
  // 这里模拟"已经登录过"的状态，因此需要同时写入映射表。
  await evaluate(`
    (() => {
      const access = 'mock-access-token-${Date.now()}';
      const refresh = 'mock-refresh-token-${Date.now()}';
      localStorage.setItem('mall_admin_access_token', access);
      localStorage.setItem('mall_admin_refresh_token', refresh);
      localStorage.setItem('mall_admin_mock_token_owner', JSON.stringify({ [access]: 'operator01', [refresh]: 'operator01' }));
      return 'ok';
    })()
  `)
  await goto(`${BASE_URL}/product/list`)

  const state = await evaluate(`JSON.stringify({
    url: location.pathname,
    hasTable: !!document.querySelector('.el-table'),
    rows: document.querySelectorAll('.el-table__body tbody tr').length,
    tabs: [...document.querySelectorAll('.el-tabs__item')].map(e => e.innerText.trim()),
    title: document.title,
    primaryColor: getComputedStyle(document.documentElement).getPropertyValue('--el-color-primary').trim(),
    fontBase: getComputedStyle(document.documentElement).getPropertyValue('--el-font-size-base').trim(),
    bodyFont: getComputedStyle(document.body).fontSize
  })`)
  console.log('   渲染状态:', state)
  await screenshot('02-product-list')

  console.log('3) 切换到「仓库中」Tab ...')
  const clicked = await evaluate(`
    (() => {
      const tab = [...document.querySelectorAll('.el-tabs__item')].find(e => e.innerText.includes('仓库中'));
      if (!tab) return 'tab-not-found';
      tab.click();
      return 'clicked';
    })()
  `)
  await sleep(900)
  const afterTab = await evaluate(
    `JSON.stringify({ clicked: ${JSON.stringify(clicked)}, rows: document.querySelectorAll('.el-table__body tbody tr').length })`,
  )
  console.log('   Tab 切换结果:', afterTab)
  await screenshot('03-tab-off-shelf')

  console.log('4) 打开发布商品页 ...')
  await goto(`${BASE_URL}/product/create`)
  await screenshot('04-product-create')

  console.log('5) 打开库存管理页 ...')
  await goto(`${BASE_URL}/inventory/list`)
  await screenshot('05-inventory')

  console.log('6) 打开工作台 ...')
  await goto(`${BASE_URL}/workbench`)
  await screenshot('06-workbench')

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
} catch (e) {
  console.error('验证失败:', e.message)
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
