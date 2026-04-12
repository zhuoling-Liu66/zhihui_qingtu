<template>
  <main class="page page-publish">
    <section class="page-section publish-layout">
      <div class="publish-intro card-surface">
        <p class="section-head__kicker">发布页</p>
        <h1>创建即发布，封面上传也一起完成</h1>
        <p>
          现在活动不再经过草稿、发布、结束的复杂流转。创建完成后会直接进入“已发布”状态，
          后续只保留“已发布 / 已取消”两种管理操作；签到改为活动级开关，由参与者自助签到。
        </p>
        <div class="publish-intro__tips">
          <span>支持上传活动封面</span>
          <span>第二课堂活动填写学分和学分类型</span>
          <span>志愿活动填写服务时长</span>
        </div>
      </div>

      <div class="publish-form-wrap card-surface">
        <div v-if="!user" class="empty-login">
          <h3>登录后才能发布活动</h3>
          <p>请先在右上角完成登录或注册，再回来发布校园活动。</p>
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
                <span>建议横向图片，活动卡片和详情页都会直接展示</span>
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
              placeholder="写清楚面向人群、流程、奖励和注意事项"
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
            <el-button :loading="submitting" type="warning" size="large" @click="handlePublish">
              创建并发布活动
            </el-button>
          </div>
        </el-form>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CoverImage from '@/components/CoverImage.vue'
import { activityApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'

const router = useRouter()
const { user } = useSession()

const tags = ref([])
const submitting = ref(false)
const coverUploading = ref(false)

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

const modeHint = computed(() => {
  if (form.activityType === 1) return '当前为第二课堂学分活动：需要填写学分和学分类型。'
  if (form.activityType === 2) return '当前为志愿服务活动：只需要填写志愿服务时长，不需要设置学分。'
  return '当前为非学分活动：系统会自动隐藏学分相关字段。'
})

const handleCoverChange = async (uploadFile) => {
  if (!uploadFile?.raw) {
    return
  }

  coverUploading.value = true
  try {
    const result = await activityApi.uploadCover(uploadFile.raw)
    form.coverUrl = result.coverUrl
    ElMessage.success('活动封面上传成功')
  } finally {
    coverUploading.value = false
  }
}

const handlePublish = async () => {
  if (!user.value?.userId) {
    return
  }

  submitting.value = true
  try {
    const payload = {
      publisherUserId: user.value.userId,
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
    }

    const created = await activityApi.create(payload)
    ElMessage.success(`活动发布成功，活动ID：${created.activityId}`)
    router.push({ name: 'activity-detail', params: { id: created.activityId } })
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  tags.value = await activityApi.tags()
})
</script>
