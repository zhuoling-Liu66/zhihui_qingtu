import http from './http'

export const authApi = {
  login(payload) {
    return http.post('/auth/login', payload)
  },
  register(payload) {
    return http.post('/auth/register', payload)
  },
  /**
   * 登出：把当前 token 加入服务端黑名单，使其在剩余有效期内立即失效。
   *
   * 为什么不能只清本地缓存：JWT 是无状态的，服务端签发后不保存任何东西，
   * 前端把 localStorage 里的 token 删掉，**服务端眼里那个 token 依然完全有效**
   * （本项目有效期 12 小时）。如果被截获仍能用满 12 小时。
   * 所以"退出登录"必须调这个接口，后端会按 `jti` 写一条
   * `auth:token:blacklist:{jti}`（TTL = 该 token 剩余寿命），拦截器每次校验时查一次。
   *
   * 该接口本身也要走拦截器，所以调用时必须带着 token（请求拦截器已自动注入）。
   */
  logout() {
    return http.post('/auth/logout')
  },
}

export const activityApi = {
  list(params) {
    return http.get('/activities', { params })
  },
  detail(activityId) {
    return http.get(`/activities/${activityId}`)
  },
  tags() {
    return http.get('/activities/tags')
  },
  /**
   * 热门活动榜（按累计报名人数降序）。
   *
   * 数据源是后端 Redis 的 ZSet `activity:hot:enroll`，只存 activityId + 分数，
   * 服务端会批量回表补全详情（不是 N+1 查询），返回结构与 `detail` 一致。
   *
   * @param {number} limit 返回条数，后端限定 1~50，默认 10
   */
  hot(limit = 10) {
    return http.get('/activities/hot', { params: { limit } })
  },
  /**
   * 查询活动剩余名额。
   *
   * 用于详情页展示"仅剩 N 个名额"的紧迫感提示。
   * 注意后端返回的 `remaining` 是**从数据库现算的权威值**，不是从 Redis 读的
   * （Redis 里那个库存值是为了高并发扣减，可能与真实值有短暂偏差）。
   *
   * @returns {Promise<{activityId:number,maxParticipant:number|null,currentParticipant:number,remaining:number|null,limited:boolean}>}
   *          `limited` 为 false 时 `remaining` 是 null，前端不应展示"剩余名额"。
   */
  stock(activityId) {
    return http.get(`/activities/${activityId}/stock`)
  },
  create(payload) {
    return http.post('/activities', payload)
  },
  update(activityId, payload) {
    return http.put(`/activities/${activityId}`, payload)
  },
  updateStatus(activityId, payload) {
    return http.patch(`/activities/${activityId}/status`, payload)
  },
  updateSignInSwitch(activityId, payload) {
    return http.patch(`/activities/${activityId}/sign-in-switch`, payload)
  },
  uploadCover(file) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post('/activities/covers', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
  },
  /**
   * 删除活动。
   * <p>
   * 已去掉 `operatorUserId` 参数：后端改为从 JWT 取当前登录身份。
   * 前端再传这个参数也不会被读取（后端 DTO/参数已删除），
   * 这是"身份只由服务端推导"的必然结果。
   */
  remove(activityId) {
    return http.delete(`/activities/${activityId}`)
  },
}

export const enrollApi = {
  /**
   * 报名活动。
   * <p>
   * 请求体里**不再需要 userId** —— 报名人由后端从 JWT 取。
   * 调用方只需传 `{ activityId }`。
   */
  create(payload) {
    return http.post('/enrollments', payload)
  },
  list(params) {
    return http.get('/enrollments', { params })
  },
  /** 取消报名：后端按当前登录用户校验归属，不需要（也无法）指定 userId。 */
  cancel(enrollId) {
    return http.delete(`/enrollments/${enrollId}`)
  },
  /** 签到：同上。 */
  signIn(enrollId) {
    return http.patch(`/enrollments/${enrollId}/sign-in`)
  },
  feedback(enrollId, payload) {
    return http.patch(`/enrollments/${enrollId}/feedback`, payload)
  },
}

export const userApi = {
  profile(userId) {
    return http.get(`/users/${encodeURIComponent(userId)}`)
  },
  updateProfile(userId, payload) {
    return http.put(`/users/${encodeURIComponent(userId)}`, payload)
  },
  creditOverview(userId) {
    return http.get(`/users/${encodeURIComponent(userId)}/credit-overview`)
  },
  histories(userId) {
    return http.get(`/users/${encodeURIComponent(userId)}/histories`)
  },
  updateInterestTags(userId, payload) {
    return http.put(`/users/${encodeURIComponent(userId)}/interest-tags`, payload)
  },
}

export const teamApi = {
  list(params) {
    return http.get('/teams', { params })
  },
  detail(teamId) {
    return http.get(`/teams/${teamId}`)
  },
  create(payload) {
    return http.post('/teams', payload)
  },
  join(teamId, payload) {
    return http.post(`/teams/${teamId}/join`, payload)
  },
  reviewMember(teamId, memberId, payload) {
    return http.patch(`/teams/${teamId}/members/${memberId}/review`, payload)
  },
  exit(teamId, memberId, payload) {
    return http.patch(`/teams/${teamId}/members/${memberId}/exit`, payload)
  },
  dismiss(teamId, payload) {
    return http.patch(`/teams/${teamId}/dismiss`, payload)
  },
}

export const creditApi = {
  list(params) {
    return http.get('/credits', { params })
  },
  /**
   * 学分统计。
   * <p>
   * 已去掉 `userId` 参数：后端强制只统计**当前登录用户**的学分。
   * 这是为了堵住"查任意同学学分明细"的信息泄露。
   */
  statistics() {
    return http.get('/credits/statistics')
  },
  /** 审核学分：审核人由后端从 JWT 取，请求体里不需要（也不应传）`verifyUserId`。 */
  review(recordId, payload) {
    return http.patch(`/credits/${recordId}/review`, payload)
  },
}
