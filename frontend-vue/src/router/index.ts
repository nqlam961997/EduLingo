import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import ChatView from '../views/ChatView.vue'
import PictureView from '../views/PictureView.vue'
import DashboardView from '../views/DashboardView.vue'
import AssessmentView from '../views/AssessmentView.vue'
import VocabularyView from '../views/VocabularyView.vue'
import VocabularyLevelView from '../views/VocabularyLevelView.vue'
import VocabularySetView from '../views/VocabularySetView.vue'
import VocabularyLearnView from '../views/VocabularyLearnView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: DashboardView },
    { path: '/chat', component: ChatView },
    { path: '/picture', component: PictureView },
    { path: '/assessment', component: AssessmentView },
    { path: '/vocabulary', component: VocabularyView },
    { path: '/vocabulary/level/:level', component: VocabularyLevelView },
    { path: '/vocabulary/set/:setId', component: VocabularySetView },
    { path: '/vocabulary/set/:setId/learn/:mode', component: VocabularyLearnView }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) return '/login'
})

export default router
