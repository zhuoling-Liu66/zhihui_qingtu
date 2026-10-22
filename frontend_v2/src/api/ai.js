import http from './http'
import { getToken, emitUnauthorized } from '@/composables/useSession'

/**
 * AI 助手接口封装。
 *
 * 普通接口走 axios（复用统一拦截器：401 弹登录、403 提示、错误 toast）；
 * 流式对话必须走 fetch——浏览器原生 EventSource 不能带 Authorization 头，
 * 而把 token 放进 query 参数会漏进 Nginx 访问日志和浏览器历史。
 */
export const aiApi = {
  createConversation(payload = {}) {
    return http.post('/ai/conversations', payload)
  },
  listConversations(params) {
    return http.get('/ai/conversations', { params })
  },
  listMessages(conversationId, params) {
    return http.get(`/ai/conversations/${conversationId}/messages`, { params })
  },
  removeConversation(conversationId) {
    return http.delete(`/ai/conversations/${conversationId}`)
  },
  chat(payload) {
    return http.post('/ai/chat', payload)
  },
  confirmAction(actionId) {
    return http.post(`/ai/actions/${actionId}/confirm`)
  },
  cancelAction(actionId) {
    return http.post(`/ai/actions/${actionId}/cancel`)
  },
  recommendActivities(params) {
    return http.get('/ai/recommendations/activities', { params })
  },
  recommendTeams(params) {
    return http.get('/ai/recommendations/teams', { params })
  },
  feedback(payload) {
    return http.post('/ai/feedback', payload)
  },
  quota() {
    return http.get('/ai/quota')
  },
}

/**
 * 解析一个 SSE 事件块。
 *
 * SSE 规范里 `event:` 与 `data:` 后面可以有一个可选空格，两种都要兼容；
 * 以 `:` 开头的是注释（心跳），直接忽略。
 *
 * @param {string} raw 单个事件块（不含结尾空行）
 * @param {Object} handlers 事件名 → 处理函数
 */
function dispatchEvent(raw, handlers) {
  let event = 'message'
  const dataLines = []
  for (const line of raw.split('\n')) {
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (!dataLines.length) return
  let data
  try {
    data = JSON.parse(dataLines.join('\n'))
  } catch {
    return
  }
  handlers[event]?.(data)
}

/**
 * 发起流式对话。
 *
 * @param {Object} payload { conversationId, message, context }
 * @param {Object} handlers { meta, delta, tool, card, action, usage, done, error }
 * @param {AbortSignal} signal 用于"停止生成"
 * @returns {Promise<void>}
 */
export async function streamChat(payload, handlers, signal) {
  const res = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`,
      // 不要只写 text/event-stream：参数错误时后端返回 JSON，
      // Accept 里带上 json 才能让错误响应正常协商成 JSON 而不是 406
      Accept: 'text/event-stream, application/json',
    },
    body: JSON.stringify(payload),
    signal,
  })

  const contentType = res.headers.get('content-type') || ''
  if (!res.ok || !contentType.includes('text/event-stream')) {
    const body = await res.json().catch(() => ({}))
    if (res.status === 401) {
      emitUnauthorized()
    }
    const error = new Error(body.message || '请求失败')
    error.code = body.code ?? res.status
    throw error
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    // 统一换行：CRLF 可能被切在两个 chunk 上，因此每次归一化整个 buffer
    buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
    let index
    while ((index = buffer.indexOf('\n\n')) !== -1) {
      const raw = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      dispatchEvent(raw, handlers)
    }
  }
}
