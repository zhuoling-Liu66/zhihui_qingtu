<template>
  <div class="login-shell">
    <div class="login-box">
      <div class="login-brand">
        <span class="login-brand__logo">智</span>
        <div>
          <strong>智慧青图 · 管理后台</strong>
          <small>管理控制台</small>
        </div>
      </div>

      <div class="login-card">
        <h2>登录管理后台</h2>
        <p>管理员与运营人员请使用各自账号登录</p>

        <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="工号">
            <el-input
              v-model="form.studentNo"
              placeholder="请输入管理端工号"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            class="login-submit"
            type="warning"
            :loading="loading"
            native-type="submit"
          >
            登录管理后台
          </el-button>
        </el-form>

        <p class="login-foot">学生与老师账号不能登录管理后台；运营账号也不能登录客户端</p>
      </div>

      <ul class="login-facts">
        <li>普通活动与学分活动统一进入审核队列</li>
        <li>学分活动仅限管理端运营账号创建</li>
        <li>审核通过后活动才会公开并开放报名</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/admin'
import { useAdminSession } from '@/composables/useAdminSession'

const route = useRoute()
const router = useRouter()
const { setUser, role } = useAdminSession()

const form = reactive({
  studentNo: '',
  password: '',
})
const loading = ref(false)

const homeFor = () => (Number(role.value) === 4
  ? { name: 'credit-activities' }
  : { name: 'dashboard' })

const handleLogin = async () => {
  if (loading.value) return
  if (!form.studentNo.trim() || !form.password) {
    ElMessage.warning('请输入工号和密码')
    return
  }
  loading.value = true
  try {
    const data = await adminApi.login({
      studentNo: form.studentNo.trim(),
      password: form.password,
    })
    setUser(data)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect) {
      router.replace(redirect)
    } else {
      router.replace(homeFor())
    }
  } finally {
    loading.value = false
  }
}
</script>
