<template>
  <router-link
    class="activity-card"
    :to="{ name: 'activity-detail', params: { id: activity.activityId } }"
  >
    <div class="activity-card__media">
      <CoverImage
        v-if="activity.coverUrl"
        :src="activity.coverUrl"
        :alt="activity.activityTitle"
        image-class="activity-card__media-img"
        fallback-class="activity-card__media-fallback"
        fallback-text="封面失效"
      />
      <div v-else class="activity-card__media-empty">
        <span class="activity-card__monogram" aria-hidden="true">{{ monogram }}</span>
        <span class="activity-card__media-type">{{ typeShort }}</span>
      </div>
    </div>

    <div class="activity-card__body">
      <div class="activity-card__meta">
        <span class="activity-card__type">{{ formatActivityType(activity.activityType) }}</span>
        <span>{{ formatActivityStatusByActivity(activity) }}</span>
      </div>

      <h3 class="activity-card__title">{{ activity.activityTitle }}</h3>
      <p v-if="activity.activityDesc" class="activity-card__desc">{{ activity.activityDesc }}</p>

      <div class="activity-card__footer">
        <span class="stamp">{{ stamp }}</span>
        <span class="activity-card__people">
          {{ activity.currentParticipant }}/{{ activity.maxParticipant || '不限' }} 人
        </span>
      </div>

      <p class="activity-card__location">
        {{ formatShortDateTime(activity.startTime) }} ·
        {{ activity.activityLocation || '地点待定' }}
      </p>

      <div v-if="activity.tags?.length" class="activity-card__tags">
        <span v-for="tag in activity.tags.slice(0, 2)" :key="tag.tagId">
          {{ tag.tagName }}
        </span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
import { computed } from 'vue'
import CoverImage from '@/components/CoverImage.vue'
import {
  formatActivityStatusByActivity,
  formatActivityType,
  formatActivityTypeShort,
  formatShortDateTime,
  formatStamp,
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

const typeShort = computed(() => formatActivityTypeShort(props.activity.activityType))
const stamp = computed(() => formatStamp(props.activity))
const monogram = computed(() => {
  const title = String(props.activity.activityTitle || '')
    .replace(/^[【\[（(][^】\]）)]*[】\]）)]\s*/, '')
    .trim()
  const first = [...title][0] || '活'
  return first.toUpperCase()
})
</script>
