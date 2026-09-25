/**
 * 验证前端真的把 token 发出去了
 * ============================
 *
 * 背景：`frontend_v2` 原本缺少 axios **请求**拦截器，导致登录拿到的 token 一直躺在
 * localStorage 里没人用 —— 后端 `TokenInterceptor` 要求每个请求带 `token` 请求头，
 * 于是除登录/注册外**所有接口都是 401**，页面全空。修复后必须证明它真的生效了。
 *
 * 为什么要用"本地 echo 服务 + 真实源码"这种方式：
 *   只读代码看不出对错，而用 axios 拦截器去"回看" config 也不行 ——
 *   **axios 的请求拦截器是 LIFO（后注册的先执行）**，我们自己加的探针拦截器
 *   反而排在 `http.js` 注入逻辑**前面**，读到的是还没注入的 config，
 *   结果是"看起来没生效"的假失败（本项目写这个脚本时就踩了两次）。
 *
 * 所以这里彻底绕开这个问题：起一个本地 HTTP 服务，让它把**实际收到的请求头**记下来。
 * 服务端收到什么就是什么，不依赖任何拦截器的执行顺序和读取姿势。
 *
 * 用法：
 *   cd frontend_v2
 *   node --import ./scripts/node-vite-alias-hook.mjs scripts/verify-token-injection.mjs
 *
 * 退出码：0 = 全部通过；1 = 有断言失败
 */
import http from 'node:http'

// ── 1. 起一个 echo 服务，记录每个请求实际收到的 token ──────────────
const received = []
const server = http.createServer((req, res) => {
  received.push({ url: req.url, token: req.headers.token ?? null })
  res.writeHead(200, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify({ code: 0, message: 'ok', data: { ok: true } }))
})
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
const port = server.address().port
console.log(`echo 服务已启动在 127.0.0.1:${port}\n`)

// ── 2. 让 axios 在 Node 里能发真实请求 ────────────────────────────
// 必须在 import http.js 之前设置：axios 实例创建时会继承 defaults.adapter
const { default: axios } = await import('axios')
axios.defaults.adapter = 'http'

// useSession 依赖 localStorage，这里给个最小实现
const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
}

// ── 3. 加载**真实源码**（未做任何改动）──────────────────────────
const { default: api } = await import('../src/api/http.js')
const { activityApi } = await import('../src/api/platform.js')
const { useSession } = await import('../src/composables/useSession.js')

// 把请求指向 echo 服务（保持 baseURL 结构：/api + 各模块的相对路径）
api.defaults.baseURL = `http://127.0.0.1:${port}/api`

// ── 4. 断言 ─────────────────────────────────────────────────────
let pass = 0
let fail = 0
const check = (cond, label, detail = '') => {
  console.log(`  ${cond ? '✅' : '❌'} ${label}${detail ? `  ${detail}` : ''}`)
  cond ? pass++ : fail++
}

// ── 场景 A：未登录 ──────────────────────────────────
console.log('【A】未登录时的请求')
await activityApi.tags()
check(received.at(-1).token === null, 'echo 服务收到的请求里没有 token（符合预期）',
  `token=${received.at(-1).token}`)

// ── 场景 B：setUser 之后 ────────────────────────────
console.log('\n【B】setUser 写入 token 之后的请求')
const FAKE = 'FAKE-JWT-TOKEN-FOR-TEST'
useSession().setUser({ userId: 'T0001', userName: '测试', token: FAKE })

await activityApi.tags()
check(received.at(-1).token === FAKE, '★ /activities/tags 请求头里带上了 token',
  `token=${received.at(-1).token}`)

await activityApi.list({ pageNo: 0, pageSize: 3 })
check(received.at(-1).token === FAKE, '★ /activities 请求头里带上了 token')

await activityApi.hot(5)
check(received.at(-1).token === FAKE, '★ /activities/hot 请求头里带上了 token')

await activityApi.stock(123)
check(received.at(-1).token === FAKE, '★ /activities/123/stock 请求头里带上了 token')

// ── 场景 C：clearUser 之后 ──────────────────────────
console.log('\n【C】clearUser（退出登录）之后的请求')
useSession().clearUser()
await activityApi.tags()
check(received.at(-1).token === null, '★ 退出后请求不再带 token', `token=${received.at(-1).token}`)

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
server.close()
process.exitCode = fail > 0 ? 1 : 0
