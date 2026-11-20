<template>
  <main class="page page-home">
    <section class="page-section home-notice" aria-label="校园活动集市">
      <span class="home-notice__dot" aria-hidden="true"></span>
      <p>
        <strong>校园活动集市</strong>
        <span>学分活动 · 志愿服务 · 组队邀请</span>
      </p>
    </section>

    <section class="page-section marketplace-hero">
      <MegaCategoryRail :items="railPanels" />

      <div class="hero-grid">
        <article class="hero-promo">
          <div class="hero-promo__copy">
            <p class="hero-promo__eyebrow">校园活动集市</p>
            <h1>逛一圈，<br /><span class="hero-promo__line">把学分和队伍找齐</span></h1>
            <p class="hero-promo__lede">
              第二课堂学分、志愿服务时长、校园组队，都在同一条活动流里。
            </p>
            <div class="hero-promo__actions">
              <router-link to="/publish" class="home-button home-button--primary">发布活动</router-link>
              <router-link
                :to="{ name: 'section', params: { slug: 'second-class' } }"
                class="home-button home-button--secondary"
              >
                浏览活动
              </router-link>
            </div>
          </div>

          <aside class="hero-board" aria-label="本周通告">
            <div class="hero-board__head">
              <strong>本周通告</strong>
              <span>{{ feedError ? '—' : `${heroActivities.length} 场` }}</span>
            </div>

            <ul v-if="heroActivities.length" class="hero-board__list">
              <li v-for="activity in heroActivities" :key="activity.activityId">
                <router-link
                  class="hero-board__item"
                  :to="{ name: 'activity-detail', params: { id: activity.activityId } }"
                >
                  <div class="hero-board__thumb">
                    <CoverImage
                      v-if="activity.coverUrl"
                      :src="activity.coverUrl"
                      :alt="activity.activityTitle"
                      image-class="hero-board__thumb-img"
                      fallback-class="hero-board__thumb-empty"
                      fallback-text="封面"
                    />
                    <span v-else class="hero-board__thumb-empty">
                      {{ formatActivityTypeShort(activity.activityType) }}
                    </span>
                  </div>
                  <span class="hero-board__body">
                    <strong>{{ activity.activityTitle }}</strong>
                    <span>
                      {{ formatShortDateTime(activity.startTime) }} ·
                      {{ activity.activityLocation || '地点待定' }}
                    </span>
                  </span>
                  <span class="stamp">{{ formatStamp(activity) }}</span>
                </router-link>
              </li>
            </ul>

            <p v-else-if="feedError" class="hero-board__empty">活动加载失败。</p>
            <p v-else class="hero-board__empty">今天还没有新通告。</p>
          </aside>
        </article>

        <nav class="hero-links" aria-label="活动分类">
          <router-link
            v-for="module in featureModules"
            :key="module.slug"
            class="hero-link"
            :to="{ name: 'section', params: { slug: module.slug } }"
          >
            <el-icon class="hero-link__icon">
              <component :is="moduleIcons[module.slug]" />
            </el-icon>
            <span class="hero-link__body">
              <strong>{{ module.title }}</strong>
              <span>{{ module.subtitle }}</span>
            </span>
            <el-icon class="hero-link__arrow"><ArrowRight /></el-icon>
          </router-link>
        </nav>
      </div>
    </section>

    <section class="page-section recommendation-strip">
      <div class="section-head">
        <div>
          <p class="section-head__kicker">分类</p>
          <h2>主题入口</h2>
        </div>
      </div>

      <div class="recommend-grid">
        <article class="recommend-card">
          <p class="recommend-card__kicker">学分专题</p>
          <h3>第二课堂、德育、体育分开找</h3>
          <p class="recommend-card__summary">按成长方向和学分缺口筛选，不用在活动流里来回翻。</p>
          <div class="recommend-card__links">
            <router-link
              v-for="entry in creditTopics"
              :key="entry.slug"
              :to="{ name: 'section', params: { slug: entry.slug } }"
            >
              {{ entry.label }}
            </router-link>
          </div>
          <p class="recommend-card__note">6 类学分：{{ creditTypeLabels.join('、') }}</p>
        </article>

        <article class="recommend-card">
          <p class="recommend-card__kicker">校园招募</p>
          <h3 v-if="teamsError">队伍列表加载失败</h3>
          <h3 v-else-if="teamsLoading">正在加载校园队伍</h3>
          <h3 v-else>{{ teamCards.length }} 个正在招募的校园队伍</h3>
          <p class="recommend-card__summary">球搭子、兴趣小队、校园团建，从这里进。</p>

          <div v-if="teamsError" class="inline-error">
            <el-button text type="warning" @click="loadTeams">重试</el-button>
          </div>
          <ul v-else-if="teamPreviewCards.length" class="recommend-card__list">
            <li
              v-for="team in teamPreviewCards"
              :key="team.teamId"
              class="recommend-card__list-item"
            >
              <router-link :to="{ name: 'team-detail', params: { id: team.teamId } }">
                <span class="recommend-card__list-title">{{ team.teamName }}</span>
                <span class="recommend-card__list-meta">
                  {{ team.currentSize }}/{{ team.maxSize || '不限' }} 人
                </span>
              </router-link>
            </li>
          </ul>
          <p v-else-if="!teamsLoading" class="recommend-card__empty">暂时没有新的招募。</p>
        </article>
      </div>

      <nav class="topic-index" aria-label="更多主题">
        <router-link
          v-for="entry in homeTopicEntries"
          :key="entry.label"
          class="topic-index__item"
          :class="{ 'topic-index__item--active': entry.type === 'home' }"
          :to="topicLink(entry)"
        >
          {{ entry.label }}
        </router-link>
      </nav>
    </section>

    <section id="activity-feed" class="page-section content-grid">
      <section class="feed-main">
        <div class="section-head">
          <div>
            <p class="section-head__kicker">活动流</p>
            <h2>精选活动</h2>
          </div>
          <router-link
            :to="{ name: 'search', query: { q: '校园活动' } }"
            class="text-link"
          >
            查看全部
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>

        <div v-if="feedError" class="feed-state">
          <p>精选活动加载失败。</p>
          <el-button text type="warning" @click="loadFeatured">重试</el-button>
        </div>

        <div v-else-if="feedLoading" class="activity-feed" aria-busy="true" aria-label="正在加载精选活动">
          <div v-for="n in 3" :key="n" class="activity-card activity-card--skeleton" aria-hidden="true">
            <div class="activity-skeleton__media"></div>
            <div class="activity-skeleton__body">
              <span class="activity-skeleton__line activity-skeleton__line--short"></span>
              <span class="activity-skeleton__line"></span>
              <span class="activity-skeleton__line activity-skeleton__line--medium"></span>
            </div>
          </div>
        </div>

        <div v-else-if="featuredActivities.length" class="activity-feed">
          <ActivityCard
            v-for="activity in featuredActivities"
            :key="activity.activityId"
            :activity="activity"
          />
        </div>

        <p v-else class="feed-state">暂时没有可展示的活动。</p>
      </section>

      <aside class="feed-side">
        <section v-if="hotActivities.length" class="side-panel">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">按报名人数</p>
              <h3>本周热门</h3>
            </div>
          </div>
          <ol class="hot-rank">
            <li
              v-for="(item, index) in hotActivities"
              :key="item.activityId"
              class="hot-rank__item"
            >
              <router-link :to="{ name: 'activity-detail', params: { id: item.activityId } }">
                <span
                  class="hot-rank__no"
                  :class="`hot-rank__no--${index < 3 ? index + 1 : 'plain'}`"
                >
                  {{ index + 1 }}
                </span>
                <span class="hot-rank__body">
                  <strong class="hot-rank__title">{{ item.activityTitle }}</strong>
                  <span class="hot-rank__meta">
                    {{ item.currentParticipant }}/{{ item.maxParticipant || '不限' }} 人已报名
                  </span>
                </span>
              </router-link>
            </li>
          </ol>
        </section>

        <section class="side-panel">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">队伍广场</p>
              <h3>组队邀请</h3>
            </div>
          </div>
          <div v-if="teamsError" class="inline-error">
            <span>队伍信息暂时不可用。</span>
            <el-button text type="warning" @click="loadTeams">重试</el-button>
          </div>
          <div v-else-if="teamsLoading" class="side-loading" aria-hidden="true">
            <span></span><span></span><span></span>
          </div>
          <div v-else class="side-list">
            <router-link
              v-for="team in teamCards"
              :key="team.teamId"
              class="team-card"
              :to="{ name: 'team-detail', params: { id: team.teamId } }"
            >
              <div class="team-card__top">
                <strong>{{ team.teamName }}</strong>
                <span>{{ team.currentSize }}/{{ team.maxSize || '不限' }} 人</span>
              </div>
              <p>{{ team.teamDesc || '暂无队伍介绍。' }}</p>
            </router-link>
            <p v-if="!teamCards.length" class="side-empty">暂时没有新的招募。</p>
          </div>
        </section>

        <section v-if="tags.length || tagsError" class="side-panel">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">标签</p>
              <h3>常用搜索</h3>
            </div>
          </div>
          <div v-if="tagsError" class="inline-error">
            <span>标签暂时不可用。</span>
            <el-button text type="warning" @click="loadTags">重试</el-button>
          </div>
          <div v-else class="tag-cloud">
            <router-link
              v-for="tag in tags"
              :key="tag.tagId"
              class="tag-cloud__item"
              :to="{ name: 'search', query: { q: tag.tagName } }"
            >
              {{ tag.tagName }}
            </router-link>
          </div>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Reading, Timer, UserFilled } from '@element-plus/icons-vue'
