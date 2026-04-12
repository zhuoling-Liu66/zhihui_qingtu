import { sectionConfigs } from '@/constants/ui'

export function getSectionConfig(slug) {
  return sectionConfigs[slug] || sectionConfigs['second-class']
}

export function filterActivitiesBySection(activities, slug) {
  const config = getSectionConfig(slug)
  return (activities || []).filter((activity) => config.match(activity))
}

export function matchKeyword(activity, keyword) {
  const normalized = keyword?.trim().toLowerCase()
  if (!normalized) {
    return true
  }

  return Boolean(
    activity.activityTitle?.toLowerCase().includes(normalized) ||
      activity.activityDesc?.toLowerCase().includes(normalized) ||
      activity.activityLocation?.toLowerCase().includes(normalized) ||
      activity.tags?.some((tag) => tag.tagName?.toLowerCase().includes(normalized)),
  )
}
