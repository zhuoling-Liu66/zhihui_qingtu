import http from './http'

export const authApi = {
  login(payload) {
    return http.post('/auth/login', payload)
  },
  register(payload) {
    return http.post('/auth/register', payload)
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
   * 已去掉 `operatorUserId`：后端改为从 JWT 取当前登录身份，并自行判定
   * "是不是这个活动的发布者 / 管理员"（原来自报身份可以伪造）。
   */
  remove(activityId) {
    return http.delete(`/activities/${activityId}`)
  },
}

export const enrollApi = {
  /**
   * 报名活动。
   * <p>
   * 请求体里**不再需要 userId** —— 报名人由后端从 JWT 取，
   * 调用方只需传 `{ activityId }`。
   */
  create(payload) {
    return http.post('/enrollments', payload)
  },
  /**
   * 查询报名记录。
   * <p>
   * 后端 `EnrollmentQueryRequest` 已**删除 userId 字段**，因为"按学号查报名记录"
   * 等于可以查任意用户的活动参与情况（水平越权）；现在服务端强制只返回当前登录用户的记录。
   */
  list(params) {
    return http.get('/enrollments', { params })
  },
  /** 取消报名：后端按当前登录用户校验归属，不需要（也无法）指定 userId。 */
  cancel(enrollId) {
    return http.delete(`/enrollments/${enrollId}`)
  },
  /** 签到：同上，身份由后端从 JWT 推导。 */
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
  /**
   * 队伍列表。
   * <p>
   * `leaderUserId` / `memberUserId` 是**筛选条件**（按队长/成员查队伍），已保留；
   * 后端 `TeamQueryRequest` 里它们仍在，可见范围由服务端按角色决定。
   */
  list(params) {
    return http.get('/teams', { params })
  },
  detail(teamId) {
    return http.get(`/teams/${teamId}`)
  },
  /** 创建队伍：请求体里不再需要 `leaderUserId`，队长 = 当前登录用户（后端从 JWT 取）。 */
  create(payload) {
    return http.post('/teams', payload)
  },
  /** 申请入队：无请求体，申请人 = 当前登录用户（后端从令牌解析）。 */
  join(teamId) {
    return http.post(`/teams/${teamId}/join`)
  },
  /** 审批成员：请求体里不再需要 `leaderUserId`，后端自行判定"你是不是该队队长"。 */
  reviewMember(teamId, memberId, payload) {
    return http.patch(`/teams/${teamId}/members/${memberId}/review`, payload)
  },
  /** 退出/移出成员：无请求体，后端自行判定"是你自己还是队长"。 */
  exit(teamId, memberId) {
    return http.patch(`/teams/${teamId}/members/${memberId}/exit`)
  },
  /** 解散队伍：无请求体，仅队长可解散，由后端判定。 */
  dismiss(teamId) {
    return http.patch(`/teams/${teamId}/dismiss`)
  },
}

export const creditApi = {
  list(params) {
    return http.get('/credits', { params })
  },
  /**
   * 学分统计。
   * <p>
   * 已去掉 `userId` 参数（与 v2 保持一致）：后端强制只统计**当前登录用户**的学分，
   * 避免通过"传别人学号"读到任意同学的学分明细。
   * 注：后端 `GET /credits/statistics` 目前仍保留一个**仅管理员/老师生效**的可选
   * `userId`（目标用户筛选，不是身份），v1 没有任何调用点，因此这里不暴露它。
   */
  statistics() {
    return http.get('/credits/statistics')
  },
  /** 审核学分：审核人由后端从 JWT 取，请求体里不需要（也不应传）`verifyUserId`。 */
  review(recordId, payload) {
    return http.patch(`/credits/${recordId}/review`, payload)
  },
}
