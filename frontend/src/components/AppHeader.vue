<template>
  <header class="top-header">
    <div class="top-header__inner">
      <router-link class="brand-mark" to="/">
        <span class="brand-mark__logo">闲</span>
        <div>
          <strong>校园活动集市</strong>
          <small>Campus Goofish</small>
        </div>
      </router-link>

      <div class="search-hub">
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

        <div class="quick-links">
          <button
            v-for="item in topQuickLinks"
            :key="item.slug"
            class="quick-links__item"
            type="button"
            @click="router.push({ name: 'section', params: { slug: item.slug } })"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div class="header-actions">
        <router-link class="header-action" to="/publish">发布活动</router-link>
        <router-link class="header-action" to="/my-published">我发布的</router-link>
        <router-link class="header-action" to="/my-joined">我参加的</router-link>
        <router-link class="header-action" to="/profile">个人中心</router-link>

        <template v-if="isLoggedIn">
          <el-dropdown>
            <span class="header-user">
              {{ user?.userName || user?.userId }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/my-published')">我发布的活动</el-dropdown-item>
                <el-dropdown-item @click="router.push('/my-joined')">我参加的活动</el-dropdown-item>
                <el-dropdown-item @click="router.push('/profile')">个人主页</el-dropdown-item>
                <el-dropdown-item @click="clearUser">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button round color="#1f2937" plain @click="$emit('open-auth')">登录 / 注册</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { topQuickLinks } from '@/constants/ui'
import { useSession } from '@/composables/useSession'

defineEmits(['open-auth'])

const route = useRoute()
const router = useRouter()
const { user, isLoggedIn, clearUser } = useSession()

const searchText = ref(route.query.q || route.query.keyword || '')

watch(
  () => route.fullPath,
  () => {
    searchText.value = route.query.q || route.query.keyword || ''
  },
)

const submitSearch = () => {
  const q = searchText.value?.trim()
  router.push({
    name: 'search',
    query: q ? { q } : {},
  })
}
</script>
