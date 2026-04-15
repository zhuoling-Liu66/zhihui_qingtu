<template>
  <main class="page page-manage-activities">
    <section class="page-section">
      <div v-if="!user" class="card-surface empty-login">
        <h2>登录后查看我发布的活动</h2>
        <p>这里会集中展示你发布的活动，并提供编辑、删除、状态切换和签到开关设置。</p>
      </div>

      <template v-else>
        <section class="manage-header card-surface">
          <div>
            <p class="section-head__kicker">我的活动</p>
            <h1>我发布的活动</h1>
            <p>你可以在这里统一管理自己发布的活动，删除活动时会连同报名、学分、组队和推荐日志一起清除。</p>
          </div>
          <el-button type="warning" size="large" @click="router.push('/publish')">继续发布活动</el-button>
        </section>

        <section class="manage-grid">
          <article v-for="activity in activities" :key="activity.activityId" class="manage-card card-surface">
            <div class="manage-card__cover">
              <CoverImage
                v-if="activity.coverUrl"
                :src="activity.coverUrl"
                :alt="activity.activityTitle"
                fallback-class="manage-card__cover--empty"
                fallback-text="封面失效"
              />
              <div v-else class="manage-card__cover--empty">暂无封面</div>
            </div>

            <div class="manage-card__body">
              <div class="manage-card__head">
                <div>
                  <h3>{{ activity.activityTitle }}</h3>
                  <p>{{ formatMetric(activity) }}</p>
                </div>
                <el-tag round type="warning">{{ formatActivityStatusByActivity(activity) }}</el-tag>
              </div>

              <div class="manage-card__meta">
                <span>活动ID #{{ activity.activityId }}</span>
                <span>{{ activity.activityLocation || '校内待定' }}</span>
                <span>{{ activity.currentParticipant }}/{{ activity.maxParticipant || '不限' }} 人</span>
              </div>

              <div class="manage-card__switch">
                <span>签到开关</span>
                <el-switch
                  :model-value="Number(activity.signInEnabled) === 1"
                  @change="(value) => toggleSignIn(activity, value)"
                />
              </div>

              <div class="manage-card__actions">
                <el-button plain @click="router.push({ name: 'publish', query: { edit: activity.activityId } })">
                  编辑活动
                </el-button>
                <el-button
                  v-if="activity.activityType === 1"
                  type="warning"
                  plain
                  @click="router.push({ name: 'credit-review', query: { activityId: activity.activityId } })"
                >
                  学分审核
                </el-button>
                <el-button
                  v-if="activity.activityStatus !== 4"
                  plain
                  @click="changeStatus(activity, 4)"
                >
                  取消活动
                </el-button>
                <el-button
                  v-else
                  type="warning"
                  plain
                  @click="changeStatus(activity, 1)"
                >
                  重新发布
                </el-button>
                <el-button type="danger" plain @click="removeActivity(activity)">删除活动</el-button>
                <el-button @click="router.push({ name: 'activity-detail', params: { id: activity.activityId } })">
                  查看详情
                </el-button>
              </div>
            </div>
          </article>
        </section>
      </template>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CoverImage from '@/components/CoverImage.vue'
import { activityApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'
import { formatActivityStatusByActivity, formatMetric } from '@/utils/format'

const router = useRouter()
const { user } = useSession()

const activities = ref([])

const loadActivities = async () => {
  if (!user.value?.userId) return
  const page = await activityApi.list({
    publisherUserId: user.value.userId,
    pageNo: 0,
    pageSize: 50,
  })
  activities.value = page.records || []
}

const changeStatus = async (activity, status) => {
  await activityApi.updateStatus(activity.activityId, {
    operatorUserId: user.value.userId,
    activityStatus: status,
  })
  ElMessage.success(status === 4 ? '活动已取消' : '活动已重新发布')
  await loadActivities()
}

const toggleSignIn = async (activity, value) => {
  await activityApi.updateSignInSwitch(activity.activityId, {
    operatorUserId: user.value.userId,
    signInEnabled: value ? 1 : 0,
  })
  ElMessage.success(value ? '签到已开启' : '签到已关闭')
  await loadActivities()
}

const removeActivity = async (activity) => {
  await ElMessageBox.confirm(
    `删除“${activity.activityTitle}”后，相关报名、学分、组队和推荐日志都会一起清除，是否继续？`,
    '删除活动确认',
    {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '先不删',
    },
  )

  await activityApi.remove(activity.activityId, user.value.userId)
  ElMessage.success('活动已删除')
  await loadActivities()
}

onMounted(loadActivities)
</script>
