/**
 * 整页截图工具：保留首页完整长图，便于视觉评审与回归对比。
 * 用法：node scripts/shot.mjs [baseUrl]
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'

const BASE_URL = process.argv[2] || 'http://127.0.0.1:5175'
const OUT_DIR = resolve('verify-out')
const PORT = 9336
const USER_DATA = join(tmpdir(), `mall-shot-${Date.now()}`)

const chromePath = [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
].find((p) => existsSync(p))

if (!chromePath) {
  console.error('未找到 Chrome/Edge')
  process.exit(2)
}

mkdirSync(OUT_DIR, { recursive: true })
const chrome = spawn(
  chromePath,
  ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${USER_DATA}`, '--hide-scrollbars', 'about:blank'],
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
  width: 1440,
  height: 900,
  deviceScaleFactor: 1,
  mobile: false,
})
await call('Page.navigate', { url: BASE_URL })
await sleep(3000)

const { result } = await call('Runtime.evaluate', {
  expression: 'document.body.scrollHeight',
  returnByValue: true,
})
const height = result.value

await call('Emulation.setDeviceMetricsOverride', {
  width: 1440,
  height,
  deviceScaleFactor: 1,
  mobile: false,
})
await sleep(900)

const { data } = await call('Page.captureScreenshot', { format: 'png' })
const file = join(OUT_DIR, 'home-full.png')
writeFileSync(file, Buffer.from(data, 'base64'))
console.log(`整页截图: ${file} (1440×${height})`)

ws.close()
chrome.kill()
