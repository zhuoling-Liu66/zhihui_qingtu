<template>
  <div v-loading="loadingDetail" class="admin-form-page">
    <section class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <h2>活动信息</h2>
          <p v-if="isAdmin">管理员可发布全部类型活动，发布即公开，无需审核</p>
          <p v-else>运营人员可发布学分或志愿活动，提交后进入管理员审核队列</p>
        </div>
        <el-button plain @click="router.push(backTarget)">返回列表</el-button>
      </div>

      <el-alert
        v-if="isAdmin"
        class="admin-review-banner"
        type="success"
        :closable="false"
        show-icon
        title="发布即公开"
        description="管理员本身就是活动审核人，发布后立即出现在学生端公开列表并开放报名。"
      />
      <el-alert
        v-else
        class="admin-review-banner"
        type="warning"
        :closable="false"
        show-icon
        title="提交后需管理员审核"
        description="审核通过前活动不会公开；修改并保存已通过的活动，会重新进入待审核状态。"
      />

      <el-alert
        v-if="isEdit && Number(form.reviewStatus) === 2"
        class="admin-review-banner"
        type="error"
        :closable="false"
        show-icon
        title="上次审核被驳回"
        :description="form.reviewReason || '请修改后重新提交审核。'"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="admin-cover-row">
          <div class="admin-cover-preview">
            <img v-if="form.coverUrl" :src="form.coverUrl" alt="活动封面预览" />
            <span v-else>暂无封面</span>
          </div>
          <div>
            <div class="admin-filter-actions">
              <el-upload
                :show-file-list="false"
                :auto-upload="false"
                accept="image/*"
                :on-change="handleCoverChange"
              >
                <el-button :loading="coverUploading" plain>
                  {{ form.coverUrl ? '更换封面' : '上传封面' }}
                </el-button>
              </el-upload>
              <el-button v-if="form.coverUrl" plain @click="form.coverUrl = ''">移除</el-button>
            </div>
            <p class="admin-dialog-hint">建议 16:9 横图，不超过 5MB</p>
          </div>
        </div>

        <div class="admin-form-grid">
          <el-form-item label="活动标题" prop="activityTitle">
            <el-input v-model="form.activityTitle" maxlength="255" placeholder="例如：创新创业讲座" />
          </el-form-item>
          <el-form-item label="活动类型" prop="activityType">
            <el-select v-model="form.activityType" placeholder="选择活动类型">
              <el-option v-if="isAdmin" :value="1" label="第二课堂学分活动" />
              <el-option v-if="isAdmin" :value="2" label="志愿服务活动" />
              <el-option v-if="isAdmin" :value="3" label="运动健身活动" />
              <el-option v-if="isAdmin" :value="4" label="兴趣团建活动" />
              <el-option v-if="!isAdmin" :value="1" label="第二课堂学分活动" />
              <el-option v-if="!isAdmin" :value="2" label="志愿服务活动" />
            </el-select>
          </el-form-item>

          <template v-if="Number(form.activityType) === 1">
            <el-form-item label="学分模式">
              <el-radio-group v-model="form.creditMode">
                <el-radio-button :value="1">固定学分</el-radio-button>
                <el-radio-button :value="2">组合学分</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="学分类型" prop="creditType">
              <el-select v-model="form.creditType" placeholder="选择学分类型">
                <el-option :value="1" label="创新创业" />
                <el-option :value="2" label="智育" />
                <el-option :value="3" label="德育" />
                <el-option :value="4" label="体育" />
                <el-option :value="5" label="美育" />
                <el-option :value="6" label="劳育" />
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="Number(form.creditMode) === 1"
              label="活动学分"
              prop="activityCredit"
            >
              <el-input-number v-model="form.activityCredit" :min="0.05" :step="0.05" :precision="2" />
            </el-form-item>
            <el-form-item v-else label="学分档位（不同学分对应不同名额）" class="admin-tier-field">
              <div class="admin-tier-editor">
                <div v-for="(tier, index) in form.creditTiers" :key="index" class="admin-tier-row">
                  <el-input-number v-model="tier.credit" :min="0.05" :step="0.05" :precision="2" />
                  <span class="admin-tier-unit">学分</span>
                  <el-input-number v-model="tier.quota" :min="1" :step="1" />
                  <span class="admin-tier-unit">名</span>
                  <el-button text type="danger" @click="removeTier(index)">删除</el-button>
                </div>
                <el-button plain size="small" @click="addTier">添加档位</el-button>
                <p class="admin-dialog-hint">
                  例如：1 学分 3 名、0.5 学分 20 名。发放时发布者给每名学生选择档位，名额用完不能再选。
                </p>
              </div>
            </el-form-item>
          </template>

          <template v-if="Number(form.activityType) === 2">
            <el-form-item label="志愿时长模式">
              <el-radio-group v-model="form.volunteerMode">
                <el-radio-button :value="1">固定时长</el-radio-button>
                <el-radio-button :value="2">灵活时长</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item
              v-if="Number(form.volunteerMode) === 1"
              label="志愿时长（小时）"
              prop="volunteerHours"
            >
              <el-input-number v-model="form.volunteerHours" :min="0.5" :step="0.5" :precision="2" />
            </el-form-item>
            <el-form-item v-else label="灵活时长说明">
              <p class="admin-dialog-hint">
                不预设统一时长。活动结束后，发布者在「参与人员」中为每名学生录入实际时长并提交，
                由管理员审核通过后计入统计。
              </p>
            </el-form-item>
          </template>

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
          <el-form-item label="活动地点" prop="activityLocation">
            <el-input v-model="form.activityLocation" maxlength="255" placeholder="例如：图书馆报告厅" />
          </el-form-item>
          <el-form-item label="人数上限" prop="maxParticipant">
            <el-input-number v-model="form.maxParticipant" :min="1" :step="1" />
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

        <div class="admin-form-actions">
          <el-button @click="router.push(backTarget)">取消</el-button>
          <el-button type="warning" :loading="submitting" @click="handleSubmit">
            {{ isAdmin ? '立即发布' : isEdit ? '保存并重新提交审核' : '提交审核' }}
          </el-button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/admin'
