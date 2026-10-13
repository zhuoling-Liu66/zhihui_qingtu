<template>
  <main class="page page-detail">
    <section v-if="team" class="page-section detail-layout">
      <section class="detail-main">
        <article class="detail-hero card-surface">
          <div class="detail-hero__copy">
            <p class="section-head__kicker">组队详情</p>
            <h1>{{ team.teamName }}</h1>
            <p>{{ team.teamDesc || '队长还没有补充详细说明。' }}</p>
            <div class="detail-hero__chips">
              <span>{{ teamStatusText(team.teamStatus) }}</span>
              <span>{{ team.currentSize }}/{{ team.maxSize || '不限' }} 人</span>
              <span>{{ team.activityTitle || '未关联活动' }}</span>
            </div>
            <div class="detail-actions">
              <el-button
                v-if="canJoin"
                type="warning"
                size="large"
                @click="applyJoin"
              >
                申请加入
              </el-button>
              <el-button
                v-if="canExit"
                size="large"
                @click="exitTeam(currentMember)"
              >
                退出队伍
              </el-button>
              <el-button
                v-if="isLeader"
                type="danger"
                plain
                size="large"
                @click="dismissTeam"
              >
                解散队伍
              </el-button>
            </div>
          </div>

          <div class="detail-hero__stats">
            <div class="detail-stat">
              <span>队伍编号</span>
              <strong>#{{ team.teamId }}</strong>
            </div>
            <div class="detail-stat">
              <span>队长</span>
              <strong>{{ team.leaderUserId }}</strong>
            </div>
            <div class="detail-stat">
              <span>我的状态</span>
              <strong>{{ currentMemberStatusText }}</strong>
            </div>
          </div>
        </article>

        <article class="detail-section card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">已加入成员</p>
              <h3>队伍成员</h3>
            </div>
          </div>
          <div class="side-list">
            <article v-for="member in joinedMembers" :key="member.memberId" class="team-card">
              <div class="team-card__top">
                <strong>{{ member.userId }}</strong>
                <span>{{ member.userId === team.leaderUserId ? '队长' : '成员' }}</span>
              </div>
              <div class="team-card__meta">
                <span>{{ member.joinTime || '待加入时间' }}</span>
              </div>
              <div v-if="isLeader && member.userId !== team.leaderUserId" class="manage-card__actions">
                <el-button plain @click="exitTeam(member)">移出队伍</el-button>
              </div>
            </article>
          </div>
        </article>

        <article v-if="isLeader" class="detail-section card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">待审核成员</p>
              <h3>入队申请</h3>
            </div>
          </div>
          <div class="side-list">
            <article v-for="member in pendingMembers" :key="member.memberId" class="team-card">
              <div class="team-card__top">
                <strong>{{ member.userId }}</strong>
                <span>待审核</span>
              </div>
              <div class="manage-card__actions">
                <el-button type="warning" plain @click="reviewMember(member, 1)">通过</el-button>
                <el-button plain @click="reviewMember(member, 2)">驳回</el-button>
              </div>
            </article>
            <p v-if="!pendingMembers.length" class="empty-copy">暂无待审核申请。</p>
          </div>
        </article>
      </section>

      <aside class="detail-side">
        <section class="side-panel card-surface">
          <div class="section-head section-head--compact">
            <div>
              <p class="section-head__kicker">快捷操作</p>
              <h3>更多入口</h3>
            </div>
          </div>
          <div class="side-list">
            <el-button plain @click="router.push('/my-teams')">返回我的队伍</el-button>
            <el-button
              v-if="team.activityId"
              plain
              @click="router.push({ name: 'activity-detail', params: { id: team.activityId } })"
            >
              查看关联活动
            </el-button>
          </div>
        </section>
      </aside>
    </section>

    <section v-else class="page-section card-surface detail-loading">
      <el-skeleton :rows="6" animated />
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { teamApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'

const route = useRoute()
const router = useRouter()
const { user } = useSession()

const team = ref(null)

const joinedMembers = computed(() =>
  (team.value?.members || []).filter((member) => member.memberStatus === 1),
)
const pendingMembers = computed(() =>
  (team.value?.members || []).filter((member) => member.memberStatus === 0),
)
const currentMember = computed(() =>
  (team.value?.members || []).find((member) => member.userId === user.value?.userId),
)
const isLeader = computed(() => user.value?.userId && team.value?.leaderUserId === user.value.userId)
const canJoin = computed(() => {
  if (!user.value?.userId || !team.value) return false
  if (isLeader.value || currentMember.value) return false
  return team.value.teamStatus !== 1 && team.value.teamStatus !== 2
})
const canExit = computed(() => currentMember.value?.memberStatus === 1 && !isLeader.value)
const currentMemberStatusText = computed(() => {
  if (isLeader.value) return '队长'
  if (!currentMember.value) return '未加入'
  return memberStatusText(currentMember.value.memberStatus)
})

const teamStatusText = (status) => {
  const map = {
    0: '招募中',
    1: '已满员',
    2: '已解散',
  }
  return map[status] || `状态 ${status}`
}

const memberStatusText = (status) => {
  const map = {
    0: '待审核',
    1: '已加入',
    2: '已驳回',
    3: '已退出',
  }
  return map[status] || `状态 ${status}`
}

const loadTeam = async () => {
  team.value = await teamApi.detail(route.params.id)
}

const applyJoin = async () => {
  // 不再传 userId：申请人 = 当前登录用户，后端从 JWT 取
  await teamApi.join(route.params.id)
  ElMessage.success('入队申请已提交')
  await loadTeam()
}

const reviewMember = async (member, memberStatus) => {
  // 不再传 leaderUserId：后端用"当前登录用户是不是该队队长"来判定审批权，
  // 前端传什么都不会被采信（这也正是修掉越权的关键）
  await teamApi.reviewMember(route.params.id, member.memberId, {
    memberStatus,
  })
  ElMessage.success(memberStatus === 1 ? '已通过申请' : '已驳回申请')
  await loadTeam()
}

const exitTeam = async (member) => {
  // 不再传 operatorUserId：后端从 JWT 取身份，再校验"是你自己还是队长"
  await teamApi.exit(route.params.id, member.memberId)
  ElMessage.success(member.userId === user.value.userId ? '已退出队伍' : '成员已移出')
  await loadTeam()
}

const dismissTeam = async () => {
  await ElMessageBox.confirm('解散后当前队伍成员记录会被清空，是否继续？', '解散队伍确认', {
    type: 'warning',
    confirmButtonText: '确认解散',
    cancelButtonText: '先保留',
  })
  // 不再传 operatorUserId：仅队长可解散，由后端判定
  await teamApi.dismiss(route.params.id)
  ElMessage.success('队伍已解散')
  await loadTeam()
}

watch(
  () => route.params.id,
  () => loadTeam(),
)

onMounted(loadTeam)
</script>
