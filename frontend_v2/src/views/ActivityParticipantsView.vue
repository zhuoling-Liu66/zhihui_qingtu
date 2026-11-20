<template>
  <main class="page page-manage-activities">
    <section class="page-section">
      <section class="manage-header card-surface">
        <div>
          <p class="section-head__kicker">参与人员</p>
          <h1>{{ summary.activityTitle || '活动参与人员' }}</h1>
          <p>
            学生自助签到/签退后，签退会生成待确认的发放记录。你可以修正考勤，
            并按人调整学分或志愿时长；灵活志愿时长录入后需提交管理员审核。
          </p>
        </div>
        <div class="participants-page__head-actions">
          <el-button @click="router.back()">返回</el-button>
          <el-button :loading="loading" @click="loadParticipants">刷新</el-button>
        </div>
      </section>

      <section class="card-surface participants-page">
        <div class="participants-page__summary">
          <span>报名 <strong>{{ summary.totalEnrolled ?? 0 }}</strong></span>
          <span>签到 <strong>{{ summary.signedInCount ?? 0 }}</strong></span>
          <span>签退 <strong>{{ summary.completedCount ?? 0 }}</strong></span>
          <span>待确认 <strong>{{ summary.pendingAwardCount ?? 0 }}</strong></span>
          <span v-if="isFlexibleVolunteer">待审核 <strong>{{ summary.reviewingAwardCount ?? 0 }}</strong></span>
          <span>已发放 <strong>{{ summary.approvedAwardCount ?? 0 }}</strong></span>
          <span>不发放 <strong>{{ summary.rejectedAwardCount ?? 0 }}</strong></span>
          <span v-for="tier in summary.creditTiers || []" :key="tier.tierId" class="participants-page__tier">
            {{ tier.credit }} 学分：{{ tier.used }}/{{ tier.quota }}
          </span>
          <el-button
            type="warning"
            size="small"
            :disabled="batchDisabled"
            :loading="confirming"
            @click="handleConfirmAll"
          >
            {{ batchLabel }}
          </el-button>
        </div>

        <el-table v-loading="loading" :data="rows" empty-text="还没有人报名">
          <el-table-column label="学生" min-width="140">
            <template #default="{ row }">
              <div class="participants-page__name">{{ row.userName || '未知用户' }}</div>
              <div class="participants-page__sub mono">{{ row.userId }}</div>
            </template>
          </el-table-column>
          <el-table-column label="报名状态" width="100">
            <template #default="{ row }">
              <el-tag :type="enrollTagType(row.enrollStatus)" size="small">
                {{ enrollStatusText(row.enrollStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="签到时间" width="150">
            <template #default="{ row }"><span class="mono">{{ formatDateTime(row.signInTime, '未签到') }}</span></template>
          </el-table-column>
          <el-table-column label="签退时间" width="150">
            <template #default="{ row }"><span class="mono">{{ formatDateTime(row.signOutTime, '未签退') }}</span></template>
          </el-table-column>
          <el-table-column label="成绩/时长" width="140">
            <template #default="{ row }">
              <span v-if="!row.recordId" class="participants-page__sub">—</span>
              <span v-else-if="Number(summary.activityType) === 1">
                <span class="mono">{{ row.credit ?? 0 }}</span> 学分
                <div class="participants-page__sub">{{ formatCreditType(row.creditType) }}</div>
              </span>
              <span v-else>
                <span class="mono">{{ row.volunteerHours ?? '—' }}</span>
                {{ row.volunteerHours ? '小时' : '' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="发放状态" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.verifyStatus !== null && row.verifyStatus !== undefined" :type="awardTagType(row.verifyStatus)" size="small">
                {{ awardStatusText(row.verifyStatus) }}
              </el-tag>
              <span v-else class="participants-page__sub">未生成</span>
              <div v-if="Number(row.verifyStatus) === 2 && row.rejectReason" class="participants-page__sub">
                {{ row.rejectReason }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button text type="warning" @click="openManage(row)">管理</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="totalElements > pageSize"
          class="admin-pagination"
          layout="total, prev, pager, next"
          :current-page="pageNo + 1"
          :page-size="pageSize"
          :total="totalElements"
          @current-change="handlePageChange"
        />
      </section>
    </section>

    <el-dialog v-model="manageVisible" title="人员与发放管理" width="560px">
      <div v-if="manageTarget" class="participants-page__manage">
        <p class="participants-page__hint">
          {{ manageTarget.userName || '未知用户' }}（{{ manageTarget.userId }}） ·
          {{ enrollStatusText(manageTarget.enrollStatus) }} ·
          签到 {{ formatDateTime(manageTarget.signInTime, '—') }} ·
          签退 {{ formatDateTime(manageTarget.signOutTime, '—') }} ·
          当前状态 {{ awardStatusText(manageTarget.verifyStatus) }}
        </p>

        <div class="participants-page__block">
          <h4>考勤修正</h4>
          <div class="participants-page__attendance">
            <el-button
              v-if="Number(manageTarget.enrollStatus) === 1"
              size="small"
              type="warning"
              plain
              @click="handleAttendance('MARK_SIGN_IN')"
            >
              补签到
            </el-button>
            <el-button
              v-if="Number(manageTarget.enrollStatus) === 3"
              size="small"
              type="warning"
              plain
              @click="handleAttendance('MARK_SIGN_OUT')"
            >
              补签退（生成发放记录）
            </el-button>
            <el-button
              v-if="Number(manageTarget.enrollStatus) === 3"
              size="small"
              plain
              @click="handleAttendance('REVERT_SIGN_IN')"
            >
              撤销签到
            </el-button>
            <el-button
              v-if="Number(manageTarget.enrollStatus) === 4"
              size="small"
              type="danger"
              plain
              @click="handleAttendance('REVERT_SIGN_OUT')"
            >
              撤销签退
            </el-button>
          </div>
        </div>

        <div v-if="hasAwardType" class="participants-page__block">
          <h4>发放确认</h4>
          <template v-if="manageTarget.recordId">
            <el-form label-position="top">
              <template v-if="Number(summary.activityType) === 1">
                <el-form-item v-if="isComboCredit" label="发放档位（每个档位有独立名额）">
                  <el-select v-model="awardForm.credit" placeholder="选择学分档位">
                    <el-option
                      v-for="tier in summary.creditTiers || []"
                      :key="tier.tierId"
                      :value="Number(tier.credit)"
                      :label="`${tier.credit} 学分（已用 ${tier.used}/${tier.quota}）`"
                      :disabled="tier.used >= tier.quota && Number(tier.credit) !== Number(manageTarget.credit)"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item v-else label="发放学分（可按人修改，0.05 的倍数）">
                  <div class="participants-page__award-row">
                    <el-input-number
                      v-model="awardForm.credit"
                      :min="0.05"
                      :step="0.05"
                      :precision="2"
                    />
                    <el-select v-model="awardForm.creditType" placeholder="学分类型">
                      <el-option :value="1" label="创新创业" />
                      <el-option :value="2" label="智育" />
                      <el-option :value="3" label="德育" />
                      <el-option :value="4" label="体育" />
                      <el-option :value="5" label="美育" />
                      <el-option :value="6" label="劳育" />
                    </el-select>
                  </div>
                </el-form-item>
              </template>

              <el-form-item v-else :label="isFlexibleVolunteer ? '录入志愿时长（小时）' : '发放志愿时长（小时）'">
                <el-input-number
                  v-model="awardForm.volunteerHours"
                  :min="0.5"
                  :step="0.5"
                  :precision="2"
                />
              </el-form-item>

              <el-form-item v-if="!isFlexibleVolunteer" label="发放结论">
                <el-radio-group v-model="awardForm.decision">
                  <el-radio-button :value="1">发放</el-radio-button>
                  <el-radio-button :value="2">不发放</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <p v-else class="participants-page__hint">
                录入时长后点击「提交管理员审核」，管理员通过后才计入统计。
              </p>

              <el-form-item v-if="!isFlexibleVolunteer && awardForm.decision === 2" label="不发放原因">
                <el-input
                  v-model="awardForm.rejectReason"
                  type="textarea"
                  :rows="2"
                  maxlength="255"
                  show-word-limit
                  placeholder="可选，学生能在个人中心看到"
                />
              </el-form-item>
            </el-form>
          </template>
          <p v-else class="participants-page__hint">
            该参与者还没有发放记录。补签退后会自动生成；未签退不得发放。
          </p>
        </div>
      </div>

      <template #footer>
        <el-button @click="manageVisible = false">关闭</el-button>
        <el-button
          v-if="manageTarget && manageTarget.recordId"
          type="warning"
          :loading="awardSaving"
          @click="handleSaveAward"
        >
          {{ isFlexibleVolunteer ? '提交管理员审核' : '保存发放信息' }}
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { enrollApi } from '@/api/platform'
import { formatCreditType, formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const activityId = computed(() => route.params.id)

const summary = ref({})
const rows = ref([])
const totalElements = ref(0)
const pageNo = ref(0)
const pageSize = 10
const loading = ref(false)
const confirming = ref(false)
let loadToken = 0

const manageVisible = ref(false)
const manageTarget = ref(null)
const awardSaving = ref(false)
const awardForm = reactive({
  credit: 1,
  creditType: 2,
  volunteerHours: 1,
  decision: 1,
  rejectReason: '',
})

const hasAwardType = computed(() => [1, 2].includes(Number(summary.value.activityType)))
const isComboCredit = computed(() => Number(summary.value.activityType) === 1 && Number(summary.value.creditMode) === 2)
const isFlexibleVolunteer = computed(() => Number(summary.value.activityType) === 2 && Number(summary.value.volunteerMode) === 2)

const batchLabel = computed(() => (isFlexibleVolunteer.value ? '一键提交审核' : '一键确认待发放'))

const batchDisabled = computed(() => !Number(summary.value.pendingAwardCount))

const enrollStatusText = (status) => {
  if (Number(status) === 3) return '已签到'
  if (Number(status) === 4) return '已完成'
  return '已报名'
}

const enrollTagType = (status) => {
  if (Number(status) === 4) return 'success'
  if (Number(status) === 3) return 'warning'
  return 'info'
}

const awardStatusText = (status) => {
  if (Number(status) === 1) return '已发放'
  if (Number(status) === 2) return '不发放'
  if (Number(status) === 3) return '待审核'
  if (Number(status) === 0) return '待确认'
  return '未生成'
}

const awardTagType = (status) => {
  if (Number(status) === 1) return 'success'
  if (Number(status) === 2) return 'danger'
  if (Number(status) === 3) return 'warning'
  return 'info'
}

const loadParticipants = async () => {
  if (!activityId.value) return
  const requestId = ++loadToken
  loading.value = true
  try {
    const response = await enrollApi.activityParticipants(activityId.value, {
      pageNo: pageNo.value,
      pageSize,
    })
    if (requestId !== loadToken) return
    summary.value = response || {}
    rows.value = response?.page?.records || []
    totalElements.value = response?.page?.totalElements || 0
    if (!rows.value.length && pageNo.value > 0) {
      pageNo.value -= 1
      await loadParticipants()
    }
    if (manageTarget.value) {
      const latest = rows.value.find((item) => item.enrollId === manageTarget.value.enrollId)
      if (latest) manageTarget.value = latest
    }
  } finally {
    if (requestId === loadToken) loading.value = false
  }
}

const handlePageChange = (current) => {
  pageNo.value = current - 1
  loadParticipants()
}

const openManage = (row) => {
  manageTarget.value = row
  if (isComboCredit.value) {
    const currentCredit = Number(row.credit ?? 0)
    awardForm.credit = currentCredit > 0
      ? currentCredit
      : (summary.value.creditTiers?.[0] ? Number(summary.value.creditTiers[0].credit) : null)
  } else {
    awardForm.credit = Number(row.credit ?? summary.value.activityCredit ?? 1)
  }
  awardForm.creditType = row.creditType ?? summary.value.creditType ?? 2
  awardForm.volunteerHours = Number(row.volunteerHours ?? summary.value.volunteerHours ?? 1)
  awardForm.decision = Number(row.verifyStatus) === 2 ? 2 : 1
  awardForm.rejectReason = row.rejectReason || ''
  manageVisible.value = true
}

const attendanceLabels = {
  MARK_SIGN_IN: '补签到',
  MARK_SIGN_OUT: '补签退',
  REVERT_SIGN_IN: '撤销签到',
  REVERT_SIGN_OUT: '撤销签退',
}

const handleAttendance = async (action) => {
  if (!manageTarget.value) return
  await ElMessageBox.confirm(
    `确认对该参与者执行「${attendanceLabels[action]}」吗？`,
    '考勤操作确认',
    { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
  )
  await enrollApi.updateAttendance(manageTarget.value.enrollId, { action })
  ElMessage.success('考勤已更新')
  await loadParticipants()
}

const handleSaveAward = async () => {
  if (!manageTarget.value || awardSaving.value) return
  if (isComboCredit.value && Number(awardForm.decision) !== 2 && !awardForm.credit) {
    ElMessage.warning('请先选择学分档位')
    return
  }
  awardSaving.value = true
  try {
    const payload = {}
    if (Number(summary.value.activityType) === 1) {
      payload.credit = awardForm.credit
      payload.creditType = isComboCredit.value
        ? (summary.value.creditType ?? 2)
        : awardForm.creditType
      payload.verifyStatus = awardForm.decision
    } else {
      payload.volunteerHours = awardForm.volunteerHours
      payload.verifyStatus = isFlexibleVolunteer.value ? 3 : awardForm.decision
    }
    if (!isFlexibleVolunteer.value && Number(awardForm.decision) === 2 && awardForm.rejectReason.trim()) {
      payload.rejectReason = awardForm.rejectReason.trim()
    }
    await enrollApi.updateAward(manageTarget.value.enrollId, payload)
    ElMessage.success(isFlexibleVolunteer.value ? '已提交管理员审核' : '发放信息已保存')
    await loadParticipants()
  } finally {
    awardSaving.value = false
  }
}

const handleConfirmAll = async () => {
  const actionText = batchLabel.value
  const count = summary.value.pendingAwardCount
  await ElMessageBox.confirm(
    `确认执行「${actionText}」吗？共 ${count} 条记录。`,
    actionText,
    { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
  )
  confirming.value = true
  try {
    await enrollApi.confirmAwards(activityId.value)
    ElMessage.success(`${actionText}完成`)
    await loadParticipants()
  } finally {
    confirming.value = false
  }
}

onMounted(loadParticipants)
</script>

<style scoped>
.participants-page__head-actions {
  display: flex;
  gap: 8px;
}

.participants-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
}

.participants-page__summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  font-size: 13px;
  color: var(--text-2, #666);
}

.participants-page__summary strong {
  color: var(--text-1, #111);
}

.participants-page__tier {
  color: #b26a00;
}

.participants-page__name {
  font-weight: 600;
}

.participants-page__sub {
  color: var(--text-2, #888);
  font-size: 12px;
}

.participants-page__manage {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.participants-page__block h4 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.participants-page__attendance {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.participants-page__award-row {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
}

/* flex 容器里必须给输入框固定宽度，否则会被旁边的下拉框压到 0 宽 */
.participants-page__award-row :deep(.el-input-number) {
  width: 170px;
  flex: 0 0 170px;
}

.participants-page__award-row :deep(.el-select) {
  flex: 1 1 auto;
  min-width: 160px;
}

.participants-page__hint {
  margin: 0;
  color: var(--text-2, #888);
  font-size: 13px;
  line-height: 1.6;
}
</style>
