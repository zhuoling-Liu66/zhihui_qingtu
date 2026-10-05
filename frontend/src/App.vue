<template>
  <div class="app-shell">
    <AppHeader />
    <router-view />
    <AuthDialog v-model="authVisible" />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import AppHeader from './components/AppHeader.vue'
import AuthDialog from './components/AuthDialog.vue'
import { useAuthDialog } from './composables/useAuthDialog'
import { setUnauthorizedHandler } from '@/composables/useSession'

const { authVisible, openAuth } = useAuthDialog()

/**
 * 把"收到 401"这件事接到登录弹窗上。
 *
 * 为什么在 App.vue 这一层做：`api/http.js` 是纯请求层，不该知道界面长什么样；
 * 而登录弹窗的状态由 `useAuthDialog` 持有。所以由 `useSession` 提供注册入口，
 * 这里注册回调，请求层只管"通知"，界面层管"怎么响应" —— 同时也避免了
 * `http → session → platform → http` 的循环依赖。
 *
 * 触发时机：token 有效期 12 小时，过期后第一个请求就会 401；
 * 或用户在其他标签页点了登出（token 进了黑名单）。没有这段兜底的话，
 * 用户只会看到满屏"未登录"的错误提示，却不知道该做什么。
 */
onMounted(() => {
  setUnauthorizedHandler(() => {
    openAuth()
  })
})
</script>
