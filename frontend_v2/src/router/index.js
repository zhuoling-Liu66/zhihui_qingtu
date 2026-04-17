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
    path: '/credit-review',
    name: 'credit-review',
    component: () => import('@/views/CreditReviewView.vue'),
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
]

export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})
