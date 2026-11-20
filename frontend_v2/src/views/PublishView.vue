<template>
  <main class="page page-publish">
    <section class="page-section publish-shell">
      <div v-if="!user" class="publish-gate card-surface">
        <div class="publish-gate__icon">
          <el-icon><Position /></el-icon>
        </div>
        <h1>登录后发布活动</h1>
        <p>使用校园账号登录即可继续。</p>
        <el-button type="warning" size="large" @click="openAuth?.()">登录</el-button>
      </div>

      <div v-else-if="!canPublish" class="publish-gate card-surface">
        <div class="publish-gate__icon">
          <el-icon><Lock /></el-icon>
        </div>
        <h1>当前账号不能在此发布活动</h1>
        <p>学生和老师可以发布无学分活动；学分活动请使用管理端运营账号发布。</p>
        <div class="publish-gate__actions">
          <el-button type="warning" size="large" @click="router.push('/')">返回首页</el-button>
        </div>
      </div>

      <div v-else-if="editLoadFailed" class="publish-gate card-surface">
        <div class="publish-gate__icon">
          <el-icon><WarningFilled /></el-icon>
        </div>
        <h1>活动加载失败</h1>
        <p>活动可能已被删除，或链接有误。</p>
        <div class="publish-gate__actions">
          <el-button plain @click="router.push('/my-published')">返回我的活动</el-button>
          <el-button type="warning" @click="loadEditActivity">重试</el-button>
        </div>
      </div>

      <div v-else-if="editingCreditActivity" class="publish-gate card-surface">
        <div class="publish-gate__icon">
          <el-icon><Lock /></el-icon>
        </div>
        <h1>学分活动需在管理端维护</h1>
        <p>带学分的第二课堂活动由管理端运营账号发布，并提交管理员审核。</p>
        <div class="publish-gate__actions">
          <el-button plain @click="router.push('/my-published')">返回我的活动</el-button>
        </div>
      </div>

      <div v-else v-loading="loadingDetail" class="publish-sheet card-surface">
        <header class="publish-head">
          <h1>{{ isEditMode ? '编辑活动' : '发布活动' }}</h1>
          <span class="publish-head__hint publish-head__hint--static">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ modeTip }}</span>
          </span>
        </header>

        <el-alert
          class="publish-review-tip"
          type="warning"
          :closable="false"
          show-icon
          title="活动发布后需管理员审核"
          description="审核通过前不会出现在公开列表，也不能被报名；修改活动内容后会重新进入审核。"
        />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          :disabled="loadingDetail"
          label-position="top"
          class="publish-form"
          @submit.prevent="handleSubmit"
        >
          <div class="cover-field">
            <span class="cover-field__label">活动封面<em>选填</em></span>
            <div class="cover-field__row">
              <div class="cover-field__preview" :class="{ 'cover-field__preview--filled': form.coverUrl }">
                <CoverImage
                  v-if="form.coverUrl"
                  :src="form.coverUrl"
                  alt="活动封面预览"
                  fallback-class="cover-field__fallback"
                  fallback-text="预览失败"
                />
                <div v-else class="cover-field__empty">
                  <el-icon><Picture /></el-icon>
                  <span>暂无封面</span>
                </div>
              </div>
              <div class="cover-field__side">
                <div class="cover-field__actions">
                  <el-upload
                    :show-file-list="false"
                    :auto-upload="false"
                    accept="image/*"
                    :on-change="handleCoverChange"
                  >
                    <el-button :loading="coverUploading" type="warning" plain>
                      {{ form.coverUrl ? '更换封面' : '上传封面' }}
                    </el-button>
                  </el-upload>
                  <el-button v-if="form.coverUrl" plain @click="form.coverUrl = ''">移除</el-button>
                </div>
                <p class="cover-field__hint">建议 16:9 横图，不超过 5MB</p>
              </div>
            </div>
          </div>

          <div class="publish-form__group">
            <div class="grid-two">
              <el-form-item label="活动标题" prop="activityTitle">
                <el-input v-model="form.activityTitle" maxlength="255" placeholder="例如：周末篮球赛 / 社团团建" />
              </el-form-item>
              <el-form-item label="活动类型" prop="activityType">
                <el-select v-model="form.activityType">
                  <el-option :value="3" label="运动健身活动" />
                  <el-option :value="4" label="兴趣团建活动" />
                </el-select>
                <p class="publish-form__hint">学分与志愿时长活动由管理端发布；客户端仅支持运动、兴趣类活动。</p>
              </el-form-item>
            </div>

            <el-form-item label="活动描述" prop="activityDesc">
              <el-input
                v-model="form.activityDesc"
                type="textarea"
                :rows="5"
                placeholder="活动流程、参与对象和注意事项"
              />
            </el-form-item>
          </div>

          <div class="publish-form__group">
            <div class="grid-two">
              <el-form-item label="开始时间" prop="startTime">
                <el-date-picker
                  v-model="form.startTime"
                  type="datetime"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="选择开始时间"
                />
              </el-form-item>
              <el-form-item label="结束时间" prop="endTime">
                <el-date-picker
                  v-model="form.endTime"
                  type="datetime"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="选择结束时间"
                />
              </el-form-item>
            </div>

            <div class="grid-two">
              <el-form-item label="活动地点" prop="activityLocation">
                <el-input v-model="form.activityLocation" placeholder="例如：大学生活动中心 201" />
              </el-form-item>
              <el-form-item label="活动标签" prop="tagIds">
                <el-select v-model="form.tagIds" multiple collapse-tags placeholder="选择标签">
                  <el-option
                    v-for="tag in tags"
                    :key="tag.tagId"
                    :label="tag.tagName"
                    :value="tag.tagId"
                  />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <div class="publish-form__group">
            <div class="publish-facts">
              <el-form-item label="人数上限" prop="maxParticipant">
                <el-input-number v-model="form.maxParticipant" :min="1" :step="1" />
              </el-form-item>
            </div>
          </div>

          <div class="publish-actions">
            <el-button
              v-if="isEditMode"
              text
              class="publish-actions__back"
              @click="router.push('/my-published')"
            >
              返回我的活动
            </el-button>
            <el-button
              :loading="submitting"
              type="warning"
              size="large"
              native-type="submit"
            >
              {{ submitText }}
            </el-button>
          </div>
        </el-form>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled, Lock, Picture, Position, WarningFilled } from '@element-plus/icons-vue'
