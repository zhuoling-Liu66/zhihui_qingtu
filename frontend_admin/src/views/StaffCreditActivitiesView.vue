<template>
  <div class="admin-page">
    <section class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <h2>我发布的活动</h2>
          <p>运营人员发布的学分/志愿活动需经管理员审核通过后才会公开</p>
        </div>
        <div class="admin-filter-actions">
          <el-button plain :loading="loading" @click="loadActivities">刷新</el-button>
          <el-button type="warning" @click="router.push({ name: 'credit-activity-create' })">
            发布活动
          </el-button>
        </div>
      </div>

      <div class="admin-filters">
        <el-form-item label="审核状态">
          <el-select v-model="reviewStatus" clearable placeholder="全部状态">
            <el-option :value="0" label="待审核" />
            <el-option :value="1" label="审核通过" />
            <el-option :value="2" label="审核驳回" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            clearable
            placeholder="活动标题 / 描述 / 地点"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <div class="admin-filter-actions">
          <el-button type="warning" @click="handleSearch">查询</el-button>
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

      <el-table v-loading="loading" :data="rows" empty-text="还没有创建学分活动">
        <el-table-column label="活动" min-width="250">
          <template #default="{ row }">
            <div class="admin-cell-title">{{ row.activityTitle }}</div>
            <div class="admin-cell-sub"><span class="mono">#{{ row.activityId }}</span> · {{ row.activityLocation || '地点待定' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="类型与奖励" width="180">
          <template #default="{ row }">
            <template v-if="Number(row.activityType) === 1">
              <div>
                <span class="mono">{{ row.activityCredit }}</span> 学分
                <el-tag v-if="Number(row.creditMode) === 2" size="small" type="warning">组合</el-tag>
              </div>
              <div class="admin-cell-sub">{{ formatCreditType(row.creditType) }}</div>
            </template>
            <template v-else-if="Number(row.activityType) === 2">
              <div>
                志愿
                <span class="mono">{{ Number(row.volunteerMode) === 2 ? '灵活' : (row.volunteerHours ?? 0) }}</span>
                {{ Number(row.volunteerMode) === 2 ? '' : '小时' }}
                <el-tag v-if="Number(row.volunteerMode) === 2" size="small" type="warning">灵活</el-tag>
              </div>
              <div class="admin-cell-sub">志愿时长</div>
            </template>
            <template v-else>
              <div>{{ Number(row.activityType) === 3 ? '运动健身' : '兴趣团建' }}</div>
              <div class="admin-cell-sub">无学分/时长</div>
            </template>
          </template>
        </el-table-column>

        <el-table-column label="审核状态" width="130">
          <template #default="{ row }">
            <el-tag :type="reviewTagType(row.reviewStatus)" size="small">
              {{ formatReviewStatus(row.reviewStatus) }}
            </el-tag>
            <div v-if="Number(row.reviewStatus) === 2 && row.reviewReason" class="admin-cell-sub">
              {{ row.reviewReason }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="活动状态" width="100">
          <template #default="{ row }">
            <el-tag :type="activityTagType(row.displayStatus)" size="small">
              {{ formatActivityStatus(row.displayStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="签到开关" width="105">
          <template #default="{ row }">
            <el-switch
              :model-value="Number(row.signInEnabled) === 1"
              :disabled="Number(row.reviewStatus) !== 1"
              @change="(value) => toggleSignIn(row, value)"
            />
          </template>
        </el-table-column>

        <el-table-column label="活动时间" min-width="180">
          <template #default="{ row }">
            <div class="mono">{{ formatDateTime(row.startTime) }}</div>
            <div class="admin-cell-sub mono">至 {{ formatDateTime(row.endTime) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openParticipants(row)">参与人员</el-button>
            <el-button
              text
              type="primary"
              @click="router.push({ name: 'credit-activity-edit', params: { id: row.activityId } })"
            >
              编辑
            </el-button>
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

    <el-dialog
      v-model="participantsVisible"
      :title="participantsActivity ? `参与人员 · ${participantsActivity.activityTitle}` : '参与人员'"
      width="920px"
    >
      <ParticipantsManager
        v-if="participantsVisible && participantsActivity"
        :activity-id="participantsActivity.activityId"
      />
      <template #footer>
        <el-button @click="participantsVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/admin'
import ParticipantsManager from '@/components/ParticipantsManager.vue'
import {
  activityTagType,
  formatActivityStatus,
  formatCreditType,
  formatDateTime,
  formatReviewStatus,
  reviewTagType,
} from '@/utils/format'

const router = useRouter()

const rows = ref([])
const totalElements = ref(0)
const pageNo = ref(0)
const pageSize = 10
const loading = ref(false)
const reviewStatus = ref(null)
const keyword = ref('')
const participantsVisible = ref(false)
const participantsActivity = ref(null)
let loadToken = 0

const openParticipants = (row) => {
  participantsActivity.value = row
  participantsVisible.value = true
}

const toggleSignIn = async (row, value) => {
  await adminApi.updateActivitySignInSwitch(row.activityId, {
    signInEnabled: value ? 1 : 0,
  })
  ElMessage.success(value ? '签到已开启' : '签到已关闭')
  await loadActivities()
}

const loadActivities = async () => {
  const requestId = ++loadToken
  loading.value = true
  try {
    const params = { pageNo: pageNo.value, pageSize }
    if (reviewStatus.value !== null && reviewStatus.value !== '') params.reviewStatus = reviewStatus.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const page = await adminApi.myActivities(params)
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

const handlePageChange = (current) => {
  pageNo.value = current - 1
  loadActivities()
}

onMounted(loadActivities)
</script>
