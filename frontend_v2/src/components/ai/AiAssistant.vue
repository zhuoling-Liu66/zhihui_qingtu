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
  right: 28px;
  bottom: 28px;
  z-index: 2200;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px 12px 14px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #ffd400, #ffb700);
  color: #3b2f00;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 14px 34px rgba(255, 183, 0, 0.42);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.ai-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 40px rgba(255, 183, 0, 0.5);
}

.ai-fab__mark {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(59, 47, 0, 0.9);
  color: #ffd400;
  font-size: 11px;
  letter-spacing: 0.5px;
}

.ai-fab__glow {
  position: absolute;
  inset: -6px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(255, 212, 0, 0.45), transparent 70%);
  animation: ai-pulse 2.6s ease-in-out infinite;
  pointer-events: none;
}

@keyframes ai-pulse {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(0.96);
  }
  50% {
    opacity: 0.9;
    transform: scale(1.04);
  }
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
