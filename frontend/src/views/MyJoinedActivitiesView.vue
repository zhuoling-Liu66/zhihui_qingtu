<template>
  <main class="page page-manage-activities">
    <section class="page-section">
      <div v-if="!user" class="card-surface empty-login">
        <h2>登录后查看我参加的活动</h2>
        <p>这里会集中展示你已报名的活动，并支持签到、补充评价或在活动开始前退出。</p>
      </div>

      <template v-else>
        <section class="manage-header card-surface">
          <div>
            <p class="section-head__kicker">参与记录</p>
            <h1>我参加的活动</h1>
            <p>现在采用“签到即完成”的规则。第二课堂活动签到后会直接生成待审核学分记录，之后可选补充活动评价。</p>
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
                  签到并完成
                </el-button>
                <el-button
                  plain
                  :disabled="!item.canCancel"
                  @click="handleCancel(item)"
                >
                  退出活动
                </el-button>
                <el-button
                  plain
                  :disabled="!item.canFeedback"
                  @click="openFeedbackDialog(item)"
                >
                  补充评价
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

    <el-dialog v-model="feedbackDialogVisible" title="活动评价" width="520px">
      <el-form :model="feedbackForm" label-position="top">
        <el-form-item label="评分">
          <el-rate v-model="feedbackForm.feedbackScore" :max="5" />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input
            v-model="feedbackForm.feedbackContent"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="补充你对这次活动的体验和建议"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="submittingFeedback" @click="submitFeedback">提交评价</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CoverImage from '@/components/CoverImage.vue'
import { enrollApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'
import { formatActivityStatus } from '@/utils/format'

const router = useRouter()
const { user } = useSession()

const enrollments = ref([])
const feedbackDialogVisible = ref(false)
const submittingFeedback = ref(false)
const activeEnrollmentId = ref(null)

const feedbackForm = reactive({
  feedbackScore: 5,
  feedbackContent: '',
})

const enrollmentStatusText = (status) => {
  const map = {
    1: '已报名',
    3: '已签到（旧数据）',
    4: '已完成',
  }
  return map[status] || `状态 ${status}`
}

const loadEnrollments = async () => {
  if (!user.value?.userId) return
  const page = await enrollApi.list({
    userId: user.value.userId,
    pageNo: 0,
    pageSize: 50,
  })
  enrollments.value = page.records || []
}

const handleSignIn = async (item) => {
  await enrollApi.signIn(item.enrollId, user.value.userId)
  ElMessage.success('签到成功，活动已完成')
  await loadEnrollments()
}

const handleCancel = async (item) => {
  await enrollApi.cancel(item.enrollId, user.value.userId)
  ElMessage.success('已退出活动')
  await loadEnrollments()
}

const openFeedbackDialog = (item) => {
  activeEnrollmentId.value = item.enrollId
  feedbackForm.feedbackScore = item.feedbackScore || 5
  feedbackForm.feedbackContent = item.feedbackContent || ''
  feedbackDialogVisible.value = true
}

const submitFeedback = async () => {
  if (!activeEnrollmentId.value) return
  submittingFeedback.value = true
  try {
    await enrollApi.feedback(activeEnrollmentId.value, {
      userId: user.value.userId,
      feedbackScore: feedbackForm.feedbackScore,
      feedbackContent: feedbackForm.feedbackContent,
    })
    ElMessage.success('活动评价已提交')
    feedbackDialogVisible.value = false
    await loadEnrollments()
  } finally {
    submittingFeedback.value = false
  }
}

onMounted(loadEnrollments)
</script>
