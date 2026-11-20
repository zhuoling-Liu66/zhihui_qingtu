<template>
  <main class="page page-section-view">
    <section class="page-section section-hero card-surface" :class="`section-hero--${theme}`">
      <div>
        <p class="section-head__kicker">专题页面</p>
        <h1>{{ config.title }}</h1>
        <p>{{ config.description }}</p>
        <div class="section-hero__chips">
          <span>{{ config.subtitle }}</span>
          <span>{{ totalElements }} 条活动</span>
          <span v-if="config.showTeams">{{ teams.length }} 个队伍</span>
        </div>
      </div>
      <div class="section-hero__actions">
        <el-button type="warning" size="large" @click="router.push('/publish')">发布活动</el-button>
        <el-button size="large" @click="router.push({ name: 'search', query: { q: config.title } })">去搜索页</el-button>
      </div>
    </section>

    <section class="page-section section-view-layout">
      <aside class="section-sidebar">
        <section class="side-panel card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">专题推荐</p>
              <h3>快捷切换</h3>
            </div>
          </div>
          <div class="topic-tabs topic-tabs--stack">
            <button
              v-for="entry in homeTopicEntries.filter((item) => item.type === 'section')"
              :key="entry.slug"
              :class="['topic-tab', { 'topic-tab--active': entry.slug === route.params.slug }]"
              type="button"
              @click="router.push({ name: 'section', params: { slug: entry.slug } })"
            >
              <span class="topic-tab__symbol">{{ entry.icon }}</span>
              <span class="topic-tab__label">{{ entry.label }}</span>
            </button>
          </div>
        </section>
      </aside>

      <section class="section-main">
        <div class="section-head">
          <div>
            <p class="section-head__kicker">活动展示</p>
            <h2>{{ config.title }} 专场</h2>
          </div>
          <el-input v-model="keyword" clearable class="feed-search" placeholder="在当前专题页继续搜索" />
        </div>

        <div class="section-card-grid">
          <ActivityCard
            v-for="activity in activities"
            :key="activity.activityId"
            :activity="activity"
            dense
          />
        </div>

        <p v-if="!loading && !activities.length" class="search-empty">
          当前专题下没有找到匹配的活动。
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

        <section v-if="config.showTeams" class="section-team-block card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">搭子专区</p>
              <h3>对应队伍展示</h3>
            </div>
          </div>
          <div class="team-grid">
            <article
              v-for="team in teams"
              :key="team.teamId"
              class="team-card"
              @click="router.push({ name: 'team-detail', params: { id: team.teamId } })"
            >
              <div class="team-card__top">
                <strong>{{ team.teamName }}</strong>
                <span>#{{ team.teamId }}</span>
              </div>
              <p>{{ team.teamDesc || '适合该专题页的组队或临时搭子信息。' }}</p>
              <div class="team-card__meta">
                <span>队长 {{ team.leaderUserId }}</span>
                <span>{{ team.currentSize }}/{{ team.maxSize || '不限' }} 人</span>
              </div>
            </article>
          </div>
        </section>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActivityCard from '@/components/ActivityCard.vue'
import { activityApi, teamApi } from '@/api/platform'
import { homeTopicEntries } from '@/constants/ui'
import { getSectionConfig } from '@/utils/catalog'

const route = useRoute()
const router = useRouter()

const activities = ref([])
const teams = ref([])
const keyword = ref(route.query.q || '')

// 专题筛选和关键词都交给后端，分页按后端返回的总数走。
// 以前是"先取 48 条 + 前端过滤"，排在第 49 条之后的活动在专题页永远看不到。
const pageNo = ref(0)
const pageSize = 12
const totalElements = ref(0)
const loading = ref(false)
let loadToken = 0

const config = computed(() => getSectionConfig(route.params.slug))
const theme = computed(() => config.value.theme || 'gold')

const loadActivities = async () => {
  const requestId = ++loadToken
  loading.value = true
  try {
    const page = await activityApi.list({
      pageNo: pageNo.value,
      pageSize,
      // 白名单专题标识：后端只认固定值，不开放任意查询表达式
      section: route.params.slug,
      keyword: keyword.value.trim() || undefined,
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

const loadTeams = async () => {
  // 队伍列表保持原来的小范围展示，本次不扩 scope
  const page = await teamApi.list({ pageNo: 0, pageSize: 12 })
  teams.value = page.records || []
}

const handlePageChange = (current) => {
  pageNo.value = current - 1
  loadActivities()
}

let keywordTimer = null
watch(keyword, () => {
  clearTimeout(keywordTimer)
  // 输入时防抖，避免每敲一个字都打一次接口
  keywordTimer = setTimeout(() => {
    pageNo.value = 0
    loadActivities()
  }, 300)
})

watch(
  () => route.query.q,
  (value) => {
    keyword.value = value || ''
  },
)

watch(
  () => route.params.slug,
  () => {
    keyword.value = route.query.q || ''
    pageNo.value = 0
    // 切换专题必须重新查询：规则在后端，不能复用上一个专题的结果
    loadActivities()
  },
)

onMounted(async () => {
  await Promise.all([loadActivities(), loadTeams()])
})
</script>
