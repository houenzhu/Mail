/**
 * 登录流程验收：真实走一遍「填表 → 提交 → 跳转 → 商品列表」，
 * 而不是靠注入 token 抄近路。同时验证买家账号被正确拒绝。
 *
 * 用法：node scripts/verify-login.mjs [baseUrl] [outDir]
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://localhost:5174'
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9336
const USER_DATA = join(tmpdir(), `mall-admin-login-${Date.now()}`)

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

/** 在 Element Plus 表单里填值：必须派发 input 事件，否则 v-model 不更新 */
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

async function clickLogin() {
  return evaluate(`
    (() => {
      const btn = [...document.querySelectorAll('button')].find(b => b.innerText.replace(/\\s/g,'') === '登录');
      if (!btn) return 'btn-not-found';
      btn.click();
      return 'clicked';
    })()
  `)
}

let failures = 0
function check(name, ok, detail = '') {
  console.log(`  ${ok ? '✅' : '❌'} ${name}${detail ? ' — ' + detail : ''}`)
  if (!ok) failures++
}

try {
  console.log('场景 A：正确账号 operator01 登录')
  await call('Page.navigate', { url: `${BASE_URL}/login` })
  await sleep(2000)
  console.log('  填表:', await fillForm('operator01', 'Operator@123456'))
  console.log('  点击登录:', await clickLogin())
  await sleep(2200)

  const afterLogin = JSON.parse(
    await evaluate(`JSON.stringify({
      path: location.pathname,
      hasSidebar: !!document.querySelector('.sidebar'),
      menuItems: [...document.querySelectorAll('.side-menu .el-menu-item, .side-menu .el-sub-menu__title')].map(e => e.innerText.trim()).filter(Boolean).slice(0, 8),
      hasToken: !!localStorage.getItem('mall_admin_access_token'),
      hasOwnerMap: !!localStorage.getItem('mall_admin_mock_token_owner'),
      userText: document.querySelector('.user-area')?.innerText.replace(/\\s+/g,' ') || ''
    })`),
  )
  console.log('  结果:', afterLogin)
  await shot('10-after-login')
  check('登录后进入工作台', afterLogin.path === '/workbench', afterLogin.path)
  check('后台框架已渲染（侧边菜单存在）', afterLogin.hasSidebar)
  check('令牌已写入', afterLogin.hasToken)
  check('顶部显示当前用户', afterLogin.userText.includes('运营小张'), afterLogin.userText)

  console.log('\n场景 A2：从工作台进入商品列表')
  await call('Page.navigate', { url: `${BASE_URL}/product/list` })
  await sleep(2000)
  const onList = JSON.parse(
    await evaluate(`JSON.stringify({
      rows: document.querySelectorAll('.el-table__body tbody tr').length,
      tabs: [...document.querySelectorAll('.el-tabs__item')].map(e => e.innerText.trim())
    })`),
  )
  console.log('  结果:', onList)
  await shot('12-product-list-real')
  check('用已登录会话可直接访问商品列表', onList.rows > 0, `${onList.rows} 行`)
  // 计数是独立 span，innerText 形如 "全部\n3"（不是 "(3)"），因此断言"每个 Tab 末尾都是数字"
  check(
    '状态 Tab 带计数',
    onList.tabs.length > 0 && onList.tabs.every((t) => /\d+\s*$/.test(t)),
    onList.tabs.join(' / '),
  )

  console.log('\n场景 B：退出登录')
  await evaluate(`localStorage.clear(); 'cleared'`)
  await call('Page.navigate', { url: `${BASE_URL}/product/list` })
  await sleep(1800)
  const afterClear = JSON.parse(
    await evaluate(`JSON.stringify({ path: location.pathname, hasLoginForm: !!document.querySelector('.login-page') })`),
  )
  console.log('  结果:', afterClear)
  check('无令牌访问受保护路由 → 回到登录页', afterClear.path === '/login' && afterClear.hasLoginForm)

  console.log('\n场景 C：买家账号 buyer01 应被拒绝')
  await fillForm('buyer01', 'Buyer@123456')
  console.log('  点击登录:', await clickLogin())
  await sleep(1800)
  const afterBuyer = JSON.parse(
    await evaluate(`JSON.stringify({
      path: location.pathname,
      toast: [...document.querySelectorAll('.el-message')].map(e => e.innerText.trim()).join(' | ')
    })`),
  )
  console.log('  结果:', afterBuyer)
  await shot('11-buyer-denied')
  check('仍停留在登录页', afterBuyer.path === '/login', afterBuyer.path)
  check('给出拒绝提示', /后台访问权限|无权限/.test(afterBuyer.toast), afterBuyer.toast)
} catch (e) {
  console.error('执行失败:', e.message)
  failures++
} finally {
  ws.close()
  chrome.kill()
  await sleep(300)
  try { rmSync(USER_DATA, { recursive: true, force: true }) } catch {}
}

console.log(failures ? `\n❌ ${failures} 项未通过` : '\n✅ 登录流程全部通过')
process.exitCode = failures ? 1 : 0
