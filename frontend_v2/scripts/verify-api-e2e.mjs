/**
 * 前端接口对齐 · 端到端验证（打真实后端）
 * ======================================
 *
 * 本脚本加载 `frontend_v2` 的真实源码，对 **真实后端（localhost:8080）** 跑一遍关键流程，
 * 用来回答一个问题：**把请求拦截器修好之后，前端到底能不能正常用了？**
 *
 * 与 `verify-token-injection.mjs` 的分工：
 *   那个脚本用本地 echo 服务证明"token 确实发出了"（不依赖后端）；
 *   本脚本证明"带着 token 打真实后端能拿到数据"（不依赖拦截器读取姿势）。
 *   两个脚本都用断言，不用"回看 axios config"的脆弱写法 —— 原因见前者的注释。
 *
 * 用法（需先启动后端 + MySQL + Redis）：
 *   cd frontend_v2
 *   node --import ./scripts/node-vite-alias-hook.mjs scripts/verify-api-e2e.mjs
 *
 * 环境变量：
 *   BACKEND   后端地址，默认 http://localhost:8080
 *   STUDENT_NO / PASSWORD  测试账号，默认 T0001 / test123456
 *
 * 退出码：0 = 全部通过；1 = 有断言失败；2 = 脚本自身出错
 */
import http from 'node:http'

const BACKEND = process.env.BACKEND || 'http://localhost:8080'
const STUDENT_NO = process.env.STUDENT_NO || 'T0001'
const PASSWORD = process.env.PASSWORD || 'test123456'

// ── 让 axios 在 Node 里发真实请求 ────────────────────────────────
const { default: axios } = await import('axios')
axios.defaults.adapter = 'http'

const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
}

const { default: api } = await import('../src/api/http.js')
const { authApi, activityApi, enrollApi } = await import('../src/api/platform.js')
const { useSession, getToken } = await import('../src/composables/useSession.js')

api.defaults.baseURL = `${BACKEND}/api`

const session = useSession()

let pass = 0
let fail = 0
const check = (cond, label, detail = '') => {
  console.log(`  ${cond ? '✅' : '❌'} ${label}${detail ? `  ${detail}` : ''}`)
  cond ? pass++ : fail++
}
const section = (t) => console.log(`\n──────── ${t} ────────`)

