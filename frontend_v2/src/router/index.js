import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
  },
  {
    path: '/section/:slug',
    name: 'section',
    component: () => import('@/views/SectionView.vue'),
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('@/views/SearchView.vue'),
  },
  {
    path: '/publish',
    name: 'publish',
    component: () => import('@/views/PublishView.vue'),
  },
  {
    path: '/my-published',
    name: 'my-published',
    component: () => import('@/views/MyPublishedActivitiesView.vue'),
  },
  {
    path: '/my-joined',
    name: 'my-joined',
    component: () => import('@/views/MyJoinedActivitiesView.vue'),
  },
  {
    path: '/my-teams',
    name: 'my-teams',
    component: () => import('@/views/MyTeamsView.vue'),
  },
  {
    path: '/my-published/:id/participants',
    name: 'activity-participants',
    component: () => import('@/views/ActivityParticipantsView.vue'),
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
  },
  {
    path: '/activities/:id',
    name: 'activity-detail',
    component: () => import('@/views/ActivityDetailView.vue'),
  },
  {
    path: '/teams/:id',
    name: 'team-detail',
    component: () => import('@/views/TeamDetailView.vue'),
  },
  {
    // 后台已拆分到独立的 frontend_admin，旧地址统一回首页
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

export default router
