<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <router-link class="admin-brand" :to="home">
        <span class="admin-brand__logo">智</span>
        <div>
          <strong>智慧青图 · 管理后台</strong>
          <small>管理控制台</small>
        </div>
      </router-link>

      <nav class="admin-nav">
        <router-link
          v-for="item in navItems"
          :key="item.name"
          class="admin-nav__item"
          :class="{ 'admin-nav__item--active': isActive(item) }"
          :to="{ name: item.name }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div>
          <p class="admin-topbar__eyebrow">{{ portalLabel }}</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="admin-topbar__actions">
          <span class="admin-user">
            {{ user?.userName || user?.userId }}
            <small>{{ isAdmin ? '管理员' : '运营人员' }}</small>
          </span>
          <el-button plain :loading="loggingOut" @click="handleLogout">退出登录</el-button>
        </div>
      </header>

      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Calendar,
  CircleCheck,
  DataAnalysis,
  Plus,
  User,
} from '@element-plus/icons-vue'
import { adminApi } from '@/api/admin'
import { useAdminSession } from '@/composables/useAdminSession'

const route = useRoute()
const router = useRouter()
const { user, isAdmin, isStaff, isLoggedIn, clearUser } = useAdminSession()

const loggingOut = ref(false)

const adminNav = [
  { label: '数据概览', name: 'dashboard', icon: DataAnalysis },
  { label: '活动审核', name: 'activity-review', icon: CircleCheck },
  { label: '发布活动', name: 'admin-activity-create', icon: Plus },
  { label: '账号管理', name: 'accounts', icon: User },
]

const staffNav = [
  { label: '活动管理', name: 'credit-activities', icon: Calendar },
]

const navItems = computed(() => (isStaff.value ? staffNav : adminNav))
const home = computed(() => (isStaff.value ? { name: 'credit-activities' } : { name: 'dashboard' }))
const pageTitle = computed(() => route.meta.title || '管理后台')
const portalLabel = computed(() => (isStaff.value ? '运营工作台' : '管理员工作台'))

const isActive = (item) => {
  if (item.name === 'credit-activities') {
    return route.name === 'credit-activities'
      || route.name === 'credit-activity-create'
      || route.name === 'credit-activity-edit'
  }
  return route.name === item.name
}

watch(isLoggedIn, (loggedIn) => {
  if (!loggedIn) {
    router.replace({ name: 'login' })
  }
})

const handleLogout = async () => {
  loggingOut.value = true
  try {
    await adminApi.logout()
    ElMessage.success('已退出登录')
  } catch {
    // 令牌可能已过期；本地状态仍然要清干净
  } finally {
    clearUser()
    loggingOut.value = false
    router.replace({ name: 'login' })
  }
}
</script>
