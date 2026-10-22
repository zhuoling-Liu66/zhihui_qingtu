<template>
  <div class="ai-action" :class="stateClass">
    <div class="ai-action__top">
      <span class="ai-action__badge">待确认报名</span>
      <span class="ai-action__expire">有效期至 {{ expireText }}</span>
    </div>

    <p class="ai-action__title">
      {{ action.summary?.activityTitle || action.summary?.text || '待确认的报名' }}
    </p>
    <p v-if="detailText" class="ai-action__meta">{{ detailText }}</p>

    <p v-if="error" class="ai-action__error">{{ error }}</p>

    <div class="ai-action__foot">
      <button
        type="button"
        class="ai-action__btn ai-action__btn--primary"
        :disabled="locked"
        @click="$emit('confirm')"
      >
        {{ confirmText }}
      </button>
      <button type="button" class="ai-action__btn" :disabled="locked" @click="$emit('cancel')">
        取消
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  action: { type: Object, required: true },
  status: { type: String, default: '' },
  error: { type: String, default: '' },
  result: { type: Object, default: null },
})

defineEmits(['confirm', 'cancel'])

const stateClass = computed(() => {
  if (props.status === 'executed') return 'ai-action--done'
  if (props.status === 'cancelled') return 'ai-action--muted'
  if (props.status === 'failed') return 'ai-action--failed'
  return ''
})

/** 已执行/已取消/执行中都锁住按钮：重复点确认虽然后端幂等，但没必要制造困惑 */
const locked = computed(() =>
  ['executed', 'cancelled', 'executing'].includes(props.status),
)

const confirmText = computed(() => {
  if (props.status === 'executing') return '正在报名…'
  if (props.status === 'executed') return '已报名'
  if (props.status === 'cancelled') return '已取消'
  return '确认报名'
})

const expireText = computed(() => {
  const raw = props.action.expiresAt
  return raw ? String(raw).replace('T', ' ').slice(5, 16) : '5 分钟内'
})

const detailText = computed(() => {
  const summary = props.action.summary || {}
  const parts = []
  if (summary.startTime) {
    parts.push(String(summary.startTime).replace('T', ' ').slice(5, 16))
  }
  if (summary.activityLocation) {
    parts.push(summary.activityLocation)
  }
  if (summary.remaining !== null && summary.remaining !== undefined) {
    parts.push(`剩 ${summary.remaining} 个名额`)
  }
  return parts.join(' · ')
})
</script>

<style scoped>
.ai-action {
  border: 1px solid rgba(255, 183, 0, 0.55);
  background: linear-gradient(135deg, rgba(255, 250, 235, 0.98), #ffffff);
  border-radius: 14px;
  padding: 12px 14px;
  box-shadow: 0 8px 20px rgba(255, 183, 0, 0.12);
}

.ai-action--done {
  border-color: rgba(16, 185, 129, 0.45);
  background: linear-gradient(135deg, rgba(236, 253, 245, 0.98), #ffffff);
  box-shadow: none;
}

.ai-action--muted {
  border-color: rgba(148, 163, 184, 0.5);
  background: #f8fafc;
  box-shadow: none;
  opacity: 0.85;
}

.ai-action--failed {
  border-color: rgba(239, 68, 68, 0.45);
  background: linear-gradient(135deg, rgba(254, 242, 242, 0.98), #ffffff);
}

.ai-action__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ai-action__badge {
  font-size: 12px;
  font-weight: 600;
  color: #92400e;
  background: rgba(255, 212, 0, 0.25);
  border-radius: 999px;
  padding: 2px 8px;
}

.ai-action--done .ai-action__badge {
  color: #047857;
  background: rgba(16, 185, 129, 0.16);
}

.ai-action__expire {
  font-size: 11px;
  color: #94a3b8;
}

.ai-action__title {
  margin: 8px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: #1d2433;
  line-height: 1.45;
}

.ai-action__meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6b7280;
}

.ai-action__error {
  margin: 8px 0 0;
  font-size: 12px;
  color: #b91c1c;
}

.ai-action__foot {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.ai-action__btn {
  flex: 1;
  border: 1px solid rgba(37, 49, 67, 0.14);
  background: #fff;
  color: #334155;
  border-radius: 10px;
  padding: 7px 10px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.18s ease, opacity 0.18s ease;
}

.ai-action__btn--primary {
  background: linear-gradient(135deg, #ffd400, #ffb700);
  border-color: transparent;
  color: #3b2f00;
  font-weight: 600;
}

.ai-action__btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
