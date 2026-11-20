import http from './http'

/**
 * 管理端接口。
 *
 * 权限由后端 AdminGuard 回查数据库角色：
 * - 管理员：概览、发布活动（免审核）、活动审核、参与人员管理、账号管理；
 * - 运营人员：学分活动发布/编辑、参与人员管理（仅自己发布的活动）。
 */
export const adminApi = {
  login(payload) {
    return http.post('/admin/auth/login', payload)
  },

  logout() {
    return http.post('/auth/logout')
  },

  dashboard() {
    return http.get('/admin/dashboard')
  },

  // ── 活动发布 / 审核 / 管理（管理员）─────────────────────
  createActivity(payload) {
    return http.post('/admin/activities', payload)
  },
  activities(params) {
    return http.get('/admin/activities', { params })
  },
  activityDetail(activityId) {
    return http.get(`/activities/${activityId}`)
  },
  reviewActivity(activityId, payload) {
    return http.patch(`/admin/activities/${activityId}/review`, payload)
  },
  updateActivityStatus(activityId, payload) {
    return http.patch(`/admin/activities/${activityId}/status`, payload)
  },
  updateActivitySignInSwitch(activityId, payload) {
    return http.patch(`/activities/${activityId}/sign-in-switch`, payload)
  },
  removeActivity(activityId) {
    return http.delete(`/admin/activities/${activityId}`)
  },

  // ── 参与人员与发放（发布者 / 管理员）────────────────────
  activityParticipants(activityId, params) {
    return http.get(`/enrollments/activity/${activityId}`, { params })
  },
  updateAttendance(enrollId, payload) {
    return http.patch(`/enrollments/${enrollId}/attendance`, payload)
  },
  updateAward(enrollId, payload) {
    return http.patch(`/enrollments/${enrollId}/award`, payload)
  },
  confirmAwards(activityId) {
    return http.patch(`/enrollments/activity/${activityId}/awards/confirm`)
  },
  analyzeAwardImport(activityId, formData) {
    return http.post(`/admin/activities/${activityId}/award-import/analyze`, formData)
  },
  applyAwardImport(activityId, payload) {
    return http.post(`/admin/activities/${activityId}/award-import/apply`, payload)
  },

  // ── 运营人员活动发布（学分/志愿，需审核）────────────────
  myActivities(params) {
    return http.get('/admin/credit-activities/mine', { params })
  },
  createStaffActivity(payload) {
    return http.post('/admin/credit-activities', payload)
  },
  updateStaffActivity(activityId, payload) {
    return http.put(`/admin/credit-activities/${activityId}`, payload)
  },

  // ── 账号管理（管理员）────────────────────────────────────
  users(params) {
    return http.get('/admin/users', { params })
  },
  staffAccounts(params) {
    return http.get('/admin/users', { params: { ...params, userRole: 4 } })
  },
  createStaff(payload) {
    return http.post('/admin/users/staff', payload)
  },
  resetStaffPassword(studentNo, payload) {
    return http.patch(`/admin/users/${encodeURIComponent(studentNo)}/password`, payload)
  },
  updateUserRole(studentNo, payload) {
    return http.patch(`/admin/users/${encodeURIComponent(studentNo)}/role`, payload)
  },
  // 禁用/启用运营账号：accountStatus 0 正常 / 1 禁用，禁用立即生效（含已登录令牌）
  updateAccountStatus(studentNo, payload) {
    return http.patch(`/admin/users/${encodeURIComponent(studentNo)}/status`, payload)
  },
  // 注销运营账号（软删除），注销后不可登录，历史数据保留
  deleteStaffAccount(studentNo) {
    return http.delete(`/admin/users/${encodeURIComponent(studentNo)}`)
  },

  // ── 公共资源 ─────────────────────────────────────────────
  tags() {
    return http.get('/activities/tags')
  },
  uploadCover(file) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post('/activities/covers', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
