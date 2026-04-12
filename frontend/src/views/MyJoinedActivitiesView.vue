<template>
  <main class="page page-manage-activities">
    <section class="page-section">
      <div v-if="!user" class="card-surface empty-login">
        <h2>登录后查看我参加的活动</h2>
        <p>这里会集中展示你已报名的活动，并支持签到或退出。</p>
      </div>

      <template v-else>
        <section class="manage-header card-surface">
          <div>
            <p class="section-head__kicker">参与记录</p>
            <h1>我参加的活动</h1>
            <p>如果活动已经开启签到，你可以直接自助签到；未签到且活动未结束时，可以退出活动。</p>
          </div>
          <el-button @click="loadEnrollments">刷新列表</el-button>
        </section>

        <section class="manage-grid">
          <article v-for="item in enrollments" :key="item.enrollId" class="manage-card card-surface">
            <div class="manage-card__cover">
              <CoverImage
                v-if="item.activityCoverUrl"
                :src="item.activityCoverUrl"
                :alt="item.activityTitle"
                fallback-class="manage-card__cover--empty"
                fallback-text="封面失效"
              />
              <div v-else class="manage-card__cover--empty">暂无封面</div>
            </div>

            <div class="manage-card__body">
              <div class="manage-card__head">
                <div>
                  <h3>{{ item.activityTitle }}</h3>
                  <p>报名状态：{{ enrollmentStatusText(item.enrollStatus) }}</p>
                </div>
                <el-tag round>{{ formatActivityStatus(item.displayStatus ?? item.activityStatus) }}</el-tag>
              </div>

              <div class="manage-card__meta">
                <span>活动ID #{{ item.activityId }}</span>
                <span>{{ item.activityLocation || '校内待定' }}</span>
                <span>{{ item.startTime || '待定时间' }}</span>
              </div>

              <div class="manage-card__switch">
                <span>签到状态</span>
                <strong>{{ Number(item.signInEnabled) === 1 ? '活动已开放签到' : '活动暂未开放签到' }}</strong>
              </div>

              <div class="manage-card__actions">
                <el-button
                  type="warning"
                  plain
                  :disabled="!item.canSignIn"
                  @click="handleSignIn(item)"
                >
                  立即签到
                </el-button>
                <el-button
                  plain
                  :disabled="!item.canCancel"
                  @click="handleCancel(item)"
                >
                  退出活动
                </el-button>
                <el-button @click="router.push({ name: 'activity-detail', params: { id: item.activityId } })">
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
import { ElMessage } from 'element-plus'
import CoverImage from '@/components/CoverImage.vue'
import { enrollApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'
import { formatActivityStatus } from '@/utils/format'

const router = useRouter()
const { user } = useSession()

const enrollments = ref([])

const enrollmentStatusText = (status) => {
  const map = {
    1: '已报名',
    3: '已签到',
    4: '已完成',
  }
  return map[status] || `状态 ${status}`
}

const loadEnrollments = async () => {
  if (!user.value?.userId) {
    return
  }
  const page = await enrollApi.list({
    userId: user.value.userId,
    pageNo: 0,
    pageSize: 50,
  })
  enrollments.value = page.records || []
}

const handleSignIn = async (item) => {
  await enrollApi.signIn(item.enrollId, user.value.userId)
  ElMessage.success('签到成功')
  await loadEnrollments()
}

const handleCancel = async (item) => {
  await enrollApi.cancel(item.enrollId, user.value.userId)
  ElMessage.success('已退出活动')
  await loadEnrollments()
}

onMounted(loadEnrollments)
</script>
