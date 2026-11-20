<template>
  <el-dialog
    :model-value="modelValue"
    title="AI 智能导入发放"
    width="980px"
    append-to-body
    @update:model-value="(value) => emit('update:modelValue', value)"
    @closed="reset"
  >
    <div class="award-import">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="上传表格后由 AI 自动识别"
        description="支持 .xlsx / .xls / .csv。AI 会自动识别表头、学号/姓名列、时长单位或学分列；学号优先匹配、姓名兜底；只处理已报名且已签退的参与者。保存后进入“待审核”，由管理员确认发放。"
      />

      <div class="award-import__upload">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls,.csv"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-button :loading="analyzing" plain>选择表格文件</el-button>
        </el-upload>
        <span v-if="file" class="award-import__file">{{ file.name }}</span>
        <el-button type="warning" :loading="analyzing" :disabled="!file" @click="handleAnalyze">
          AI 识别并预览
        </el-button>
      </div>

      <template v-if="preview">
        <div class="award-import__meta">
          <el-tag :type="preview.aiUsed ? 'success' : 'info'" size="small">
            {{ preview.aiUsed ? 'AI 识别' : '规则识别' }}
          </el-tag>
          <el-tag size="small" type="info">
            识别为：{{ preview.valueKind === 'credit' ? '学分表' : '志愿时长表' }}
          </el-tag>
          <span>表头：第 {{ preview.headerRow }} 行</span>
          <span v-for="(label, key) in mappingEntries" :key="key">{{ label }}</span>
          <span v-if="preview.hoursUnit === 'min'">单位：分钟（已换算为小时）</span>
        </div>
        <p class="award-import__notes">{{ preview.aiNotes }}</p>

        <div class="award-import__summary">
          <span>数据行 <strong>{{ preview.summary.total }}</strong></span>
          <span>可保存 <strong>{{ updateCount }}</strong></span>
          <span>未匹配 <strong>{{ preview.summary.unmatched }}</strong></span>
          <span>未签退 <strong>{{ preview.summary.noRecord }}</strong></span>
          <span>已发放跳过 <strong>{{ preview.summary.skippedApproved }}</strong></span>
          <span>冲突 <strong>{{ conflictCount }}</strong></span>
        </div>

        <div v-if="preview.creditTiers && preview.creditTiers.length" class="award-import__tiers">
          档位：
          <el-tag v-for="tier in preview.creditTiers" :key="tier.tierId" size="small" type="warning">
            {{ tier.credit }} 学分 · 当前 {{ tier.used }}/{{ tier.quota }} · 本次 {{ tier.selected || 0 }}
          </el-tag>
        </div>

        <el-table :data="preview.rows" size="small" max-height="360" empty-text="没有可导入的数据">
          <el-table-column label="保存" width="55" align="center">
            <template #default="{ row }">
              <el-checkbox
                v-model="row.selected"
                :disabled="row.action !== 'UPDATE'"
              />
            </template>
          </el-table-column>
          <el-table-column label="行" width="60" prop="rowNumber" />
          <el-table-column label="姓名" width="100" prop="name" />
          <el-table-column label="学号" width="130">
            <template #default="{ row }"><span class="mono">{{ row.studentNo || '—' }}</span></template>
          </el-table-column>
          <el-table-column label="表内值" width="90" align="right">
            <template #default="{ row }"><span class="mono">{{ row.rawValue ?? '—' }}</span></template>
          </el-table-column>
          <el-table-column label="匹配学生" width="150">
            <template #default="{ row }">
              <div v-if="row.enrollId">{{ row.matchedName || row.userId }}</div>
              <div v-if="row.userId" class="admin-cell-sub mono">{{ row.userId }}</div>
              <span v-else class="admin-cell-sub">—</span>
            </template>
          </el-table-column>
          <el-table-column label="当前" width="110" align="right">
            <template #default="{ row }"><span class="mono">{{ row.currentValue ?? '—' }}</span></template>
          </el-table-column>
          <el-table-column label="结果" width="110">
            <template #default="{ row }">
              <el-tag :type="actionTagType(row.action)" size="small">{{ actionText(row.action) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="160" prop="reason" />
        </el-table>
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        type="warning"
        :disabled="!preview || selectedCount === 0"
        :loading="applying"
        @click="handleApply"
      >
        保存 {{ selectedCount }} 条（待审核）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/admin'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  activityId: { type: [Number, String], required: true },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const uploadRef = ref(null)
const file = ref(null)
const analyzing = ref(false)
const applying = ref(false)
const preview = ref(null)

const mappingEntries = computed(() => {
  const mapping = preview.value?.mapping || {}
  const labels = []
  if (mapping.studentNo) labels.push(`学号列：${mapping.studentNo}`)
  if (mapping.name) labels.push(`姓名列：${mapping.name}`)
  if (mapping.hours) labels.push(`时长列：${mapping.hours}`)
  if (mapping.credit) labels.push(`学分列：${mapping.credit}`)
  return labels
})

const updateCount = computed(() =>
  (preview.value?.rows || []).filter((row) => row.action === 'UPDATE').length)

const selectedCount = computed(() =>
  (preview.value?.rows || []).filter((row) => row.action === 'UPDATE' && row.selected).length)

const conflictCount = computed(() => {
  const summary = preview.value?.summary || {}
  return Number(summary.invalid || 0) + Number(summary.duplicate || 0) + Number(summary.tierFull || 0)
})

const actionText = (action) => ({
  UPDATE: '可保存',
  SKIP_APPROVED: '已发放跳过',
  UNMATCHED: '未匹配',
  AMBIGUOUS: '重名歧义',
  NO_RECORD: '未签退',
  INVALID: '数据无效',
  DUPLICATE: '重复冲突',
  TIER_FULL: '名额已满',
}[action] || action)

const actionTagType = (action) => ({
  UPDATE: 'success',
  SKIP_APPROVED: 'info',
  UNMATCHED: 'warning',
  AMBIGUOUS: 'warning',
  NO_RECORD: 'warning',
  INVALID: 'danger',
  DUPLICATE: 'danger',
  TIER_FULL: 'danger',
}[action] || 'info')

const handleFileChange = (uploadFile) => {
  file.value = uploadFile?.raw || null
  preview.value = null
}

const handleFileRemove = () => {
  file.value = null
  preview.value = null
}

const handleAnalyze = async () => {
  if (!file.value || analyzing.value) return
  const formData = new FormData()
  formData.append('file', file.value)
  analyzing.value = true
  try {
    preview.value = await adminApi.analyzeAwardImport(props.activityId, formData)
    ElMessage.success('识别完成，请核对预览后保存')
  } finally {
    analyzing.value = false
  }
}

const handleApply = async () => {
  if (!preview.value || applying.value) return
  const rows = preview.value.rows
    .filter((row) => row.action === 'UPDATE' && row.selected)
    .map((row) => ({ enrollId: row.enrollId, value: row.rawValue }))
  if (!rows.length) {
    ElMessage.warning('请至少勾选一条可保存的数据')
    return
  }
  applying.value = true
  try {
    const result = await adminApi.applyAwardImport(props.activityId, { rows })
    ElMessage.success(result?.message || '已保存，等待管理员审核')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    applying.value = false
  }
}

const reset = () => {
  file.value = null
  preview.value = null
  uploadRef.value?.clearFiles?.()
}
</script>

<style scoped>
.award-import {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.award-import__upload {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.award-import__file {
  font-size: 13px;
  color: var(--text-2, #666);
}

.award-import__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--text-2, #666);
}

.award-import__notes {
  margin: 0;
  font-size: 13px;
  color: var(--text-3, #888);
}

.award-import__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--text-2, #666);
}

.award-import__summary strong {
  color: var(--text-1, #111);
}

.award-import__tiers {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-2, #666);
}
</style>
