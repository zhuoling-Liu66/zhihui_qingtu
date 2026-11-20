<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { setUnauthorizedHandler } from '@/composables/useAdminSession'

const router = useRouter()

onMounted(() => {
  setUnauthorizedHandler(() => {
    router.replace({ name: 'login' })
  })
})
</script>
