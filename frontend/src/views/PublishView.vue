<template>
  <main class="page page-publish">
    <section class="page-section publish-layout">
      <div class="publish-intro card-surface">
        <p class="section-head__kicker">{{ isEditMode ? '活动编辑' : '发布页' }}</p>
        <h1>{{ isEditMode ? '修改活动信息并即时生效' : '创建即发布，封面上传也一起完成' }}</h1>
        <p>
          活动创建后默认进入已发布状态，后续只保留“已发布 / 已取消”的简化管理。
          现在同一套表单也支持编辑已有活动，方便发布者随时调整封面、时间、地点和标签。
        </p>
        <div class="publish-intro__tips">
          <span>支持上传活动封面</span>
          <span>第二课堂活动填写学分和学分类型</span>
          <span>志愿活动填写服务时长</span>
        </div>
      </div>

      <div class="publish-form-wrap card-surface">
        <div v-if="!user" class="empty-login">
          <h3>登录后才能发布或编辑活动</h3>
          <p>请先完成登录，再回来管理校园活动。</p>
        </div>

        <el-form v-else :model="form" label-position="top" class="publish-form">
          <div class="cover-upload-panel">
            <div class="cover-upload-panel__preview">
              <CoverImage
                v-if="form.coverUrl"
                :src="form.coverUrl"
                alt="活动封面预览"
                fallback-class="cover-upload-panel__placeholder"
                fallback-text="封面预览失败，请重新上传"
              />
              <div v-else class="cover-upload-panel__placeholder">
                <strong>上传活动封面</strong>
                <span>建议使用横向图片，列表卡片和详情页都会直接展示。</span>
              </div>
            </div>
            <div class="cover-upload-panel__actions">
              <el-upload
                :show-file-list="false"
                :auto-upload="false"
                accept="image/*"
                :on-change="handleCoverChange"
              >
                <el-button :loading="coverUploading" type="warning">上传封面</el-button>
              </el-upload>
              <el-button v-if="form.coverUrl" plain @click="form.coverUrl = ''">移除封面</el-button>
            </div>
          </div>

          <div class="grid-two">
            <el-form-item label="活动标题">
              <el-input v-model="form.activityTitle" placeholder="例如：校园志愿服务招募" />
            </el-form-item>
            <el-form-item label="活动类型">
              <el-select v-model="form.activityType">
                <el-option :value="1" label="第二课堂学分活动" />
                <el-option :value="2" label="志愿服务活动" />
                <el-option :value="3" label="运动健身活动" />
                <el-option :value="4" label="兴趣团建活动" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="活动描述">
            <el-input
              v-model="form.activityDesc"
              type="textarea"
              :rows="5"
              placeholder="写清楚活动流程、参与对象、收获和注意事项"
            />
          </el-form-item>

          <div class="grid-two">
            <el-form-item v-if="form.activityType === 1" label="活动学分">
              <el-input-number v-model="form.activityCredit" :min="0" :step="0.05" :precision="2" />
            </el-form-item>
            <el-form-item v-if="form.activityType === 1" label="学分类型">
              <el-select v-model="form.creditType" placeholder="请选择学分类型">
                <el-option :value="1" label="创新创业" />
                <el-option :value="2" label="智育" />
                <el-option :value="3" label="德育" />
                <el-option :value="4" label="体育" />
                <el-option :value="5" label="美育" />
                <el-option :value="6" label="劳育" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.activityType === 2" label="志愿服务时长（小时）">
              <el-input-number v-model="form.volunteerHours" :min="0" :step="0.5" :precision="1" />
            </el-form-item>
            <el-form-item label="最大参与人数">
              <el-input-number v-model="form.maxParticipant" :min="1" :step="1" />
            </el-form-item>
          </div>

          <div class="grid-two">
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
            </el-form-item>
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
            </el-form-item>
          </div>

          <div class="grid-two">
            <el-form-item label="活动地点">
              <el-input v-model="form.activityLocation" placeholder="例如：大学生活动中心 201" />
            </el-form-item>
            <el-form-item label="活动标签">
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

          <div class="publish-form__footer">
            <el-alert
              :title="modeHint"
              type="warning"
              :closable="false"
              show-icon
            />
            <div class="publish-form__actions">
              <el-button v-if="isEditMode" plain @click="router.push('/my-published')">返回我的活动</el-button>
              <el-button :loading="submitting" type="warning" size="large" @click="handleSubmit">
                {{ submitText }}
              </el-button>
            </div>
          </div>
        </el-form>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CoverImage from '@/components/CoverImage.vue'
import { activityApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'

const route = useRoute()
const router = useRouter()
const { user } = useSession()

const tags = ref([])
const submitting = ref(false)
const coverUploading = ref(false)
const loadingDetail = ref(false)

const form = reactive({
  activityTitle: '',
  activityDesc: '',
  activityType: 1,
  activityCredit: 1,
  creditType: 2,
  volunteerHours: 2,
  coverUrl: '',
  startTime: '',
  endTime: '',
  activityLocation: '',
  maxParticipant: 30,
  tagIds: [],
})

const isEditMode = computed(() => Boolean(route.query.edit))
const editingActivityId = computed(() => route.query.edit)
const submitText = computed(() => (isEditMode.value ? '保存活动修改' : '创建并发布活动'))
const modeHint = computed(() => {
  if (form.activityType === 1) return '当前为第二课堂学分活动：需要填写学分和学分类型。'
  if (form.activityType === 2) return '当前为志愿服务活动：只需要填写志愿服务时长，不需要设置学分。'
  return '当前为非学分活动：系统会自动隐藏学分相关字段。'
})

function resetForm() {
  Object.assign(form, {
    activityTitle: '',
    activityDesc: '',
    activityType: 1,
    activityCredit: 1,
    creditType: 2,
    volunteerHours: 2,
    coverUrl: '',
    startTime: '',
    endTime: '',
    activityLocation: '',
    maxParticipant: 30,
    tagIds: [],
  })
}

function fillForm(activity) {
  Object.assign(form, {
    activityTitle: activity.activityTitle || '',
    activityDesc: activity.activityDesc || '',
    activityType: activity.activityType ?? 1,
    activityCredit: activity.activityCredit ?? 1,
    creditType: activity.creditType ?? 2,
    volunteerHours: activity.volunteerHours ?? 2,
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

  coverUploading.value = true
  try {
    const result = await activityApi.uploadCover(uploadFile.raw)
    form.coverUrl = result.coverUrl
    ElMessage.success('活动封面上传成功')
  } finally {
    coverUploading.value = false
  }
}

const buildPayload = () => ({
  activityTitle: form.activityTitle,
  activityDesc: form.activityDesc,
  activityType: form.activityType,
  activityCredit: form.activityType === 1 ? form.activityCredit : null,
  creditType: form.activityType === 1 ? form.creditType : null,
  volunteerHours: form.activityType === 2 ? form.volunteerHours : null,
  coverUrl: form.coverUrl || null,
  startTime: form.startTime,
  endTime: form.endTime,
  activityLocation: form.activityLocation,
  maxParticipant: form.maxParticipant,
  tagIds: form.tagIds,
})

const handleSubmit = async () => {
  if (!user.value?.userId) return

  submitting.value = true
  try {
    if (isEditMode.value) {
      const updated = await activityApi.update(editingActivityId.value, {
        operatorUserId: user.value.userId,
        ...buildPayload(),
      })
      ElMessage.success('活动修改成功')
      router.push({ name: 'activity-detail', params: { id: updated.activityId } })
      return
    }

    const created = await activityApi.create({
      publisherUserId: user.value.userId,
      ...buildPayload(),
    })
    ElMessage.success(`活动发布成功，活动ID：${created.activityId}`)
    router.push({ name: 'activity-detail', params: { id: created.activityId } })
  } finally {
    submitting.value = false
  }
}

const loadEditActivity = async () => {
  if (!isEditMode.value) {
    resetForm()
    return
  }

  loadingDetail.value = true
  try {
    const detail = await activityApi.detail(editingActivityId.value)
    fillForm(detail)
  } finally {
    loadingDetail.value = false
  }
}

watch(
  () => route.query.edit,
  async () => {
    await loadEditActivity()
  },
)

onMounted(async () => {
  tags.value = await activityApi.tags()
  await loadEditActivity()
})
</script>
