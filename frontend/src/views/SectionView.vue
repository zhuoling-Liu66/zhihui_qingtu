<template>
  <main class="page page-section-view">
    <section class="page-section section-hero card-surface" :class="`section-hero--${theme}`">
      <div>
        <p class="section-head__kicker">专题页面</p>
        <h1>{{ config.title }}</h1>
        <p>{{ config.description }}</p>
        <div class="section-hero__chips">
          <span>{{ config.subtitle }}</span>
          <span>{{ filteredActivities.length }} 条活动</span>
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
              <span>{{ entry.icon }}</span>
              {{ entry.label }}
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
            v-for="activity in displayedActivities"
            :key="activity.activityId"
            :activity="activity"
            dense
          />
        </div>

        <section v-if="config.showTeams" class="section-team-block card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">搭子专区</p>
              <h3>对应队伍展示</h3>
            </div>
          </div>
          <div class="team-grid">
            <article v-for="team in teams" :key="team.teamId" class="team-card">
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
import { filterActivitiesBySection, getSectionConfig, matchKeyword } from '@/utils/catalog'

const route = useRoute()
const router = useRouter()

const activities = ref([])
const teams = ref([])
const keyword = ref(route.query.q || '')

const config = computed(() => getSectionConfig(route.params.slug))
const theme = computed(() => config.value.theme || 'gold')
const filteredActivities = computed(() => filterActivitiesBySection(activities.value, route.params.slug))
const displayedActivities = computed(() =>
  filteredActivities.value.filter((activity) => matchKeyword(activity, keyword.value)),
)

const loadData = async () => {
  const [activityPage, teamPage] = await Promise.all([
    activityApi.list({ pageNo: 0, pageSize: 48 }),
    teamApi.list({ pageNo: 0, pageSize: 12 }),
  ])
  activities.value = activityPage.records || []
  teams.value = teamPage.records || []
}

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
  },
)

onMounted(loadData)
</script>
