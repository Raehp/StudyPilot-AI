import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录', public: true }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/register/RegisterView.vue'),
      meta: { title: '注册', public: true }
    },
    {
      path: '/glass-showcase',
      name: 'glass-showcase',
      component: () => import('@/components/GlassShowcase.vue'),
      meta: { title: 'Liquid Glass 演示', public: true }
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '工作台' }
        },
        {
          path: 'reading',
          name: 'reading',
          component: () => import('@/views/reading/PaperReadingView.vue'),
          meta: { title: '论文阅读' }
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/knowledge/KnowledgeView.vue'),
          meta: { title: '科研知识库' }
        },
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/chat/ChatView.vue'),
          meta: { title: 'AI 智能问答' }
        },
        {
          path: 'plan',
          name: 'plan',
          component: () => import('@/views/plan/PlanView.vue'),
          meta: { title: '学习计划' }
        },
        {
          path: 'quiz',
          name: 'quiz',
          component: () => import('@/views/quiz/QuizView.vue'),
          meta: { title: '智能出题' }
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/settings/SettingsView.vue'),
          meta: { title: '个人设置' }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

router.beforeEach((to) => {
  const token = getToken()
  if (!to.meta.public && !token) {
    return { path: '/login' }
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    return { path: '/' }
  }
  document.title = to.meta.title ? `${to.meta.title} - StudyPilot AI` : 'StudyPilot AI'
  return true
})

export default router