<template>
  <main class="page page-detail">
    <section class="page-section detail-layout" v-if="activity">
      <section class="detail-main">
        <article class="detail-hero card-surface">
          <div class="detail-hero__media">
            <CoverImage
              v-if="activity.coverUrl"
              :src="activity.coverUrl"
              :alt="activity.activityTitle"
              fallback-class="detail-hero__fallback"
              fallback-text="封面失效"
            />
            <div v-else class="detail-hero__fallback">
              <span>{{ formatActivityType(activity.activityType) }}</span>
            </div>
          </div>

          <div class="detail-hero__copy">
            <p class="section-head__kicker">{{ formatActivityType(activity.activityType) }}</p>
            <h1>{{ activity.activityTitle }}</h1>
            <p>{{ activity.activityDesc || '发布者暂未补充活动描述。' }}</p>
            <div class="detail-hero__chips">
              <span v-for="tag in activity.tags || []" :key="tag.tagId">#{{ tag.tagName }}</span>
            </div>
            <div v-if="isEnrollable" class="detail-actions">
              <el-button
                type="warning"
                size="large"
                :loading="enrolling"
                :disabled="!canEnroll"
                @click="handleEnroll"
              >
                {{ enrollButtonText }}
              </el-button>
              <!--
                名额紧张提示：数据来自 GET /api/activities/{id}/stock。
                后端返回的 remaining 是**从数据库现算的权威值**（不是从 Redis 读的），
                因为 Redis 里那个库存是为了高并发扣减、可能有短暂偏差，展示给人看的必须准确。
                limited=false（活动不限人数）时刻意不显示这块，避免"剩余 null 个"这种荒唐文案。
              -->
              <span v-if="showStockHint" class="detail-stock-hint" :class="{ 'detail-stock-hint--urgent': isStockUrgent }">
                {{ stockHintText }}
              </span>
            </div>
          </div>

          <div class="detail-hero__stats">
            <div class="detail-stat">
              <span>核心指标</span>
              <strong>{{ formatMetric(activity) }}</strong>
            </div>
            <div class="detail-stat">
              <span>活动状态</span>
              <strong>{{ formatActivityStatusByActivity(activity) }}</strong>
            </div>
            <div class="detail-stat">
              <span>参与人数</span>
              <strong>{{ activity.currentParticipant }}/{{ activity.maxParticipant || '不限' }}</strong>
            </div>
            <div class="detail-stat">
              <span>签到开关</span>
              <strong>{{ Number(activity.signInEnabled) === 1 ? '已开启' : '未开启' }}</strong>
            </div>
          </div>
        </article>

        <article class="detail-section card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">活动信息</p>
              <h3>报名前先看清楚</h3>
            </div>
          </div>
          <div class="detail-grid">
            <div><span>开始时间</span><strong>{{ activity.startTime || '待定' }}</strong></div>
            <div><span>结束时间</span><strong>{{ activity.endTime || '待定' }}</strong></div>
            <div><span>地点</span><strong>{{ activity.activityLocation || '校内待定' }}</strong></div>
            <div><span>发布者</span><strong>{{ activity.publisherUserId }}</strong></div>
          </div>
        </article>
      </section>

      <aside class="detail-side">
        <section class="side-panel card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">同类活动</p>
              <h3>你可能还会看</h3>
            </div>
          </div>
          <div class="side-list">
            <router-link
              v-for="item in relatedActivities"
              :key="item.activityId"
              class="related-card"
              :to="{ name: 'activity-detail', params: { id: item.activityId } }"
            >
              <strong>{{ item.activityTitle }}</strong>
              <span>{{ formatMetric(item) }}</span>
            </router-link>
          </div>
        </section>
      </aside>
    </section>

    <section v-else class="page-section card-surface detail-loading">
      <el-skeleton :rows="7" animated />
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import CoverImage from '@/components/CoverImage.vue'
import { activityApi, enrollApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'
import {
  formatActivityStatusByActivity,
  formatActivityType,
  formatMetric,
} from '@/utils/format'

const route = useRoute()
const { user } = useSession()

const activity = ref(null)
const stock = ref(null)
const relatedActivitiesPool = ref([])
const enrolling = ref(false)

const relatedActivities = computed(() =>
  relatedActivitiesPool.value
    .filter((item) => item.activityId !== activity.value?.activityId && item.activityType === activity.value?.activityType)
    .slice(0, 4),
)

// ─────────────────────────── 名额提示 ───────────────────────────

/** 是否展示名额提示。活动不存在或活动不限人数（limited=false）时不展示。 */
const showStockHint = computed(() => Boolean(stock.value?.limited))

const isStockUrgent = computed(() => {
  const remaining = stock.value?.remaining
  return typeof remaining === 'number' && remaining > 0 && remaining <= 3
})

const stockHintText = computed(() => {
  const s = stock.value
  if (!s?.limited) {
    return ''
  }
  if (s.remaining === 0) {
    return '名额已满'
  }
  return isStockUrgent.value ? `仅剩 ${s.remaining} 个名额，抓紧报名` : `剩余 ${s.remaining} 个名额`
})

// ─────────────────────────── 报名按钮状态 ───────────────────────────

/** 活动是否处于可报名状态（后端用 displayStatus 表示"已结束"，它是按 endTime 现算的）。 */
const isEnrollable = computed(() => {
  if (!activity.value) {
    return false
  }
  const displayStatus = activity.value.displayStatus ?? activity.value.activityStatus
  // 1=已发布；3=已结束（现算）；4=已取消。只有 1 允许报名。
  return displayStatus === 1
})

const isSoldOut = computed(() => stock.value?.limited === true && stock.value?.remaining === 0)

const canEnroll = computed(() => isEnrollable.value && !isSoldOut.value)

const enrollButtonText = computed(() => {
  if (!isEnrollable.value) {
    return '当前不可报名'
  }
  return isSoldOut.value ? '名额已满' : '我要报名'
})

// ─────────────────────────── 数据加载 ───────────────────────────

const loadDetail = async () => {
  const activityId = route.params.id
  activity.value = await activityApi.detail(activityId)

  // 名额单独取，失败时降级为"不展示名额提示"（模板里 showStockHint 会因 stock 为 null 而 false），
  // 而不是让整个详情页打不开 —— 名额只是辅助信息，详情本身才是主体。
  try {
    stock.value = await activityApi.stock(activityId)
  } catch {
    stock.value = null
  }

  const page = await activityApi.list({ pageNo: 0, pageSize: 8 })
  relatedActivitiesPool.value = page.records || []
}

const handleEnroll = async () => {
  if (!user.value?.userId) {
    ElMessage.warning('请先登录后再报名')
    return
  }

  enrolling.value = true
  try {
    await enrollApi.create({
      userId: user.value.userId,
      activityId: activity.value.activityId,
    })
    ElMessage.success('报名成功')

    // 报名成功后必须重新拉取，否则页面上的"参与人数/剩余名额"还是报名前的旧值。
    // 注意后端在报名成功后会主动让活动详情缓存失效（activityCacheService.evict），
    // 所以这里重新请求拿到的一定是最新数据，不会读到缓存里的旧计数。
    await loadDetail()
  } catch {
    // 请求层已统一弹出错误提示，这里只做状态纠正。
    // 报名失败的常见原因是"名额已满"或"重复报名"，二者后端都归为 409（状态冲突），
    // 前端无法也不应依赖错误码区分具体原因，因此统一刷新一次：
    // 若是名额已满，说明本地看到的名额已过期，刷新能纠正显示；
    // 若是重复报名，刷新同样无害。用 message 文案做逻辑分支是脆弱的，故不采用。
    await loadDetail()
  } finally {
    enrolling.value = false
  }
}

watch(
  () => route.params.id,
  () => loadDetail(),
)

onMounted(loadDetail)
</script>
