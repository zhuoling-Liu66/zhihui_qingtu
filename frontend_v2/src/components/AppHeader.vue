<template>
  <aside class="workspace-sidebar card-surface">
    <router-link class="sidebar-brand" to="/">
      <span class="sidebar-brand__logo">闲</span>
      <div>
        <strong>智慧青图</strong>
        <small>Campus Goofish</small>
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
          <button type="button" class="sidebar-userbox__button sidebar-userbox__button--ghost" @click="clearUser">
            退出登录
          </button>
        </div>
        <p class="sidebar-userbox__hint">学分审核入口：我发布的活动 -> 学分活动卡片</p>
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
          placeholder="搜活动、搜标签、搜组队"
          @keyup.enter="submitSearch"
        >
          <template #suffix>
            <el-button type="warning" round @click="submitSearch">搜索</el-button>
          </template>
        </el-input>
      </div>

      <div class="workspace-topbar__summary">
        <span class="workspace-topbar__eyebrow">Campus Activity Hub</span>
        <strong>把第二课堂、志愿服务、组队邀请和 AI 推荐放进同一条校园信息流。</strong>
      </div>
    </div>

    <div class="workspace-topbar__topics">
      <button
        v-for="item in topQuickLinks"
        :key="item.slug"
        class="workspace-topbar__topic"
        type="button"
        @click="router.push({ name: 'section', params: { slug: item.slug } })"
      >
        {{ item.label }}
      </button>
    </div>
  </header>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Calendar,
  DataAnalysis,
  Files,
  HomeFilled,
  Position,
  UserFilled,
} from '@element-plus/icons-vue'
import { topQuickLinks } from '@/constants/ui'
import { useSession } from '@/composables/useSession'

defineEmits(['open-auth'])

const route = useRoute()
const router = useRouter()
const { user, isLoggedIn, clearUser } = useSession()

const workspaceLinks = [
  { label: '首页逛场', to: { name: 'home' }, icon: HomeFilled, names: ['home'] },
  { label: '发布活动', to: { name: 'publish' }, icon: Position, names: ['publish'] },
  { label: '我发布的活动', to: { name: 'my-published' }, icon: Files, names: ['my-published', 'credit-review'] },
  { label: '我参加的活动', to: { name: 'my-joined' }, icon: Calendar, names: ['my-joined'] },
  { label: '我的队伍', to: { name: 'my-teams' }, icon: DataAnalysis, names: ['my-teams', 'team-detail'] },
  { label: '个人中心', to: { name: 'profile' }, icon: UserFilled, names: ['profile'] },
]

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
