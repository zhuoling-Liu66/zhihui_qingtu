/**
 * 验证用的 `@/api/ai` 替身（仅开发期验证，不参与打包）。
 *
 * 为什么需要它：`useAiChat.js` 依赖 `streamChat` 真正去发 fetch、读 SSE。
 * 在 Node 里我们不想真的起后端，而是想**精确控制**"什么时候吐哪个 delta"，
 * 以便断言"每来一个 delta，响应式内容就更新一次"。所以把 `streamChat`
 * 委托给测试注入的 `globalThis.__streamChatImpl`，其余接口给最小实现。
 *
 * 真实的 `streamChat` SSE 解析逻辑会在 verify 脚本里用另一条路径单独验证。
 */
export const aiApi = {
  quota: async () => globalThis.__quotaResult ?? { enabled: true, remainingTokens: 1000 },
  confirmAction: async () => ({ result: { activityTitle: '测试活动' } }),
  cancelAction: async () => ({}),
  feedback: async () => ({}),
}

export async function streamChat(payload, handlers, signal) {
  if (typeof globalThis.__streamChatImpl !== 'function') {
    throw new Error('测试未注入 __streamChatImpl')
  }
  return globalThis.__streamChatImpl(payload, handlers, signal)
}