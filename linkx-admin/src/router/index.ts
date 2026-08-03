import { watch } from 'vue'
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import i18n, { tGlobal } from '@/i18n'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, titleKey: 'route.login' },
  },
  {
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
    meta: { public: true, titleKey: 'route.forbidden' },
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
        meta: { titleKey: 'route.dashboard', permission: 'admin:dashboard:view' },
      },
      {
        path: 'users',
        name: 'UserList',
        component: () => import('@/views/UserListView.vue'),
        meta: { titleKey: 'route.users', permission: 'admin:user:list' },
      },
      {
        path: 'users/:id',
        name: 'UserDetail',
        component: () => import('@/views/UserDetailView.vue'),
        meta: { titleKey: 'route.userDetail', permission: 'admin:user:view', hidden: true },
      },
      {
        path: 'blacklist',
        name: 'Blacklist',
        component: () => import('@/views/BlacklistView.vue'),
        meta: { titleKey: 'route.blacklist', permission: 'admin:blacklist:list' },
      },
      {
        path: 'devices',
        name: 'Devices',
        component: () => import('@/views/DeviceListView.vue'),
        meta: { titleKey: 'route.devices', permission: 'admin:device:list' },
      },
      {
        path: 'roles',
        name: 'RoleList',
        component: () => import('@/views/RoleListView.vue'),
        meta: { titleKey: 'route.roles', permission: 'admin:role:list' },
      },
      {
        path: 'permissions',
        name: 'PermissionList',
        component: () => import('@/views/PermissionListView.vue'),
        meta: { titleKey: 'route.permissions', permission: 'admin:permission:list' },
      },
      {
        path: 'menus',
        name: 'MenuList',
        component: () => import('@/views/MenuListView.vue'),
        meta: { titleKey: 'route.menus', permission: 'admin:menu:list' },
      },
      {
        path: 'depts',
        name: 'DeptList',
        component: () => import('@/views/DeptListView.vue'),
        meta: { titleKey: 'route.depts', permission: 'admin:dept:list' },
      },
      {
        path: 'audit-logs',
        name: 'AuditLogs',
        component: () => import('@/views/AuditLogView.vue'),
        meta: { titleKey: 'route.auditLogs', permission: 'admin:audit:list' },
      },
      {
        path: 'login-logs',
        name: 'LoginLogs',
        component: () => import('@/views/LoginLogView.vue'),
        meta: { titleKey: 'route.loginLogs', permission: 'admin:login-log:list' },
      },
      {
        path: 'abnormal-access',
        name: 'AbnormalAccess',
        component: () => import('@/views/AbnormalAccessView.vue'),
        meta: { titleKey: 'route.abnormalAccess', permission: 'admin:abnormal-access:list' },
      },
      {
        path: 'risk-events',
        name: 'RiskEvents',
        component: () => import('@/views/RiskEventView.vue'),
        meta: { titleKey: 'route.riskEvents', permission: 'admin:risk-event:list' },
      },
      {
        path: 'rate-limits',
        name: 'RateLimits',
        component: () => import('@/views/RateLimitView.vue'),
        meta: { titleKey: 'route.rateLimits', permission: 'admin:rate-limit:list' },
      },
      {
        path: 'feedback',
        name: 'FeedbackList',
        component: () => import('@/views/FeedbackListView.vue'),
        meta: { titleKey: 'route.feedback', permission: 'admin:feedback:list' },
      },
      {
        path: 'feedback-dispatch-rules',
        name: 'FeedbackDispatchRules',
        component: () => import('@/views/FeedbackDispatchRuleListView.vue'),
        meta: {
          titleKey: 'route.feedbackDispatchRules',
          permission: 'admin:feedback-dispatch-rule:list',
        },
      },
      {
        path: 'reviews',
        name: 'ReviewList',
        component: () => import('@/views/ReviewListView.vue'),
        meta: { titleKey: 'route.reviews', permission: 'admin:review:list' },
      },
      {
        path: 'reports',
        name: 'ReportList',
        component: () => import('@/views/ReviewListView.vue'),
        meta: {
          titleKey: 'route.reports',
          permission: 'admin:review:list',
          reportOnly: true,
        },
      },
      {
        path: 'announcement-reviews',
        name: 'AnnouncementReviewList',
        component: () => import('@/views/ReviewListView.vue'),
        meta: {
          titleKey: 'route.announcementReviews',
          permission: 'admin:review:list',
          announcementOnly: true,
        },
      },
      {
        path: 'sensitive-words',
        name: 'SensitiveWordList',
        component: () => import('@/views/SensitiveWordListView.vue'),
        meta: { titleKey: 'route.sensitiveWords', permission: 'admin:sensitive-word:list' },
      },
      {
        path: 'notices',
        name: 'Notices',
        component: () => import('@/views/NoticeView.vue'),
        meta: { titleKey: 'route.notices', permission: 'admin:notice:list' },
      },
      {
        path: 'notice-inbox',
        name: 'NoticeInbox',
        component: () => import('@/views/NoticeInboxView.vue'),
        meta: { titleKey: 'route.noticeInbox', permission: 'admin:notice:inbox' },
      },
      {
        path: 'banners',
        name: 'Banners',
        component: () => import('@/views/BannerListView.vue'),
        meta: { titleKey: 'route.banners', permission: 'admin:banner:list' },
      },
      {
        path: 'recommends',
        name: 'Recommends',
        component: () => import('@/views/RecommendListView.vue'),
        meta: { titleKey: 'route.recommends', permission: 'admin:recommend:list' },
      },
      {
        path: 'activities',
        name: 'Activities',
        component: () => import('@/views/ActivityListView.vue'),
        meta: { titleKey: 'route.activities', permission: 'admin:activity:list' },
      },
      {
        path: 'homepage-orchestration',
        name: 'HomepageOrchestration',
        component: () => import('@/views/HomepageOrchestrationView.vue'),
        meta: { titleKey: 'route.homepageOrchestration', permission: 'admin:homepage:list' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/SettingView.vue'),
        meta: { titleKey: 'route.settings', permission: 'admin:setting:view' },
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/StatisticsView.vue'),
        meta: { titleKey: 'route.statistics', permission: 'admin:statistics:view' },
      },
      {
        path: 'versions',
        name: 'VersionList',
        component: () => import('@/views/VersionListView.vue'),
        meta: { titleKey: 'route.versions', permission: 'admin:version:list' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { titleKey: 'route.profile', hidden: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true, titleKey: 'route.notFound' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export function syncDocumentTitle(titleKey?: string) {
  const key = titleKey || 'app.brand'
  document.title = `${tGlobal(key)} - ${tGlobal('app.name')}`
}

router.beforeEach(async (to) => {
  syncDocumentTitle(to.meta.titleKey as string | undefined)
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

watch(
  () => i18n.global.locale.value,
  () => {
    const key = router.currentRoute.value.meta.titleKey as string | undefined
    syncDocumentTitle(key)
  }
)

export default router
