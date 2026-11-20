<template>
  <div class="app-shell">
    <AppHeader @open-auth="authVisible = true" />
    <router-view />
    <!-- AI 助手：悬浮球 + 抽屉。未登录时点击会触发 require-auth 弹出登录框 -->
    <AiAssistant @require-auth="authVisible = true" />

    <AuthDialog v-model="authVisible" />
  </div>
</template>

<script setup>
import { onMounted, provide, ref } from 'vue'
import AppHeader from './components/AppHeader.vue'
import AuthDialog from './components/AuthDialog.vue'
import AiAssistant from './components/ai/AiAssistant.vue'
import { setUnauthorizedHandler } from '@/composables/useSession'

const authVisible = ref(false)

/**
 * 页面内主动拉起登录弹窗的入口。
 * 未登录用户停在发布页等需要身份的操作时，不必再自己去找顶栏的头像按钮。
 */
provide('openAuth', () => {
  authVisible.value = true
})

/**
 * 把"收到 401"这件事接到登录弹窗上。
 *
 * 为什么要在 App.vue 这一层做：`api/http.js` 是纯请求层，不该知道界面长什么样；
 * 而登录弹窗由 App.vue 持有。所以由 `useSession` 提供注册入口，这里注册回调，
 * 请求层只管"通知"，界面层管"怎么响应"。
 *
 * 触发时机：token 有效期 12 小时（`jwt.expiration=43200000`），
 * 过期后第一个请求就会 401；或者用户在其他标签页点了登出（token 进了黑名单）。
 * 没有这段兜底的话，用户会看到满屏"未登录"的错误提示但不知道该做什么。
 */
onMounted(() => {
  setUnauthorizedHandler(() => {
    authVisible.value = true
  })
})
</script>

