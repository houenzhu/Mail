/**
 * M4 第二切片 UI 验收：退款列表/详情 + 订单发货弹窗。
 *
 * 真实登录后走页面，不注入 token：
 *  1. 退款页渲染状态 Tab、表格、筛选栏，详情抽屉展示审计字段；
 *  2. 退款 Tab 切换后列表全部为对应状态；
 *  3. 订单详情里「已支付」订单出现发货按钮，弹窗可填写并提交；
 *  4. 发货后订单状态变为「已发货」，物流信息回显。
 *
 * 用法：node scripts/verify-refund.mjs [baseUrl] [outDir]
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://localhost:5174'
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9338
const USER_DATA = join(tmpdir(), `mall-admin-refund-${Date.now()}`)

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
  console.log('退款与发货页验收（M4 第二切片）')
  await call('Page.navigate', { url: `${BASE_URL}/login?redirect=/refund/list` })
  await sleep(2000)
  console.log('  填表:', await fillForm('admin', 'Admin@123456'))
  console.log('  点击登录:', await clickLogin())
  await sleep(3000)

  const afterLogin = JSON.parse(await evaluate(`JSON.stringify({ path: location.pathname })`))
  check('登录后按 redirect 进入退款页', afterLogin.path === '/refund/list', afterLogin.path)

  await call('Page.navigate', { url: `${BASE_URL}/refund/list` })
  await sleep(2500)
  const refundPage = JSON.parse(await evaluate(`JSON.stringify({
    tabs: [...document.querySelectorAll('.el-tabs__item')].map(e => e.innerText.trim()),
    rows: document.querySelectorAll('.el-table__body tbody tr').length,
    headers: [...document.querySelectorAll('.el-table__header th')].map(e => e.innerText.trim()).filter(Boolean),
    summary: document.querySelector('.table-head .text-muted')?.innerText.trim() || '',
    filters: [...document.querySelectorAll('.filter-form .el-form-item__label')].map(e => e.innerText.trim())
  })`))
  console.log('  退款页状态:', refundPage)
  await shot('30-refund-list')
  check('退款状态 Tab 已渲染（全部 + 3 态）', refundPage.tabs.length === 4, refundPage.tabs.join('/'))
  check('退款表格有数据', refundPage.rows > 0, `${refundPage.rows} 行`)
  check('表头含退款单号/订单号/退款金额/状态',
    ['退款单号','订单号','退款金额','状态'].every(h => refundPage.headers.includes(h)),
    refundPage.headers.join(' / '))
  check('筛选栏含退款单号与订单号', refundPage.filters.includes('退款单号') && refundPage.filters.includes('订单号'),
    refundPage.filters.join(' / '))
  check('顶部统计显示退款单总数', /共\s*[\d,]+\s*笔退款单/.test(refundPage.summary), refundPage.summary)

  console.log('\n  打开退款详情')
  const refundOpened = await evaluate(`(() => {
    const btn = document.querySelector('.el-table__body tbody tr td .el-button');
    if (!btn) return 'no-button';
    btn.click();
    return 'clicked';
  })()`)
  await sleep(2000)
  const refundDetail = JSON.parse(await evaluate(`JSON.stringify({
    drawerVisible: !!document.querySelector('.el-drawer'),
    labels: [...document.querySelectorAll('.el-drawer .el-descriptions__label')].map(e => e.innerText.trim()),
    hasAlert: !!document.querySelector('.el-drawer .el-alert')
  })`))
  console.log('  退款详情:', refundDetail)
  await shot('31-refund-detail')
  check('退款详情抽屉打开', refundDetail.drawerVisible && refundOpened === 'clicked')
  check('详情含支付单号与渠道退款单号',
    refundDetail.labels.includes('支付单号') && refundDetail.labels.includes('渠道退款单号'),
    refundDetail.labels.join(' / '))
  check('详情含发起方审计字段', refundDetail.labels.includes('发起方'), refundDetail.labels.join(' / '))

  await evaluate(`document.querySelector('.el-drawer__close-btn')?.click()`)
  await sleep(600)

  console.log('\n  切换到「退款成功」Tab')
  await evaluate(`(() => {
    const tab = [...document.querySelectorAll('.el-tabs__item')].find(e => e.innerText.trim() === '退款成功');
    if (!tab) return 'not-found';
    tab.click();
    return 'clicked';
  })()`)
  await sleep(2000)
  const refundTab = JSON.parse(await evaluate(`JSON.stringify({
    rows: document.querySelectorAll('.el-table__body tbody tr').length,
    statuses: [...document.querySelectorAll('.el-table__body tbody tr')].map(r => r.querySelector('.el-tag')?.innerText.trim()).filter(Boolean)
  })`))
  console.log('  Tab 状态:', refundTab)
  await shot('32-refund-success-tab')
  check('退款成功 Tab 有数据', refundTab.rows > 0, `${refundTab.rows} 行`)
  check('Tab 筛选生效（全部退款成功）',
    refundTab.statuses.length > 0 && refundTab.statuses.every(s => s === '退款成功'),
    refundTab.statuses.join(','))

  console.log('\n  订单发货弹窗')
  await call('Page.navigate', { url: `${BASE_URL}/order/list` })
  await sleep(2500)
  await evaluate(`(() => {
    const tab = [...document.querySelectorAll('.el-tabs__item')].find(e => e.innerText.trim() === '已支付');
    if (tab) tab.click();
  })()`)
  await sleep(2000)
  await evaluate(`(() => {
    const btn = document.querySelector('.el-table__body tbody tr td .el-button');
    if (btn) btn.click();
  })()`)
  await sleep(2000)
  const shipState = JSON.parse(await evaluate(`JSON.stringify({
    shipButton: [...document.querySelectorAll('.el-drawer button')].some(b => b.innerText.trim() === '发货'),
    status: document.querySelector('.el-drawer .el-tag')?.innerText.trim() || ''
  })`))
  console.log('  发货按钮状态:', shipState)
  await shot('33-order-paid-detail')
  check('已支付订单详情出现「发货」按钮', shipState.shipButton && shipState.status === '已支付', JSON.stringify(shipState))

  if (shipState.shipButton) {
    await evaluate(`(() => {
      const btn = [...document.querySelectorAll('.el-drawer button')].find(b => b.innerText.trim() === '发货');
      if (btn) btn.click();
    })()`)
    await sleep(1200)
    const dialogState = JSON.parse(await evaluate(`JSON.stringify({
      visible: !!document.querySelector('.el-dialog'),
      title: document.querySelector('.el-dialog__title')?.innerText.trim() || '',
      inputs: document.querySelectorAll('.el-dialog .el-input__inner').length,
      tip: document.querySelector('.el-dialog .text-muted')?.innerText.trim() || ''
    })`))
    console.log('  发货弹窗:', dialogState)
    await shot('34-ship-dialog')
    check('发货弹窗打开且含两个输入框', dialogState.visible && dialogState.title === '订单发货' && dialogState.inputs === 2,
      JSON.stringify(dialogState))
    check('弹窗提示单号不可修改', dialogState.tip.includes('不可修改'), dialogState.tip)

    const filled = await evaluate(`(() => {
      const setVal = (el, v) => {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(el, v);
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      };
      const inputs = [...document.querySelectorAll('.el-dialog .el-input__inner')];
      if (inputs.length < 2) return 'inputs-not-found';
      setVal(inputs[0], '顺丰速运');
      setVal(inputs[1], 'SF-UI-' + Date.now());
      return 'filled';
    })()`)
    console.log('  填写物流:', filled)
    await evaluate(`(() => {
      const btn = [...document.querySelectorAll('.el-dialog button')].find(b => b.innerText.trim() === '确认发货');
      if (btn) btn.click();
    })()`)
    await sleep(3000)
    const shipped = JSON.parse(await evaluate(`JSON.stringify({
      dialogVisible: (() => {
        const el = document.querySelector('.el-overlay-dialog');
        return !!el && getComputedStyle(el).display !== 'none' && el.offsetParent !== null;
      })(),
      status: document.querySelector('.el-drawer .el-tag')?.innerText.trim() || '',
      labels: [...document.querySelectorAll('.el-drawer .el-descriptions__label')].map(e => e.innerText.trim()),
      timeline: document.querySelectorAll('.el-drawer .el-timeline-item').length
    })`))
    console.log('  发货后:', shipped)
    await shot('35-order-shipped')
    check('发货成功：弹窗关闭', !shipped.dialogVisible)
    check('发货成功：状态变为已发货', shipped.status === '已发货', shipped.status)
    check('发货成功：回显物流公司与单号',
      shipped.labels.includes('物流公司') && shipped.labels.includes('物流单号'), shipped.labels.join(' / '))
    check('发货成功：时间线新增一条', shipped.timeline >= 3, `${shipped.timeline} 条`)
  }

  console.log(failures === 0 ? '\n✅ 退款与发货验收全部通过' : `\n❌ ${failures} 项未通过`)
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
