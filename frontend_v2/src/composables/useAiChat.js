import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi, streamChat } from '@/api/ai'

/**
 * 对话状态机。
 *
 * <p>把"发消息、收事件、更新气泡、确认动作"集中在这里，组件只负责渲染。
 * 这样 SSE 解析、错误分类、中断这些容易写错的逻辑不会被 JSX/模板淹没。
 */
export function useAiChat() {
  /** 消息列表：{ key, role, content, cards, action, status, error } */
  const messages = ref([])
  /** 是否正在生成 */
  const streaming = ref(false)
  /** 会话ID；首轮由后端 meta 事件返回 */
  const conversationId = ref(null)
  /** 每次内容变化 +1，供组件 watch 后自动滚到底部 */
  const scrollTick = ref(0)

  let controller = null

  function reset() {
    controller?.abort()
    controller = null
    streaming.value = false
    conversationId.value = null
    messages.value = []
  }

  function stop() {
    controller?.abort()
    controller = null
    streaming.value = false
    const last = messages.value[messages.value.length - 1]
    if (last?.status === 'streaming') {
      last.status = 'done'
    }
  }

  /**
   * 发送一条消息。
   *
   * @param {string} text 用户输入
   */
  async function send(text) {
    const content = (text || '').trim()
    if (!content || streaming.value) {
      return
    }

    messages.value.push({
      key: `u-${Date.now()}`,
      role: 'user',
      content,
      cards: null,
      action: null,
      status: 'done',
    })

    // 必须用 reactive() 包一层：push 进响应式数组后，局部变量若仍指向原始对象，
    // 下面 SSE 回调里的 assistant.content += ... 会绕过 Vue 的 proxy 写入，
    // 视图收不到通知 —— 表现就是"流式输出不生效"，要等整段结束才一次性显示。
    const assistant = reactive({
      key: `a-${Date.now()}`,
      role: 'assistant',
      content: '',
      cards: { activities: [], teams: [] },
      action: null,
      actionStatus: '',
      actionError: '',
      actionResult: null,
      usage: null,
      toolText: '',
      status: 'streaming',
      error: '',
    })
    messages.value.push(assistant)
    scrollTick.value += 1
    streaming.value = true
    controller = new AbortController()

    try {
      await streamChat(
        { conversationId: conversationId.value, message: content },
        {
          meta(data) {
            conversationId.value = data.conversationId
            assistant.messageId = data.messageId
          },
          delta(data) {
            assistant.content += data.text || ''
            scrollTick.value += 1
          },
          tool(data) {
            if (data.status === 'started') {
              assistant.toolText = toolLabel(data.name)
            } else {
              assistant.toolText = ''
            }
            scrollTick.value += 1
          },
          card(data) {
            if (data.type === 'activity') {
              // 同一条回复里可能重复出现同一个活动，按 ID 去重
              if (!assistant.cards.activities.some((item) => item.activityId === data.data.activityId)) {
                assistant.cards.activities.push(data.data)
              }
            } else if (data.type === 'team') {
              if (!assistant.cards.teams.some((item) => item.teamId === data.data.teamId)) {
                assistant.cards.teams.push(data.data)
              }
            }
            scrollTick.value += 1
          },
          action(data) {
            assistant.action = data
            assistant.actionStatus = data.status
            scrollTick.value += 1
          },
          usage(data) {
            assistant.usage = data
          },
          done() {
            assistant.status = 'done'
            assistant.toolText = ''
            scrollTick.value += 1
          },
          error(data) {
            assistant.status = 'error'
            assistant.error = data.message || '生成失败'
            scrollTick.value += 1
          },
        },
        controller.signal,
      )
      if (assistant.status === 'streaming') {
        assistant.status = 'done'
      }
    } catch (error) {
      if (error?.name === 'AbortError') {
        assistant.status = 'done'
      } else {
        assistant.status = 'error'
        assistant.error = error?.message || '请求失败'
        // 401 已由 streamChat 处理；429/503 这里补一句可操作的提示
        if (error?.code === 429) {
          ElMessage.warning('今日额度用完或发得太快，稍后再试')
        } else if (error?.code === 503) {
          ElMessage.error('AI 暂时不可用，可以先看看推荐')
        }
      }
    } finally {
      streaming.value = false
      controller = null
      scrollTick.value += 1
    }
  }

  /**
   * 确认待确认动作（真正写库）。
   */
  async function confirmAction(message) {
    const action = message.action
    if (!action || message.actionStatus === 'executing' || message.actionStatus === 'executed') {
      return
    }
    message.actionStatus = 'executing'
    try {
      const result = await aiApi.confirmAction(action.actionId)
      message.actionStatus = 'executed'
      message.actionResult = result?.result || null
      messages.value.push({
        key: `s-${Date.now()}`,
        role: 'system',
        content: `已报名「${result?.result?.activityTitle || '该活动'}」`,
        status: 'done',
      })
    } catch (error) {
      message.actionStatus = 'failed'
      message.actionError = error?.response?.data?.message || '确认失败，请重试'
    } finally {
      scrollTick.value += 1
    }
  }

  /**
   * 取消待确认动作。
   */
  async function cancelAction(message) {
    const action = message.action
    if (!action || message.actionStatus === 'executing') {
      return
    }
    try {
      await aiApi.cancelAction(action.actionId)
      message.actionStatus = 'cancelled'
    } catch (error) {
      message.actionError = error?.response?.data?.message || '取消失败'
    } finally {
      scrollTick.value += 1
    }
  }

  function toolLabel(name) {
    const labels = {
      search_activities: '正在查询活动…',
      get_activity_detail: '正在查看活动详情…',
      search_teams: '正在查找队伍…',
      get_my_profile: '正在读取你的兴趣偏好…',
      get_my_enrollments: '正在查看你的报名记录…',
      get_my_credit_overview: '正在统计你的学分…',
      propose_enroll: '正在准备报名确认…',
    }
    return labels[name] || '正在处理…'
  }

  return {
    messages,
    streaming,
    conversationId,
    scrollTick,
    send,
    stop,
    reset,
    confirmAction,
    cancelAction,
  }
}
