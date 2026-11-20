<template>
  <div class="ai-assistant">
    <button type="button" class="ai-fab" :aria-label="'打开 AI 助手'" @click="open">
      <span class="ai-fab__glow" aria-hidden="true"></span>
      <span class="ai-fab__mark">AI</span>
      <span class="ai-fab__label">小助手</span>
    </button>

    <el-drawer
      v-model="visible"
      class="ai-drawer"
      :with-header="false"
      size="420px"
      :close-on-click-modal="true"
    >
      <section class="ai-panel">
        <header class="ai-panel__head">
          <div class="ai-panel__id">
            <span class="ai-panel__avatar">AI</span>
            <div>
              <strong>青途小助手</strong>
              <small>{{ quotaText }}</small>
            </div>
          </div>
          <div class="ai-panel__head-actions">
            <button type="button" class="ai-ghost" @click="newChat">新对话</button>
            <button type="button" class="ai-ghost" @click="visible = false">关闭</button>
          </div>
        </header>

        <div ref="scrollRef" class="ai-panel__body">
          <div v-if="!messages.length" class="ai-empty">
            <p class="ai-empty__title">想找点什么？</p>
            <p class="ai-empty__sub">可以按时间、类型、学分找活动，也可以让我帮你找还缺人的队伍。</p>
            <button
              v-for="question in suggestions"
              :key="question"
              type="button"
              class="ai-suggestion"
              @click="send(question)"
            >
              {{ question }}
            </button>
          </div>

          <article
            v-for="message in messages"
            :key="message.key"
            class="ai-msg"
            :class="`ai-msg--${message.role}`"
          >
            <template v-if="message.role !== 'system'">
              <div class="ai-bubble">
                <p class="ai-bubble__text">{{ message.content }}<span v-if="message.status === 'streaming'" class="ai-caret">▍</span></p>
                <p v-if="message.toolText" class="ai-bubble__tool">{{ message.toolText }}</p>
              </div>
            </template>
            <p v-else class="ai-system">{{ message.content }}</p>

            <div v-if="message.cards?.activities?.length" class="ai-cards">
              <AiActivityCard
                v-for="activity in message.cards.activities"
                :key="`a-${activity.activityId}`"
                :activity="activity"
                @open="openActivity(activity, message)"
              />
            </div>

            <div v-if="message.cards?.teams?.length" class="ai-cards">
              <AiTeamCard
                v-for="team in message.cards.teams"
                :key="`t-${team.teamId}`"
                :team="team"
                @open="openTeam(team, message)"
              />
            </div>

            <AiActionCard
              v-if="message.action"
              :action="message.action"
              :status="message.actionStatus"
              :error="message.actionError"
              :result="message.actionResult"
              @confirm="confirmAction(message)"
              @cancel="cancelAction(message)"
            />

            <p v-if="message.error" class="ai-error">{{ message.error }}</p>
          </article>
        </div>

        <footer class="ai-panel__foot">
          <textarea
            v-model="draft"
            class="ai-input"
            rows="1"
            maxlength="2000"
            placeholder="说你想找的活动，比如：这周末有什么志愿活动"
            @keydown.enter.exact.prevent="handleSend"
          ></textarea>
          <button
            type="button"
            class="ai-send"
            :class="{ 'ai-send--stop': streaming }"
            :disabled="!streaming && !draft.trim()"
            @click="handleSend"
          >
            {{ streaming ? '停止' : '发送' }}
          </button>
        </footer>
      </section>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { aiApi } from '@/api/ai'
import { useSession } from '@/composables/useSession'
import { useAiChat } from '@/composables/useAiChat'
import AiActivityCard from './AiActivityCard.vue'
import AiTeamCard from './AiTeamCard.vue'
import AiActionCard from './AiActionCard.vue'

const emit = defineEmits(['require-auth'])

const router = useRouter()
const { isLoggedIn } = useSession()
const {
  messages,
  streaming,
  scrollTick,
  send: sendMessage,
  stop,
  reset,
  confirmAction,
  cancelAction,
} = useAiChat()

const visible = ref(false)
const draft = ref('')
const quota = ref(null)
const scrollRef = ref(null)

const suggestions = [
  '这周末有什么志愿活动',
  '推荐点能拿美育学分的活动',
  '还有缺人的队伍吗',
]

const quotaText = computed(() => {
  if (!quota.value) return '正在连接…'
  if (!quota.value.enabled) return 'AI 功能未启用'
  return `今日剩余 ${Number(quota.value.remainingTokens || 0).toLocaleString()} tokens`
})

function open() {
  if (!isLoggedIn.value) {
    emit('require-auth')
    return
  }
  visible.value = true
  loadQuota()
}

async function loadQuota() {
  try {
    quota.value = await aiApi.quota()
  } catch {
    // 额度接口失败不影响聊天，静默即可
  }
}

// 一轮生成结束（streaming 由 true→false）就重拉配额。
// 后端在发 usage/done 事件之前已 recordTokens 落库，所以此刻取到的就是最新剩余量；
// 否则"今日剩余 tokens"会一直停在打开抽屉时的旧值，非得刷新页面才更新。
// 用边沿判断（oldValue && !value）避免初始化/重复触发；停止、报错同样会走到这里，重拉无副作用。
watch(streaming, (value, oldValue) => {
  if (oldValue && !value) {
    loadQuota()
  }
})

function handleSend() {
  if (streaming.value) {
    stop()
    return
  }
  const text = draft.value.trim()
  if (!text) return
  draft.value = ''
  sendMessage(text)
}

function send(text) {
  if (streaming.value) return
  draft.value = ''
  sendMessage(text)
}

function newChat() {
  reset()
  draft.value = ''
  loadQuota()
}