import ActivityCard from '@/components/ActivityCard.vue'
import CoverImage from '@/components/CoverImage.vue'
import MegaCategoryRail from '@/components/MegaCategoryRail.vue'
import { activityApi, teamApi } from '@/api/platform'
import { creditTypeMap, featureModules, homeTopicEntries, railPanels } from '@/constants/ui'
import {
  formatActivityTypeShort,
  formatShortDateTime,
  formatStamp,
} from '@/utils/format'

const activities = ref([])
const tags = ref([])
const teamCards = ref([])
const hotActivities = ref([])

const feedLoading = ref(true)
const feedError = ref(false)
const teamsLoading = ref(true)
const teamsError = ref(false)
const tagsError = ref(false)

const featuredActivities = computed(() =>
  activities.value.filter((item) => [1, 2, 3].includes(item.activityStatus)).slice(0, 9),
)

const heroActivities = computed(() => {
  const list = featuredActivities.value
  const withCover = list.filter((item) => item.coverUrl)
  const withoutCover = list.filter((item) => !item.coverUrl)
  return [...withCover, ...withoutCover].slice(0, 3)
})

const teamPreviewCards = computed(() => teamCards.value.slice(0, 3))
const creditTopics = computed(() =>
  homeTopicEntries.filter((entry) => ['second-class', 'moral', 'physical'].includes(entry.slug)),
)
const creditTypeLabels = Object.values(creditTypeMap)

