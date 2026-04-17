import { activityStatusMap, activityTypeMap, creditTypeMap } from '@/constants/ui'

export function formatFallback(value, fallback = '未设置') {
  return value === null || value === undefined || value === '' ? fallback : value
}

export function getActivityStatusCode(activity) {
  return activity?.displayStatus ?? activity?.activityStatus
}

export function formatActivityType(value) {
  return activityTypeMap[value] || `类型 ${value}`
}

export function formatActivityStatus(value) {
  return activityStatusMap[value] || `状态 ${value}`
}

export function formatActivityStatusByActivity(activity) {
  return formatActivityStatus(getActivityStatusCode(activity))
}

export function formatCreditType(value) {
  return creditTypeMap[value] || '未设置'
}

export function formatMetric(activity) {
  if (activity.activityType === 1) {
    return `学分 ${formatFallback(activity.activityCredit, 0)} · ${formatCreditType(activity.creditType)}`
  }

  if (activity.activityType === 2) {
    return `志愿服务 ${formatFallback(activity.volunteerHours, 0)} 小时`
  }

  return '校园参与 / 组队互动'
}

export function pickCardTone(activityType) {
  const palette = {
    1: 'amber',
    2: 'cyan',
    3: 'teal',
    4: 'rose',
  }
  return palette[activityType] || 'amber'
}
