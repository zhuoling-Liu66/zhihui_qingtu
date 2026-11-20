<template>
  <main class="page page-profile">
    <section class="page-section profile-layout">
      <div v-if="!user" class="card-surface empty-login">
        <h2>登录后查看个人中心</h2>
        <p>这里会集中展示你的资料、兴趣标签、第二课堂学分统计和参与历史。</p>
      </div>

      <template v-else>
        <section class="profile-main">
          <article class="profile-hero card-surface">
            <div>
              <p class="section-head__kicker">个人中心</p>
              <h1>{{ profile?.userName || user.userName }}</h1>
              <p>{{ profile?.major || user.major || '未填写专业' }} · {{ profile?.grade || user.grade || '未填写年级' }}</p>
            </div>
            <div class="profile-hero__meta">
              <span>学号/工号 {{ profile?.studentNo || user.userId }}</span>
              <span>角色 {{ formatUserRole(profile?.userRole) }}</span>
            </div>
          </article>


          <article class="card-surface">
            <div class="section-head section-head--compact">
              <div>
                <p class="section-head__kicker">资料编辑</p>
                <h3>更新专业、年级和联系方式</h3>
              </div>
            </div>
            <el-form :model="profileForm" label-position="top">
              <div class="grid-two">
                <el-form-item label="姓名">
                  <el-input v-model="profileForm.userName" />
                </el-form-item>
                <el-form-item label="手机号">
                  <el-input v-model="profileForm.userPhone" />
                </el-form-item>
              </div>
              <div class="grid-two">
                <el-form-item label="邮箱">
                  <el-input v-model="profileForm.userEmail" />
                </el-form-item>
                <el-form-item label="专业">
                  <el-input v-model="profileForm.major" />
                </el-form-item>
              </div>
              <div class="grid-two">
                <el-form-item label="年级">
                  <el-input v-model="profileForm.grade" />
                </el-form-item>
                <el-form-item label="兴趣标签">
                  <el-select v-model="interestTagIds" multiple collapse-tags>
                    <el-option v-for="tag in tags" :key="tag.tagId" :label="tag.tagName" :value="tag.tagId" />
                  </el-select>
                </el-form-item>
              </div>
              <div class="profile-actions">
                <el-button type="warning" @click="saveProfile">保存资料</el-button>
                <el-button @click="saveInterests">保存兴趣标签</el-button>
              </div>
            </el-form>
          </article>

          <article class="card-surface">
            <div class="section-head section-head--compact">
              <div>
                <p class="section-head__kicker">参与历史</p>
                <h3>最近参与的活动</h3>
              </div>
            </div>
            <div class="history-list">
              <article v-for="item in histories" :key="item.enrollId" class="history-item">
                <div class="history-item__top">
                  <strong>{{ item.activityTitle }}</strong>
                  <span
                    class="status-pill"
                    :class="`status-pill--${enrollTone(item.enrollStatus)}`"
                  >
                    {{ formatEnrollStatus(item.enrollStatus) }}
                  </span>
                </div>
                <p class="history-item__meta">
                  {{ formatActivityType(item.activityType) }} · {{ formatDateTime(item.startTime) }}
                </p>
              </article>
            </div>
            <p v-if="!histories.length" class="search-empty">
              还没有参与记录，去首页逛一场感兴趣的活动吧。
            </p>
          </article>
        </section>

        <aside class="profile-side">
          <article class="card-surface">
            <div class="section-head section-head--compact">
              <div>
                <p class="section-head__kicker">学分统计</p>
                <h3>六类学分总览</h3>
              </div>
            </div>
            <div class="credit-grid">
              <div class="credit-tile credit-tile--total">
                <span>总学分</span>
                <strong>{{ overview?.totalCredit || 0 }}</strong>
              </div>
              <div class="credit-tile">
                <span>志愿时长</span>
                <strong>{{ overview?.totalVolunteerHours || 0 }}</strong>
              </div>
              <div class="credit-tile"><span>创新创业</span><strong>{{ overview?.innovationEntrepreneurshipCredit || 0 }}</strong></div>
              <div class="credit-tile"><span>智育</span><strong>{{ overview?.intellectualEducationCredit || 0 }}</strong></div>
              <div class="credit-tile"><span>德育</span><strong>{{ overview?.moralEducationCredit || 0 }}</strong></div>
              <div class="credit-tile"><span>体育</span><strong>{{ overview?.physicalEducationCredit || 0 }}</strong></div>
              <div class="credit-tile"><span>美育</span><strong>{{ overview?.aestheticEducationCredit || 0 }}</strong></div>
              <div class="credit-tile"><span>劳育</span><strong>{{ overview?.laborEducationCredit || 0 }}</strong></div>
            </div>
          </article>

          <article class="card-surface">
            <div class="section-head section-head--compact">
              <div>
                <p class="section-head__kicker">发放记录</p>
                <h3>最近发放结果</h3>
              </div>
            </div>
            <div class="side-list">
              <article v-for="credit in credits" :key="credit.recordId" class="history-item">
                <div class="history-item__top">
                  <strong v-if="Number(credit.volunteerHours) > 0">
                    志愿服务 +{{ credit.volunteerHours }} 小时
                  </strong>
                  <strong v-else>
                    {{ formatCreditType(credit.creditType) }} +{{ credit.credit }} 分
                  </strong>
                  <span
                    class="status-pill"
                    :class="`status-pill--${verifyTone(credit.verifyStatus)}`"
                  >
                    {{ formatVerifyStatus(credit.verifyStatus) }}
                  </span>
                </div>
                <p class="history-item__meta">活动 #{{ credit.activityId }}</p>
                <p v-if="credit.rejectReason" class="history-item__reason">
                  不发放原因：{{ credit.rejectReason }}
                </p>
              </article>
            </div>
            <p v-if="!credits.length" class="search-empty">
              暂无发放记录，参加学分/志愿活动并完成签退后，会生成待发布者确认的记录。
            </p>
          </article>
        </aside>
      </template>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { activityApi, creditApi, userApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'
import {
  formatActivityType,
  formatCreditType,
  formatDateTime,
  formatEnrollStatus,
  formatUserRole,
  formatVerifyStatus,
  statusTone,
} from '@/utils/format'

const { user } = useSession()

/* 报名状态配色：已完成=绿；已报名/已签到=黄（流程进行中） */
const enrollTone = (value) => statusTone(value, [4], [], [1, 3])

/* 审核状态配色：通过=绿；驳回=红；待审核=黄 */
const verifyTone = (value) => statusTone(value, [1], [2], [0, 3])

const profile = ref(null)
const overview = ref(null)

const histories = ref([])
const credits = ref([])
const tags = ref([])
const interestTagIds = ref([])

const profileForm = reactive({
  userName: '',
  userPhone: '',
  userEmail: '',
  major: '',
  grade: '',
})

const loadData = async () => {
  if (!user.value?.userId) return

  const [profileData, overviewData, historyData, creditPage, tagList] = await Promise.all([
    userApi.profile(user.value.userId),
    userApi.creditOverview(user.value.userId),
    userApi.histories(user.value.userId),
    creditApi.list({ userId: user.value.userId, pageNo: 0, pageSize: 6 }),
    activityApi.tags(),
  ])

  profile.value = profileData
  overview.value = overviewData
  histories.value = historyData || []
  credits.value = creditPage.records || []
  tags.value = tagList || []
  interestTagIds.value = (profileData.interestTags || []).map((tag) => tag.tagId)

  Object.assign(profileForm, {
    userName: profileData.userName || '',
    userPhone: profileData.userPhone || '',
    userEmail: profileData.userEmail || '',
    major: profileData.major || '',
    grade: profileData.grade || '',
  })
}

const saveProfile = async () => {
  await userApi.updateProfile(user.value.userId, profileForm)
  ElMessage.success('资料已更新')
  await loadData()
}

const saveInterests = async () => {
  await userApi.updateInterestTags(user.value.userId, { tagIds: interestTagIds.value })
  ElMessage.success('兴趣标签已更新')
  await loadData()
}

onMounted(loadData)
</script>