const moduleIcons = {
  'second-class': Reading,
  volunteer: Timer,
  team: UserFilled,
}

const topicLink = (entry) =>
  entry.type === 'home'
    ? { name: 'home' }
    : { name: 'section', params: { slug: entry.slug } }

const loadHot = async () => {
  try {
    hotActivities.value = (await activityApi.hot(5)) || []
  } catch {
    hotActivities.value = []
  }
}

const loadFeatured = async () => {
  feedLoading.value = true
  feedError.value = false
  try {
    const page = await activityApi.list({ pageNo: 0, pageSize: 18 })
    activities.value = page.records || []
  } catch {
    activities.value = []
    feedError.value = true
  } finally {
    feedLoading.value = false
  }
}

const loadTags = async () => {
  tagsError.value = false
  try {
    tags.value = (await activityApi.tags()) || []
  } catch {
    tags.value = []
    tagsError.value = true
  }
}

const loadTeams = async () => {
  teamsLoading.value = true
  teamsError.value = false
  try {
    const page = await teamApi.list({ pageNo: 0, pageSize: 4 })
    teamCards.value = page.records || []
  } catch {
    teamCards.value = []
    teamsError.value = true
  } finally {
    teamsLoading.value = false
  }
}

/**
 * 各区块独立加载、独立失败：
 * 热门榜、标签、队伍都是可选内容，任何一个慢或失败都不能拖住精选活动。
 */
const loadHomeData = () => {
  loadHot()
  return Promise.all([loadFeatured(), loadTags(), loadTeams()])
}

onMounted(loadHomeData)
</script>
