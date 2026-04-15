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
  remove(activityId, operatorUserId) {
    return http.delete(`/activities/${activityId}`, {
      params: { operatorUserId },
    })
  },
}

export const enrollApi = {
  create(payload) {
    return http.post('/enrollments', payload)
  },
  list(params) {
    return http.get('/enrollments', { params })
  },
  cancel(enrollId, userId) {
    return http.delete(`/enrollments/${enrollId}`, {
      params: { userId },
    })
  },
  signIn(enrollId, userId) {
    return http.patch(`/enrollments/${enrollId}/sign-in`, null, {
      params: { userId },
    })
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
  statistics(userId) {
    return http.get('/credits/statistics', { params: { userId } })
  },
  review(recordId, payload) {
    return http.patch(`/credits/${recordId}/review`, payload)
  },
}

export const recommendationApi = {
  query(payload) {
    return http.post('/recommendations/query', payload)
  },
  log(payload) {
    return http.post('/recommendations/logs', payload)
  },
}
