import { createRouter, createWebHistory } from 'vue-router'
import { useAdminSession } from '@/composables/useAdminSession'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/views/AdminLayout.vue'),
    meta: { requiresPortal: true },
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '数据概览', roles: [2] },
      },
      {
        path: 'review',
        name: 'activity-review',
        component: () => import('@/views/ActivityReviewView.vue'),
        meta: { title: '活动审核', roles: [2] },
      },
      {
        path: 'accounts',
        name: 'accounts',
        component: () => import('@/views/AccountsView.vue'),
        meta: { title: '账号管理', roles: [2] },
      },
      {
        path: 'activities/new',
        name: 'admin-activity-create',
        component: () => import('@/views/StaffCreditActivityFormView.vue'),
        meta: { title: '发布活动', roles: [2] },
      },
      {
        path: 'credit-activities',
        name: 'credit-activities',
        component: () => import('@/views/StaffCreditActivitiesView.vue'),
        meta: { title: '活动管理', roles: [4] },
      },
      {
        path: 'credit-activities/new',
        name: 'credit-activity-create',
        component: () => import('@/views/StaffCreditActivityFormView.vue'),
        meta: { title: '发布活动', roles: [4] },
      },
      {
        path: 'credit-activities/:id/edit',
        name: 'credit-activity-edit',
        component: () => import('@/views/StaffCreditActivityFormView.vue'),
        meta: { title: '编辑活动', roles: [4] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

const PORTAL_ROLES = [2, 4]

function homeFor(role) {
  return Number(role) === 4
    ? { name: 'credit-activities' }
    : { name: 'dashboard' }
}

router.beforeEach((to) => {
  const { isLoggedIn, role, clearUser } = useAdminSession()

  // 本地缓存可能残留学生/老师等非管理端角色。若不清理，守卫会在
  // dashboard 与目标页之间来回重定向，最终表现为整页白屏。
  if (isLoggedIn.value && !PORTAL_ROLES.includes(role.value)) {
    clearUser()
    if (to.meta.public) {
      return true
    }
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.public) {
    if (isLoggedIn.value && to.name === 'login') {
      return homeFor(role.value)
    }
    return true
  }

  if (to.matched.some((record) => record.meta.requiresPortal) && !isLoggedIn.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const requiredRoles = to.meta.roles
  if (requiredRoles && !requiredRoles.includes(role.value)) {
    return homeFor(role.value)
  }

  return true
})

export default router
