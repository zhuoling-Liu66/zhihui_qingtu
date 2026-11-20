import {
  activityReviewStatusMap,
  activityStatusMap,
  activityTypeMap,
  creditTypeMap,
  enrollStatusMap,
  publisherRecordStatusMap,
  publisherRecordTypeMap,
  publisherStatusMap,
  userRoleMap,
  verifyStatusMap,
} from '@/constants/ui'

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

export function formatUserRole(value) {
  return userRoleMap[value] || '未知角色'
}

export function formatActivityReviewStatus(value) {
  return activityReviewStatusMap[value] ?? '待审核'
}

export function formatPublisherStatus(value) {
  return publisherStatusMap[value] || '未开通'
}

export function formatPublisherRecordType(value) {
  return publisherRecordTypeMap[value] || '权限调整'
}

export function formatPublisherRecordStatus(value) {
  return publisherRecordStatusMap[value] || '未知状态'
}

export function formatEnrollStatus(value) {
  return enrollStatusMap[value] || `状态 ${value}`
}

export function formatVerifyStatus(value) {
  return verifyStatusMap[value] ?? '未知状态'
}

/* 状态药丸配色：待办=黄、成功=绿、驳回=红、其余灰 */
export function statusTone(value, successValues = [], dangerValues = [], pendingValues = []) {
  if (successValues.includes(Number(value))) return 'success'
  if (dangerValues.includes(Number(value))) return 'danger'
  if (pendingValues.includes(Number(value))) return 'pending'
  return 'plain'
}

/* 数据库时间多为 "2026-09-24T00:00:00" ISO 字符串，直接展示观感差 */
export function formatDateTime(value, fallback = '待定时间') {
  if (!value) return fallback
  return String(value).replace('T', ' ').slice(0, 16)
}

/* 卡片上的短时间：2026-12-01T10:00:00 -> 12月01日 10:00 */
export function formatShortDateTime(value, fallback = '时间待定') {
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return fallback
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getMonth() + 1}月${date.getDate()}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/* 封面兜底和类别标记用的短类别 */
export function formatActivityTypeShort(value) {
  return (
    {
      1: '学分',
      2: '志愿',
      3: '运动',
      4: '团建',
    }[value] || '活动'
  )
}

/* 学分印章：有学分/时长就写数字，没有就退到类别 */
export function formatStamp(activity) {
  if (!activity) return ''
  if (activity.activityType === 1) {
    const credit = Number(activity.activityCredit)
    return credit > 0 ? `${credit} 学分` : '学分活动'
  }
  if (activity.activityType === 2) {
    const hours = Number(activity.volunteerHours)
    return hours > 0 ? `${hours} 小时` : '志愿服务'
  }
  return formatActivityTypeShort(activity.activityType)
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
