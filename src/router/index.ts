import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue')
    },
    {
      path: '/moments',
      name: 'moments',
      component: () => import('../components/MomentsModal.vue')
    },
    {
      path: '/note-editor',
      name: 'note-editor',
      component: () => import('../components/NoteEditor.vue')
    }
  ]
})

export default router
