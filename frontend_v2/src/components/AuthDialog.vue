<template>
  <el-dialog
    :model-value="modelValue"
    width="680px"
    class="auth-dialog"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="auth-dialog__header">
        <h3>进入校园活动集市</h3>
        <p>登录后即可报名活动、发布内容和查看个人学分。</p>
      </div>
    </template>

    <el-tabs v-model="activeTab" stretch>
      <el-tab-pane label="登录" name="login">
        <el-form :model="loginForm" label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="学号 / 工号">
            <el-input v-model="loginForm.studentNo" placeholder="请输入学号或工号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button :loading="loading" type="warning" size="large" class="full-width" @click="handleLogin">
            立即登录
          </el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="注册" name="register">
        <el-form :model="registerForm" label-position="top" @submit.prevent="handleRegister">
          <div class="grid-two">
            <el-form-item label="学号 / 工号">
              <el-input v-model="registerForm.studentNo" placeholder="如 2023123456" />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="registerForm.userName" placeholder="请输入姓名" />
            </el-form-item>
          </div>
          <div class="grid-two">
            <el-form-item label="账号类型">
              <el-input model-value="学生（注册后默认）" disabled />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="registerForm.password" show-password placeholder="至少 6 位" />
            </el-form-item>
          </div>
          <div class="grid-two">
            <el-form-item label="手机号">
              <el-input v-model="registerForm.userPhone" placeholder="建议填写，避免唯一索引空值冲突" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="registerForm.userEmail" placeholder="建议填写有效邮箱" />
            </el-form-item>
          </div>
          <div class="grid-two">
            <el-form-item label="专业">
              <el-input v-model="registerForm.major" placeholder="如 软件工程" />
            </el-form-item>
            <el-form-item label="年级">
              <el-input v-model="registerForm.grade" placeholder="如 2023级" />
            </el-form-item>
          </div>
          <el-button :loading="loading" type="warning" size="large" class="full-width" @click="handleRegister">
            创建账号
          </el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/platform'
import { useSession } from '@/composables/useSession'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue'])

const { setUser } = useSession()

const activeTab = ref('login')
const loading = ref(false)

const loginForm = reactive({
  studentNo: '',
  password: '',
})

const registerForm = reactive({
  studentNo: '',
  userName: '',
  password: '',
  userPhone: '',
  userEmail: '',
  major: '',
  grade: '',
})

const closeDialog = () => emit('update:modelValue', false)

const handleLogin = async () => {
  loading.value = true
  try {
    const data = await authApi.login(loginForm)
    setUser(data)
    ElMessage.success(`欢迎回来，${data.userName}`)
    closeDialog()
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  loading.value = true
  try {
    await authApi.register({
      ...registerForm,
      userPhone: registerForm.userPhone || undefined,
      userEmail: registerForm.userEmail || undefined,
      major: registerForm.major || undefined,
      grade: registerForm.grade || undefined,
    })
    ElMessage.success('注册成功，请直接登录')
    activeTab.value = 'login'
    loginForm.studentNo = registerForm.studentNo
    loginForm.password = registerForm.password
  } finally {
    loading.value = false
  }
}
</script>