function openActivity(activity, message) {
  reportClick('ACTIVITY', activity.activityId, message)
  visible.value = false
  router.push(`/activities/${activity.activityId}`)
}

function openTeam(team, message) {
  reportClick('TEAM', team.teamId, message)
  visible.value = false
  router.push(`/teams/${team.teamId}`)
}

/** 点击埋点失败不阻断跳转：统计是锦上添花，导航是主路径。 */
function reportClick(targetType, targetId, message) {
  aiApi
    .feedback({
      targetType,
      targetId,
      event: 'CLICK',
      scene: 'CHAT',
      messageId: message?.messageId,
    })
    .catch(() => {})
}

watch(scrollTick, async () => {
  await nextTick()
  const el = scrollRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
})

watch(visible, (value) => {
  if (value) {
    nextTick(() => {
      const el = scrollRef.value
      if (el) el.scrollTop = el.scrollHeight
    })
  }
})
</script>

<style scoped>
.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2200;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 8px 8px;
  border: 1px solid var(--line, rgba(31, 27, 22, 0.09));
  border-radius: 10px;
  background: var(--paper-strong, #fffdf9);
  color: var(--ink, #1f1b16);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(31, 27, 22, 0.08);
  transition:
    background-color 150ms cubic-bezier(0.22, 1, 0.36, 1),
    border-color 150ms cubic-bezier(0.22, 1, 0.36, 1);
}

.ai-fab:hover {
  background: var(--paper, #f7f4ee);
  border-color: var(--line-strong, rgba(31, 27, 22, 0.16));
  transform: none;
  box-shadow: 0 8px 24px rgba(31, 27, 22, 0.08);
}

.ai-fab__mark {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 5px;
  background: var(--ink, #1f1b16);
  color: var(--brand-yellow, #f2c200);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0;
}

.ai-fab__glow {
  display: none;
}

.ai-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.ai-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
}

.ai-panel__id {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-panel__avatar {
  display: inline-grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffd400, #ffb700);
  color: #3b2f00;
  font-weight: 800;
  font-size: 13px;
}

.ai-panel__id strong {
  display: block;
  font-size: 15px;
  color: #1d2433;
}

.ai-panel__id small {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
}

.ai-panel__head-actions {
  display: flex;
  gap: 6px;
}

.ai-ghost {
  border: 1px solid rgba(37, 49, 67, 0.12);
  background: #fff;
  color: #475569;
  border-radius: 8px;
  padding: 5px 9px;
  font-size: 12px;
  cursor: pointer;
}

.ai-ghost:hover {
  border-color: rgba(255, 183, 0, 0.5);
  color: #92400e;
}

.ai-panel__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 18px 24px;
  background: #fbfbfc;
}

.ai-empty {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 24px;
}

.ai-empty__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1d2433;
}

.ai-empty__sub {
  margin: 0 0 6px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}

.ai-suggestion {
  text-align: left;
  border: 1px solid rgba(37, 49, 67, 0.1);
  background: #fff;
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 13px;
  color: #334155;
  cursor: pointer;
  transition: border-color 0.18s ease, transform 0.18s ease;
}

.ai-suggestion:hover {
  border-color: rgba(255, 183, 0, 0.55);
  transform: translateX(2px);
}

.ai-msg {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.ai-msg--user {
  align-items: flex-end;
}

.ai-msg--assistant {
  align-items: flex-start;
}

.ai-bubble {
  max-width: 92%;
  border-radius: 14px;
  padding: 10px 13px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.07);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.03);
}

.ai-msg--user .ai-bubble {
  background: linear-gradient(135deg, #fff6d6, #fff1bf);
  border-color: rgba(255, 183, 0, 0.35);
}

.ai-bubble__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: #1d2433;
  /* 不开 Markdown 渲染：纯文本 + pre-wrap 已够用，且天然免疫 HTML 注入 */
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-bubble__tool {
  margin: 6px 0 0;
  font-size: 11px;
  color: #94a3b8;
}

.ai-caret {
  display: inline-block;
  margin-left: 2px;
  color: #ffb700;
  animation: ai-blink 1s step-end infinite;
}

@keyframes ai-blink {
  50% {
    opacity: 0;
  }
}

.ai-system {
  align-self: center;
  margin: 0;
  padding: 4px 10px;
  font-size: 12px;
  color: #047857;
  background: rgba(16, 185, 129, 0.1);
  border-radius: 999px;
}

.ai-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.ai-error {
  margin: 0;
  font-size: 12px;
  color: #b91c1c;
}

.ai-panel__foot {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px 16px;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
  background: #fff;
}

.ai-input {
  flex: 1;
  min-height: 42px;
  max-height: 120px;
  resize: none;
  border: 1px solid rgba(37, 49, 67, 0.14);
  border-radius: 12px;
  padding: 10px 12px;
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: #1d2433;
  outline: none;
  transition: border-color 0.18s ease;
}

.ai-input:focus {
  border-color: rgba(255, 183, 0, 0.7);
}

.ai-send {
  border: none;
  border-radius: 12px;
  padding: 11px 16px;
  background: linear-gradient(135deg, #ffd400, #ffb700);
  color: #3b2f00;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}

.ai-send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ai-send--stop {
  background: #1d2433;
  color: #fff;
}

@media (max-width: 480px) {
  .ai-fab {
    right: 16px;
    bottom: 16px;
  }
}
</style>

<style>
/* el-drawer 被 teleport 到 body，scoped 样式选不中，只能写全局 */
.ai-drawer {
  max-width: 100vw;
}

.ai-drawer .el-drawer__body {
  padding: 0;
  overflow: hidden;
}
</style>
