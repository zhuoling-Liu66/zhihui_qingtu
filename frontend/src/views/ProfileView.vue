<template>
  <main class="page page-profile">
    <section class="page-section profile-layout">
      <div v-if="!user" class="card-surface empty-login">
        <h2>登录后查看个人中心</h2>
        <p>这里会集中展示你的资料、兴趣标签、第二课堂学分统计和参与历史。</p>
        <div class="empty-login__actions">
          <button type="button" class="login-entry-button" @click="openAuth">登录</button>
          <button type="button" class="empty-login__secondary" @click="router.push('/')">先去首页</button>
        </div>
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
              <span>角色 {{ profile?.userRole }}</span>
              <span>发布权限 {{ profile?.publisherStatus }}</span>
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
                <strong>{{ item.activityTitle }}</strong>
                <span>{{ item.startTime || '待定时间' }}</span>
                <p>活动ID {{ item.activityId }} · 类型 {{ item.activityType }} · 状态 {{ item.enrollStatus }}</p>
              </article>
            </div>
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
              <div class="credit-tile"><span>总学分</span><strong>{{ overview?.totalCredit || 0 }}</strong></div>
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
                <p class="section-head__kicker">学分记录</p>
                <h3>最近审核结果</h3>
              </div>
            </div>
            <div class="side-list">
              <article v-for="credit in credits" :key="credit.recordId" class="team-card">
                <div class="team-card__top">
                  <strong>记录 #{{ credit.recordId }}</strong>
                  <span>{{ credit.credit }}</span>
                </div>
                <p>活动 {{ credit.activityId }} · 类型 {{ credit.creditType }}</p>
                <div class="team-card__meta">
                  <span>状态 {{ credit.verifyStatus }}</span>
                  <span>{{ credit.rejectReason || '审核中或已通过' }}</span>
                </div>
              </article>
            </div>
          </article>
        </aside>
      </template>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { activityApi, creditApi, userApi } from '@/api/platform'
import { useAuthDialog } from '@/composables/useAuthDialog'
import { useSession } from '@/composables/useSession'

const router = useRouter()
const { user } = useSession()
const { openAuth } = useAuthDialog()

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
