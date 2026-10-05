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
 * 请求拦截器：把 JWT 放进标准的 Authorization 头。
 *
 * 后端已从"手写拦截器"迁移到 **Spring Security 的 OAuth2 Resource Server**，
 * 它按标准约定从 `Authorization: Bearer <token>` 取令牌。
 *
 * 历史背景（前端这里也踩过一并修复）：
 *   1. 最初本实例**只有响应拦截器、没有请求拦截器**，登录拿到的 token
 *      一直躺在 localStorage 里没人用，导致除登录外所有接口 401，
 *      表现为"登录成功但页面全空"（登录本身不需要 token，所以看不出问题）。
 *   2. 后来补了拦截器，但用的是项目自定义的裸 `token` 头；
 *      换成 Spring Security 后这个头不再被默认读取，会造成**所有带合法 token
 *      的请求都 401**（令牌压根没被解析，认证上下文为空）。
 *      后端为此实现了 `CompositeBearerTokenResolver` 同时兼容两种头，
 *      但前端统一改用标准格式，既符合规范，也和 Swagger/Postman/网关等工具一致。
 */
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    // 用 AxiosHeaders 的 .set() 而不是 `config.headers.Authorization = ...`：
    // axios 1.x 把 config.headers 规范化成 AxiosHeaders 实例，.set() 是保证生效的写法
    // （直接属性赋值在部分版本/适配器下不可靠，本项目实测踩过）。
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

/**
 * 判断一个响应是不是"未授权（401）"。
 *
 * 迁移到 Spring Security 后，401 和 403 由框架区分开了，含义不同：
 *   · **401** = 没登录 / 令牌无效或过期 → 应引导用户重新登录
 *   · **403** = 已登录但没有权限   → 重新登录也没用，只需提示
 * 所以这里要把两者分开处理，不能都当成"登录失效"。
 *
 * 判据要同时看 HTTP 状态码和业务 code：
 * 框架写的 401/403 带 HTTP 状态码；而项目里其他业务错误是
 * HTTP 200 + 非 0 的 body.code。只看一边都会漏。
 */
function isUnauthorized(error) {
  return error?.response?.status === 401
    || error?.response?.data?.code === 4010
    || error?.response?.data?.code === 401
}

/** 已登录但无权限（403）：只提示，不弹登录框。 */
function isForbidden(error) {
  return error?.response?.status === 403
    || error?.response?.data?.code === 4030
    || error?.response?.data?.code === 403
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
      // 令牌无效/过期：清掉本地失效凭证并弹登录框。
      // emitUnauthorized 会清 session（token 有效期 12 小时，过期后必然走到这里），
      // 并由 App.vue 注册的回调弹出登录框。
      const message = error.response?.data?.message || '登录已失效，请重新登录'
      ElMessage.warning(message)
      emitUnauthorized()
      return Promise.reject(error)
    }

    if (isForbidden(error)) {
      // 权限不足：不动 session（用户还是登录状态），只给提示。
      // 如果这里也清 session，用户会被无辜踢下线，体验很差。
      ElMessage.error(error.response?.data?.message || '没有权限执行该操作')
      return Promise.reject(error)
    }

    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default http
