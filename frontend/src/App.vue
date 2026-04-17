<template>
  <div class="app-shell">
    <AppHeader />
    <router-view />
    <AuthDialog v-model="authVisible" />
  </div>
</template>

<script setup>
import AppHeader from './components/AppHeader.vue'
import AuthDialog from './components/AuthDialog.vue'
import { useAuthDialog } from './composables/useAuthDialog'

const { authVisible } = useAuthDialog()
</script>
