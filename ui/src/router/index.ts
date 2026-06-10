import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getGroupWorklist } from '@/api/worklist'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/auth/callback',
      name: 'auth-callback',
      component: () => import('@/views/AuthCallbackView.vue'),
      meta: { public: true },
    },
    {
      path: '/auth/silent-renew',
      name: 'auth-silent-renew',
      component: () => import('@/views/AuthSilentRenewView.vue'),
      meta: { public: true },
    },
    {
      path: '/submissions',
      name: 'my-submissions',
      component: () => import('@/views/MySubmissionsView.vue'),
    },
    {
      path: '/submit/:definitionId',
      name: 'submit-flow',
      component: () => import('@/views/SubmitFlowView.vue'),
    },
    {
      path: '/flows/:flowId',
      name: 'flow-detail',
      component: () => import('@/views/FlowDetailView.vue'),
    },
    {
      path: '/worklist',
      name: 'worklist',
      component: () => import('@/views/WorklistView.vue'),
    },
    {
      path: '/tasks/:taskId',
      name: 'task-detail',
      component: () => import('@/views/TaskDetailView.vue'),
    },
    {
      path: '/',
      redirect: () => ({ name: 'my-submissions' }),
    },
  ],
})

router.beforeEach(async (to: RouteLocationNormalized) => {
  if (to.meta.public) return true

  const auth = useAuthStore()
  await auth.loadUser()

  if (!auth.isAuthenticated) {
    await auth.signIn(to.fullPath)
    return false
  }

  // Smart landing: redirect to worklist if user has pending tasks (FR-016)
  if (to.path === '/' || to.name === 'my-submissions') {
    try {
      const tasks = await getGroupWorklist()
      if (tasks.length > 0) {
        return { name: 'worklist' }
      }
    } catch {
      // If worklist call fails, proceed to submissions as default
    }
  }

  return true
})

export { router }
export default router
