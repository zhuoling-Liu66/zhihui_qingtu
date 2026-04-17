<template>
  <main class="page page-home">
    <section class="page-section page-section--announcement">
      <div class="announcement-bar">
        <span>春季活动簿持续更新，学分活动、志愿服务、校园组队与 AI 推荐都在这一页慢慢展开。</span>
      </div>
    </section>

    <section class="page-section marketplace-hero">
      <MegaCategoryRail :items="railPanels" />

      <div class="hero-grid">
        <article class="hero-promo hero-promo--primary">
          <div class="hero-promo__content">
            <p class="hero-promo__eyebrow">校园精选</p>
            <h1>逛活动</h1>
            <p class="hero-promo__copy">把第二课堂、志愿服务、校园组队和生活互助放进一个轻松好逛的信息流里。</p>
            <span class="hero-promo__note">新</span>
          </div>
          <div class="hero-promo__art" aria-hidden="true">
            <span class="hero-promo__stamp">2026</span>
            <div class="hero-promo__building"></div>
            <span class="hero-promo__caption">Campus Life</span>
          </div>
          <div class="hero-promo__actions">
            <router-link to="/publish" class="hero-pill hero-pill--filled">去发布</router-link>
            <router-link :to="{ name: 'section', params: { slug: 'second-class' } }" class="hero-pill">去看看</router-link>
          </div>
        </article>

        <article
          v-for="module in featureModules"
          :key="module.title"
          class="hero-module"
          :class="`hero-module--${module.accent}`"
          @click="jumpFeature(module.slug)"
        >
          <span class="hero-module__badge">{{ module.badge }}</span>
          <div class="hero-module__copy">
            <component :is="resolveFeatureIcon(module.icon)" class="hero-module__icon" />
            <div>
              <h3>{{ module.title }}</h3>
              <p>{{ module.subtitle }}</p>
            </div>
          </div>
          <span v-if="module.handNote" class="hero-module__handnote">{{ module.handNote }}</span>
          <span v-if="module.paperNote" class="hero-module__paper">{{ module.paperNote }}</span>
          <strong>{{ module.stat }}</strong>
        </article>
      </div>
    </section>

    <section class="page-section recommendation-strip">
      <div class="section-head">
        <div>
          <p class="section-head__kicker">猜你喜欢</p>
          <h2>AI 推荐与主题入口</h2>
        </div>
        <el-button text @click="loadRecommendations">刷新推荐</el-button>
      </div>

      <div class="topic-tabs">
        <button
          v-for="entry in homeTopicEntries"
          :key="entry.label"
          class="topic-tab"
          type="button"
          @click="jumpTopic(entry)"
        >
          <span class="topic-tab__symbol">{{ entry.icon }}</span>
          <span class="topic-tab__label">{{ entry.label }}</span>
        </button>
      </div>

      <div class="recommend-grid">
        <article class="recommend-card recommend-card--ai">
          <p class="section-head__kicker">AI 推荐</p>
          <h3>AI 推荐</h3>
          <p>{{ recommendationSummary }}</p>
          <span class="recommend-card__handnote">智能匹配中</span>
          <div class="recommend-card__chips">
            <span v-for="tag in visibleRecommendReasons" :key="tag">{{ tag }}</span>
          </div>
        </article>

        <article class="recommend-card recommend-card--credit">
          <p class="section-head__kicker">学分专区</p>
          <h3>第二课堂与六类学分入口</h3>
          <p>点击顶部标签或左侧悬浮入口，都能进入对应的专门界面。</p>
        </article>

        <article class="recommend-card recommend-card--team">
          <p class="section-head__kicker">组队邀请</p>
          <h3>校园组队广场</h3>
          <p>{{ teamCards.length }} 个队伍正在招募，球搭子、兴趣小队、校园团建都能从这里进入。</p>
        </article>
      </div>
    </section>

    <section id="activity-feed" class="page-section content-grid">
      <section class="feed-main">
        <div class="section-head">
        <div>
          <p class="section-head__kicker">活动流</p>
          <h2>逛活动</h2>
        </div>
          <el-button type="warning" plain @click="router.push({ name: 'search', query: { q: '校园活动' } })">
            进入搜索页
          </el-button>
        </div>

        <div class="activity-feed">
          <ActivityCard
            v-for="activity in featuredActivities"
            :key="activity.activityId"
            :activity="activity"
          />
        </div>
      </section>

      <aside class="feed-side">
        <section class="side-panel card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">队伍广场</p>
              <h3>组队邀请</h3>
            </div>
          </div>
          <div class="side-list">
            <article
              v-for="team in teamCards"
              :key="team.teamId"
              class="team-card"
              @click="router.push({ name: 'team-detail', params: { id: team.teamId } })"
            >
              <div class="team-card__top">
                <strong>{{ team.teamName }}</strong>
                <span>队伍 #{{ team.teamId }}</span>
              </div>
              <p>{{ team.teamDesc || '适合在首页右栏展示的即时组队招募信息。' }}</p>
              <div class="team-card__meta">
                <span>队长 {{ team.leaderUserId }}</span>
                <span>{{ team.currentSize }}/{{ team.maxSize || '不限' }} 人</span>
              </div>
            </article>
          </div>
        </section>

        <section class="side-panel card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">标签导航</p>
              <h3>常用搜索</h3>
            </div>
          </div>
          <div class="tag-cloud">
            <button
              v-for="tag in tags"
              :key="tag.tagId"
              type="button"
              class="tag-cloud__item"
              @click="router.push({ name: 'search', query: { q: tag.tagName } })"
            >
              #{{ tag.tagName }}
            </button>
          </div>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Clock, Connection, Opportunity, Reading } from '@element-plus/icons-vue'
