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
            <div class="detail-actions">
              <el-button type="warning" size="large" @click="handleEnroll">我要报名</el-button>
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
const relatedActivitiesPool = ref([])

const relatedActivities = computed(() =>
  relatedActivitiesPool.value
    .filter((item) => item.activityId !== activity.value?.activityId && item.activityType === activity.value?.activityType)
    .slice(0, 4),
)

const loadDetail = async () => {
  const activityId = route.params.id
  activity.value = await activityApi.detail(activityId)
  const page = await activityApi.list({ pageNo: 0, pageSize: 8 })
  relatedActivitiesPool.value = page.records || []
}

const handleEnroll = async () => {
  if (!user.value?.userId) {
    ElMessage.warning('请先登录后再报名')
    return
  }

  // 不再传 userId：报名人由后端从 JWT 取当前登录用户，
  // 请求体里自报学号等于可以替别人报名占名额（可伪造）。
  await enrollApi.create({
    activityId: activity.value.activityId,
  })

  ElMessage.success('报名成功')
}

watch(
  () => route.params.id,
  () => loadDetail(),
)

onMounted(loadDetail)
</script>
