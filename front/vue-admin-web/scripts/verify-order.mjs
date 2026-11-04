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
await call('Log.enable')
await call('Runtime.enable')
ws.addEventListener('message', (e) => { const m = JSON.parse(e.data); if (m.method === 'Log.entryAdded') { console.log('BROWSER-LOG', JSON.stringify(m.params.entry).slice(0, 400)) } })
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
  console.log('订单管理页验收（M4 查询切片）')
  await call('Page.navigate', { url: `${BASE_URL}/login?redirect=/order/list` })
  await sleep(2000)
  console.log('  填表:', await fillForm('admin', 'Admin@123456'))
  console.log('  点击登录:', await clickLogin())
  await sleep(3000)

  const afterLogin = JSON.parse(await evaluate(`JSON.stringify({
    path: location.pathname,
    hasSidebar: !!document.querySelector('.sidebar')
  })`))
  console.log('  登录后路径:', afterLogin.path)
  check('登录后按 redirect 直接进入订单页', afterLogin.path === '/order/list', afterLogin.path)

  await call('Page.navigate', { url: `${BASE_URL}/order/list` })
  await sleep(2500)

  const pageState = JSON.parse(await evaluate(`JSON.stringify({
    tabs: [...document.querySelectorAll('.el-tabs__item')].map(e => e.innerText.trim()),
    rows: document.querySelectorAll('.el-table__body tbody tr').length,
    headers: [...document.querySelectorAll('.el-table__header th')].map(e => e.innerText.trim()).filter(Boolean),
    summary: document.querySelector('.table-head .text-muted')?.innerText.trim() || '',
    firstOrderNo: document.querySelector('.el-table__body tbody tr td .el-button')?.innerText.trim() || '',
    hasEmpty: !!document.querySelector('.el-empty')
  })`))
  console.log('  页面状态:', pageState)
  await shot('20-order-list')

  check('状态 Tab 已渲染（7 个状态 + 全部）', pageState.tabs.length === 8, `${pageState.tabs.length} 个`)
  check('订单表格有数据', pageState.rows > 0, `${pageState.rows} 行`)
  check('表头含关键列', ['订单号','状态','应付金额','收货人'].every(h => pageState.headers.includes(h)), pageState.headers.join(' / '))
  check('顶部统计显示总笔数', /共\s*[\d,]+\s*笔订单/.test(pageState.summary), pageState.summary)

  // 打开详情抽屉
  console.log('\n  打开订单详情')
  const opened = await evaluate(`(() => {
    const btn = document.querySelector('.el-table__body tbody tr td .el-button');
    if (!btn) return 'no-button';
    btn.click();
    return 'clicked';
  })()`)
  await sleep(2000)
  const detailState = JSON.parse(await evaluate(`JSON.stringify({
    drawerVisible: !!document.querySelector('.el-drawer'),
    title: document.querySelector('.el-drawer__title')?.innerText.trim() || '',
    descriptions: [...document.querySelectorAll('.el-descriptions__label')].map(e => e.innerText.trim()).slice(0, 8),
    itemRows: document.querySelectorAll('.el-drawer .el-table__body tbody tr').length,
    timelineItems: document.querySelectorAll('.el-drawer .el-timeline-item').length,
    sectionTitles: [...document.querySelectorAll('.el-drawer .section-title')].map(e => e.innerText.trim())
  })`))
  console.log('  详情状态:', detailState)
  await shot('21-order-detail')
  check('详情抽屉打开', detailState.drawerVisible && opened === 'clicked')
  check('详情含商品明细', detailState.itemRows > 0, `${detailState.itemRows} 行`)
  check('详情含状态流转时间线', detailState.timelineItems > 0, `${detailState.timelineItems} 条`)
  check('详情有分区标题', detailState.sectionTitles.includes('商品明细') && detailState.sectionTitles.includes('状态流转'), detailState.sectionTitles.join(' / '))

  // 状态 Tab 切换
  console.log('\n  切换到「已支付」Tab')
  await evaluate(`(() => {
    const tab = [...document.querySelectorAll('.el-tabs__item')].find(e => e.innerText.includes('已支付'));
    if (!tab) return 'not-found';
    tab.click();
    return 'clicked';
  })()`)
  await sleep(2000)
  const tabState = JSON.parse(await evaluate(`JSON.stringify({
    rows: document.querySelectorAll('.el-table__body tbody tr').length,
    statuses: [...document.querySelectorAll('.el-table__body tbody tr')].map(r => r.querySelector('.el-tag')?.innerText.trim()).filter(Boolean)
  })`))
  console.log('  Tab 状态:', tabState)
  await shot('22-order-paid-tab')
  check('切换 Tab 后仍有数据', tabState.rows > 0, `${tabState.rows} 行`)
  check('Tab 筛选生效（全是已支付）', tabState.statuses.length > 0 && tabState.statuses.every(s => s === '已支付'), tabState.statuses.join(','))

  console.log(failures === 0 ? '\n✅ 订单管理页验收全部通过' : `\n❌ ${failures} 项未通过`)
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

