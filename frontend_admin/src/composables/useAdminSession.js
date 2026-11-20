import { computed, reactive } from 'vue'

/**
 * 管理端会话。
 *
 * 与管理端 token 使用**独立的 localStorage key**，避免同浏览器同时登录
 * 客户端与后台时互相覆盖。运营人员账号只能通过管理端登录，客户端登录会被后端拒绝。
 */
const STORAGE_KEY = 'zhihuiqingtu-admin-user'
const TOKEN_KEY = 'zhihuiqingtu-admin-token'

function loadUser() {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

const state = reactive({
  user: loadUser(),
  token: localStorage.getItem(TOKEN_KEY) || '',
})

let unauthorizedHandler = null

function persistToken(token) {
  state.token = token || ''
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

function setUser(user) {
  state.user = user
  localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
  persistToken(user?.token)
}

function clearUser() {
  state.user = null
  localStorage.removeItem(STORAGE_KEY)
  persistToken('')
}

export function getToken() {
  return state.token || ''
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

export function emitUnauthorized() {
  clearUser()
  unauthorizedHandler?.()
}

export function useAdminSession() {
  const user = computed(() => state.user)
  const role = computed(() => Number(state.user?.userRole))
  const isAdmin = computed(() => role.value === 2)
  const isStaff = computed(() => role.value === 4)
  const isLoggedIn = computed(() => Boolean(state.user))

  return {
    user,
    role,
    isAdmin,
    isStaff,
    isLoggedIn,
    setUser,
    clearUser,
    getToken,
  }
}
