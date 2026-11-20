<template>
  <aside class="workspace-sidebar card-surface">
    <router-link class="sidebar-brand" to="/">
      <span class="sidebar-brand__logo">闲</span>
      <div>
        <strong>智慧青图</strong>
        <small>校园活动集市</small>
      </div>
    </router-link>

    <section class="sidebar-section">
      <p class="sidebar-section__title">校园工作台</p>
      <nav class="sidebar-nav">
        <router-link
          v-for="item in workspaceLinks"
          :key="item.label"
          class="sidebar-nav__item"
          :class="{ 'sidebar-nav__item--active': isActive(item) }"
          :to="item.to"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </section>

    <section class="sidebar-userbox">
      <template v-if="isLoggedIn">
        <div class="sidebar-userbox__meta">
          <span class="sidebar-userbox__eyebrow">当前账号</span>
          <strong>{{ user?.userName || user?.userId }}</strong>
        </div>
        <div class="sidebar-userbox__actions">
          <router-link class="sidebar-userbox__button" to="/profile">个人中心</router-link>
          <button
            type="button"
            class="sidebar-userbox__button sidebar-userbox__button--ghost"
            :disabled="loggingOut"
            @click="handleLogout"
          >
            {{ loggingOut ? '退出中…' : '退出登录' }}
          </button>
        </div>
      </template>
      <template v-else>
        <p class="sidebar-userbox__eyebrow">账号入口</p>
        <h4>登录后同步你的学分与推荐</h4>
        <p class="sidebar-userbox__hint">系统会结合兴趣标签、专业年级和参与历史给出更合适的活动。</p>
        <button type="button" class="login-entry-button" @click="$emit('open-auth')">登录</button>
      </template>
    </section>
  </aside>

  <header class="workspace-topbar card-surface">
    <div class="workspace-topbar__main">
      <div class="workspace-topbar__search">
        <el-input
          v-model="searchText"
          class="search-hub__input"
          placeholder="搜活动、标签或队伍"
          @keyup.enter="submitSearch"
        >
          <template #suffix>
            <button type="button" class="search-submit" @click="submitSearch">搜索</button>
          </template>
        </el-input>
      </div>

      <!--
        顶栏头像：已登录显示用户名首字，点击进入个人中心；
        未登录显示占位头像，点击拉起登录弹窗，让"登录"在顶栏也始终有一个稳定入口。
      -->
      <button
        v-if="isLoggedIn"
        type="button"
        class="topbar-avatar"
        :title="(user?.userName || user?.userId || '') + ' · 个人中心'"
        @click="router.push({ name: 'profile' })"
      >
        {{ avatarChar }}
      </button>
      <button
        v-else
        type="button"
        class="topbar-avatar topbar-avatar--ghost"
        title="登录"
        @click="$emit('open-auth')"
      >
        <el-icon><UserFilled /></el-icon>
      </button>
    </div>

    <div class="workspace-topbar__topics">
      <router-link
        v-for="item in topQuickLinks"
        :key="item.slug"
        class="workspace-topbar__topic"
        :to="{ name: 'section', params: { slug: item.slug } }"
      >
        {{ item.label }}
      </router-link>
    </div>
  </header>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Calendar,
  DataAnalysis,
  Files,
  HomeFilled,
  Position,
  UserFilled,
} from '@element-plus/icons-vue'
import { authApi } from '@/api/platform'
import { topQuickLinks } from '@/constants/ui'
import { useSession } from '@/composables/useSession'

defineEmits(['open-auth'])

const route = useRoute()
const router = useRouter()
const { user, isLoggedIn, clearUser } = useSession()

/* 头像字符：优先用户名首字，退回用户ID首字；用展开运算符避免代理对截断 */
const avatarChar = computed(() => {
  const name = (user.value?.userName || user.value?.userId || '').trim()
  return [...name][0] || '青'
})

const loggingOut = ref(false)

/**
 * 退出登录。
 *
 * 关键点：**必须先调后端 `/auth/logout`，再清本地缓存**。
 *
 * 原来的实现只有一个 `clearUser()`（删 localStorage），这在 JWT 下是不够的：
 * JWT 是无状态的，服务端不保存任何会话，所以"前端删掉 token"并不会让服务端那串 token 失效 ——
 * 它依然能通过签名校验，直到 12 小时后自然过期。若期间被截获，等于没退出。
 *
 * 后端现在会把 token 的 `jti` 写进 Redis 黑名单（TTL = 该 token 剩余寿命），
 * 拦截器每次请求多查一步，从而实现"立即失效"。
 *
 * 顺序不能颠倒：如果先 `clearUser()`，请求拦截器就拿不到 token 了，
 * 登出接口会因为缺 token 而 401，黑名单永远写不进去。
 * 而登出请求本身也可能失败（比如网络断了），这时仍然要清本地会话，
 * 否则用户会卡在"退不出去"的状态 —— 所以用 try/finally。
 */
const handleLogout = async () => {
  loggingOut.value = true
  try {
    await authApi.logout()
    ElMessage.success('已退出登录')
  } catch {
    // 请求层已经弹过错误提示了，这里只负责保证本地状态被清干净
  } finally {
    clearUser()
    loggingOut.value = false
    router.push({ name: 'home' })
  }
}

const workspaceLinks = computed(() => [
  { label: '首页逛场', to: { name: 'home' }, icon: HomeFilled, names: ['home'] },
  { label: '发布活动', to: { name: 'publish' }, icon: Position, names: ['publish'] },
  { label: '我发布的活动', to: { name: 'my-published' }, icon: Files, names: ['my-published', 'activity-participants'] },
  { label: '我参加的活动', to: { name: 'my-joined' }, icon: Calendar, names: ['my-joined'] },
  { label: '我的队伍', to: { name: 'my-teams' }, icon: DataAnalysis, names: ['my-teams', 'team-detail'] },
  { label: '个人中心', to: { name: 'profile' }, icon: UserFilled, names: ['profile'] },
])

const searchText = ref(route.query.q || route.query.keyword || '')

watch(
  () => route.fullPath,
  () => {
    searchText.value = route.query.q || route.query.keyword || ''
  },
)

const isActive = (item) => item.names.includes(route.name)

const submitSearch = () => {
  const q = searchText.value?.trim()
  router.push({
    name: 'search',
    query: q ? { q } : {},
  })
}
</script>
