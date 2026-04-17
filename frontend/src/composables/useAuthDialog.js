import { ref } from 'vue'

const authVisible = ref(false)

function openAuth() {
  authVisible.value = true
}

function closeAuth() {
  authVisible.value = false
}

export function useAuthDialog() {
  return {
    authVisible,
    openAuth,
    closeAuth,
  }
}
