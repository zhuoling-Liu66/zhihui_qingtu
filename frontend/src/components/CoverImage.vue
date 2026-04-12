<template>
  <img
    v-if="src && !failed"
    :src="src"
    :alt="alt"
    :class="imageClass"
    @error="failed = true"
  />
  <div v-else :class="fallbackClass">
    <span>{{ fallbackText }}</span>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  src: {
    type: String,
    default: '',
  },
  alt: {
    type: String,
    default: '活动封面',
  },
  imageClass: {
    type: String,
    default: '',
  },
  fallbackClass: {
    type: String,
    default: 'cover-fallback',
  },
  fallbackText: {
    type: String,
    default: '暂无封面',
  },
})

const failed = ref(false)

watch(
  () => props.src,
  () => {
    failed.value = false
  },
)
</script>
