/**
 * 验证 AI 流式输出 & 剩余 tokens 实时刷新
 * ========================================
 *
 * 背景（两个线上问题）：
 *   1. 「流式输出在前端看来没生效」——useAiChat.js 里 assistant 曾是普通对象，
 *      push 进响应式数组后，SSE 回调仍改原始对象，绕过 Vue proxy，视图不更新，
 *      整段回复要等生成结束才一次性蹦出来。修复：assistant 用 reactive() 包裹。
 *   2. 「剩余 tokens 要刷新才变」——AiAssistant.vue 只在打开抽屉/新对话时拉配额，
 *      一轮对话结束、后端已 recordTokens 后前端没重拉。修复：watch(streaming)
 *      在 true→false 边沿 loadQuota()。
 *
 * 本脚本加载**真实源码**来证明修复生效，而不是读代码"看起来对"：
 *   · 用可控的 streamChat 替身驱动真实 useAiChat，断言"每来一个 delta，
 *     响应式内容就更新一次"（问题 1 的核心）。
 *   · 用真实 useAiChat 的 streaming ref 复现组件里的 watch 边沿逻辑，
 *     断言一轮结束恰好触发一次配额重拉（问题 2 的核心）。
 *   · 另外用假 fetch + 真实 streamChat 验证 SSE 解析本身没坏（整条流式链路）。
 *
 * 用法：
 *   cd frontend_v2
 *   node --import ./scripts/node-vite-alias-hook.mjs scripts/verify-ai-stream-quota.mjs
 *
 * 退出码：0 = 全部通过；1 = 有断言失败
 */
import { registerHooks } from 'node:module'
import { pathToFileURL } from 'node:url'
import path from 'node:path'
import { effect, watch, nextTick } from 'vue'

// ── 0. 把 `@/api/ai` 指到可控替身（其余交给 --import 的别名 hook）──────────
// 后注册的 hook 先执行：这里只拦 `@/api/ai`，其余 nextResolve 交给别名 hook。
const AI_STUB = pathToFileURL(path.join(process.cwd(), 'scripts/stub-ai-api.mjs')).href
registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === '@/api/ai') {
      return { url: AI_STUB, shortCircuit: true }
    }
    return nextResolve(specifier, context)
  },
})

// useSession / http.js 依赖 localStorage，给个最小实现（真实 streamChat 路径要用）
const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
}
// useSession 在模块加载时就读 token，须提前写入正确的键名
globalThis.localStorage.setItem('campus-activity-vue-token', 'FAKE-JWT')

// ── 断言工具 ──────────────────────────────────────────────────────────────
let pass = 0
let fail = 0
const check = (cond, label, detail = '') => {
  console.log(`  ${cond ? '✅' : '❌'} ${label}${detail ? `  ${detail}` : ''}`)
  cond ? pass++ : fail++
}
const tick = (ms = 5) => new Promise((r) => setTimeout(r, ms))

// ── 加载真实 useAiChat（其内部 `@/api/ai` 已被替身接管）──────────────────
const { useAiChat } = await import('../src/composables/useAiChat.js')

// ══════════════════════════════════════════════════════════════════════════
// 【A】流式响应性：每个 delta 都应触发一次视图更新
// ══════════════════════════════════════════════════════════════════════════
console.log('【A】流式输出逐段触发视图更新（问题 1）')
{
  const parts = ['你', '好', '，', '世', '界']
  globalThis.__streamChatImpl = async (payload, handlers) => {
    handlers.meta({ conversationId: 42, messageId: 100 })
    for (const text of parts) {
      handlers.delta({ text })
      await tick() // 模拟网络分段到达
    }
    handlers.usage({ promptTokens: 10, completionTokens: 5, totalTokens: 15 })
    handlers.done()
  }

  const { messages, send } = useAiChat()
  const pending = send('测试消息') // send 同步 push 了 user + assistant 两条
  const assistant = messages.value[messages.value.length - 1]

  // 用一个响应式 effect 模拟"模板渲染读取 content"，记录每次更新后的快照
  const snapshots = []
  const stop = effect(() => {
    void assistant.content
    snapshots.push(assistant.content)
  })
  await pending
  stop()

  const final = assistant.content
  check(final === '你好，世界', '最终内容完整拼接', `content=${JSON.stringify(final)}`)
  // 修复前：快照只有 ['', '你好，世界']（一次性）。修复后：应出现逐段前缀。
  check(snapshots.length >= parts.length + 1, '渲染次数随 delta 逐次增加（非一次性）',
    `渲染 ${snapshots.length} 次，delta ${parts.length} 段`)
  const hasIntermediate = ['你', '你好', '你好，世'].every((p) => snapshots.includes(p))
  check(hasIntermediate, '出现过中间前缀快照，证明是逐段流式而非整段跳出',
    `快照=${JSON.stringify(snapshots)}`)
  check(assistant.status === 'done', '结束后状态为 done')
}

// ══════════════════════════════════════════════════════════════════════════
// 【B】配额实时刷新：一轮结束（streaming true→false）恰好重拉一次
// ══════════════════════════════════════════════════════════════════════════
console.log('\n【B】剩余 tokens 在一轮结束后自动刷新（问题 2）')
{
  globalThis.__streamChatImpl = async (payload, handlers) => {
    handlers.meta({ conversationId: 1, messageId: 1 })
    handlers.delta({ text: 'x' })
    handlers.done()
  }

  const chat = useAiChat()
  // 复现 AiAssistant.vue 里的 watch 边沿逻辑（与组件一致：oldValue && !value）
  let reloads = 0
  watch(chat.streaming, (value, oldValue) => {
    if (oldValue && !value) reloads++
  })

  await chat.send('hi')
  await nextTick() // 等 watch 回调 flush
  check(reloads === 1, '一轮生成结束恰好触发一次配额重拉', `reloads=${reloads}`)

  // 再发一轮，应再触发一次（累计 2），证明不是一次性
  await chat.send('hi again')
  await nextTick()
  check(reloads === 2, '第二轮结束再触发一次（持续生效）', `reloads=${reloads}`)
}

// ══════════════════════════════════════════════════════════════════════════
// 【C】真实 streamChat 的 SSE 解析：delta 事件被正确解析并按序回调
// ══════════════════════════════════════════════════════════════════════════
console.log('\n【C】真实 streamChat 的 SSE 解析链路')
{
  // 走相对路径导入真实 ai.js（不被 `@/api/ai` 替身拦截）
  const realAi = await import('../src/api/ai.js')
  globalThis.localStorage.setItem('token', 'FAKE-JWT')

  const sse =
    'event: meta\ndata: {"conversationId":1}\n\n' +
    'event: delta\ndata: {"text":"你好"}\n\n' +
    'event: delta\ndata: {"text":"，世界"}\n\n' +
    'event: done\ndata: {}\n\n'
  globalThis.fetch = async () => ({
    ok: true,
    status: 200,
    headers: { get: (k) => (k.toLowerCase() === 'content-type' ? 'text/event-stream' : null) },
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sse))
        controller.close()
      },
    }),
  })

  const deltas = []
  let sawMeta = false
  let sawDone = false
  await realAi.streamChat({ message: 'hi' }, {
    meta: () => { sawMeta = true },
    delta: (d) => deltas.push(d.text),
    done: () => { sawDone = true },
  }, undefined)

  check(sawMeta, 'meta 事件被解析')
  check(deltas.join('') === '你好，世界', 'delta 事件按序解析并拼接', `deltas=${JSON.stringify(deltas)}`)
  check(sawDone, 'done 事件被解析')
}

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
process.exitCode = fail > 0 ? 1 : 0