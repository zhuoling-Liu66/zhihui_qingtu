<template>
  <main class="page page-home">
    <section class="page-section page-section--announcement">
      <div class="announcement-bar">
        <span>📣 网页版已升级为校园活动集市风格，支持学分活动、志愿服务和组队邀请。</span>
      </div>
    </section>

    <section class="page-section marketplace-hero">
      <MegaCategoryRail :items="railPanels" />

      <div class="hero-grid">
        <article class="hero-promo hero-promo--primary">
          <div>
            <p class="hero-promo__eyebrow">校园精选</p>
            <h1>像逛闲鱼一样</h1>
            <h1>逛活动</h1>
            <p>把第二课堂、志愿活动、校园组队和生活互助放进一个轻松好逛的信息流里。</p>
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
          <div>
            <span class="hero-module__badge">{{ module.badge }}</span>
            <h3>{{ module.title }}</h3>
            <p>{{ module.subtitle }}</p>
          </div>
          <strong>{{ module.stat }}</strong>
        </article>
      </div>
    </section>

    <section class="page-section recommendation-strip card-surface">
      <div class="section-head">
        <div>
          <p class="section-head__kicker">猜你喜欢</p>
          <h2>主题入口</h2>
        </div>
      </div>

      <div class="recommend-grid">
        <article class="recommend-card recommend-card--credit" @click="jumpToSection('second-class')">
          <p class="section-head__kicker">学分专题</p>
          <h3>第二课堂、德育、体育可直达专题页</h3>
          <p class="recommend-card__summary">按成长方向和学分缺口继续筛选，不必在活动流里来回翻找。</p>
          <div class="recommend-card__chips">
            <span v-for="entry in creditTopics" :key="entry.slug" @click.stop="jumpToSection(entry.slug)">
              {{ entry.label }}
            </span>
          </div>
        </article>

        <article class="recommend-card recommend-card--team" @click="jumpToSection('team')">
          <p class="section-head__kicker">校园招募</p>
          <h3>{{ teamCards.length }} 个正在招募的校园队伍</h3>
          <p class="recommend-card__summary">球搭子、兴趣小队、校园团建都可以从这里继续进入。</p>

          <ul v-if="teamPreviewCards.length" class="recommend-card__list recommend-card__list--compact">
            <li
              v-for="team in teamPreviewCards"
              :key="team.teamId"
              class="recommend-card__list-item"
              @click.stop="goTeamDetail(team.teamId)"
            >
              <span class="recommend-card__list-title">{{ team.teamName }}</span>
              <span class="recommend-card__list-meta">
                {{ team.currentSize }}/{{ team.maxSize || '不限' }} 人
              </span>
            </li>
          </ul>
          <div v-else class="recommend-card__placeholder">
            组队广场暂时没有新的招募信息，稍后可以刷新首页继续查看。
          </div>
        </article>
      </div>

      <div class="topic-tabs">
        <button
          v-for="entry in homeTopicEntries"
          :key="entry.label"
          class="topic-tab"
          :class="{ 'topic-tab--active': activeTopicKey === entry.label }"
          type="button"
          @click="jumpTopic(entry)"
        >
          <span class="topic-tab__symbol">{{ entry.icon }}</span>
          <span class="topic-tab__label">{{ entry.label }}</span>
        </button>
      </div>
    </section>

    <section id="activity-feed" class="page-section content-grid">
      <section class="feed-main">
        <div class="section-head">
          <div>
            <p class="section-head__kicker">活动流</p>
            <h2>首页精选活动</h2>
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
        <!--
          本周热门：数据来自后端 Redis 的 ZSet `activity:hot:enroll`。
          为什么值得单独放一块：它是"按累计报名人数实时排序"的榜单，
          用 MySQL 做需要 ORDER BY count DESC 扫全表，而 ZSet 的 ZREVRANGE 是 O(log N + 10)。
          所以这里的顺序本身就带信息量，不要用前端再排序把它打乱。
        -->
        <section v-if="hotActivities.length" class="side-panel card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">本周热门</p>
              <h3>大家都在报</h3>
            </div>
          </div>
          <ol class="hot-rank">
            <li
              v-for="(item, index) in hotActivities"
              :key="item.activityId"
              class="hot-rank__item"
              @click="router.push({ name: 'activity-detail', params: { id: item.activityId } })"
            >
              <span class="hot-rank__no" :class="`hot-rank__no--${index < 3 ? index + 1 : 'plain'}`">
                {{ index + 1 }}
              </span>
              <div class="hot-rank__body">
                <strong class="hot-rank__title">{{ item.activityTitle }}</strong>
                <span class="hot-rank__meta">
                  {{ item.currentParticipant }}/{{ item.maxParticipant || '不限' }} 人已报名
                </span>
              </div>
            </li>
          </ol>
        </section>

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
import ActivityCard from '@/components/ActivityCard.vue'
import MegaCategoryRail from '@/components/MegaCategoryRail.vue'
import { activityApi, teamApi } from '@/api/platform'
import { featureModules, homeTopicEntries, railPanels } from '@/constants/ui'

const router = useRouter()

const activities = ref([])
const tags = ref([])
const teamCards = ref([])
const hotActivities = ref([])
const activeTopicKey = ref(homeTopicEntries[0]?.label || '')

const featuredActivities = computed(() =>
  activities.value.filter((item) => [1, 2, 3].includes(item.activityStatus)).slice(0, 9),
)

const teamPreviewCards = computed(() => teamCards.value.slice(0, 3))
const creditTopics = computed(() =>
  homeTopicEntries.filter((entry) => ['second-class', 'moral', 'physical'].includes(entry.slug)),
)

const jumpTopic = (entry) => {
  activeTopicKey.value = entry.label
  if (entry.type === 'home') {
    router.push({ name: 'home' })
    return
  }
  router.push({ name: 'section', params: { slug: entry.slug } })
}

const jumpFeature = (slug) => {
  router.push({ name: 'section', params: { slug } })
}

const jumpToSection = (slug) => {
  router.push({ name: 'section', params: { slug } })
}

const goTeamDetail = (teamId) => {
  router.push({ name: 'team-detail', params: { id: teamId } })
}

const loadHomeData = async () => {
  // 先把"锦上添花"的热门榜单独取，失败也不影响主体内容。
  // 为什么单独写一个 try：Redis 临时不可用时热门榜会失败，
  // 但活动列表是首页主体，它失败才应该让用户知道，所以两者不能绑在同一个 try 里。
  try {
    hotActivities.value = (await activityApi.hot(5)) || []
  } catch {
    // 请求层已经提示过了，这里静默降级：榜单区块直接不渲染（模板里有 v-if）
    hotActivities.value = []
  }

  const [activityPage, tagList, teamPage] = await Promise.all([
    activityApi.list({ pageNo: 0, pageSize: 18 }),
    activityApi.tags(),
    teamApi.list({ pageNo: 0, pageSize: 4 }),
  ])

  activities.value = activityPage.records || []
  tags.value = tagList || []
  teamCards.value = teamPage.records || []
}

onMounted(async () => {
  await loadHomeData()
})
</script>