async function main() {
  // ══════════════════════════════════════════════════════════════
  section('0. 先确认后端活着')
  // ══════════════════════════════════════════════════════════════
  const alive = await new Promise((resolve) => {
    const req = http.request({ method: 'GET', host: new URL(BACKEND).hostname, port: new URL(BACKEND).port, path: '/api/auth/login' }, (res) => resolve(res.statusCode))
    req.on('error', () => resolve(null))
    req.end()
  })
  if (alive === null) {
    throw new Error(`连不上后端 ${BACKEND}，请先启动应用（mvnw spring-boot:run）`)
  }
  check(true, `后端可连通 ${BACKEND}`, `探针 HTTP ${alive}`)

  // ══════════════════════════════════════════════════════════════
  section('1. 未登录：受保护接口应被拒（修复前前端就卡在这里）')
  // ══════════════════════════════════════════════════════════════
  {
    let rejected = false
    let code = null
    try {
      await activityApi.tags()
    } catch (error) {
      rejected = true
      code = error.response?.data?.code
    }
    check(rejected, '未登录请求被拒', `code=${code}`)
    check(code === 4010, '业务码是 4010（未登录），与后端 TokenInterceptor 约定一致')
  }

  // ══════════════════════════════════════════════════════════════
  section('2. 登录 → token 落库')
  // ══════════════════════════════════════════════════════════════
  {
    const data = await authApi.login({ studentNo: STUDENT_NO, password: PASSWORD })
    session.setUser(data)
    check(Boolean(data.token), '登录成功并返回 token')
    check(getToken() === data.token, 'getToken() 取到的就是登录返回的 token')
    check(session.isLoggedIn.value === true, '会话状态为已登录')
  }

  // ══════════════════════════════════════════════════════════════
  section('3. 已有接口：修复后应全部可用（原来全是 401）')
  // ══════════════════════════════════════════════════════════════
  let sampleActivityId = null
  {
    const tags = await activityApi.tags()
    check(Array.isArray(tags) && tags.length > 0, 'GET /activities/tags', `${tags.length} 个标签`)

    const page = await activityApi.list({ pageNo: 0, pageSize: 5 })
    check(page?.records?.length > 0, 'GET /activities', `共 ${page?.totalElements} 条`)
    sampleActivityId = page?.records?.[0]?.activityId ?? null

    if (sampleActivityId) {
      const detail = await activityApi.detail(sampleActivityId)
      check(detail?.activityId === sampleActivityId, `GET /activities/${sampleActivityId}`)
      check(
        [1, 3, 4].includes(detail?.displayStatus),
        '详情里带 displayStatus（后端按 endTime 现算，不是缓存里冻住的值）',
        `displayStatus=${detail?.displayStatus}`,
      )
    }

    const enrollments = await enrollApi.list({ userId: STUDENT_NO, pageNo: 0, pageSize: 3 })
    check(Array.isArray(enrollments?.records), 'GET /enrollments（我的报名）', `共 ${enrollments?.totalElements} 条`)
  }

  // ══════════════════════════════════════════════════════════════
  section('4. Redis 改造新增的三个接口')
  // ══════════════════════════════════════════════════════════════
  {
    // 4.1 剩余名额
    if (sampleActivityId) {
      const stock = await activityApi.stock(sampleActivityId)
      check(typeof stock?.limited === 'boolean', `GET /activities/${sampleActivityId}/stock 可用`,
        `remaining=${stock?.remaining} limited=${stock?.limited}`)
      check(
        stock.limited ? typeof stock.remaining === 'number' : stock.remaining === null,
        '不限人数时 remaining 为 null（前端据此不展示"剩余名额"）',
      )
    }

    // 4.2 热门榜
    const hot = await activityApi.hot(5)
    check(Array.isArray(hot), 'GET /activities/hot 可用', `返回 ${hot.length} 条`)
    if (hot.length >= 2) {
      const counts = hot.map((a) => a.currentParticipant ?? 0)
      const descending = counts.every((v, i) => i === 0 || counts[i - 1] >= v)
      check(descending, '榜单按报名人数降序（真读 Redis ZSet）', `人数序列 ${counts.join(' ≥ ')}`)
    } else {
      console.log('  ℹ️ 榜单不足 2 条，跳过排序断言（跑 docs/api-test/redis-gate-proof.mjs 会有数据）')
    }

    // 4.3 登出：必须让服务端 token 立即失效
    const beforeLogout = await api.get(`/users/${STUDENT_NO}`).then(() => 'ok', () => 'fail')
    check(beforeLogout === 'ok', '登出前：受保护接口可访问')

    await authApi.logout()
    check(true, 'POST /auth/logout 调用成功')

    let afterStatus = null
    let afterCode = null
    try {
      await api.get(`/users/${STUDENT_NO}`)
    } catch (error) {
      afterStatus = error.response?.status
      afterCode = error.response?.data?.code
    }
    check(afterStatus === 401, '★ 登出后同一 token 立即失效（不是等 12 小时）', `HTTP ${afterStatus}`)
    check(afterCode === 4010, '业务码 4010')
    check(getToken() === '', '★ 响应拦截器已自动清掉本地失效 token')
    check(session.isLoggedIn.value === false, '会话状态回到未登录')
  }

  // ══════════════════════════════════════════════════════════════
  section('5. 401 兜底：重新登录后应恢复正常')
  // ══════════════════════════════════════════════════════════════
  {
    const data = await authApi.login({ studentNo: STUDENT_NO, password: PASSWORD })
    session.setUser(data)
    const tags = await activityApi.tags()
    check(tags.length > 0, '重新登录后接口恢复可用（验证 401 清理没有破坏后续登录）')
  }

  // ══════════════════════════════════════════════════════════════
  console.log('\n════════════════ 结果 ════════════════')
  console.log(`通过 ${pass} 项，失败 ${fail} 项`)
  console.log('══════════════════════════════════════')
  process.exitCode = fail > 0 ? 1 : 0
}

main().catch((error) => {
  console.error('\n脚本执行失败：', error.message)
  process.exitCode = 2
})