import { useAdminSession } from '@/composables/useAdminSession'

const route = useRoute()
const router = useRouter()
const { isAdmin } = useAdminSession()

const formRef = ref(null)
const tags = ref([])
const loadingDetail = ref(false)
const coverUploading = ref(false)
const submitting = ref(false)

const isEdit = computed(() => Boolean(route.params.id))
const activityId = computed(() => route.params.id)
const backTarget = computed(() => (isAdmin.value
  ? { name: 'activity-review' }
  : { name: 'credit-activities' }))

const form = reactive({
  activityType: 1,
  creditMode: 1,
  volunteerMode: 1,
  activityTitle: '',
  activityDesc: '',
  activityCredit: 1,
  creditType: 2,
  volunteerHours: 1,
  creditTiers: [{ credit: 1, quota: 10 }],
  coverUrl: '',
  startTime: '',
  endTime: '',
  activityLocation: '',
  maxParticipant: 30,
  tagIds: [],
  reviewStatus: null,
  reviewReason: '',
})

const addTier = () => {
  form.creditTiers.push({ credit: 0.5, quota: 10 })
}

const removeTier = (index) => {
  if (form.creditTiers.length <= 1) {
    ElMessage.warning('至少保留一个学分档位')
    return
  }
  form.creditTiers.splice(index, 1)
}

const validateCredit = (_rule, value, callback) => {
  if (Number(form.activityType) !== 1 || Number(form.creditMode) !== 1) {
    callback()
    return
  }
  if (value === null || value === undefined || Number(value) <= 0) {
    callback(new Error('学分必须大于 0'))
    return
  }
  if (Math.round(Number(value) * 100) % 5 !== 0) {
    callback(new Error('学分必须以 0.05 为单位'))
    return
  }
  callback()
}

const validateCreditType = (_rule, value, callback) => {
  if (Number(form.activityType) !== 1 || value) {
    callback()
    return
  }
  callback(new Error('请选择学分类型'))
}

const validateVolunteerHours = (_rule, value, callback) => {
  if (Number(form.activityType) !== 2 || Number(form.volunteerMode) !== 1) {
    callback()
    return
  }
  if (value === null || value === undefined || Number(value) <= 0) {
    callback(new Error('志愿时长必须大于 0'))
    return
  }
  callback()
}

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
  activityCredit: [{ validator: validateCredit, trigger: 'change' }],
  creditType: [{ validator: validateCreditType, trigger: 'change' }],
  volunteerHours: [{ validator: validateVolunteerHours, trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    { validator: validateEndTime, trigger: 'change' },
  ],
  maxParticipant: [{ required: true, message: '请填写人数上限', trigger: 'change' }],
}

