<template>
  <div v-loading="loading" class="admin-page">
    <section class="admin-stat-grid">
      <article class="admin-stat-card admin-stat-card--accent">
        <span class="admin-stat-card__label">待审核活动</span>
        <strong class="admin-stat-card__value">{{ stats.pendingActivityCount ?? '—' }}</strong>
        <span class="admin-stat-card__hint">
          普通活动与学分活动都需审核通过后公开
        </span>
        <router-link class="admin-stat-card__link" :to="{ name: 'activity-review', query: { reviewStatus: 0 } }">去审核</router-link>
      </article>

      <article class="admin-stat-card">
        <span class="admin-stat-card__label">活动总数</span>
        <strong class="admin-stat-card__value">{{ stats.activityTotal ?? '—' }}</strong>
        <span class="admin-stat-card__hint">
          未结束 {{ stats.publishedActivityCount ?? 0 }} · 已结束 {{ stats.finishedActivityCount ?? 0 }}
        </span>
      </article>

      <article class="admin-stat-card">
        <span class="admin-stat-card__label">待确认发放</span>
        <strong class="admin-stat-card__value">{{ stats.pendingCreditCount ?? '—' }}</strong>
        <span class="admin-stat-card__hint">学生签退后生成，由活动发布者逐人确认，管理员可在活动详情中调整</span>
        <router-link class="admin-stat-card__link" :to="{ name: 'activity-review' }">去活动详情处理</router-link>
      </article>

      <article class="admin-stat-card">
        <span class="admin-stat-card__label">账号</span>
        <strong class="admin-stat-card__value">{{ stats.userTotal ?? '—' }}</strong>
        <span class="admin-stat-card__hint">
          学生 {{ stats.studentCount ?? 0 }} · 老师 {{ stats.teacherCount ?? 0 }} · 运营 {{ stats.staffCount ?? 0 }}
        </span>
      </article>
    </section>

    <div class="admin-grid-two">
      <section class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <h2>最新活动</h2>
            <p>最近提交的 5 个活动</p>
          </div>
          <el-button text @click="router.push({ name: 'activity-review' })">查看全部</el-button>
        </div>

        <el-table :data="stats.recentActivities || []" size="small" empty-text="暂无活动">
          <el-table-column label="活动" min-width="190">
            <template #default="{ row }">
              <div class="admin-cell-title">{{ row.activityTitle }}</div>
              <div class="admin-cell-sub"><span class="mono">#{{ row.activityId }}</span> · {{ row.publisherName || row.publisherUserId }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <div class="admin-status-stack">
                <el-tag :type="reviewTagType(row.reviewStatus)" size="small">
                  {{ formatReviewStatus(row.reviewStatus) }}
                </el-tag>
                <el-tag :type="activityTagType(row.displayStatus)" size="small">
                  {{ formatActivityStatus(row.displayStatus) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" width="120">
            <template #default="{ row }"><span class="mono">{{ formatMonthDayTime(row.startTime) }}</span></template>
          </el-table-column>
        </el-table>
      </section>

      <section class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <h2>最新用户</h2>
            <p>最近注册的 5 个账号</p>
          </div>
          <el-button text @click="router.push({ name: 'accounts' })">查看全部</el-button>
        </div>

        <el-table :data="stats.recentUsers || []" size="small" empty-text="暂无用户">
          <el-table-column label="用户" min-width="160">
            <template #default="{ row }">
              <div class="admin-cell-title">{{ row.userName }}</div>
              <div class="admin-cell-sub"><span class="mono">{{ row.studentNo }}</span></div>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="95">
            <template #default="{ row }">
              <el-tag :type="roleTagType(row.userRole)" size="small">{{ formatRole(row.userRole) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="150">
            <template #default="{ row }"><span class="mono">{{ formatDateTime(row.createTime) }}</span></template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <section class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <h2>快捷入口</h2>
          <p>常用的审核与账号操作</p>
        </div>
      </div>
      <div class="admin-quick">
        <el-button type="warning" @click="router.push({ name: 'activity-review' })">活动审核</el-button>
        <el-button @click="router.push({ name: 'admin-activity-create' })">发布活动</el-button>
        <el-button @click="router.push({ name: 'accounts' })">创建运营账号</el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
import {
  activityTagType,
  formatActivityStatus,
  formatDateTime,
  formatMonthDayTime,
  formatReviewStatus,
  formatRole,
  reviewTagType,
} from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const stats = ref({})

const roleTagType = (role) => {
  if (Number(role) === 2) return 'danger'
  if (Number(role) === 3) return 'warning'
  if (Number(role) === 4) return 'warning'
  return 'info'
}

const loadDashboard = async () => {
  loading.value = true
  try {
    stats.value = await adminApi.dashboard()
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.admin-status-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.admin-stat-card__link {
  margin-top: auto;
  align-self: flex-start;
  color: var(--text-1);
  font-size: 12px;
  font-weight: 500;
}

.admin-stat-card__link:hover {
  text-decoration: underline;
}
</style>
