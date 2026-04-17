<template>
  <main class="page page-search-view">
    <section class="page-section search-layout">
      <aside class="search-side card-surface">
        <div class="section-head section-head--compact">
          <div>
            <p class="section-head__kicker">分类入口</p>
            <h3>快速跳转</h3>
          </div>
        </div>
        <div class="topic-tabs topic-tabs--stack">
          <button
            v-for="entry in homeTopicEntries.filter((item) => item.type === 'section')"
            :key="entry.slug"
            class="topic-tab"
            type="button"
            @click="router.push({ name: 'section', params: { slug: entry.slug } })"
          >
            <span>{{ entry.icon }}</span>
            {{ entry.label }}
          </button>
        </div>
      </aside>

      <section class="search-main">
        <div class="search-summary card-surface">
          <p class="section-head__kicker">搜索结果</p>
          <h1>“{{ keyword || '全部活动' }}” 的搜索结果</h1>
          <p>参考闲鱼搜索页的结果式布局，当前优先展示和关键词最相关的活动卡片。</p>

          <div class="search-filters">
            <button
              v-for="entry in filterChips"
              :key="entry.label"
              :class="['topic-tab', { 'topic-tab--active': activeChip === entry.key }]"
              type="button"
              @click="activeChip = entry.key"
            >
              {{ entry.label }}
            </button>
          </div>
        </div>

        <div class="search-result-grid">
          <ActivityCard
            v-for="activity in filteredResults"
            :key="activity.activityId"
            :activity="activity"
            dense
          />
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActivityCard from '@/components/ActivityCard.vue'
import { activityApi } from '@/api/platform'
import { homeTopicEntries } from '@/constants/ui'
import { matchKeyword } from '@/utils/catalog'

const route = useRoute()
const router = useRouter()

const keyword = ref(route.query.q || '')
const activeChip = ref('all')
const activities = ref([])

const filterChips = [
  { key: 'all', label: '全部' },
  { key: 'second-class', label: '第二课堂' },
  { key: 'volunteer', label: '志愿服务' },
  { key: 'sports', label: '运动健身' },
  { key: 'interest', label: '兴趣团建' },
]

const searchResults = computed(() =>
  (activities.value || []).filter((activity) => matchKeyword(activity, keyword.value)),
)

const filteredResults = computed(() => {
  if (activeChip.value === 'all') {
    return searchResults.value
  }

  const typeMap = {
    'second-class': 1,
    volunteer: 2,
    sports: 3,
    interest: 4,
  }

  return searchResults.value.filter((activity) => activity.activityType === typeMap[activeChip.value])
})

const loadData = async () => {
  const page = await activityApi.list({ pageNo: 0, pageSize: 60 })
  activities.value = page.records || []
}

watch(
  () => route.query.q,
  (value) => {
    keyword.value = value || ''
  },
)

onMounted(loadData)
</script>
