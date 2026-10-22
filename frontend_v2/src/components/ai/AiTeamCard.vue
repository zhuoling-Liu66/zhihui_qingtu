<template>
  <button type="button" class="ai-team" @click="$emit('open', team)">
    <div class="ai-team__top">
      <span class="ai-team__name">{{ team.teamName }}</span>
      <span class="ai-team__missing">还差 {{ team.missing }} 人</span>
    </div>
    <p v-if="team.activityTitle" class="ai-team__meta">活动：{{ team.activityTitle }}</p>
    <p class="ai-team__meta">
      {{ team.currentSize }}/{{ team.maxSize }} 人
      <template v-if="team.leaderName"> · 队长 {{ team.leaderName }}</template>
    </p>
    <p v-if="team.reason" class="ai-team__reason">{{ team.reason }}</p>
  </button>
</template>

<script setup>
defineProps({
  team: { type: Object, required: true },
})
defineEmits(['open'])
</script>

<style scoped>
.ai-team {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid rgba(37, 49, 67, 0.1);
  border-radius: 14px;
  padding: 12px 14px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.18s ease, transform 0.18s ease;
  font: inherit;
  color: inherit;
}

.ai-team:hover {
  border-color: rgba(59, 130, 246, 0.45);
  transform: translateY(-1px);
}

.ai-team__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ai-team__name {
  font-size: 14px;
  font-weight: 600;
  color: #1d2433;
}

.ai-team__missing {
  font-size: 12px;
  color: #1d4ed8;
  background: rgba(59, 130, 246, 0.12);
  border-radius: 999px;
  padding: 2px 8px;
  white-space: nowrap;
}

.ai-team__meta {
  margin: 6px 0 0;
  font-size: 12px;
  color: #6b7280;
}

.ai-team__reason {
  margin: 8px 0 0;
  font-size: 12px;
  color: #1d4ed8;
}
</style>
