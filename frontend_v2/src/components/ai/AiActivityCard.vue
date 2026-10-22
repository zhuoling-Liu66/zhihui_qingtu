<template>
  <button type="button" class="ai-card" @click="$emit('open', activity)">
    <div class="ai-card__top">
      <span class="ai-card__type">{{ activity.activityTypeName || '校园活动' }}</span>
      <span v-if="activity.remaining !== null && activity.remaining !== undefined" class="ai-card__badge">
        剩 {{ activity.remaining }} 个名额
      </span>
    </div>
    <p class="ai-card__title">{{ activity.activityTitle }}</p>
    <p class="ai-card__meta">
      {{ timeText }}<template v-if="activity.activityLocation"> · {{ activity.activityLocation }}</template>
    </p>
    <p v-if="activity.tags && activity.tags.length" class="ai-card__tags">
      <span v-for="tag in activity.tags" :key="tag.tagId" class="ai-tag">{{ tag.tagName }}</span>
    </p>
    <p v-if="activity.reason" class="ai-card__reason">{{ activity.reason }}</p>
  </button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  activity: { type: Object, required: true },
})
defineEmits(['open'])

/** 后端返回 ISO-8601（带 T），这里只展示到分钟。 */
const timeText = computed(() => {
  const raw = props.activity.startTime
  return raw ? String(raw).replace('T', ' ').slice(0, 16) : '时间待定'
})
</script>

<style scoped>
.ai-card {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid rgba(37, 49, 67, 0.1);
  border-radius: 14px;
  padding: 12px 14px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
  font: inherit;
  color: inherit;
}

.ai-card:hover {
  border-color: rgba(255, 183, 0, 0.55);
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.ai-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ai-card__type {
  font-size: 12px;
  color: #8a6d00;
  background: rgba(255, 212, 0, 0.18);
  border-radius: 999px;
  padding: 2px 8px;
}

.ai-card__badge {
  font-size: 12px;
  color: #b45309;
  background: rgba(251, 146, 60, 0.14);
  border-radius: 999px;
  padding: 2px 8px;
  white-space: nowrap;
}

.ai-card__title {
  margin: 8px 0 4px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  color: #1d2433;
}

.ai-card__meta {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}

.ai-card__tags {
  margin: 6px 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ai-tag {
  font-size: 11px;
  color: #475569;
  background: #f1f5f9;
  border-radius: 6px;
  padding: 1px 6px;
}

.ai-card__reason {
  margin: 8px 0 0;
  font-size: 12px;
  color: #0f766e;
  background: rgba(20, 184, 166, 0.08);
  border-radius: 8px;
  padding: 6px 8px;
  line-height: 1.5;
}
</style>
