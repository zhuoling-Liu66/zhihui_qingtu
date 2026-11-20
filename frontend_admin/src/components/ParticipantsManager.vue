<template>
  <div class="participants-manager">
    <div class="participants-manager__head">
      <div class="participants-manager__summary">
        <span>报名 <strong>{{ summary.totalEnrolled ?? 0 }}</strong></span>
        <span>签到 <strong>{{ summary.signedInCount ?? 0 }}</strong></span>
        <span>签退 <strong>{{ summary.completedCount ?? 0 }}</strong></span>
        <span>待确认 <strong>{{ summary.pendingAwardCount ?? 0 }}</strong></span>
        <span v-if="Number(summary.reviewingAwardCount)">待审核 <strong>{{ summary.reviewingAwardCount }}</strong></span>
        <span>已发放 <strong>{{ summary.approvedAwardCount ?? 0 }}</strong></span>
        <span>不发放 <strong>{{ summary.rejectedAwardCount ?? 0 }}</strong></span>
        <span v-for="tier in summary.creditTiers || []" :key="tier.tierId" class="participants-manager__tier">
          {{ tier.credit }} 学分：{{ tier.used }}/{{ tier.quota }}
        </span>
      </div>
      <div class="participants-manager__actions">
        <el-button plain size="small" :loading="loading" @click="loadParticipants">刷新</el-button>
        <el-button v-if="hasAwardType" size="small" plain @click="importVisible = true">
          AI 智能导入
        </el-button>
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
    </div>

    <el-table v-loading="loading" :data="rows" size="small" empty-text="还没有人报名">
      <el-table-column label="学生" min-width="130">
        <template #default="{ row }">
          <div class="admin-cell-title">{{ row.userName || '未知用户' }}</div>
          <div class="admin-cell-sub mono">{{ row.userId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="报名状态" width="90">
        <template #default="{ row }">
          <el-tag :type="enrollTagType(row.enrollStatus)" size="small">
            {{ enrollStatusText(row.enrollStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签到时间" width="140">
        <template #default="{ row }"><span class="mono">{{ formatDateTime(row.signInTime, '未签到') }}</span></template>
      </el-table-column>
      <el-table-column label="签退时间" width="140">
        <template #default="{ row }"><span class="mono">{{ formatDateTime(row.signOutTime, '未签退') }}</span></template>
      </el-table-column>
      <el-table-column label="成绩/时长" width="130">
        <template #default="{ row }">
          <span v-if="!row.recordId" class="admin-cell-sub">—</span>
          <span v-else-if="Number(summary.activityType) === 1">
            <span class="mono">{{ row.credit ?? 0 }}</span> 学分
            <div class="admin-cell-sub">{{ formatCreditType(row.creditType) }}</div>
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
          <span v-else class="admin-cell-sub">未生成</span>
          <div v-if="Number(row.verifyStatus) === 2 && row.rejectReason" class="admin-cell-sub">
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

    <el-dialog
      v-model="manageVisible"
      title="人员与发放管理"
      width="560px"
      append-to-body
    >
      <div v-if="manageTarget" class="participants-manager__manage">
        <p class="admin-dialog-hint">
          {{ manageTarget.userName || '未知用户' }}（{{ manageTarget.userId }}） ·
          {{ enrollStatusText(manageTarget.enrollStatus) }} ·
          签到 {{ formatDateTime(manageTarget.signInTime, '—') }} ·
          签退 {{ formatDateTime(manageTarget.signOutTime, '—') }} ·
          当前状态 {{ awardStatusText(manageTarget.verifyStatus) }}
        </p>

        <div class="participants-manager__block">
          <h4>考勤修正</h4>
          <div class="participants-manager__attendance">
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

        <div v-if="hasAwardType" class="participants-manager__block">
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
                  <div class="participants-manager__award-row">
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

              <el-form-item v-if="showDecision" label="发放结论">
                <el-radio-group v-model="awardForm.decision">
                  <el-radio-button :value="1">发放</el-radio-button>
                  <el-radio-button :value="2">不发放</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <p v-else class="admin-dialog-hint">
                {{ staffHoldReview
                  ? '该记录来自 AI 导入，等待管理员审核；你可以调整金额，保存后仍保持待审核。'
                  : '灵活时长由发布者录入后提交，管理员审核通过后才计入统计。' }}
              </p>

              <el-form-item v-if="showDecision && awardForm.decision === 2" label="不发放原因">
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
          <p v-else class="admin-dialog-hint">
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
          {{ saveButtonLabel }}
        </el-button>
      </template>
    </el-dialog>

    <AwardImportDialog
      v-model="importVisible"
      :activity-id="activityId"
      @saved="loadParticipants"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import { useAdminSession } from '@/composables/useAdminSession'
import AwardImportDialog from '@/components/AwardImportDialog.vue'
import { formatCreditType, formatDateTime } from '@/utils/format'

const props = defineProps({
  activityId: {
    type: [Number, String],
    required: true,
  },
})

const { isAdmin } = useAdminSession()

const summary = ref({})
const rows = ref([])
const totalElements = ref(0)
const pageNo = ref(0)
const pageSize = 10
const loading = ref(false)
const confirming = ref(false)
let loadToken = 0

const manageVisible = ref(false)
const importVisible = ref(false)
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
// AI 导入的记录处于待审核：运营只能调金额，不能自行通过/驳回
const staffHoldReview = computed(() => !isAdmin.value && Number(manageTarget.value?.verifyStatus) === 3)
const showDecision = computed(() => !(isFlexibleVolunteer.value && !isAdmin.value) && !staffHoldReview.value)
const saveButtonLabel = computed(() => {
  if (staffHoldReview.value) return '保存金额（保持待审核）'
  if (isFlexibleVolunteer.value && !isAdmin.value) return '提交管理员审核'
  return '保存发放信息'
})

const batchLabel = computed(() => {
  if (isAdmin.value && (isFlexibleVolunteer.value || Number(summary.value.reviewingAwardCount) > 0)) {
    return '一键通过待审核'
  }
  if (isFlexibleVolunteer.value) {
    return '一键提交审核'
  }
  return '一键确认待发放'
})

const batchDisabled = computed(() => {
  if (isFlexibleVolunteer.value && isAdmin.value) {
    return !Number(summary.value.reviewingAwardCount)
  }
  if (isAdmin.value && Number(summary.value.reviewingAwardCount) > 0) {
    return false
  }
  return !Number(summary.value.pendingAwardCount)
})

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
  if (!props.activityId) return
  const requestId = ++loadToken
  loading.value = true
  try {
    const response = await adminApi.activityParticipants(props.activityId, {
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
  const combo = isComboCredit.value
  if (combo) {
    const currentCredit = Number(row.credit ?? 0)
    awardForm.credit = currentCredit > 0
      ? currentCredit
      : (summary.value.creditTiers?.[0] ? Number(summary.value.creditTiers[0].credit) : null)
  } else {
    awardForm.credit = Number(row.credit ?? summary.value.activityCredit ?? 1)
  }
  awardForm.creditType = row.creditType ?? summary.value.creditType ?? 2
  awardForm.volunteerHours = Number(row.volunteerHours ?? summary.value.volunteerHours ?? 1)
  if (isFlexibleVolunteer.value && !isAdmin.value) {
    awardForm.decision = 3
  } else {
    awardForm.decision = Number(row.verifyStatus) === 2 ? 2 : 1
  }
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
  await adminApi.updateAttendance(manageTarget.value.enrollId, { action })
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
      if (!staffHoldReview.value) {
        payload.verifyStatus = awardForm.decision
      }
    } else {
      payload.volunteerHours = awardForm.volunteerHours
      if (isFlexibleVolunteer.value && !isAdmin.value) {
        payload.verifyStatus = 3
      } else if (!staffHoldReview.value) {
        payload.verifyStatus = awardForm.decision
      }
    }
    if (!staffHoldReview.value && Number(awardForm.decision) === 2 && awardForm.rejectReason.trim()) {
      payload.rejectReason = awardForm.rejectReason.trim()
    }
    await adminApi.updateAward(manageTarget.value.enrollId, payload)
    ElMessage.success(staffHoldReview.value
      ? '金额已保存，仍等待管理员审核'
      : (isFlexibleVolunteer.value && !isAdmin.value
        ? '已提交管理员审核'
        : '发放信息已保存'))
    await loadParticipants()
  } finally {
    awardSaving.value = false
  }
}

const handleConfirmAll = async () => {
  const actionText = batchLabel.value
  let count = summary.value.pendingAwardCount
  if (isAdmin.value && Number(summary.value.reviewingAwardCount) > 0) {
    count = summary.value.reviewingAwardCount
  }
  await ElMessageBox.confirm(
    `确认执行「${actionText}」吗？共 ${count} 条记录。`,
    actionText,
    { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
  )
  confirming.value = true
  try {
    await adminApi.confirmAwards(props.activityId)
    ElMessage.success(`${actionText}完成`)
    await loadParticipants()
  } finally {
    confirming.value = false
  }
}

watch(
  () => props.activityId,
  () => {
    pageNo.value = 0
    loadParticipants()
  },
)

onMounted(loadParticipants)
</script>

<style scoped>
.participants-manager {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.participants-manager__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.participants-manager__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 13px;
  color: var(--text-2, #666);
}

.participants-manager__summary strong {
  color: var(--text-1, #111);
}

.participants-manager__tier {
  color: #b26a00;
}

.participants-manager__actions {
  display: flex;
  gap: 8px;
}

.participants-manager__manage {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.participants-manager__block h4 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.participants-manager__attendance {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.participants-manager__award-row {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
}

/*
 * 发放行是 flex 容器：如果不给 el-input-number 固定基准宽度，
 * 旁边的 select 会把它压到很窄，内部数字输入框甚至变成 0 宽（看不见金额）。
 */
.participants-manager__award-row :deep(.el-input-number) {
  width: 170px;
  flex: 0 0 170px;
}

.participants-manager__award-row :deep(.el-select) {
  flex: 1 1 auto;
  min-width: 160px;
}
</style>
