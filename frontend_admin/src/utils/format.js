const activityTypeMap = {
  1: '第二课堂学分活动',
  2: '志愿服务活动',
  3: '运动健身活动',
  4: '兴趣团建活动',
}

const activityStatusMap = {
  1: '未结束',
  3: '已结束',
  4: '已取消',
}

const reviewStatusMap = {
  0: '待审核',
  1: '审核通过',
  2: '审核驳回',
}

const creditTypeMap = {
  1: '创新创业',
  2: '智育',
  3: '德育',
  4: '体育',
  5: '美育',
  6: '劳育',
}

const roleMap = {
  1: '学生',
  2: '管理员',
  3: '老师',
  4: '运营人员',
}

export function formatActivityType(value) {
  return activityTypeMap[value] || `类型 ${value}`
}

export function formatActivityStatus(value) {
  return activityStatusMap[value] || `状态 ${value}`
}

export function formatReviewStatus(value) {
  return reviewStatusMap[value] ?? '待审核'
}

export function formatCreditType(value) {
  return creditTypeMap[value] || '未设置'
}

export function formatRole(value) {
  return roleMap[value] || '未知角色'
}

export function formatDateTime(value, fallback = '—') {
  if (!value) return fallback
  return String(value).replace('T', ' ').slice(0, 16)
}

export function formatMonthDayTime(value, fallback = '—') {
  if (!value) return fallback
  return String(value).replace('T', ' ').slice(5, 16)
}

export function reviewTagType(value) {
  if (Number(value) === 1) return 'success'
  if (Number(value) === 2) return 'danger'
  return 'warning'
}

export function activityTagType(value) {
  if (Number(value) === 4) return 'info'
  if (Number(value) === 3) return 'danger'
  return 'success'
}
