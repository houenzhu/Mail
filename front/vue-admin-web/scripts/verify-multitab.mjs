/**
 * M4 收尾验收：多标签页令牌同步。
 *
 * 后端刷新令牌一次一换，重放旧令牌会强制全端下线。因此这里必须证明：
 *  1. 两个标签页**同时**触发 401 时，只有一次 `/auth/refresh` 真正发出；
 *  2. 刷新结果被两个标签共享，两边都保持登录且能继续访问受保护接口；
 *  3. 一个标签退出登录，另一个标签跟随回登录页，不会用死令牌继续请求。
 *
 * 用法：node scripts/verify-multitab.mjs [baseUrl] [outDir]
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://localhost:5174'
const OUT_DIR = resolve(process.argv[3] || 'verify-out')
const PORT = 9339
const USER_DATA = join(tmpdir(), `mall-admin-multitab-${Date.now()}`)

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

async function openTab(url) {
  const { targetId } = await send('Target.createTarget', { url })
  const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true })
  const call = (m, p) => send(m, p, sessionId)
  await call('Page.enable')
  await call('Runtime.enable')
  const evaluate = async (expr) => {
    const r = await call('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text)
    return r.result.value
  }
  const shot = async (name) => {
    const { data } = await call('Page.captureScreenshot', { format: 'png' })
    writeFileSync(join(OUT_DIR, `${name}.png`), Buffer.from(data, 'base64'))
  }
  return { targetId, evaluate, shot, call, close: () => send('Target.closeTarget', { targetId }) }
}

async function fillLogin(tab, username, password) {
  return tab.evaluate(`
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

async function clickLogin(tab) {
  return tab.evaluate(`
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

/**
 * 在页面内埋点统计 /auth/refresh 次数。
 * axios 在浏览器里走 XHR，另有一部分代码用 fetch，两条路径都要拦。
 * 埋点必须在制造 401 之前完成，否则会漏掉请求。
 */
const INSTRUMENT = `
  (() => {
    if (window.__refreshLog) return window.__refreshLog.length;
    window.__refreshLog = [];
    const isRefresh = (url) => String(url).includes('/auth/refresh');

    const open = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (method, url, ...rest) {
      if (isRefresh(url)) window.__refreshLog.push(String(url));
      return open.call(this, method, url, ...rest);
    };

    const fetchOrig = window.fetch;
    window.fetch = function (...args) {
      const url = typeof args[0] === 'string' ? args[0] : args[0]?.url;
      if (isRefresh(url)) window.__refreshLog.push(String(url));
      return fetchOrig.apply(this, args);
    };
    return 0;
  })()
`

