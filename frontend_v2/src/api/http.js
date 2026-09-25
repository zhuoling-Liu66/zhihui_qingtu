import axios from 'axios'
import { ElMessage } from 'element-plus'
import { emitUnauthorized, getToken } from '@/composables/useSession'

/**
 * 统一的 axios 实例。
 *
 * baseURL 是 `/api`，配合 `vite.config.js` 里的代理转发到后端 8080。
 * 所以各 api 模块里写的是 `/activities`、`/auth/login`（**不带** `/api` 前缀）。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 12000,
})

/**
 * 请求拦截器：注入 token 请求头。
 *
 * 这是**本文件最关键的一段**。后端 `TokenInterceptor` 要求除
 * `/login`、`/register`、`/uploads/**`、接口文档外，**所有请求都必须带 `token` 请求头**，
 * 否则直接返回 `401 {"code":4010,"message":"未登录，请先登录"}`。
 *
 * 在此之前本实例只有响应拦截器、没有请求拦截器，导致登录后拿到的 token
 * 一直躺在 localStorage 里没被用上 —— 表现为"登录成功，但首页/详情/列表全是空的"。
 * 而登录接口本身不需要 token，所以登录功能正常，问题看起来像"登录了但没生效"。
 *
 * 注意请求头的名字是 **`token`**（不是标准的 `Authorization: Bearer xxx`），
 * 这是本项目后端的约定，见 `interceptor/TokenInterceptor.java` 里的 `request.getHeader("token")`。
 */
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    // 用 AxiosHeaders 的 .set() 而不是 `config.headers.token = token`：
    // axios 1.x 把 config.headers 规范化成 AxiosHeaders 实例，.set() 是它保证生效的写法，
    // 这也是官方文档和社区一致推荐的方式（属性赋值在部分版本/部分适配器下不可靠）。
    config.headers.set('token', token)
  }
  return config
})

/**
 * 判断一个响应是不是"未授权"。
 *
 * 要同时看两个地方，因为项目里未授权有两种返回形态：
 * 1. **拦截器**直接写的：HTTP 状态码是 401，响应体是 `{code:4010,...}`；
 * 2. 其他业务错误：HTTP 状态码 200，业务 `code` 非 0。
 *
 * 只判断 `error.response.status === 401` 会漏掉第 2 种；
 * 只判断业务 code 又会把网络层的 401 当成普通异常。
 */
function isUnauthorized(error) {
  return error?.response?.status === 401
    || error?.response?.data?.code === 4010
    || error?.response?.data?.code === 401
}

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload?.code === 0) {
      // 后端统一返回 ApiResponse{code,message,data}，这里直接把 data 交给调用方，
      // 于是业务代码写的是 `const page = await activityApi.list(...)`，不用层层解包。
      return payload.data
    }

    const message = payload?.message || '请求失败'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  (error) => {
    if (isUnauthorized(error)) {
      // 401 单独处理：不弹"网络异常"这种误导性提示，而是提示重新登录。
      // emitUnauthorized 会清掉本地失效凭证（token 有效期 12 小时，过期后必然走到这里），
      // 并由 App.vue 注册的回调弹出登录框。
      const message = error.response?.data?.message || '登录已失效，请重新登录'
      ElMessage.warning(message)
      emitUnauthorized()
      return Promise.reject(error)
    }

    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default http
