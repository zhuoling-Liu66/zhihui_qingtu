import { computed, reactive } from 'vue'

/**
 * 登录会话。
 *
 * <h3>为什么 token 要单独取出来管</h3>
 * 后端 `LoginResponse` 里带一个 `token` 字段，它是**唯一凭证**：
 * `TokenInterceptor` 要求每个请求都带 `token` 请求头，否则一律 401。
 *
 * 本模块原来只把整个登录响应原样塞进 localStorage 当"用户信息"用，
 * 从没把里面的 token 当成凭证发给后端 —— 结果除登录/注册外所有接口都是 401。
 * 所以这里显式提供 {@link getToken} 给 axios 请求拦截器使用。
 *
 * <h3>为什么用 response 拦截器而不是在这里 import http</h3>
 * `api/http.js` 需要 `getToken()`，如果本模块又去 import `api/platform.js` 调登出接口，
 * 就形成 `http → session → platform → http` 的循环依赖，Vite 打包时会出现
 * "undefined is not a function" 这类难查的初始化顺序问题。
 *
 * 解决办法是**反转依赖方向**：本模块不 import http，只暴露一个
 * {@link setUnauthorizedHandler} 让上层（App.vue）注册回调。
 * 需要跳登录时自己主动 `emitUnauthorized()`，由注册方决定怎么做。
 */

const STORAGE_KEY = 'campus-activity-vue-user'
const TOKEN_KEY = 'campus-activity-vue-token'

function loadUser() {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch {
    // localStorage 里是坏数据（用户手改过、或旧版本格式残留）时清理掉，
    // 否则每次启动都会在 JSON.parse 这里抛异常，页面直接白屏。
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

const sessionState = reactive({
  user: loadUser(),
  // 单独存 token：这样即使某天 token 不再放在用户对象里，取凭证的逻辑也不用改。
  // 初始值优先读独立 key，读不到再回落到用户对象里的 token 字段（兼容既有数据）。
  token: localStorage.getItem(TOKEN_KEY) || loadUser()?.token || '',
})

/** 401 时的回调，由 App.vue 注册（用于弹登录框）。 */
let unauthorizedHandler = null

function persistToken(token) {
  sessionState.token = token || ''
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

function setUser(user) {
  sessionState.user = user
  localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
  // 登录响应里的 token 同时单独落一份，供 axios 请求拦截器读取
  persistToken(user?.token)
}

function clearUser() {
  sessionState.user = null
  localStorage.removeItem(STORAGE_KEY)
  persistToken('')
}

/**
 * 读取当前 token。axios 请求拦截器用这个把凭证放进请求头。
 *
 * @returns {string} token；未登录时返回空字符串
 */
export function getToken() {
  return sessionState.token || ''
}

/**
 * 注册"未授权"回调。
 *
 * @param {Function} handler 收到 401 时调用
 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

/**
 * 通知上层收到了 401。
 *
 * 这里**顺手清掉本地会话**：既然服务端说凭证无效（过期、被登出），
 * 本地再留着它只会让后续每个请求继续 401，不如清干净让用户重新登录。
 */
export function emitUnauthorized() {
  clearUser()
  unauthorizedHandler?.()
}

export function useSession() {
  return {
    sessionState,
    user: computed(() => sessionState.user),
    isLoggedIn: computed(() => Boolean(sessionState.user)),
    setUser,
    clearUser,
    getToken,
  }
}
