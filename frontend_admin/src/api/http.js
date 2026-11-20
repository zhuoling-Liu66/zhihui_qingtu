import axios from 'axios'
import { ElMessage } from 'element-plus'
import { emitUnauthorized, getToken } from '@/composables/useAdminSession'

/**
 * 管理端请求实例。
 *
 * 与客户端使用同一后端，但 token 来自独立的管理端会话；
 * 401 时清空管理端会话并回到登录页。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 12000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload?.code === 0) {
      return payload.data
    }
    const message = payload?.message || '请求失败'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  (error) => {
    if (error.response?.status === 401) {
      ElMessage.warning(error.response?.data?.message || '登录已失效，请重新登录')
      emitUnauthorized()
      return Promise.reject(error)
    }
    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default http
