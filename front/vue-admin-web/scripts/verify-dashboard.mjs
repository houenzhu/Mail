/**
 * M5 数据看板 UI 验收：真实登录后打开看板，断言指标、趋势图与待办跳转。
 *
 * 不注入 token、不 mock 接口：页面上的每个数字都应来自
 * `GET /api/admin/dashboard`，即订单 / 支付 / 库存三个服务的实时聚合。
 *
 * 校验点：
 *  1. 带 redirect 的登录能落到 /dashboard；
 *  2. 4 张指标卡都有非空数值，且 GMV 带 ¥、金额格式正确；
 *  3. 两张 SVG 趋势图都有折线路径与日期刻度，无空白图；
 *  4. 库存区渲染售罄率与 4 个 SKU 统计，待办渲染 2 项；
 *  5. 切换「近 30 天」后图表刻度增加；
 *  6. 点击待办能进入对应订单列表并带上状态筛选。
 *
 * 用法：node scripts/verify-dashboard.mjs [baseUrl] [outDir]
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://localhost:5174'
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9339
const USER_DATA = join(tmpdir(), `mall-admin-dashboard-${Date.now()}`)

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
ws.addEventListener('message', (e) => {
  const m = JSON.parse(e.data)
  if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
    console.log('BROWSER-LOG', JSON.stringify(m.params.entry).slice(0, 300))
  }
})

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

async function readDashboard() {
  return JSON.parse(await evaluate(`JSON.stringify({
    path: location.pathname,
    title: document.querySelector('.page-title')?.innerText.trim() || '',
    hasError: !!document.querySelector('.el-alert--error'),
    errorText: document.querySelector('.el-alert__title')?.innerText.trim() || '',
    cards: [...document.querySelectorAll('.metric-card')].map(c => ({
      label: c.querySelector('.metric-label')?.innerText.trim() || '',
      value: c.querySelector('.metric-value')?.innerText.trim() || ''
    })),
    charts: [...document.querySelectorAll('svg.chart')].map(s => ({
      label: s.getAttribute('aria-label') || '',
      path: s.querySelector('path.line-primary, path.line-secondary')?.getAttribute('d') || '',
      dots: s.querySelectorAll('circle').length,
      ticks: [...s.querySelectorAll('text.axis-label')].map(t => t.textContent.trim())
    })),
    sellOutLabel: [...document.querySelectorAll('.card-header .text-muted')].map(e => e.innerText.trim()).find(t => t.includes('售罄率')) || '',
    progressVisible: !!document.querySelector('.el-progress'),
    stockRows: [...document.querySelectorAll('.stock-item')].map(i => ({
      value: i.querySelector('.stock-value')?.innerText.trim() || '',
      label: i.querySelector('.stock-label')?.innerText.trim() || ''
    })),
    todos: [...document.querySelectorAll('.todo-item')].map(i => ({
      title: i.querySelector('.todo-title')?.innerText.trim() || '',
      count: i.querySelector('.todo-count')?.innerText.trim() || ''
    }))
  })`))
}

try {
  console.log('数据看板验收（M5）')
  await call('Page.navigate', { url: `${BASE_URL}/login?redirect=/dashboard` })
  await sleep(2000)
  console.log('  填表:', await fillForm('admin', 'Admin@123456'))
  console.log('  点击登录:', await clickLogin())
  await sleep(3500)

  const state = await readDashboard()
  console.log('  路径:', state.path, '| 卡片:', state.cards.map(c => `${c.label}=${c.value}`).join(' '))
  await shot('50-dashboard')

  check('登录后按 redirect 进入 /dashboard', state.path === '/dashboard', state.path)
  check('页面标题为「数据看板」', state.title === '数据看板', state.title)
  check('看板没有错误提示', !state.hasError, state.errorText)
  check('渲染 4 张指标卡', state.cards.length === 4, `${state.cards.length} 张`)
  check('指标卡数值均非空', state.cards.every(c => c.value && c.value !== '—'), state.cards.map(c => c.value).join(','))
  check('GMV 卡为金额格式', /^¥[\d,]+\.\d{2}$/.test(state.cards.find(c => c.label.includes('GMV'))?.value || ''), state.cards.find(c => c.label.includes('GMV'))?.value || '')
  check('两张 SVG 趋势图', state.charts.length === 2, `${state.charts.length} 张`)
  check('趋势图有折线路径', state.charts.every(c => c.path.length > 10), state.charts.map(c => c.path.length).join('/'))
  check('趋势图有点位与日期刻度', state.charts.every(c => c.dots > 0 && c.ticks.length >= 2), state.charts.map(c => `${c.dots}点/${c.ticks.length}刻度`).join(' '))
  check('库存区渲染售罄率', state.sellOutLabel.includes('售罄率'), state.sellOutLabel)
  check('库存统计 4 项', state.stockRows.length === 4, state.stockRows.map(r => r.label).join('/'))
  check('售罄率有进度条或空态说明', state.progressVisible || state.sellOutLabel.includes('—'), String(state.progressVisible))
  check('待办渲染 3 项', state.todos.length === 3, state.todos.map(t => `${t.title}=${t.count}`).join(' '))

  console.log('\n  切换到「近 30 天」')
  await evaluate(`(() => {
    const label = [...document.querySelectorAll('.el-radio-button')].find(e => e.innerText.includes('30'));
    if (!label) return 'not-found';
    label.click();
    return 'clicked';
  })()`)
  await sleep(2500)
  const monthState = await readDashboard()
  await shot('51-dashboard-30d')
  // 页面把刻度限制在最多 7 个（避免 30 天时标签重叠），所以不能用刻度数判断。
  // 用折线路径点数判断：30 天应有 30 个数据点，7 天只有 7 个。
  const monthDots = monthState.charts[0]?.dots || 0
  const weekDots = state.charts[0]?.dots || 0
  check('近 30 天图表刷新为 30 个点', monthDots === 30 && weekDots === 7, `${weekDots} → ${monthDots}`)

  console.log('\n  点击待办「待发货」')
  const clicked = await evaluate(`(() => {
    const item = [...document.querySelectorAll('.todo-item')].find(i => i.innerText.includes('待发货'));
    if (!item) return 'not-found';
    item.click();
    return 'clicked';
  })()`)
  await sleep(2500)
  const afterTodo = JSON.parse(await evaluate(`JSON.stringify({
    path: location.pathname,
    search: location.search,
    hasTable: !!document.querySelector('.el-table'),
    activeTab: document.querySelector('.el-tabs__item.is-active')?.innerText.trim() || ''
  })`))
  console.log('  跳转后:', afterTodo.path + afterTodo.search, '| Tab:', afterTodo.activeTab)
  await shot('52-dashboard-todo-order')
  check('点击待办进入订单列表', clicked === 'clicked' && afterTodo.path === '/order/list', afterTodo.path + afterTodo.search)
  check('订单列表带状态筛选', afterTodo.search.includes('status=20') || afterTodo.activeTab.includes('已支付'), afterTodo.search || afterTodo.activeTab)
  check('订单表格已渲染', afterTodo.hasTable)

  console.log(failures === 0 ? '\n✅ 数据看板验收全部通过' : `\n❌ ${failures} 项未通过`)
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
