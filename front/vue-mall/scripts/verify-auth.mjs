/**
 * 买家端认证验收：无头 Chrome 实际渲染并操作登录 / 注册 / 账户页。
 * 用法：node scripts/verify-auth.mjs [baseUrl] [outDir]
 *
 * 覆盖：未登录守卫、错误密码、登录、刷新恢复、accessToken 过期自动续期、
 * 注销清令牌、注册表单校验，以及 1440 / 390 两档布局与横向溢出检查。
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = (process.argv[2] || 'http://127.0.0.1:5175').replace(/\/$/, '')
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9337
const USER_DATA = join(tmpdir(), `mall-auth-verify-${Date.now()}`)
const ACCESS_KEY = 'mall_buyer_access_token'
const REFRESH_KEY = 'mall_buyer_refresh_token'

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

  async function waitFor(expression, timeout = 8000) {
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
    await sleep(900)
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

  async function tokens() {
    return evaluate(`JSON.stringify({
      access: localStorage.getItem(${JSON.stringify(ACCESS_KEY)}),
      refresh: localStorage.getItem(${JSON.stringify(REFRESH_KEY)})
    })`)
  }

  async function layout(name) {
    const data = JSON.parse(
      await evaluate(`JSON.stringify({
        width: window.innerWidth,
        overflow: document.documentElement.scrollWidth > window.innerWidth + 1,
        bodyWidth: document.body.scrollWidth,
        pageHeight: document.documentElement.scrollHeight,
        authCard: !!document.querySelector('.auth-card'),
        accountHero: !!document.querySelector('.account-hero')
      })`),
    )
    record(
      `${name} 无横向溢出且结构完整`,
      !data.overflow && data.pageHeight > 300 && (data.authCard || data.accountHero),
      JSON.stringify(data),
    )
    return data
  }

  console.log('1) 未登录访问账户中心 ...')
  await viewport(1440, 900)
  await goto('/account')
  await waitFor("location.pathname === '/login'")
  const guardPath = await path()
  record(
    '未登录守卫带 redirect 与 reason',
    guardPath.includes('/login') &&
      guardPath.includes('redirect=/account') &&
      guardPath.includes('reason=required'),
    guardPath,
  )
  await screenshot('auth-login-desktop')

  console.log('2) 错误密码 ...')
  await fill('#login-username', 'buyer01')
  await fill('#login-password', 'WrongPass123')
  await click('button[type="submit"]')
  await waitFor("!!document.querySelector('.auth-alert')")
  const errorText = await evaluate("document.querySelector('.auth-alert')?.innerText || ''")
  record('错误密码提示准确', errorText.includes('账号或密码错误'), errorText.trim())

  console.log('3) 正确账号登录 ...')
  await fill('#login-password', 'Buyer@123456')
  await click('button[type="submit"]')
  await waitFor("location.pathname === '/account'")
  await waitFor("!!document.querySelector('.account-hero')")
  await sleep(500)
  const accountState = JSON.parse(
    await evaluate(`JSON.stringify({
      path: location.pathname,
      greeting: document.querySelector('.account-hero__greeting')?.innerText || '',
      headerName: document.querySelector('.header-account__name')?.innerText || '',
      fields: [...document.querySelectorAll('.account-fields dd')].map((n) => n.innerText).join('|')
    })`),
  )
  record(
    '登录后进入账户中心并展示资料',
    accountState.path === '/account' &&
      accountState.greeting.includes('小哲') &&
      accountState.headerName.includes('小哲') &&
      accountState.fields.includes('buyer01'),
    JSON.stringify(accountState),
  )
  await layout('桌面账户页')
  await screenshot('auth-account-desktop')

  console.log('4) 刷新账户页恢复登录态 ...')
  await call('Page.reload', { ignoreCache: true })
  await sleep(1400)
  const restored = JSON.parse(
    await evaluate(`JSON.stringify({
      path: location.pathname,
      greeting: document.querySelector('.account-hero__greeting')?.innerText || ''
    })`),
  )
  record(
    '刷新后通过 /auth/me 恢复登录态',
    restored.path === '/account' && restored.greeting.includes('小哲'),
    JSON.stringify(restored),
  )

  console.log('5) accessToken 失效后自动续期 ...')
  expectedAuthFailure = true
  const before = JSON.parse(await tokens())
  await evaluate(`localStorage.setItem(${JSON.stringify(ACCESS_KEY)}, localStorage.getItem(${JSON.stringify(ACCESS_KEY)}) + '__expired__')`)
  await call('Page.reload', { ignoreCache: true })
  await sleep(1800)
  const refreshed = JSON.parse(
    await evaluate(`JSON.stringify({
      path: location.pathname,
      greeting: document.querySelector('.account-hero__greeting')?.innerText || '',
      access: localStorage.getItem(${JSON.stringify(ACCESS_KEY)}),
      refresh: localStorage.getItem(${JSON.stringify(REFRESH_KEY)})
    })`),
  )
  expectedAuthFailure = false
  record(
    '过期 accessToken 自动 refresh 并保持登录',
    refreshed.path === '/account' &&
      refreshed.greeting.includes('小哲') &&
      refreshed.access !== before.access &&
      refreshed.refresh !== before.refresh &&
      !refreshed.access.includes('__expired__'),
    `access 已轮换=${refreshed.access !== before.access}, refresh 已轮换=${refreshed.refresh !== before.refresh}`,
  )

  console.log('6) 账户菜单交互 ...')
  await click('.header-account__trigger')
  await sleep(200)
  const menuOpen = await evaluate("!!document.querySelector('.header-account__menu')")
  await call('Input.dispatchKeyEvent', {
    type: 'keyDown',
    key: 'Escape',
    code: 'Escape',
    windowsVirtualKeyCode: 27,
  })
  await sleep(200)
  const menuClosed = await evaluate("!document.querySelector('.header-account__menu')")
  record('账户菜单可打开并支持 Esc 关闭', menuOpen && menuClosed)

  console.log('7) 移动端账户页 ...')
  await viewport(390, 844)
  await call('Page.reload', { ignoreCache: true })
  await sleep(1400)
  await layout('移动端账户页')
  await screenshot('auth-account-mobile')

  console.log('8) 注销并清除令牌 ...')
  await viewport(1440, 900)
  await goto('/account')
  await waitFor("!!document.querySelector('.account-hero')")
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find((node) => node.innerText.includes('退出登录'))
    if (!button) throw new Error('未找到退出登录按钮')
    button.click()
    return true
  })()`)
  await waitFor("location.pathname === '/'")
  await sleep(500)
  const afterLogout = JSON.parse(await tokens())
  const logoutPath = await path()
  record(
    '注销回首页且两个令牌均清除',
    logoutPath === '/' && !afterLogout.access && !afterLogout.refresh,
    `path=${logoutPath}, access=${afterLogout.access}, refresh=${afterLogout.refresh}`,
  )

  console.log('9) 移动端登录页 ...')
  await viewport(390, 844)
  await goto('/login')
  await sleep(600)
  await layout('移动端登录页')
  await screenshot('auth-login-mobile')

  console.log('10) 注册表单校验 ...')
  await viewport(1440, 900)
  await goto('/register')
  await sleep(500)
  await click('button[type="submit"]')
  await sleep(300)
  const validation = JSON.parse(
    await evaluate(`JSON.stringify({
      errors: [...document.querySelectorAll('.auth-field__error')].map((n) => n.innerText),
      path: location.pathname
    })`),
  )
  const validationText = validation.errors.join('|')
  record(
    '注册空表单校验用户名/手机号/密码/协议',
    validation.path === '/register' &&
      validationText.includes('请输入用户名') &&
      validationText.includes('请输入手机号') &&
      validationText.includes('请输入密码') &&
      validationText.includes('同意'),
    validationText,
  )

  await fill('#register-username', 'ab')
  await fill('#register-phone', '12345')
  await fill('#register-password', 'abc')
  await click('button[type="submit"]')
  await sleep(300)
  const invalidText = await evaluate(
    "[...document.querySelectorAll('.auth-field__error')].map((n) => n.innerText).join('|')",
  )
  record(
    '注册格式校验生效',
    invalidText.includes('4~32 位') &&
      invalidText.includes('11 位') &&
      invalidText.includes('8~32 位'),
    invalidText,
  )

  await fill('#register-username', 'buyer_new_verify')
  await fill('#register-phone', '13900001111')
  await fill('#register-nickname', '')
  await fill('#register-password', 'Verify1234')
  await evaluate(`(() => {
    const checkbox = document.querySelector('.auth-check input[type="checkbox"]')
    if (!checkbox) throw new Error('未找到协议复选框')
    checkbox.click()
    return true
  })()`)
  const nicknameOptional = await evaluate(
    "!document.querySelector('#register-nickname').required && !!document.querySelector('.password-hints')",
  )
  record('昵称选填且密码强度提示可用', nicknameOptional)
  await layout('桌面注册页')
  await screenshot('auth-register-desktop')

  await viewport(390, 844)
  await call('Page.reload', { ignoreCache: true })
  await sleep(1200)
  await layout('移动端注册页')
  await screenshot('auth-register-mobile')

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