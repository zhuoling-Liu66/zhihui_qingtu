import { computed, reactive } from 'vue'

const STORAGE_KEY = 'campus-activity-vue-user'

function loadUser() {
  const raw = localStorage.getItem(STORAGE_KEY)
  return raw ? JSON.parse(raw) : null
}

const sessionState = reactive({
  user: loadUser(),
})

function setUser(user) {
  sessionState.user = user
  localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
}

function clearUser() {
  sessionState.user = null
  localStorage.removeItem(STORAGE_KEY)
}

export function useSession() {
  return {
    sessionState,
    user: computed(() => sessionState.user),
    isLoggedIn: computed(() => Boolean(sessionState.user)),
    setUser,
    clearUser,
  }
}
