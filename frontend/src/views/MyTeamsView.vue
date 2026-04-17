<template>
  <main class="page page-manage-activities">
    <section class="page-section">
      <div v-if="!user" class="card-surface empty-login">
        <h2>登录后查看我的队伍</h2>
        <p>这里可以创建队伍、查看自己带领或参与的队伍，并进入详情页处理审批。</p>
        <div class="empty-login__actions">
          <button type="button" class="login-entry-button" @click="openAuth">登录</button>
          <button type="button" class="empty-login__secondary" @click="router.push('/')">先去首页</button>
        </div>
      </div>

      <template v-else>
        <section class="manage-header card-surface">
          <div>
            <p class="section-head__kicker">组队空间</p>
            <h1>我的队伍</h1>
            <p>组队保持“申请加入 - 队长审核”的逻辑。你可以在这里创建队伍，并快速进入详情页管理成员。</p>
          </div>
          <el-button @click="loadTeams">刷新队伍</el-button>
        </section>

        <section class="card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">创建队伍</p>
              <h3>发起新的组队邀请</h3>
            </div>
          </div>

          <el-form :model="teamForm" label-position="top">
            <div class="grid-two">
              <el-form-item label="队伍名称">
                <el-input v-model="teamForm.teamName" placeholder="例如：周末篮球搭子" />
              </el-form-item>
              <el-form-item label="关联活动（可选）">
                <el-select v-model="teamForm.activityId" clearable placeholder="不关联也可以创建队伍">
                  <el-option
                    v-for="activity in activityOptions"
                    :key="activity.activityId"
                    :label="activity.activityTitle"
                    :value="activity.activityId"
                  />
                </el-select>
              </el-form-item>
            </div>
            <div class="grid-two">
              <el-form-item label="队伍简介">
                <el-input v-model="teamForm.teamDesc" placeholder="简单说明招募对象、时间和风格" />
              </el-form-item>
              <el-form-item label="最大人数">
                <el-input-number v-model="teamForm.maxSize" :min="1" :step="1" />
              </el-form-item>
            </div>
            <el-button type="warning" :loading="creatingTeam" @click="createTeam">创建队伍</el-button>
          </el-form>
        </section>

        <section class="manage-grid">
          <article
            v-for="team in displayTeams"
            :key="team.teamId"
            class="manage-card card-surface"
          >
            <div class="manage-card__body manage-card__body--single">
              <div class="manage-card__head">
                <div>
                  <h3>{{ team.teamName }}</h3>
                  <p>{{ team.activityTitle || '未关联活动' }}</p>
                </div>
                <el-tag round>{{ teamStatusText(team.teamStatus) }}</el-tag>
              </div>

              <div class="manage-card__meta">
                <span>队伍ID #{{ team.teamId }}</span>
                <span>队长 {{ team.leaderUserId }}</span>
                <span>{{ team.currentSize }}/{{ team.maxSize || '不限' }} 人</span>
              </div>

              <div class="manage-card__switch">
                <span>我的身份</span>
                <strong>{{ team.leaderUserId === user.userId ? '队长' : '成员/申请中' }}</strong>
              </div>

              <div class="manage-card__actions">
                <el-button type="warning" plain @click="router.push({ name: 'team-detail', params: { id: team.teamId } })">
                  进入详情
                </el-button>
                <el-button
                  v-if="team.leaderUserId === user.userId"
                  plain
                  @click="router.push({ name: 'team-detail', params: { id: team.teamId } })"
                >
                  审核成员
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { activityApi, teamApi } from '@/api/platform'
import { useAuthDialog } from '@/composables/useAuthDialog'
import { useSession } from '@/composables/useSession'

const router = useRouter()
const { user } = useSession()
const { openAuth } = useAuthDialog()

const leaderTeams = ref([])
const memberTeams = ref([])
const activityOptions = ref([])
const creatingTeam = ref(false)

const teamForm = reactive({
  activityId: null,
  teamName: '',
  teamDesc: '',
  maxSize: 5,
})

const displayTeams = computed(() => {
  const teamMap = new Map()
  ;[...leaderTeams.value, ...memberTeams.value].forEach((team) => {
    if (!teamMap.has(team.teamId)) {
      teamMap.set(team.teamId, team)
    }
  })
  return Array.from(teamMap.values())
})

const teamStatusText = (status) => {
  const map = {
    0: '招募中',
    1: '已满员',
    2: '已解散',
  }
  return map[status] || `状态 ${status}`
}

const loadTeams = async () => {
  if (!user.value?.userId) return

  const [leaderPage, memberPage, activityPage] = await Promise.all([
    teamApi.list({ leaderUserId: user.value.userId, pageNo: 0, pageSize: 50 }),
    teamApi.list({ memberUserId: user.value.userId, pageNo: 0, pageSize: 50 }),
    activityApi.list({ pageNo: 0, pageSize: 100 }),
  ])

  leaderTeams.value = leaderPage.records || []
  memberTeams.value = memberPage.records || []
  activityOptions.value = (activityPage.records || []).filter((activity) => [3, 4].includes(activity.activityType))
}

const createTeam = async () => {
  creatingTeam.value = true
  try {
    const created = await teamApi.create({
      leaderUserId: user.value.userId,
      activityId: teamForm.activityId || null,
      teamName: teamForm.teamName,
      teamDesc: teamForm.teamDesc,
      maxSize: teamForm.maxSize,
    })
    ElMessage.success('队伍创建成功')
    Object.assign(teamForm, {
      activityId: null,
      teamName: '',
      teamDesc: '',
      maxSize: 5,
    })
    await loadTeams()
    router.push({ name: 'team-detail', params: { id: created.teamId } })
  } finally {
    creatingTeam.value = false
  }
}

onMounted(loadTeams)
</script>
