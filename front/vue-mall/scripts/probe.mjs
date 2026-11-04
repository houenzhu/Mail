/**
 * 瀑布流结构探针：等布局稳定后再测列数、列底落差、跨列与横向溢出。
 * 用法：node scripts/probe.mjs [baseUrl] [width]
 */
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://127.0.0.1:5175'
const WIDTH = Number(process.argv[3]) || 1440
const PORT = 9338
const USER_DATA = join(tmpdir(), `mall-probe-${Date.now()}`)

const chromePath = [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
].find((p) => existsSync(p))

if (!chromePath) {
  console.error('未找到 Chrome/Edge')
  process.exit(2)
}

const chrome = spawn(
  chromePath,
  ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${USER_DATA}`, 'about:blank'],
  { stdio: 'ignore' },
)

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

let wsUrl
for (let i = 0; i < 60; i++) {
  try {
    const json = await (await fetch(`http://127.0.0.1:${PORT}/json/version`)).json()
    if (json.webSocketDebuggerUrl) {
      wsUrl = json.webSocketDebuggerUrl
      break
    }
  } catch {
    // 等待浏览器启动
  }
  await sleep(200)
}

const ws = new WebSocket(wsUrl)
await new Promise((res, rej) => {
  ws.addEventListener('open', res)
  ws.addEventListener('error', rej)
})

let nextId = 1
const pending = new Map()
ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data)
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg.result)
    pending.delete(msg.id)
  }
})

const send = (method, params = {}, sessionId) =>
  new Promise((resolveResult) => {
    const id = nextId++
    pending.set(id, resolveResult)
    ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }))
  })

const { targetId } = await send('Target.createTarget', { url: 'about:blank' })
const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true })
const call = (method, params) => send(method, params, sessionId)

await call('Page.enable')
await call('Emulation.setDeviceMetricsOverride', {
  width: WIDTH,
  height: 900,
  deviceScaleFactor: 1,
  mobile: WIDTH < 600,
})
await call('Page.navigate', { url: BASE_URL })

/** 轮询直到瀑布流布局稳定：数量、容器高度、首卡位置连续两次一致 */
const stable = await (async () => {
  let previous = ''
  for (let i = 0; i < 50; i += 1) {
    await sleep(300)
    const { result } = await call('Runtime.evaluate', {
      expression: `(() => {
        const grid = document.querySelector('.masonry')
        if (!grid || !grid.classList.contains('is-ready')) return ''
        const items = [...grid.querySelectorAll(':scope > .masonry__item')]
        if (!items.length) return ''
        const first = items[0]
        return [items.length, Math.round(grid.getBoundingClientRect().height), first.style.transform].join('|')
      })()`,
      returnByValue: true,
    })
    const current = String(result.value || '')
    if (current && current === previous) return current
    previous = current
  }
  return previous
})()

if (!stable) {
  console.log(JSON.stringify({ viewport: WIDTH, error: '瀑布流未在 15 秒内完成布局' }, null, 2))
} else {
  const { result } = await call('Runtime.evaluate', {
    expression: `JSON.stringify((() => {
      const grid = document.querySelector('.masonry')
      const items = [...document.querySelectorAll('.masonry__item')]
      const cards = [...document.querySelectorAll('.recommend .product-card')]
      const gridRect = grid.getBoundingClientRect()
      const columns = Number(getComputedStyle(grid).getPropertyValue('--masonry-columns')) || 0
      const bottoms = new Map()
      items.forEach((item) => {
        const rect = item.getBoundingClientRect()
        const left = Math.round(rect.left - gridRect.left)
        bottoms.set(left, Math.max(bottoms.get(left) || 0, rect.bottom - gridRect.top))
      })
      const values = [...bottoms.values()]
      const featured = document.querySelector('.recommend .product-card--featured')
      const featuredWidth = featured?.getBoundingClientRect().width || 0
      const columnWidth = (gridRect.width - 16 * (columns - 1)) / columns
      return {
        viewport: window.innerWidth,
        columns,
        items: items.length,
        cards: cards.length,
        columnBottoms: values.map((v) => Math.round(v)),
        spread: values.length > 1 ? Math.round(Math.max(...values) - Math.min(...values)) : 0,
        featuredSpan: columnWidth > 0 ? Math.round(featuredWidth / columnWidth) : 0,
        gridHeight: Math.round(gridRect.height),
        overflow: document.body.scrollWidth > window.innerWidth + 1,
        notice: document.querySelector('.recommend__degraded')?.innerText || ''
      }
    })(), null, 2)`,
    returnByValue: true,
  })
  console.log(result.value)
}

ws.close()
chrome.kill()
