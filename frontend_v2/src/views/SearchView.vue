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
            <span class="topic-tab__symbol">{{ entry.icon }}</span>
            <span class="topic-tab__label">{{ entry.label }}</span>
          </button>
        </div>
      </aside>

      <section class="search-main">
        <div class="search-summary card-surface">
          <p class="section-head__kicker">搜索结果</p>
          <h1>“{{ keyword || '全部活动' }}” 的搜索结果</h1>
          <p>共 {{ totalElements }} 条活动，关键词会同时匹配标题、描述、地点和标签。</p>

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
            v-for="activity in activities"
            :key="activity.activityId"
            :activity="activity"
            dense
          />
        </div>

        <p v-if="!loading && !activities.length" class="search-empty">
          没有找到匹配的活动，换个关键词试试。
        </p>

        <el-pagination
          v-if="totalElements > pageSize"
          class="search-pagination"
          layout="prev, pager, next, total"
          :current-page="pageNo + 1"
          :page-size="pageSize"
          :total="totalElements"
          @current-change="handlePageChange"
        />
      </section>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActivityCard from '@/components/ActivityCard.vue'
import { activityApi } from '@/api/platform'
import { homeTopicEntries } from '@/constants/ui'

const route = useRoute()
const router = useRouter()

const keyword = ref(route.query.q || '')
const activeChip = ref('all')
const activities = ref([])

// 分页交给后端：以前只取前 60 条再在前端过滤，
// 导致"排在第 61 条之后、或关键词只出现在描述/地点/标签里"的活动永远搜不到。
const pageNo = ref(0)
const pageSize = 12
const totalElements = ref(0)
const loading = ref(false)

// 快速切换筛选条件时，旧响应不能覆盖新结果（与详情页同一套思路）
let loadToken = 0

const filterChips = [
  { key: 'all', label: '全部' },
  { key: 'second-class', label: '第二课堂' },
  { key: 'volunteer', label: '志愿服务' },
  { key: 'sports', label: '运动健身' },
  { key: 'interest', label: '兴趣团建' },
]

const typeMap = {
  'second-class': 1,
  volunteer: 2,
  sports: 3,
  interest: 4,
}

const loadData = async () => {
  const requestId = ++loadToken
  loading.value = true
  try {
    const page = await activityApi.list({
      pageNo: pageNo.value,
      pageSize,
      keyword: keyword.value.trim() || undefined,
      activityType: activeChip.value === 'all' ? undefined : typeMap[activeChip.value],
    })
    if (requestId !== loadToken) return
    activities.value = page.records || []
    totalElements.value = page.totalElements || 0
  } finally {
    if (requestId === loadToken) {
      loading.value = false
    }
  }
}

const handlePageChange = (current) => {
  pageNo.value = current - 1
  loadData()
}

watch(keyword, () => {
  pageNo.value = 0
  loadData()
})

watch(activeChip, () => {
  pageNo.value = 0
  loadData()
})

watch(
  () => route.query.q,
  (value) => {
    keyword.value = value || ''
  },
)

onMounted(loadData)
</script>