const loadDetail = async () => {
  if (!isEdit.value) return
  loadingDetail.value = true
  try {
    const activity = await adminApi.activityDetail(activityId.value)
    if (![1, 2].includes(Number(activity.activityType))) {
      ElMessage.error('仅学分或志愿活动可以在此编辑')
      router.replace({ name: 'credit-activities' })
      return
    }
    Object.assign(form, {
      activityType: Number(activity.activityType ?? 1),
      creditMode: Number(activity.creditMode ?? 1),
      volunteerMode: Number(activity.volunteerMode ?? 1),
      activityTitle: activity.activityTitle || '',
      activityDesc: activity.activityDesc || '',
      activityCredit: Number(activity.activityCredit ?? 1),
      creditType: activity.creditType ?? 2,
      volunteerHours: Number(activity.volunteerHours ?? 1),
      creditTiers: (activity.creditTiers || []).length
        ? activity.creditTiers.map((tier) => ({ credit: Number(tier.credit), quota: Number(tier.quota) }))
        : [{ credit: 1, quota: 10 }],
      coverUrl: activity.coverUrl || '',
      startTime: activity.startTime || '',
      endTime: activity.endTime || '',
      activityLocation: activity.activityLocation || '',
      maxParticipant: activity.maxParticipant || 30,
      tagIds: (activity.tags || []).map((tag) => tag.tagId),
      reviewStatus: activity.reviewStatus,
      reviewReason: activity.reviewReason || '',
    })
  } catch {
    router.replace(backTarget.value)
  } finally {
    loadingDetail.value = false
  }
}

const handleCoverChange = async (uploadFile) => {
  const file = uploadFile?.raw
  if (!file) return
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
    const result = await adminApi.uploadCover(file)
    form.coverUrl = result.coverUrl
    ElMessage.success('封面上传成功')
  } finally {
    coverUploading.value = false
  }
}

const validateTiers = () => {
  if (Number(form.activityType) !== 1 || Number(form.creditMode) !== 2) return true
  if (!form.creditTiers.length) {
    ElMessage.warning('组合学分至少需要一个档位')
    return false
  }
  const seen = new Set()
  for (const tier of form.creditTiers) {
    if (!tier.credit || Number(tier.credit) <= 0) {
      ElMessage.warning('档位学分必须大于 0')
      return false
    }
    if (Math.round(Number(tier.credit) * 100) % 5 !== 0) {
      ElMessage.warning('档位学分必须以 0.05 为单位')
      return false
    }
    if (!tier.quota || Number(tier.quota) < 1) {
      ElMessage.warning('档位名额必须大于 0')
      return false
    }
    const key = Number(tier.credit).toFixed(2)
    if (seen.has(key)) {
      ElMessage.warning('同一活动不能配置重复的档位学分')
      return false
    }
    seen.add(key)
  }
  return true
}

const buildPayload = () => {
  const activityType = Number(form.activityType) || 1
  const creditMode = activityType === 1 ? Number(form.creditMode) : null
  const volunteerMode = activityType === 2 ? Number(form.volunteerMode) : null
  return {
    activityTitle: form.activityTitle,
    activityDesc: form.activityDesc,
    activityType,
    creditMode,
    volunteerMode,
    activityCredit: activityType === 1 && creditMode === 1 ? form.activityCredit : null,
    creditType: activityType === 1 ? form.creditType : null,
    creditTiers: activityType === 1 && creditMode === 2
      ? form.creditTiers.map((tier) => ({ credit: tier.credit, quota: tier.quota }))
      : null,
    volunteerHours: activityType === 2 && volunteerMode === 1 ? form.volunteerHours : null,
    coverUrl: form.coverUrl || null,
    startTime: form.startTime,
    endTime: form.endTime,
    activityLocation: form.activityLocation,
    maxParticipant: form.maxParticipant,
    tagIds: form.tagIds,
  }
}

const handleSubmit = async () => {
  if (submitting.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!validateTiers()) return
  submitting.value = true
  try {
    if (isAdmin.value) {
      await adminApi.createActivity(buildPayload())
      ElMessage.success('活动已发布，已直接公开')
    } else if (isEdit.value) {
      await adminApi.updateStaffActivity(activityId.value, buildPayload())
      ElMessage.success('活动已更新，已重新提交审核')
    } else {
      await adminApi.createStaffActivity(buildPayload())
      ElMessage.success('活动已提交审核')
    }
    router.push(backTarget.value)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  tags.value = await adminApi.tags().catch(() => [])
  await loadDetail()
})
</script>

<style scoped>
.admin-tier-field :deep(.el-form-item__content) {
  display: block;
}

.admin-tier-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.admin-tier-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-tier-row :deep(.el-input-number) {
  width: 150px;
  flex: 0 0 150px;
}

.admin-tier-unit {
  color: var(--text-3, #666);
  font-size: 13px;
}
</style>
