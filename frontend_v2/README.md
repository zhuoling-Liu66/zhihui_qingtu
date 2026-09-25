# 智慧青途 · 前端（frontend_v2）

Vue 3 + Vite + Element Plus + Axios。这是**新版前端**（仓库里还有一份 `frontend/` 是旧版）。

---

## 1. 跑起来

**前置**：后端已启动在 `8080`（`./mvnw spring-boot:run`），且 MySQL + Redis 都在跑。

```bash
cd frontend_v2
npm install
npm run dev
```

打开 http://localhost:5173 ，
用测试账号登录（`docs/api-test/seed-users.mjs` 造的账号，如 `T0001` / `test123456`）。

> ⚠️ **必须登录后才能看到内容**。后端 `TokenInterceptor` 会拦住
> `/api/activities`、`/api/activities/tags` 等接口，未登录一律 401。
> 现在未登录访问会自动弹出登录框（而不是报一堆错）。

其他命令：

```bash
npm run build     # 生产构建
npm run preview   # 预览构建产物
```

---

## 2. 目录结构

```
src/
├── api/
│   ├── http.js          ★ axios 实例：请求拦截器（注入 token）+ 响应拦截器（统一拆包/401 处理）
│   └── platform.js        各业务接口定义，按模块分组（auth/activity/enroll/user/team/credit）
├── composables/
│   └── useSession.js    ★ 登录会话 + token 存取
├── views/                页面（首页/详情/发布/我的*/个人中心…）
├── components/           可复用组件（活动卡片、封面图、侧边栏+顶栏、登录弹窗）
├── utils/                格式化函数（活动类型/状态/学分/志愿时长）
└── constants/ui.js       枚举映射与导航配置
```

---

## 3. 两个容易踩的坑（都已修好，改代码时注意别改回去）

### 3.1 请求必须带 `token` 请求头

后端要求**除登录、注册、`/uploads/**`、接口文档外，所有请求都带 `token` 请求头**。
请求头的名字是 **`token`**，不是标准的 `Authorization: Bearer xxx`。

这件事由 `api/http.js` 的**请求拦截器**统一处理：

```js
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('token', token)   // 用 AxiosHeaders.set()，不要直接属性赋值
  }
  return config
})
```

**曾经的问题**：这里原来**没有请求拦截器**，登录拿到的 token 一直躺在 localStorage 里没人用，
导致除登录/注册外所有接口都是 401、页面全空。而登录本身不需要 token，
所以现象是"登录成功了，但什么都没加载出来"，很容易误判成后端问题。

### 3.2 Vite 代理端口必须和后端一致

`vite.config.js` 里的 `target` 必须等于 `application.properties` 的 `server.port`（**8080**）。
曾经写成 `8082`，表现为所有接口 `ECONNREFUSED`。

`/uploads` 也配了代理：封面图走 `<img src="/uploads/...">`，不代理的话图片全裂。
该路径后端已放行，**不需要 token**。

---

## 4. 认证流程与 JWT 的三个要点

```
登录 → LoginResponse 里带 token → useSession.setUser() 存起来（localStorage）
     → 之后每个请求由请求拦截器自动带 token
     → token 有效期 12 小时（后端 jwt.expiration）
     → 过期后第一个请求返回 401 → 响应拦截器清 session 并弹出登录框
```

**要点一：登出必须调后端接口。**
JWT 是**无状态**的：服务端签发后不保存任何东西，所以"前端删掉 localStorage 里的 token"
并不会让服务端那串 token 失效 —— 它依然能通过签名校验，直到 12 小时后自然过期。
所以 `AppHeader` 的"退出登录"会调 `POST /api/auth/logout`，
后端把该 token 的 `jti` 写进 Redis 黑名单（TTL = 剩余寿命），拦截器每次校验时查一次。

**顺序不能颠倒**：必须先调登出接口、再 `clearUser()`。
反过来的话请求拦截器已经拿不到 token，登出请求自己就 401，黑名单永远写不进去。

**要点二：axios 请求拦截器是 LIFO（后注册的先执行）。**
如果以后要加"探针"拦截器调试请求头，注意你注册的拦截器会**排在已有拦截器前面**执行，
读到的 config 可能还没被 token 拦截器处理过。想验证"请求头里到底有没有 token"，
更可靠的办法是见 §5 的脚本，或者直接看浏览器 DevTools 的 Network 面板。

**要点三：`config.headers` 是 `AxiosHeaders` 实例。**
用 `config.headers.set('token', token)`，不要用 `config.headers.token = token`。

---

## 5. 验证脚本（不需要浏览器）

`scripts/` 下有两个脚本，用 Node 直接加载 `src/` 的**真实源码**来验证纯逻辑，
避免靠人肉点页面。

```bash
# 1. token 真的发出去了吗？（起一个本地 echo 服务接收，不依赖后端）
node --import ./scripts/node-vite-alias-hook.mjs scripts/verify-token-injection.mjs

# 2. 打真实后端跑一遍完整流程（需先启动后端）
node --import ./scripts/node-vite-alias-hook.mjs scripts/verify-api-e2e.mjs
```

`node-vite-alias-hook.mjs` 的作用是让 Node 认识 Vite 的 `@/` 别名、
补全省略的扩展名、并 stub 掉 `element-plus`。
这些脚本**不参与打包**，`npm run build` 不受影响。

---

## 6. 和后端的接口对应关系

`src/api/platform.js` 里 34 个方法，一一对应后端 6 个 Controller 的 34 个接口。
其中 3 个是 Redis 改造新增的：

| 方法 | 后端接口 | 用在哪 |
|---|---|---|
| `authApi.logout()` | `POST /api/auth/logout` | 侧边栏"退出登录" |
| `activityApi.hot(limit)` | `GET /api/activities/hot` | 首页右栏"本周热门"（数据来自 Redis ZSet） |
| `activityApi.stock(id)` | `GET /api/activities/{id}/stock` | 活动详情"仅剩 N 个名额" |

> `stock` 返回的 `remaining` 是后端**从数据库现算的权威值**，不是从 Redis 读的
> （Redis 里的库存用于高并发扣减，可能有短暂偏差）。
> 活动不限人数时 `limited=false` 且 `remaining=null`，前端此时不展示名额提示。

**接口清单与字段以 `http://localhost:8080/swagger-ui.html` 为准。**

---

## 7. 后端需要重启吗？

改前端**不需要**。但注意：

- `vite.config.js`（代理配置）改动后**需要重启 `npm run dev`**，Vite 不会热更新自身配置。
- 后端的 `TokenInterceptor` 放行名单、`application.properties` 等改动需要重启后端。
