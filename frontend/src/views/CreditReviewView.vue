<template>
  <main class="page page-manage-activities">
    <section class="page-section">
      <div v-if="!canReview" class="card-surface empty-login">
        <h2>当前账号没有学分审核入口</h2>
        <p>管理员、老师，或作为第二课堂活动发布者的用户，可以在这里审核学分记录。</p>
      </div>

      <template v-else>
        <section class="manage-header card-surface">
          <div>
            <p class="section-head__kicker">学分审核</p>
            <h1>学分记录审核台</h1>
            <p>管理员和老师可审核全部学分记录；活动发布者只能审核自己发布的第二课堂活动产生的学分。</p>
          </div>
          <el-button @click="loadCredits">刷新列表</el-button>
        </section>

        <section class="card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">筛选条件</p>
              <h3>按学分状态或活动筛选</h3>
            </div>
          </div>
          <div class="grid-two">
            <el-form-item label="审核状态">
              <el-select v-model="filters.verifyStatus" clearable placeholder="全部状态">
                <el-option :value="0" label="待审核" />
                <el-option :value="1" label="已通过" />
                <el-option :value="2" label="已驳回" />
              </el-select>
            </el-form-item>
            <el-form-item label="学分类型">
              <el-select v-model="filters.creditType" clearable placeholder="全部类型">
                <el-option :value="1" label="创新创业" />
                <el-option :value="2" label="智育" />
                <el-option :value="3" label="德育" />
                <el-option :value="4" label="体育" />
                <el-option :value="5" label="美育" />
                <el-option :value="6" label="劳育" />
              </el-select>
            </el-form-item>
          </div>
          <div class="grid-two">
            <el-form-item label="学生ID">
              <el-input v-model="filters.userId" placeholder="按学号/工号筛选" />
            </el-form-item>
            <el-form-item label="活动">
              <el-select v-model="filters.activityId" clearable placeholder="全部活动">
                <el-option
                  v-for="activity in activityOptions"
                  :key="activity.activityId"
                  :label="activity.activityTitle"
                  :value="activity.activityId"
                />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <section class="manage-grid">
          <article v-for="record in visibleCredits" :key="record.recordId" class="manage-card card-surface">
            <div class="manage-card__body manage-card__body--single">
              <div class="manage-card__head">
                <div>
                  <h3>记录 #{{ record.recordId }}</h3>
                  <p>学生 {{ record.userId }} · 活动 {{ record.activityId }}</p>
                </div>
                <el-tag round>{{ reviewStatusText(record.verifyStatus) }}</el-tag>
              </div>

              <div class="manage-card__meta">
                <span>学分 {{ record.credit }}</span>
                <span>{{ creditTypeText(record.creditType) }}</span>
                <span>{{ record.verifyTime || '未审核' }}</span>
              </div>

              <p v-if="record.rejectReason" class="team-card__meta">驳回原因：{{ record.rejectReason }}</p>

              <div class="manage-card__actions">
                <el-button
                  type="warning"
                  plain
                  :disabled="record.verifyStatus === 1"
                  @click="reviewRecord(record, 1)"
                >
                  审核通过
                </el-button>
                <el-button
                  plain
                  :disabled="record.verifyStatus === 2"
                  @click="openRejectDialog(record)"
                >
                  驳回
                </el-button>
              </div>
            </div>
          </article>
        </section>
      </template>
    </section>

    <el-dialog v-model="rejectDialogVisible" title="驳回原因" width="520px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="4"
        maxlength="255"
        show-word-limit
        placeholder="请输入驳回原因"
      />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { activityApi, creditApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'

const route = useRoute()
const { user } = useSession()

const credits = ref([])
const ownActivities = ref([])
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref(null)

const filters = reactive({
  verifyStatus: null,
  creditType: null,
  userId: '',
  activityId: route.query.activityId ? Number(route.query.activityId) : null,
})

const canReview = computed(() => {
  const role = user.value?.userRole
  return Boolean(role === 2 || role === 3 || Number(user.value?.publisherStatus) === 1)
})
const isAdminOrTeacher = computed(() => [2, 3].includes(user.value?.userRole))
const activityOptions = computed(() => {
  if (isAdminOrTeacher.value) return ownActivities.value
  return ownActivities.value
})
const visibleCredits = computed(() => {
  if (isAdminOrTeacher.value) return credits.value
  const ownActivityIds = new Set(ownActivities.value.map((activity) => activity.activityId))
  return credits.value.filter((record) => ownActivityIds.has(record.activityId))
})

const creditTypeText = (type) => {
  const map = {
    1: '创新创业',
    2: '智育',
    3: '德育',
    4: '体育',
    5: '美育',
    6: '劳育',
  }
  return map[type] || `类型 ${type}`
}

const reviewStatusText = (status) => {
  const map = {
    0: '待审核',
    1: '已通过',
    2: '已驳回',
  }
  return map[status] || `状态 ${status}`
}

const loadCredits = async () => {
  if (!canReview.value) return

  if (isAdminOrTeacher.value) {
    ownActivities.value = (await activityApi.list({ pageNo: 0, pageSize: 200, activityType: 1 })).records || []
  } else {
    // publisherUserId 是"按发布者筛选活动"的查询条件，不是身份声明，因此保留。
    ownActivities.value = (await activityApi.list({
      pageNo: 0,
      pageSize: 200,
      publisherUserId: user.value.userId,
      activityType: 1,
    })).records || []
  }

  const page = await creditApi.list({
    pageNo: 0,
    pageSize: 200,
    verifyStatus: filters.verifyStatus ?? undefined,
    creditType: filters.creditType ?? undefined,
    userId: filters.userId || undefined,
    activityId: filters.activityId || undefined,
  })
  credits.value = page.records || []
}

const reviewRecord = async (record, verifyStatus, reason = '') => {
  // 不再传 verifyUserId：审核人由后端从 JWT 取，
  // 并自行判定"是不是管理员/老师/该活动发布者"，前端无从伪造。
  await creditApi.review(record.recordId, {
    verifyStatus,
    rejectReason: reason,
  })
  ElMessage.success(verifyStatus === 1 ? '学分已审核通过' : '学分已驳回')
  await loadCredits()
}

const openRejectDialog = (record) => {
  rejectTarget.value = record
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

const submitReject = async () => {
  if (!rejectTarget.value) return
  await reviewRecord(rejectTarget.value, 2, rejectReason.value)
  rejectDialogVisible.value = false
}

onMounted(loadCredits)

watch(
  () => route.query.activityId,
  async (activityId) => {
    filters.activityId = activityId ? Number(activityId) : null
    if (canReview.value) {
      await loadCredits()
    }
  },
)
</script>
