// Vue Router 工厂函数与 Hash 模式历史 API
import { createRouter, createWebHashHistory } from 'vue-router'

// 创建路由实例
const router = createRouter({
  // 使用 Hash 模式（#/path），兼容 Electron file:// 与 Web 部署无需服务端配置
  history: createWebHashHistory(),
  // 路由表：主窗口 + 两个独立 Electron 子窗口
  routes: [
    {
      path: '/',              // 默认首页路径
      name: 'home',           // 路由名称，可用于编程式导航
      // 懒加载主界面：登录后展示 AppShell
      component: () => import('../views/HomeView.vue')
    },
    {
      path: '/moments',        // 友链独立窗口路由
      name: 'moments',
      component: () => import('../components/MomentsModal.vue')
    },
    {
      path: '/note-editor',    // 笔记编辑器独立窗口路由
      name: 'note-editor',
      component: () => import('../components/NoteEditor.vue')
    }
  ]
})

// 默认导出供 main.ts 注册
export default router
