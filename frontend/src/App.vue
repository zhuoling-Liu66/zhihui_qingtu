<template>
  <div class="app-shell">
    <AppHeader @open-auth="authVisible = true" />
    <router-view />
    <AuthDialog v-model="authVisible" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AppHeader from './components/AppHeader.vue'
import AuthDialog from './components/AuthDialog.vue'

const authVisible = ref(false)
</script>