import ActivityCard from '@/components/ActivityCard.vue'
import MegaCategoryRail from '@/components/MegaCategoryRail.vue'
import { activityApi, recommendationApi, teamApi } from '@/api/platform'
import { featureModules, homeTopicEntries, railPanels } from '@/constants/ui'
import { useSession } from '@/composables/useSession'

const router = useRouter()
const { user } = useSession()

const activities = ref([])
const tags = ref([])
const teamCards = ref([])
const recommendIds = ref([])
const recommendReasons = ref({})

const featureIconMap = {
  reading: Reading,
  clock: Clock,
  connection: Connection,
  idea: Opportunity,
}

const featuredActivities = computed(() =>
  activities.value.filter((item) => [1, 2, 3].includes(item.activityStatus)).slice(0, 9),
)

const recommendedActivities = computed(() =>
  recommendIds.value
    .map((id) => activities.value.find((item) => item.activityId === id))
    .filter(Boolean),
)

const visibleRecommendReasons = computed(() =>
  Object.values(recommendReasons.value || {})
    .flat()
    .filter(Boolean)
    .slice(0, 4),
)

const recommendationTitle = computed(() => {
  if (recommendedActivities.value.length) {
    return 'AI 正在根据你的画像推送更合适的校园活动'
  }
  return '登录后可获得 AI 个性化推荐'
})

const recommendationSummary = computed(() => {
  if (recommendedActivities.value.length) {
    return `当前已命中 ${recommendedActivities.value.length} 条推荐活动，可继续进入专题页或详情页查看。`
  }
  return '未登录时仍可先浏览主题入口；登录后系统会结合兴趣标签、专业年级和学分缺口进行推荐。'
})

const jumpTopic = (entry) => {
  if (entry.type === 'home') {
    router.push({ name: 'home' })
    return
  }
  router.push({ name: 'section', params: { slug: entry.slug } })
}

const jumpFeature = (slug) => {
  if (slug === 'recommend') {
    router.push({ name: 'search', query: { q: 'AI 推荐' } })
    return
  }
  router.push({ name: 'section', params: { slug } })
}

const resolveFeatureIcon = (iconKey) => featureIconMap[iconKey] || Reading

const loadHomeData = async () => {
  const [activityPage, tagList, teamPage] = await Promise.all([
    activityApi.list({ pageNo: 0, pageSize: 18 }),
    activityApi.tags(),
    teamApi.list({ pageNo: 0, pageSize: 4 }),
  ])

  activities.value = activityPage.records || []
  tags.value = tagList || []
  teamCards.value = teamPage.records || []
}

const loadRecommendations = async () => {
  if (!user.value?.userId) {
    recommendIds.value = []
    recommendReasons.value = {}
    return
  }

  const result = await recommendationApi.query({
    userId: user.value.userId,
    grade: user.value.grade || '未知',
    major: user.value.major || '未知',
    interestTagIds: [],
    creditGap: 2,
    availableTimeSlots: ['周三晚上', '周末白天'],
    limit: 6,
  })

  recommendIds.value = result.recommendedActivityIds || []
  recommendReasons.value = result.reasonTags || {}
}

onMounted(async () => {
  await loadHomeData()
  await loadRecommendations()
})
</script>
