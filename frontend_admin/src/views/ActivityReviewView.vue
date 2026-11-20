<template>
  <div class="admin-page">
    <section class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <h2>活动筛选</h2>
          <p>发布后先审核，通过后公开并开放报名；管理员发布的活动免审核</p>
        </div>
        <div class="admin-filter-actions">
          <el-button plain :loading="loading" @click="loadActivities">刷新</el-button>
          <el-button type="warning" @click="router.push({ name: 'admin-activity-create' })">
            发布活动
          </el-button>
        </div>
      </div>

      <div class="admin-filters">
        <el-form-item label="审核状态">
          <el-select v-model="filters.reviewStatus" clearable placeholder="全部状态">
            <el-option :value="0" label="待审核" />
            <el-option :value="1" label="审核通过" />
            <el-option :value="2" label="审核驳回" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动类型">
          <el-select v-model="filters.activityType" clearable placeholder="全部类型">
            <el-option :value="1" label="第二课堂学分活动" />
            <el-option :value="2" label="志愿服务活动" />
            <el-option :value="3" label="运动健身活动" />
            <el-option :value="4" label="兴趣团建活动" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="标题 / 地点 / 发布者"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="发布者工号">
          <el-input
            v-model="filters.publisherUserId"
            clearable
            placeholder="精确工号"
            :disabled="onlyMine"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="范围">
          <el-switch
            v-model="onlyMine"
            active-text="只看我发布的"
            @change="handleSearch"
          />
        </el-form-item>
        <div class="admin-filter-actions">
          <el-button type="warning" @click="handleSearch">查询</el-button>
          <el-button plain @click="resetFilters">重置</el-button>
        </div>
      </div>
    </section>

    <section class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <h2>活动列表</h2>
          <p>共 {{ totalElements }} 个活动</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" :empty-text="emptyText">
        <el-table-column label="活动" min-width="195">
          <template #default="{ row }">
            <div class="admin-cell-title">{{ row.activityTitle }}</div>
            <div class="admin-cell-sub"><span class="mono">#{{ row.activityId }}</span> · {{ row.activityLocation || '地点待定' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="发布者" width="140">
          <template #default="{ row }">
            <div>{{ row.publisherName || '未知用户' }}</div>
            <div class="admin-cell-sub">{{ row.publisherUserId }}</div>
          </template>
        </el-table-column>

        <el-table-column label="类型与奖励" width="170">
          <template #default="{ row }">
            <div>
              {{ formatActivityType(row.activityType) }}
              <el-tag v-if="Number(row.activityType) === 1 && Number(row.creditMode) === 2" size="small" type="warning">组合</el-tag>
              <el-tag v-if="Number(row.activityType) === 2 && Number(row.volunteerMode) === 2" size="small" type="warning">灵活</el-tag>
            </div>
            <div class="admin-cell-sub">
              <template v-if="Number(row.activityType) === 1">
                {{ Number(row.creditMode) === 2 ? '按档位发放' : `${row.activityCredit ?? 0} 学分` }} · {{ formatCreditType(row.creditType) }}
              </template>
              <template v-else-if="Number(row.activityType) === 2">
                {{ Number(row.volunteerMode) === 2 ? '活动后逐人录入' : `志愿 ${row.volunteerHours ?? 0} 小时` }}
              </template>
              <template v-else>无学分</template>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="reviewTagType(row.reviewStatus)" size="small">
              {{ formatReviewStatus(row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="活动状态" width="90">
          <template #default="{ row }">
            <el-tag :type="activityTagType(row.displayStatus)" size="small">
              {{ formatActivityStatus(row.displayStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="活动时间" width="170">
          <template #default="{ row }">
            <div class="mono">{{ formatDateTime(row.startTime) }}</div>
            <div class="admin-cell-sub mono">至 {{ formatDateTime(row.endTime) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDetail(row)">查看</el-button>
            <el-button
              v-if="Number(row.reviewStatus) !== 1"
              text
              type="warning"
              @click="openReview(row)"
            >
              审核
            </el-button>
            <el-button
              v-else
              text
              type="warning"
              @click="toggleStatus(row)"
            >
              {{ Number(row.activityStatus) === 4 ? '重新发布' : '下架' }}
            </el-button>
            <el-button text type="danger" @click="removeActivity(row)">删除</el-button>
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

    <el-dialog v-model="reviewVisible" title="审核活动" width="520px">
      <div v-if="reviewTarget" class="admin-detail">
        <div class="admin-detail__head">
          <div>
            <h3>{{ reviewTarget.activityTitle }}</h3>
            <p class="admin-dialog-hint">
              {{ reviewTarget.publisherName || reviewTarget.publisherUserId }} ·
              {{ formatActivityType(reviewTarget.activityType) }}
            </p>
          </div>
        </div>

        <el-radio-group v-model="reviewForm.reviewStatus">
          <el-radio-button :value="1">审核通过</el-radio-button>
          <el-radio-button :value="2">驳回</el-radio-button>
        </el-radio-group>

        <el-input
          v-if="Number(reviewForm.reviewStatus) === 2"
          v-model="reviewForm.reviewReason"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          placeholder="请填写驳回原因，发布者会在「我发布的活动」中看到"
        />
        <p v-else class="admin-dialog-hint">
          通过后活动立即公开；若结束时间已过，会显示为已结束。
        </p>
      </div>

      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="warning" :loading="reviewSaving" @click="submitReview">
          提交审核
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="活动详情" width="920px">
      <div v-if="detailTarget" class="admin-detail">
        <img
          v-if="detailTarget.coverUrl"
          class="admin-detail__cover"
          :src="detailTarget.coverUrl"
          :alt="detailTarget.activityTitle"
        />
        <div class="admin-detail__head">
          <div>
            <h3>{{ detailTarget.activityTitle }}</h3>
            <div class="admin-detail__tags">
              <el-tag :type="reviewTagType(detailTarget.reviewStatus)" size="small">
                {{ formatReviewStatus(detailTarget.reviewStatus) }}
              </el-tag>
              <el-tag :type="activityTagType(detailTarget.displayStatus)" size="small">
                {{ formatActivityStatus(detailTarget.displayStatus) }}
              </el-tag>
              <el-tag size="small" type="info">{{ formatActivityType(detailTarget.activityType) }}</el-tag>
            </div>
          </div>
        </div>

        <p class="admin-detail__desc">{{ detailTarget.activityDesc || '暂无活动描述' }}</p>

        <div class="admin-detail__meta">
          <div><span>发布者</span><strong>{{ detailTarget.publisherName || '未知' }}（{{ detailTarget.publisherUserId }}）</strong></div>
          <div>
            <span>奖励</span>
            <strong v-if="Number(detailTarget.activityType) === 1">
              <template v-if="Number(detailTarget.creditMode) === 2">
                <el-tag size="small" type="warning">组合学分</el-tag>
                {{ formatCreditType(detailTarget.creditType) }}
                <div v-for="tier in detailTarget.creditTiers || []" :key="tier.tierId" class="admin-cell-sub">
                  {{ tier.credit }} 学分 · {{ tier.quota }} 名
                </div>
              </template>
              <template v-else>{{ detailTarget.activityCredit ?? 0 }} 学分 · {{ formatCreditType(detailTarget.creditType) }}</template>
            </strong>
            <strong v-else-if="Number(detailTarget.activityType) === 2">
              <template v-if="Number(detailTarget.volunteerMode) === 2">
                <el-tag size="small" type="warning">灵活时长</el-tag>
                活动结束后逐人录入
              </template>
              <template v-else>志愿 {{ detailTarget.volunteerHours ?? 0 }} 小时</template>
            </strong>
            <strong v-else>无学分</strong>
          </div>
          <div><span>人数</span><strong>{{ detailTarget.currentParticipant }}/{{ detailTarget.maxParticipant || '不限' }}</strong></div>
          <div><span>开始</span><strong>{{ formatDateTime(detailTarget.startTime) }}</strong></div>
          <div><span>结束</span><strong>{{ formatDateTime(detailTarget.endTime) }}</strong></div>
          <div><span>签到开关</span><strong>
            <el-switch
              :model-value="Number(detailTarget.signInEnabled) === 1"
              :disabled="Number(detailTarget.reviewStatus) !== 1"
              @change="(value) => toggleSignIn(detailTarget, value)"
            />
          </strong></div>
          <div><span>审核人</span><strong>{{ detailTarget.reviewerUserId || '尚未审核' }}</strong></div>
        </div>

        <p v-if="detailTarget.reviewReason" class="admin-detail__reason">
          审核说明：{{ detailTarget.reviewReason }}
        </p>

        <div class="admin-detail__section">
          <h4>参与人员与发放</h4>
          <p class="admin-dialog-hint">
            管理员可修正考勤、调整每人的学分/志愿时长并确认发放，权限与活动发布者一致。
          </p>
          <ParticipantsManager :activity-id="detailTarget.activityId" />
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detailTarget && Number(detailTarget.reviewStatus) !== 1"
          type="warning"
          @click="openReview(detailTarget); detailVisible = false"
        >
          去审核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import { useAdminSession } from '@/composables/useAdminSession'
import ParticipantsManager from '@/components/ParticipantsManager.vue'
import {
  activityTagType,
  formatActivityStatus,
  formatActivityType,
  formatCreditType,
  formatDateTime,
  formatReviewStatus,
  reviewTagType,
} from '@/utils/format'

const route = useRoute()
const router = useRouter()
const { user } = useAdminSession()

// 默认展示全部状态，避免待审核队列为空时整页看起来像坏了；
// 概览页「去审核」会带 reviewStatus=0 直接进入待审核队列。
const initialReviewStatus = () => {
  const raw = route.query.reviewStatus
  const value = Number(raw)
  return raw !== undefined && [0, 1, 2].includes(value) ? value : null
}

const filters = reactive({
  reviewStatus: initialReviewStatus(),
  activityType: null,
  keyword: '',
  publisherUserId: '',
})

// 管理员快速查看自己发布的活动（管理员发布免审核、立即公开）。
const onlyMine = ref(false)

const emptyText = computed(() => (Number(filters.reviewStatus) === 0
  ? '没有待审核的活动'
  : '没有符合条件的活动'))

const rows = ref([])
const totalElements = ref(0)
const pageNo = ref(0)
const pageSize = 10
const loading = ref(false)
let loadToken = 0

const reviewVisible = ref(false)
const reviewSaving = ref(false)
const reviewTarget = ref(null)
const reviewForm = reactive({
  reviewStatus: 1,
  reviewReason: '',
})

const detailVisible = ref(false)
const detailTarget = ref(null)

const buildParams = () => {
  const params = { pageNo: pageNo.value, pageSize }
  if (filters.reviewStatus !== null && filters.reviewStatus !== '') params.reviewStatus = filters.reviewStatus
  if (filters.activityType !== null && filters.activityType !== '') params.activityType = filters.activityType
  if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
  if (onlyMine.value && user.value?.userId) {
    params.publisherUserId = user.value.userId
  } else if (filters.publisherUserId.trim()) {
    params.publisherUserId = filters.publisherUserId.trim()
  }
  return params
}

const loadActivities = async () => {
  const requestId = ++loadToken
  loading.value = true
  try {
    const page = await adminApi.activities(buildParams())
    if (requestId !== loadToken) return
    rows.value = page.records || []
    totalElements.value = page.totalElements || 0
    if (!rows.value.length && pageNo.value > 0) {
      pageNo.value -= 1
      await loadActivities()
    }
  } finally {
    if (requestId === loadToken) loading.value = false
  }
}

const handleSearch = () => {
  pageNo.value = 0
  loadActivities()
}

const resetFilters = () => {
  filters.reviewStatus = null
  filters.activityType = null
  filters.keyword = ''
  filters.publisherUserId = ''
  onlyMine.value = false
  pageNo.value = 0
  loadActivities()
}

const handlePageChange = (current) => {
  pageNo.value = current - 1
  loadActivities()
}

const openReview = (row) => {
  reviewTarget.value = row
  reviewForm.reviewStatus = 1
  reviewForm.reviewReason = ''
  reviewVisible.value = true
}

const submitReview = async () => {
  if (!reviewTarget.value || reviewSaving.value) return
  if (Number(reviewForm.reviewStatus) === 2 && !reviewForm.reviewReason.trim()) {
    ElMessage.warning('驳回活动时必须填写原因')
    return
  }
  reviewSaving.value = true
  try {
    await adminApi.reviewActivity(reviewTarget.value.activityId, {
      reviewStatus: reviewForm.reviewStatus,
      reviewReason: Number(reviewForm.reviewStatus) === 2
        ? reviewForm.reviewReason.trim()
        : null,
    })
    ElMessage.success(reviewForm.reviewStatus === 1 ? '活动已审核通过' : '活动已驳回')
    reviewVisible.value = false
    await loadActivities()
  } finally {
    reviewSaving.value = false
  }
}

const openDetail = async (row) => {
  detailTarget.value = row
  detailVisible.value = true
  try {
    // 列表 VO 不带档位明细，打开详情时补一次详情查询（含 creditTiers）
    const detail = await adminApi.activityDetail(row.activityId)
    detailTarget.value = { ...row, ...detail }
  } catch {
    // 详情接口失败时保留列表行数据，不影响审核/签到等操作
  }
}

const toggleSignIn = async (row, value) => {
  await adminApi.updateActivitySignInSwitch(row.activityId, {
    signInEnabled: value ? 1 : 0,
  })
  row.signInEnabled = value ? 1 : 0
  ElMessage.success(value ? '签到已开启' : '签到已关闭')
  await loadActivities()
}

const toggleStatus = async (row) => {
  const targetStatus = Number(row.activityStatus) === 4 ? 1 : 4
  const actionText = targetStatus === 4 ? '下架' : '重新发布'
  await ElMessageBox.confirm(
    `确认${actionText}活动“${row.activityTitle}”吗？`,
    `${actionText}确认`,
    { type: 'warning', confirmButtonText: `确认${actionText}`, cancelButtonText: '取消' },
  )
  await adminApi.updateActivityStatus(row.activityId, { activityStatus: targetStatus })
  ElMessage.success(`活动已${actionText}`)
  await loadActivities()
}

const removeActivity = async (row) => {
  await ElMessageBox.confirm(
    `删除“${row.activityTitle}”后，相关报名、学分和队伍数据都会一起清除，且无法恢复。是否继续？`,
    '删除活动确认',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '先不删' },
  )
  await adminApi.removeActivity(row.activityId)
  ElMessage.success('活动已删除')
  await loadActivities()
}

onMounted(loadActivities)
</script>

<style scoped>
.admin-detail__section {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--border-1, #eee);
}

.admin-detail__section h4 {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
}
</style>
