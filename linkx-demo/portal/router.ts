/**
 * 作者：yangleduo
 */
import { createRouter, createWebHistory } from 'vue-router'
import ClientShowcaseView from './views/ClientShowcaseView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [{ path: '/:pathMatch(.*)*', name: 'showcase', component: ClientShowcaseView }],
})

export default router
