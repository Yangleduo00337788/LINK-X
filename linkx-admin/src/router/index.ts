import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
    meta: { public: true, title: '无权限' },
  },
  {
    path: '/',
    redirect: '/admin/dashboard',
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '仪表盘', permission: 'admin:dashboard:view' },
      },
      {
        path: 'users',
        name: 'UserList',
        component: () => import('@/views/UserListView.vue'),
        meta: { title: '用户管理', permission: 'admin:user:list' },
      },
      {
        path: 'users/:id',
        name: 'UserDetail',
        component: () => import('@/views/UserDetailView.vue'),
        meta: { title: '用户详情', permission: 'admin:user:view', hidden: true },
      },
      {
        path: 'roles',
        name: 'RoleList',
        component: () => import('@/views/RoleListView.vue'),
        meta: { title: '角色管理', permission: 'admin:role:list' },
      },
      {
        path: 'permissions',
        name: 'PermissionList',
        component: () => import('@/views/PermissionListView.vue'),
        meta: { title: '权限管理', permission: 'admin:permission:list' },
      },
      {
        path: 'menus',
        name: 'MenuList',
        component: () => import('@/views/MenuListView.vue'),
        meta: { title: '菜单管理', permission: 'admin:menu:list' },
      },
      {
        path: 'audit-logs',
        name: 'AuditLogs',
        component: () => import('@/views/AuditLogView.vue'),
        meta: { title: '操作日志', permission: 'admin:audit:list' },
      },
      {
        path: 'login-logs',
        name: 'LoginLogs',
        component: () => import('@/views/LoginLogView.vue'),
        meta: { title: '登录日志', permission: 'admin:login-log:list' },
      },
      {
        path: 'feedback',
        name: 'FeedbackList',
        component: () => import('@/views/FeedbackListView.vue'),
        meta: { title: '反馈管理', permission: 'admin:feedback:list' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/SettingView.vue'),
        meta: { title: '系统配置', permission: 'admin:setting:view' },
      },
      {
        path: 'versions',
        name: 'Versions',
        component: () => import('@/views/VersionView.vue'),
        meta: { title: '版本管理', permission: 'admin:version:list' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { title: '个人中心', hidden: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  document.title = `${(to.meta.title as string) || 'LinkX'} - LinkX 管理后台`
  const auth = useAuthStore()
  auth.syncTokensFromStorage()

  if (to.meta.public) {
    if (to.path === '/login' && auth.isLoggedIn) return '/admin/dashboard'
    return true
  }

  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!auth.user || !auth.menus.length) {
    try {
      await auth.fetchProfile()
    } catch {
      await auth.logout()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  const permission = to.meta.permission as string | undefined
  if (permission && !auth.hasPermission(permission)) {
    return { path: '/forbidden' }
  }

  return true
})

export default router
