<template>
  <article class="activity-card" :class="[`activity-card--${tone}`, { 'activity-card--dense': dense }]" @click="goDetail">
    <div class="activity-card__visual">
      <CoverImage
        v-if="activity.coverUrl"
        :src="activity.coverUrl"
        :alt="activity.activityTitle"
        image-class="activity-card__cover"
        fallback-class="activity-card__cover-fallback"
        fallback-text="封面失效"
      />
      <template v-else>
        <span class="activity-card__badge">{{ badge }}</span>
        <div class="activity-card__orb"></div>
      </template>
      <div class="activity-card__meta">
        <span>{{ formatActivityType(activity.activityType) }}</span>
        <span>{{ formatActivityStatusByActivity(activity) }}</span>
      </div>
    </div>

    <div class="activity-card__body">
      <h3>{{ activity.activityTitle }}</h3>
      <p class="activity-card__desc">{{ activity.activityDesc || '暂无活动简介，点击查看完整活动信息。' }}</p>
      <div class="activity-card__tags">
        <span v-for="tag in activity.tags?.slice(0, dense ? 2 : 3) || []" :key="tag.tagId">
          {{ tag.tagName }}
        </span>
      </div>
      <div class="activity-card__footer">
        <strong>{{ formatMetric(activity) }}</strong>
        <span>{{ activity.currentParticipant }}/{{ activity.maxParticipant || '不限' }} 人</span>
      </div>
      <p class="activity-card__location">{{ activity.activityLocation || '校内待定地点' }}</p>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import CoverImage from '@/components/CoverImage.vue'
import {
  formatActivityStatusByActivity,
  formatActivityType,
  formatMetric,
  pickCardTone,
} from '@/utils/format'

const props = defineProps({
  activity: {
    type: Object,
    required: true,
  },
  dense: {
    type: Boolean,
    default: false,
  },
})

const router = useRouter()

const tone = computed(() => pickCardTone(props.activity.activityType))
const badge = computed(() => {
  if (props.activity.activityType === 1) return '学分'
  if (props.activity.activityType === 2) return '志愿'
  if (props.activity.activityType === 3) return '运动'
  return '团建'
})

const goDetail = () => {
  router.push({ name: 'activity-detail', params: { id: props.activity.activityId } })
}
</script>