try {
  console.log('场景 A：标签页 1 真实登录')
  const tab1 = await openTab(`${BASE_URL}/login`)
  await sleep(2000)
  console.log('  填表:', await fillLogin(tab1, 'operator01', 'Operator@123456'))
  console.log('  点击登录:', await clickLogin(tab1))
  await sleep(2200)
  const loginState = JSON.parse(
    await tab1.evaluate(`JSON.stringify({
      path: location.pathname,
      hasToken: !!localStorage.getItem('mall_admin_access_token'),
      hasRefresh: !!localStorage.getItem('mall_admin_refresh_token')
    })`),
  )
  console.log('  结果:', loginState)
  await tab1.shot('40-multitab-login')
  check('登录成功进入工作台', loginState.path === '/workbench', loginState.path)
  check('访问/刷新令牌都已写入', loginState.hasToken && loginState.hasRefresh)

  console.log('\n场景 B：标签页 2 复用同一会话')
  const tab2 = await openTab(`${BASE_URL}/product/list`)
  await sleep(2200)
  const tab2State = JSON.parse(
    await tab2.evaluate(`JSON.stringify({
      path: location.pathname,
      rows: document.querySelectorAll('.el-table__body tbody tr').length
    })`),
  )
  console.log('  结果:', tab2State)
  check('新标签直接进入受保护页面', tab2State.path === '/product/list', tab2State.path)
  check('新标签能拉到真实数据', tab2State.rows > 0, `${tab2State.rows} 行`)

  console.log('\n场景 C：两个标签同时触发令牌过期')
  // 先把两个标签都改为坏令牌，再同时发请求，制造真实并发
  await Promise.all([
    tab1.evaluate(`(() => {
      const k = 'mall_admin_access_token';
      localStorage.setItem(k, localStorage.getItem(k) + '__expired__');
      return localStorage.getItem(k);
    })()`),
    tab2.evaluate(`(() => {
      const k = 'mall_admin_access_token';
      localStorage.setItem(k, localStorage.getItem(k) + '__expired__');
      return localStorage.getItem(k);
    })()`),
  ])

  await Promise.all([
    tab1.evaluate(INSTRUMENT),
    tab2.evaluate(INSTRUMENT),
  ])

  // 必须走应用自己的 axios 实例：原生 fetch 不经过拦截器，测不到无感刷新
  const result = await Promise.all([
    tab1.evaluate(`(async () => {
      const { default: http } = await import('/src/api/request.js');
      const [a, b] = await Promise.allSettled([
        http.post('/admin/product/page', { pageNo: 1, pageSize: 5 }),
        http.get('/auth/me'),
      ]);
      return [a.status, b.status];
    })()`),
    tab2.evaluate(`(async () => {
      const { default: http } = await import('/src/api/request.js');
      const [a, b] = await Promise.allSettled([
        http.post('/admin/product/page', { pageNo: 1, pageSize: 5 }),
        http.get('/auth/me'),
      ]);
      return [a.status, b.status];
    })()`),
  ])
  await sleep(1500)
  console.log('  两个标签各自两个请求的结果:', result)
  check(
    '两个标签的请求都成功（无感刷新生效）',
    result.flat().every((s) => s === 'fulfilled'),
    JSON.stringify(result),
  )

  const refreshHits = await Promise.all([
    tab1.evaluate('window.__refreshLog.length'),
    tab2.evaluate('window.__refreshLog.length'),
  ])
  console.log('  两个标签各自观测到的 refresh 次数:', refreshHits)
  // 关键断言：跨标签互斥生效时，整个浏览器只发出一次 refresh
  check('只发生一次令牌刷新（跨标签互斥）', refreshHits[0] + refreshHits[1] === 1, String(refreshHits))

  const tokens = await Promise.all([
    tab1.evaluate(`localStorage.getItem('mall_admin_access_token')`),
    tab2.evaluate(`localStorage.getItem('mall_admin_access_token')`),
  ])
  check('两个标签共享同一个新令牌', Boolean(tokens[0]) && tokens[0] === tokens[1], String(tokens[0]?.slice(0, 12)))
  check('新令牌不再是过期标记', !String(tokens[0]).endsWith('__expired__'))

  console.log('\n场景 D：标签页 1 退出登录，标签页 2 跟随')
  await tab1.evaluate(`localStorage.removeItem('mall_admin_access_token'); localStorage.removeItem('mall_admin_refresh_token'); 'cleared'`)
  await sleep(1500)
  const tab2After = JSON.parse(
    await tab2.evaluate(`JSON.stringify({ path: location.pathname, hasToken: !!localStorage.getItem('mall_admin_access_token') })`),
  )
  console.log('  结果:', tab2After)
  await tab2.shot('41-multitab-logout')
  check('另一标签被同步回登录页', tab2After.path === '/login', tab2After.path)
  check('令牌已被清除', !tab2After.hasToken)
} catch (e) {
  console.error('执行失败:', e.message)
  failures++
} finally {
  ws.close()
  chrome.kill()
  await sleep(300)
  try { rmSync(USER_DATA, { recursive: true, force: true }) } catch {}
}

console.log(failures ? `\n❌ ${failures} 项未通过` : '\n✅ 多标签页令牌同步全部通过')
process.exitCode = failures ? 1 : 0