import CoverImage from '@/components/CoverImage.vue'
import { activityApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'

const route = useRoute()
const router = useRouter()
const { user } = useSession()
const openAuth = inject('openAuth', null)

const tags = ref([])
const submitting = ref(false)
const coverUploading = ref(false)
const loadingDetail = ref(false)
const editLoadFailed = ref(false)
const editingCreditActivity = ref(false)
const formRef = ref(null)

const form = reactive({
  activityTitle: '',
  activityDesc: '',
  activityType: 3,
  coverUrl: '',
  startTime: '',
  endTime: '',
  activityLocation: '',
  maxParticipant: 30,
  tagIds: [],
})

const isEditMode = computed(() => Boolean(route.query.edit))
const editingActivityId = computed(() => route.query.edit)
const submitText = computed(() => (isEditMode.value ? '保存并重新提交审核' : '提交审核'))

/* 学生和老师权限一致，都可以发布无学分活动 */
const canPublish = computed(() => [1, 3].includes(Number(user.value?.userRole)))

const modeTip = computed(() => (
  isEditMode.value
    ? '修改并保存后，活动会重新进入管理员审核队列。'
    : '填写完成后提交管理员审核，审核通过后公开展示。'
))

const validateEndTime = (_rule, value, callback) => {
  if (!value || !form.startTime) {
    callback()
    return
  }
  if (!(new Date(value) > new Date(form.startTime))) {
    callback(new Error('结束时间需晚于开始时间'))
    return
  }
  callback()
}

const rules = {
  activityTitle: [{ required: true, message: '请填写活动标题', trigger: 'blur' }],
  activityType: [{ required: true, message: '请选择活动类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    { validator: validateEndTime, trigger: 'change' },
  ],
  maxParticipant: [{ required: true, message: '请填写人数上限', trigger: 'change' }],
}

function resetForm() {
  Object.assign(form, {
    activityTitle: '',
    activityDesc: '',
    activityType: 3,
    coverUrl: '',
    startTime: '',
    endTime: '',
    activityLocation: '',
    maxParticipant: 30,
    tagIds: [],
  })
  formRef.value?.clearValidate()
}

function fillForm(activity) {
  Object.assign(form, {
    activityTitle: activity.activityTitle || '',
    activityDesc: activity.activityDesc || '',
    activityType: [3, 4].includes(Number(activity.activityType)) ? Number(activity.activityType) : 3,
    coverUrl: activity.coverUrl || '',
    startTime: activity.startTime || '',
    endTime: activity.endTime || '',
    activityLocation: activity.activityLocation || '',
    maxParticipant: activity.maxParticipant || 30,
    tagIds: (activity.tags || []).map((tag) => tag.tagId),
  })
}

const handleCoverChange = async (uploadFile) => {
  if (!uploadFile?.raw) return

  const file = uploadFile.raw
  if (!file.type?.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('封面不能超过 5MB')
    return
  }

  coverUploading.value = true
  try {
    const result = await activityApi.uploadCover(file)
    form.coverUrl = result.coverUrl
    ElMessage.success('封面上传成功')
  } finally {
    coverUploading.value = false
  }
}

const buildPayload = () => ({
  activityTitle: form.activityTitle,
  activityDesc: form.activityDesc,
  activityType: form.activityType,
  activityCredit: null,
  creditType: null,
  volunteerHours: null,
  coverUrl: form.coverUrl || null,
  startTime: form.startTime,
  endTime: form.endTime,
  activityLocation: form.activityLocation,
  maxParticipant: form.maxParticipant,
  tagIds: form.tagIds,
})

const handleSubmit = async () => {
  if (!user.value?.userId || submitting.value) return

  submitting.value = true
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    if (isEditMode.value) {
      const updated = await activityApi.update(editingActivityId.value, buildPayload())
      ElMessage.success('活动已更新，已重新提交审核')
      router.push({ name: 'activity-detail', params: { id: updated.activityId } })
      return
    }

    const created = await activityApi.create(buildPayload())
    ElMessage.success('活动已提交审核，请等待管理员处理')
    router.push({ name: 'my-published' })
  } finally {
    submitting.value = false
  }
}

let editLoadToken = 0

const loadEditActivity = async () => {
  const requestId = ++editLoadToken

  if (!isEditMode.value) {
    resetForm()
    editLoadFailed.value = false
    editingCreditActivity.value = false
    loadingDetail.value = false
    return
  }

  loadingDetail.value = true
  editLoadFailed.value = false
  editingCreditActivity.value = false
  try {
    const detail = await activityApi.detail(editingActivityId.value)
    if (requestId !== editLoadToken) return
    // 学分/志愿活动受发布规则约束，客户端不提供编辑入口，避免误改考核数据。
    if ([1, 2].includes(Number(detail.activityType))) {
      editingCreditActivity.value = true
      return
    }
    fillForm(detail)
  } catch {
    if (requestId === editLoadToken) {
      editLoadFailed.value = true
    }
  } finally {
    if (requestId === editLoadToken) {
      loadingDetail.value = false
    }
  }
}

const loadPageData = async () => {
  if (!user.value?.userId) return
  tags.value = await activityApi.tags()
  await loadEditActivity()
}

onMounted(loadPageData)

watch(
  () => user.value?.userId,
  (userId, previousId) => {
    if (userId && userId !== previousId) {
      loadPageData()
    }
  },
)

watch(
  () => route.query.edit,
  async () => {
    await loadEditActivity()
  },
)
</script>

<style scoped>
.publish-review-tip {
  margin-bottom: 18px;
}

.publish-head__hint--static {
  cursor: default;
}

.publish-form__hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-2, #888);
}
</style>
