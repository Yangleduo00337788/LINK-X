/**
 * 作者：yangleduo
 */
import type { ApiEnvelope, MockContext } from '../types'
import { ok, page } from '../types'
import { trendLabels } from '../utils'
import { allDemoPermissions, demoAdminMenus, handleAdminMenus } from './fixtures/menus'
import { demoAuditLogs, demoDevices, demoFeedbackItems } from './fixtures/lists'
import { DEMO_ADMIN_PROFILE, demoAdminUserDetail, demoAdminUsers } from './fixtures/users'

function adminUser() {
  return { ...DEMO_ADMIN_PROFILE, permissions: allDemoPermissions() }
}

function bodyOf(data: unknown): Record<string, unknown> {
  return data && typeof data === 'object' ? (data as Record<string, unknown>) : {}
}

function match(path: string, re: RegExp) {
  return path.match(re)
}

function trend(name: string, key: string, days: number, base = 120) {
  const labels = trendLabels(days)
  const data = labels.map((_, i) => Math.max(0, base + Math.round(Math.sin(i / 2) * 20 + i * 3)))
  return { labels, series: [{ name, key, data }] }
}

function breakdown(items: Array<[string, string, number]>) {
  return items.map(([key, name, value]) => ({ key, name, value }))
}

const CAPTCHA =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

const users = demoAdminUsers()
const feedbacks = demoFeedbackItems().map(f => ({
  ...f,
  userId: String(10001 + (Number(f.id) % 40)),
  contact: '13800138000',
  overdue: f.status === 'pending',
  assigneeName: '沈管理',
  replies: [
    {
      id: `r-${f.id}`,
      feedbackId: f.id,
      senderType: 'admin',
      senderName: '沈管理',
      content: '已收到，演示环境已登记处理。',
      createTime: f.updateTime,
    },
  ],
}))
const devices = demoDevices()
const auditLogs = demoAuditLogs().map((row, i) => ({
  id: Number(row.id),
  operationType: row.action,
  description: row.detail,
  username: row.operator,
  ip: row.ip,
  status: i % 9 === 0 ? 'FAIL' : 'SUCCESS',
  createTime: row.createTime,
}))
const loginLogs = Array.from({ length: 40 }).map((_, i) => ({
  id: 6000 + i,
  userId: 10001 + (i % 12),
  username: `user_${String((i % 12) + 1).padStart(2, '0')}`,
  ip: `10.8.1.${20 + i}`,
  region: '上海',
  userAgent: 'LinkX Demo',
  success: i % 8 === 0 ? 0 : 1,
  reason: i % 8 === 0 ? '密码错误' : '登录成功',
  createTime: '2026-02-12 09:12:00',
}))
const reviews = Array.from({ length: 24 }).map((_, i) => ({
  id: String(7000 + i),
  sourceType: i % 2 ? 'report' : 'ai',
  targetType: i % 3 === 0 ? 'moments' : 'short-video',
  targetId: String(50000 + i),
  subjectUserId: String(10002 + (i % 8)),
  reporterUsername: 'user_03',
  title: ['疑似广告', '敏感内容', '侵权投诉'][i % 3],
  contentSnapshot: '演示审核快照内容，用于展示审核工作台。',
  evidenceUrls: ['/media/ops/moments-01.jpg'],
  riskLevel: ['low', 'medium', 'high'][i % 3],
  status: ['pending', 'approved', 'rejected'][i % 3],
  overdue: i % 7 === 0,
  createTime: '2026-02-12 11:00:00',
}))
const riskEvents = Array.from({ length: 18 }).map((_, i) => ({
  id: String(8000 + i),
  eventType: ['login_lock', 'message_storm', 'sensitive_hit'][i % 3],
  title: ['登录锁定', '消息风暴', '敏感词命中'][i % 3],
  detail: '演示风险事件，可在后台处理。',
  riskLevel: ['medium', 'high', 'critical'][i % 3],
  status: i % 2 ? 'open' : 'handled',
  username: `user_${String((i % 10) + 1).padStart(2, '0')}`,
  ip: `192.168.9.${i + 3}`,
  createTime: '2026-02-12 08:40:00',
}))
const blacklist = Array.from({ length: 12 }).map((_, i) => ({
  id: String(9000 + i),
  userId: String(10020 + i),
  username: `blocked_${i + 1}`,
  nickname: `封禁用户 ${i + 1}`,
  reason: '演示封禁：多次触发风控规则',
  status: i % 5 === 0 ? 'released' : 'active',
  createdByName: '沈管理',
  createTime: '2026-02-01 10:00:00',
}))
const roles = [
  { id: 1, roleName: '超级管理员', roleCode: 'super_admin', status: 1, remark: '全部权限', createTime: '2026-01-01 00:00:00' },
  { id: 2, roleName: '运营', roleCode: 'operator', status: 1, remark: '内容与反馈', createTime: '2026-01-01 00:00:00' },
  { id: 3, roleName: '安全', roleCode: 'security', status: 1, remark: '风控与审计', createTime: '2026-01-01 00:00:00' },
]
const depts = [
  { id: 1, parentId: 0, deptName: 'LinkX', sort: 1, status: 1, children: [
    { id: 11, parentId: 1, deptName: '研发中心', sort: 1, status: 1 },
    { id: 12, parentId: 1, deptName: '产品部', sort: 2, status: 1 },
    { id: 13, parentId: 1, deptName: '运营中心', sort: 3, status: 1 },
  ]},
]
const notices = Array.from({ length: 8 }).map((_, i) => ({
  id: String(1100 + i),
  title: i === 0 ? '演示环境说明' : `系统公告 ${i}`,
  content: '当前为静态演示站，所有接口均为本地 MOCK，数据可交互但不会写入真实服务器。',
  status: i < 3 ? 'published' : 'draft',
  read: i > 1,
  createTime: '2026-02-10 09:00:00',
  publishedAt: i < 3 ? '2026-02-10 09:00:00' : null,
}))
const banners = Array.from({ length: 6 }).map((_, i) => ({
  id: String(1200 + i),
  title: `首页 Banner ${i + 1}`,
  imageUrl: '/media/ops/banner-ops.jpg',
  imageKey: 'banner-ops.jpg',
  linkUrl: '#',
  position: i % 2 ? 'login' : 'home',
  sortOrder: i,
  status: i < 4 ? 'published' : 'draft',
  createTime: '2026-02-08 12:00:00',
}))
const recommends = Array.from({ length: 8 }).map((_, i) => ({
  id: String(1300 + i),
  slotCode: ['discover', 'chat_sidebar', 'moments'][i % 3],
  title: `推荐位 ${i + 1}`,
  subtitle: '演示运营内容',
  imageUrl: '/media/ops/banner-ops.jpg',
  linkUrl: '#',
  sortOrder: i,
  status: i < 5 ? 'published' : 'draft',
  createTime: '2026-02-08 12:00:00',
}))
const activities = Array.from({ length: 6 }).map((_, i) => ({
  id: String(1400 + i),
  title: `运营活动 ${i + 1}`,
  coverUrl: '/media/ops/moments-01.jpg',
  linkUrl: '#',
  description: '演示活动，点击无真实跳转。',
  sortOrder: i,
  status: i < 3 ? 'published' : 'draft',
  createTime: '2026-02-08 12:00:00',
}))
const versions = [
  {
    id: 'v-1',
    version: '1.0.1',
    channel: 'stable',
    platform: 'windows',
    forceUpdate: false,
    status: 'published',
    releaseNotes: '演示站版本，无需升级。',
    createTime: '2026-02-01 00:00:00',
  },
]
const sensitiveWords = Array.from({ length: 20 }).map((_, i) => ({
  id: String(1500 + i),
  word: ['违规广告', '刷单', '赌博', '诈骗话术'][i % 4] + (i > 3 ? ` ${i}` : ''),
  category: ['ad', 'fraud', 'other'][i % 3],
  level: i % 3,
  status: 1,
  createTime: '2026-01-20 00:00:00',
}))
const riskRules = Array.from({ length: 10 }).map((_, i) => ({
  id: String(1600 + i),
  name: `风控规则 ${i + 1}`,
  enabled: i !== 4,
  score: 20 + i * 5,
  description: '演示规则：短时高频触发则记一次风险。',
  createTime: '2026-01-15 00:00:00',
}))
const dutySchedules = Array.from({ length: 10 }).map((_, i) => ({
  id: String(1700 + i),
  date: `2026-02-${String((i % 28) + 1).padStart(2, '0')}`,
  shift: i % 2 ? '夜班' : '白班',
  assigneeName: ['沈管理', '林雨桐', '赵思琪'][i % 3],
  status: 1,
}))
const dispatchRules = [
  { id: 'dr-1', name: 'Bug 默认分派产品', type: 'bug', assigneeName: '林雨桐', enabled: true },
  { id: 'dr-2', name: '建议分派运营', type: 'suggestion', assigneeName: '赵思琪', enabled: true },
]
const approvalFlows = [
  { id: 'af-1', name: '内容下架审批', bizType: 'review', status: 1, stepsJson: '[]', createTime: '2026-01-01 00:00:00' },
  { id: 'af-2', name: '封禁审批', bizType: 'ban', status: 1, stepsJson: '[]', createTime: '2026-01-01 00:00:00' },
]
const approvalInbox = [
  {
    recordId: 'ar-1',
    instanceId: 'ai-1',
    title: '下架短视频 50001',
    flowName: '内容下架审批',
    bizType: 'review',
    stepName: '安全复核',
    status: 'pending',
    applicantName: '运营小周',
    createTime: '2026-02-12 10:00:00',
  },
]
let settings: Record<string, unknown> = {
  register: { registerEnabled: true, forgotPasswordEmailEnabled: true },
  login: {
    client: { captchaEnabled: false, captchaType: 'image', maxAttempts: 5, lockDurationMinutes: 15 },
    admin: { captchaEnabled: false, captchaType: 'image', maxAttempts: 5, lockDurationMinutes: 15, totpRequired: false },
  },
  password: { minLength: 6, maxLength: 64, requireUpperLower: false, requireDigit: true, requireSpecial: false },
  admin: { captchaEnabled: false },
  client: {
    captchaEnabled: false,
    appVersion: '1.0.1-demo',
    appChannel: 'demo',
    forceUpdate: false,
    maxUploadBytes: 104857600,
    sensitiveFilterEnabled: true,
    supportEmail: 'support@linkx.io',
    supportPhone: '400-000-0000',
    feedbackSlaHours: 24,
    reviewSlaHours: 12,
  },
  mail: { host: 'smtp.demo.local', port: 465, username: 'noreply@linkx.io', passwordConfigured: true, from: 'noreply@linkx.io', fromName: 'LinkX' },
  mailTemplates: { register: { subject: '注册验证码', usingDefault: true }, reset: { subject: '重置密码', usingDefault: true } },
  security: { apiSignEnabled: false, apiEncryptEnabled: false, disableFrontendDebug: false },
  storage: { provider: 'minio', minioEndpoint: 'http://demo-minio', minioBucketName: 'linkx-demo', minioSecretConfigured: true, maxUploadBytes: 104857600 },
  linkmate: {
    enabled: true,
    baseUrl: 'https://demo-llm.local',
    model: 'demo-linkmate',
    maxTokens: 2048,
    temperature: 0.7,
    dailyTokenLimit: 200000,
    apiKeyConfigured: true,
    agentEnabled: true,
    reasoningSupported: true,
    groupAiDefaults: { linkmateEnabled: true, proactiveEnabled: false, smartSummaryEnabled: true },
    groupAiOverview: { totalGroups: 86, linkmateEnabledGroups: 40, proactiveEnabledGroups: 8, smartSummaryEnabledGroups: 22 },
  },
}
const riskPolicies = {
  messageStorm: { userThreshold: 40, userWindowSeconds: 10, groupMinMembers: 5, groupLargeMembers: 50, groupMidPerMinute: 30, groupLargePerMinute: 80 },
  scoreThresholds: { mediumMin: 40, highMin: 70, criticalMin: 90 },
  rateLimits: { loginPerMinute: 20, registerPerMinute: 8, searchPerMinute: 60, listPerMinute: 120, writePerMinute: 40, uploadPerMinute: 20 },
  loginLock: { clientMaxAttempts: 5, clientLockMinutes: 15, adminMaxAttempts: 5, adminLockMinutes: 15 },
  sensitiveFilterEnabled: true,
  aiReviewEnabled: true,
  aiReviewHumanScoreMin: 70,
}
const homepageSections = [
  { id: 'hs-1', sectionType: 'banner', sectionKey: 'home_banner', title: '首页 Banner', enabled: true, sortOrder: 1, publishedCount: 4, managePath: '/admin/banners' },
  { id: 'hs-2', sectionType: 'recommend', sectionKey: 'discover', title: '发现推荐', enabled: true, sortOrder: 2, publishedCount: 5, managePath: '/admin/recommends' },
  { id: 'hs-3', sectionType: 'activity', sectionKey: 'home_activity', title: '活动', enabled: true, sortOrder: 3, publishedCount: 3, managePath: '/admin/activities' },
]
const svPosts = Array.from({ length: 16 }).map((_, i) => ({
  id: String(50000 + i),
  userId: String(10002 + (i % 8)),
  nickname: ['林雨桐', '王浩然', '赵思琪'][i % 3],
  description: `演示短视频 ${i + 1}`,
  coverUrl: '/media/ops/short-video-cover.jpg',
  transcodeStatus: i % 9 === 0 ? 'processing' : 'ready',
  playCount: 300 + i * 17,
  createTime: '2026-02-11 18:00:00',
}))
const svComments = Array.from({ length: 20 }).map((_, i) => ({
  id: String(51000 + i),
  postId: String(50000 + (i % 8)),
  userId: String(10001 + (i % 6)),
  nickname: '演示用户',
  content: '演示评论内容',
  createTime: '2026-02-12 09:00:00',
}))
const svTopics = [
  { id: 't-1', name: '企业协作', displayName: '企业协作', postCount: 28, pinned: true },
  { id: 't-2', name: '产品演示', displayName: '产品演示', postCount: 16, pinned: false },
]
const exportJobs: Array<Record<string, unknown>> = []

function findList(path: string): unknown[] | null {
  if (path.startsWith('/admin/users')) return users
  if (path.startsWith('/admin/feedback-dispatch-rules')) return dispatchRules
  if (path.startsWith('/admin/feedback')) return feedbacks
  if (path.startsWith('/admin/devices')) return devices
  if (path.startsWith('/admin/audit-logs')) return auditLogs
  if (path.startsWith('/admin/login-logs')) return loginLogs
  if (path.startsWith('/admin/reviews')) return reviews
  if (path.startsWith('/admin/risk-events')) return riskEvents
  if (path.startsWith('/admin/blacklist')) return blacklist
  if (path.startsWith('/admin/roles')) return roles
  if (path.startsWith('/admin/notices')) return notices
  if (path.startsWith('/admin/banners')) return banners
  if (path.startsWith('/admin/recommends')) return recommends
  if (path.startsWith('/admin/activities')) return activities
  if (path.startsWith('/admin/versions')) return versions
  if (path.startsWith('/admin/sensitive-words')) return sensitiveWords
  if (path.startsWith('/admin/risk-rules')) return riskRules
  if (path.startsWith('/admin/duty-schedules')) return dutySchedules
  if (path.startsWith('/admin/approval-flows')) return approvalFlows
  if (path.startsWith('/admin/short-video/posts')) return svPosts
  if (path.startsWith('/admin/short-video/comments')) return svComments
  if (path.startsWith('/admin/short-video/topics')) return svTopics
  if (path.startsWith('/admin/export-jobs')) return exportJobs
  return null
}

function monitorTrend(name: string, key: string, hours: number, base: number) {
  return trend(name, key, Math.max(7, Math.min(24, hours)), base)
}

export async function resolveAdminMock(ctx: MockContext): Promise<ApiEnvelope | undefined> {
  const { method, path, params, data } = ctx
  const body = bodyOf(data)
  const days = Number(params?.days) || 14

  if (method === 'get' && path === '/admin/auth/config') {
    return ok({ captchaEnabled: false, registerEnabled: true, apiSignEnabled: false, apiEncryptEnabled: false, totpRequired: false })
  }
  if (method === 'get' && path === '/admin/auth/captcha') {
    return ok({ type: 'image', captchaId: 'demo-captcha', imageBase64: CAPTCHA })
  }
  if (method === 'post' && path === '/admin/auth/login') {
    return ok({
      accessToken: 'demo-admin-access',
      refreshToken: 'demo-admin-refresh',
      expiresIn: 86400,
      user: { ...adminUser(), username: String(body.username || adminUser().username) },
    })
  }
  if (method === 'post' && (path === '/admin/auth/refresh' || path === '/admin/auth/logout')) {
    return ok({ accessToken: 'demo-admin-access', refreshToken: 'demo-admin-refresh', user: adminUser() })
  }
  if (method === 'get' && path === '/admin/auth/me') return ok(adminUser())
  if (method === 'put' && path === '/admin/auth/profile') return ok({ ...adminUser(), ...body })
  if (method === 'post' && path === '/admin/auth/avatar') return ok(adminUser().avatar)
  if (method === 'post' && path === '/admin/auth/password') return ok(null)
  if (path.startsWith('/admin/auth/totp') || path.startsWith('/admin/auth/login/totp') || path.startsWith('/admin/auth/step-up')) {
    if (method === 'get') return ok({ methods: ['password'], action: params?.action || 'sensitive' })
    return ok({ token: 'demo-step-up', challengeToken: 'demo-challenge', qrCode: '', secret: 'DEMOSECRET' })
  }
  if (method === 'get' && (path === '/admin/auth/menus' || path === '/admin/menus')) return ok(handleAdminMenus(ctx))
  if (method === 'get' && path === '/admin/auth/permissions') return ok(allDemoPermissions())
  if (method === 'get' && path === '/admin/permissions') {
    const items = allDemoPermissions().map((code, i) => ({
      id: i + 1,
      permissionCode: code,
      permissionName: code,
      status: 1,
    }))
    return page(items, params?.page, params?.size)
  }

  if (method === 'get' && path === '/admin/dashboard/summary') {
    return ok({
      totalUsers: 12840, activeUsers: 3260, dau: 2180, wau: 3260, mau: 8920, onlineDevices: 412,
      pendingFeedback: 18, overdueFeedback: 3, pendingReviews: 7, overdueReviews: 1, pendingReports: 4,
      todaySensitiveHits: 12, todayRiskBlocks: 2, riskEvents: 5,
    })
  }
  if (method === 'get' && path === '/admin/dashboard/realtime') {
    return ok({ onlineDevices: 412, todayNewUsers: 86, todayMessages: 98240, todayLogins: 1640, riskEvents24h: 5 })
  }
  if (method === 'get' && path === '/admin/dashboard/trends') return ok(trend('活跃用户', 'activeUsers', days, 800))
  if (method === 'get' && path === '/admin/dashboard/pending-tasks') {
    return ok([
      { type: 'feedback', title: '待处理反馈', count: 18, path: '/admin/feedback' },
      { type: 'review', title: '待审核内容', count: 7, path: '/admin/reviews' },
      { type: 'risk', title: '风险事件', count: 5, path: '/admin/risk-events' },
    ])
  }

  if (method === 'get' && path === '/admin/statistics/overview') {
    return ok({
      totalUsers: 12840, activeUsers: 3260, onlineDevices: 412, pendingFeedback: 18, pendingReviews: 7,
      riskEvents: 5, todayNewUsers: 86, todayMessages: 98240, todayLogins: 1640, totalMessages: 12840000,
      totalUploads: 284000, closedFeedback: 920,
    })
  }
  if (method === 'get' && path === '/admin/statistics/users') {
    return ok({
      trend: trend('新增用户', 'newUsers', days, 40),
      statusBreakdown: breakdown([['active', '正常', 11800], ['frozen', '冻结', 640], ['banned', '封禁', 400]]),
      newUsersInRange: 420, loginSuccessInRange: 18600, loginFailInRange: 310,
    })
  }
  if (method === 'get' && path === '/admin/statistics/content') {
    return ok({ trend: trend('消息', 'messages', days, 4000), messagesInRange: 128000, momentsInRange: 2400, uploadsInRange: 8600 })
  }
  if (method === 'get' && path === '/admin/statistics/risk') {
    return ok({
      trend: trend('风险事件', 'risk', days, 12),
      reviewStatusBreakdown: breakdown([['pending', '待审', 7], ['approved', '通过', 40], ['rejected', '驳回', 11]]),
      sensitiveHitsInRange: 86, messageStormsInRange: 4, pendingReviews: 7, resolvedReviewsInRange: 51, avgHandleMinutesInRange: 38,
    })
  }
  if (method === 'get' && path === '/admin/statistics/feedback') {
    return ok({
      trend: trend('反馈', 'feedback', days, 18),
      statusBreakdown: breakdown([['pending', '待处理', 18], ['processing', '处理中', 9], ['closed', '已关闭', 920]]),
      createdInRange: 64, repliedInRange: 51, closedInRange: 40,
    })
  }
  if (method === 'get' && path === '/admin/statistics/groups') {
    return ok({
      totalGroups: 860, activeGroupsInRange: 210, newGroupsInRange: 18, groupMessagesInRange: 42000,
      trend: trend('群消息', 'groupMessages', days, 900),
      topGroups: [
        { id: 90020, name: 'LinkX 产品冲刺群', messageCount: 1280, memberCount: 18 },
        { id: 90021, name: '华东交付支持群', messageCount: 860, memberCount: 26 },
      ],
    })
  }
  if (method === 'get' && path === '/admin/statistics/activity-heatmap') {
    const cells: number[][] = []
    for (let d = 0; d < 7; d++) for (let h = 0; h < 24; h++) cells.push([d, h, Math.round(Math.max(0, Math.sin((d + h) / 3) * 20 + 12))])
    return ok({ metric: params?.metric || 'logins', days: Number(params?.days) || 30, maxValue: 40, total: 4800, cells })
  }

  if (method === 'get' && path === '/admin/bi/metrics') {
    return ok([
      { key: 'dau', name: '日活', dimensions: ['day'] },
      { key: 'messages', name: '消息量', dimensions: ['day', 'channel'] },
      { key: 'risk', name: '风险事件', dimensions: ['day', 'level'] },
    ])
  }
  if (method === 'post' && path === '/admin/bi/query') {
    const metric = String(body.metric || 'dau')
    const t = trend(metric, metric, Number(body.days) || 14, 100)
    return ok({ metric, dimension: body.dimension || 'day', days: Number(body.days) || 14, ...t, breakdown: breakdown([['a', 'A', 40], ['b', 'B', 60]]) })
  }
  if (method === 'get' && path === '/admin/big-screen/data') {
    return ok({
      refreshedAt: new Date().toISOString(), totalUsers: 12840, dau: 2180, onlineDevices: 412,
      pendingFeedback: 18, pendingReviews: 7, todayMessages: 98240, todayLogins: 1640, todayRiskEvents: 5,
      kpiTrends: { dau: trend('dau', 'dau', 7, 2000).series[0].data },
      tickers: [{ type: 'login', title: 'user_08 登录成功', ts: Date.now() }],
    })
  }

  if (method === 'get' && path === '/admin/system-monitor') {
    return ok({
      refreshedAt: new Date().toISOString(), applicationName: 'linkx-server', activeProfile: 'demo', schemaName: 'linkx',
      runtime: {
        uptimeMs: 864000000, startTime: '2026-01-01T00:00:00Z', javaVersion: '21', osName: 'Linux', osArch: 'amd64',
        availableProcessors: 8, heapUsedBytes: 512 * 1024 * 1024, heapMaxBytes: 2048 * 1024 * 1024, heapUsagePercent: 25,
        threadCount: 86, peakThreadCount: 120, gcCount: 40,
      },
      dependencies: [
        { name: 'MySQL', status: 'UP', latencyMs: 4 },
        { name: 'Redis', status: 'UP', latencyMs: 1 },
        { name: 'MinIO', status: 'UP', latencyMs: 8 },
      ],
      connectionPool: { poolName: 'hikari', activeConnections: 6, idleConnections: 10, totalConnections: 16, maxConnections: 32 },
      http: { totalRequests: 1_280_000, clientErrorRequests: 420, serverErrorRequests: 12, avgLatencyMs: 38, p95LatencyMs: 120 },
      business: { loginSuccess: 18600, loginFailure: 310, registerSuccess: 86, registerFailure: 4, messageSent: 98240, fileUploadSuccess: 2100, fileUploadFailure: 9, tokenRefreshSuccess: 6400, tokenRefreshFailure: 2 },
      scheduledTasks: { monitorAvailable: true, totalTasks: 12, registeredTasks: 12, enabledTasks: 11, failedTasks: 0 },
      storage: { tableCount: 48, approximateRowCount: 2_400_000, dataBytes: 1_200_000_000, indexBytes: 180_000_000, totalBytes: 1_380_000_000 },
    })
  }
  if (method === 'get' && path === '/admin/system-monitor/tables') {
    const tables = ['sys_user', 'im_message', 'moments_post', 'short_video'].map((name, i) => ({
      tableName: name, engine: 'InnoDB', rowCount: 10000 * (i + 1), dataBytes: 8_000_000 * (i + 1), indexBytes: 1_000_000, totalBytes: 9_000_000 * (i + 1),
    }))
    return ok({ schemaName: 'linkx', storage: { tableCount: tables.length, approximateRowCount: 100000, dataBytes: 40_000_000, indexBytes: 4_000_000, totalBytes: 44_000_000 }, tables, tableList: tables })
  }
  if (method === 'get' && path === '/admin/system-monitor/cache') {
    return ok({
      refreshedAt: new Date().toISOString(), usedMemoryBytes: 64 * 1024 * 1024, maxMemoryBytes: 256 * 1024 * 1024,
      memoryUsagePercent: 25, connectedClients: 18, hitRatePercent: 96.2, qps: 420, redisVersion: '7.2',
      memoryTrend: monitorTrend('内存', 'mem', 24, 60), qpsTrend: monitorTrend('QPS', 'qps', 24, 400),
    })
  }
  if (method === 'get' && path === '/admin/system-monitor/service') {
    return ok({
      refreshedAt: new Date().toISOString(), osName: 'Linux', hostName: 'demo-node-1', availableProcessors: 8,
      systemCpuLoadPercent: 23.5, processCpuLoadPercent: 12.1, systemTotalMemoryBytes: 16 * 1024 ** 3, systemFreeMemoryBytes: 6 * 1024 ** 3,
      systemMemoryUsagePercent: 62, jvmHeapUsedBytes: 512 * 1024 ** 2, jvmHeapMaxBytes: 2048 * 1024 ** 2, jvmHeapUsagePercent: 25,
      diskTotalBytes: 500 * 1024 ** 3, diskFreeBytes: 260 * 1024 ** 3, diskUsagePercent: 48, diskPath: '/',
      cpuTrend: monitorTrend('CPU', 'cpu', 24, 20), memoryTrend: monitorTrend('内存', 'mem', 24, 60),
    })
  }
  if (method === 'get' && path === '/admin/system-monitor/api-stats') {
    return ok({
      refreshedAt: new Date().toISOString(), totalRequests: 1280000, successRequests: 1275000, failedRequests: 5000,
      methodDistribution: breakdown([['GET', 'GET', 900000], ['POST', 'POST', 380000]]),
      topPaths: breakdown([['/api/chat/sessions', '/api/chat/sessions', 180000], ['/api/user/me', '/api/user/me', 90000]]),
      dailyTrend: trend('请求', 'req', days, 80000),
    })
  }
  if (method === 'get' && path === '/admin/system-monitor/tasks') {
    const tasks = [{ jobId: 1, executorName: 'demo', jobName: '清理过期文件', jobStatus: 1, lastBatchStatus: 'SUCCESS', registered: true }]
    return ok({
      refreshedAt: new Date().toISOString(), monitorAvailable: true, totalTasks: 12, registeredTasks: 12, enabledTasks: 11, failedTasks: 0,
      successBatches: 240, failedBatches: 1, successRatePercent: 99.5,
      statusDistribution: breakdown([['success', '成功', 240], ['fail', '失败', 1]]),
      dailyTrend: trend('任务', 'task', days, 20), tasks,
    })
  }
  if (method === 'get' && path === '/admin/system-monitor/sql') {
    return ok({
      refreshedAt: new Date().toISOString(), activeConnections: 6, questionsTotal: 8_200_000, slowQueries: 12,
      connectionPool: { poolName: 'hikari', activeConnections: 6, idleConnections: 10, totalConnections: 16, maxConnections: 32 },
      topStatements: [{ sampleSql: 'SELECT * FROM im_message WHERE conversation_id=?', execCount: 180000, avgLatencyMs: 4.2, totalLatencyMs: 760000 }],
    })
  }
  if (method === 'get' && path === '/admin/scheduled-tasks') {
    return ok({
      adminConsoleUrl: '#', clientGroup: 'linkx-demo', monitorAvailable: true, totalTasks: 12, registeredTasks: 12, enabledTasks: 11, failedTasks: 0,
      tasks: [{ jobId: 1, executorName: 'demo', jobName: '清理过期文件', triggerType: 'CRON', triggerInterval: '0 0 * * *', jobStatus: 1, registered: true }],
    })
  }
  if (method === 'get' && match(path, /^\/admin\/scheduled-tasks\/[^/]+\/batches$/)) {
    return page([{ id: 1, jobId: 1, jobName: '清理过期文件', batchStatus: 'SUCCESS', durationMs: 320 }], params?.page, params?.size)
  }
  if (method === 'get' && match(path, /^\/admin\/scheduled-tasks\/batches\/[^/]+\/logs$/)) {
    return page([{ id: 1, taskBatchId: 1, taskId: 1, message: '演示任务执行成功', createDt: '2026-02-12 03:00:00' }], params?.page, params?.size)
  }

  if (method === 'get' && path === '/admin/settings') return ok(settings)
  if ((method === 'put' || method === 'post') && path.startsWith('/admin/settings')) {
    settings = { ...settings, ...body }
    if (path.includes('test-')) return ok('演示环境：连接测试通过')
    return ok(settings)
  }

  if (method === 'get' && path === '/admin/risk-policies') return ok(riskPolicies)
  if (method === 'put' && path === '/admin/risk-policies') return ok({ ...riskPolicies, ...body })
  if (method === 'post' && path === '/admin/risk-policies/simulate') {
    return ok({ hit: true, score: 72, level: 'high', actions: ['alert'] })
  }
  if (method === 'post' && path === '/admin/risk-rules/simulate') {
    return ok({ hit: true, matchedRuleIds: ['1600'], score: 45 })
  }

  if (method === 'get' && path === '/admin/rate-limits/hits') {
    return ok(Array.from({ length: 8 }).map((_, i) => ({ ip: `203.0.113.${i + 1}`, count: 20 + i, lastHit: '2026-02-12 14:00:00' })))
  }
  if (method === 'get' && path === '/admin/rate-limits/whitelist') return ok(['127.0.0.1', '10.0.0.0/8'])
  if (path.startsWith('/admin/rate-limits') && method !== 'get') return ok(null)

  if (method === 'get' && path === '/admin/abnormal-access/summary') {
    return ok({ total: 36, last24h: 5, uniqueIps: 12, highRisk: 2 })
  }
  if (method === 'get' && path === '/admin/abnormal-access') {
    return page(Array.from({ length: 16 }).map((_, i) => ({
      id: String(1800 + i), ip: `198.51.100.${i + 2}`, userId: String(10001 + i), username: `user_${i + 1}`,
      reason: '异地登录', riskLevel: i % 3 ? 'medium' : 'high', createTime: '2026-02-12 07:00:00',
    })), params?.page, params?.size)
  }

  if (method === 'get' && path === '/admin/homepage-sections') return ok(homepageSections)
  if (method === 'put' && path === '/admin/homepage-sections/reorder') return ok(null)

  if (method === 'get' && path === '/admin/depts') return ok(depts)

  if (method === 'get' && path === '/admin/approvals/inbox') return page(approvalInbox, params?.page, params?.size)
  if (method === 'get' && path === '/admin/approvals/cc') return page([], params?.page, params?.size)
  const apprInst = match(path, /^\/admin\/approvals\/instances\/([^/]+)$/)
  if (method === 'get' && apprInst) {
    return ok({
      id: apprInst[1], flowName: '内容下架审批', title: '下架短视频 50001', status: 'pending', applicantName: '运营小周',
      timeline: [{ id: 'tl-1', stepName: '提交', status: 'done', assigneeName: '运营小周', actionTime: '2026-02-12 10:00:00' }],
    })
  }
  if (method === 'post' && path.startsWith('/admin/approvals')) return ok(null)

  if (method === 'post' && path === '/admin/export-jobs') {
    const job = {
      id: `exp-${Date.now()}`,
      module: body.module || 'users',
      status: 'SUCCESS',
      rowCount: 80,
      fileName: `${body.module || 'export'}.csv`,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
    }
    exportJobs.unshift(job)
    return ok(job)
  }
  const expOne = match(path, /^\/admin\/export-jobs\/([^/]+)(?:\/download)?$/)
  if (expOne) {
    const job = exportJobs.find(j => String(j.id) === expOne[1]) || exportJobs[0] || {
      id: expOne[1], module: 'users', status: 'SUCCESS', fileName: 'export.csv',
    }
    return ok(job)
  }

  if (method === 'get' && path === '/admin/users') return page(users, params?.page, params?.size)
  const userDev = match(path, /^\/admin\/users\/([^/]+)\/devices$/)
  if (method === 'get' && userDev) return ok(devices.filter(d => d.userId === userDev[1]).slice(0, 4))
  const userLogin = match(path, /^\/admin\/users\/([^/]+)\/logins$/)
  if (method === 'get' && userLogin) {
    return page(
      loginLogs.filter(l => String(l.userId) === userLogin[1]).slice(0, 10),
      params?.page,
      params?.size
    )
  }
  if (method === 'get' && match(path, /^\/admin\/users\/[^/]+$/)) return ok(demoAdminUserDetail(path.split('/').pop()!))

  const fbReplies = match(path, /^\/admin\/feedback\/([^/]+)\/replies$/)
  if (method === 'get' && fbReplies) {
    const item = feedbacks.find(f => String(f.id) === fbReplies[1])
    return ok(item?.replies || [])
  }
  if (method === 'get' && match(path, /^\/admin\/feedback\/[^/]+$/)) {
    return ok(feedbacks.find(f => String(f.id) === path.split('/').pop()) || feedbacks[0])
  }
  if (method === 'get' && path === '/admin/feedback') return page(feedbacks, params?.page, params?.size)

  if (method === 'get' && path === '/admin/notices/inbox') return page(notices.filter(n => n.status === 'published'), params?.page, params?.size)

  if (method === 'post' && /\/upload$/.test(path)) {
    return ok({ objectKey: 'banner-ops.jpg', url: '/media/ops/banner-ops.jpg', fileName: 'demo.jpg' })
  }
  if (method === 'get' && path === '/admin/versions/upload/capability') {
    return ok({ multipart: true, direct: true, maxPartSize: 8 * 1024 * 1024 })
  }
  if (path.startsWith('/admin/versions/upload')) {
    return ok({ uploadId: 'demo', objectKey: 'pkg.exe', url: '/media/ops/banner-ops.jpg', etag: 'demo' })
  }

  const roleSub = match(path, /^\/admin\/roles\/([^/]+)\/(menus|permissions|users)$/)
  if (roleSub) {
    if (method === 'get' && roleSub[2] === 'menus') return ok(demoAdminMenus().flatMap(g => [g.id, ...(g.children || []).map(c => c.id)]))
    if (method === 'get' && roleSub[2] === 'permissions') return ok([1, 2, 3, 4, 5])
    if (method === 'get' && roleSub[2] === 'users') return ok(users.slice(0, 6).map(u => ({ userId: u.id, username: u.username, nickname: u.nickname })))
    return ok(null)
  }

  const list = findList(path)
  if (list && method === 'get' && !path.split('/').pop()?.includes('.')) {
    const parts = path.split('/').filter(Boolean)
    const last = parts[parts.length - 1]
    const isCollection = !/^\d+$/.test(last) && !last.includes('-') ? true : ['users','feedback','devices','reviews','roles','notices','banners','recommends','activities','versions','blacklist','risk-events','risk-rules','sensitive-words','duty-schedules','approval-flows','posts','comments','topics','dispatch-rules','export-jobs','audit-logs','login-logs'].includes(last)
    if (isCollection && !match(path, /\/\d+$/) && !match(path, /\/[a-z]+-\d+$/) && !match(path, /\/v-\d+$/) && !match(path, /\/t-\d+$/) && !match(path, /\/af-\d+$/) && !match(path, /\/dr-\d+$/) && !match(path, /\/exp-/)) {
      if (['users','feedback','devices','reviews','roles','notices','banners','recommends','activities','versions','blacklist','risk-events','risk-rules','sensitive-words','duty-schedules','approval-flows','posts','comments','topics','audit-logs','login-logs','export-jobs'].some(seg => path.endsWith('/' + seg) || path.endsWith('/' + seg.replace('posts','short-video/posts')))) {
        return page(list, params?.page, params?.size)
      }
    }
    const id = last
    const found = list.find((row: unknown) => String((row as { id?: unknown }).id) === id)
    if (found) return ok(found)
    if (path.match(/\/admin\/[^/]+$/) || path.match(/\/admin\/short-video\/[^/]+$/)) {
      return page(list, params?.page, params?.size)
    }
  }

  if (method === 'post' || method === 'put' || method === 'patch' || method === 'delete') {
    if (list) {
      const id = path.split('/').pop()
      const found = list.find((row: unknown) => String((row as { id?: unknown }).id) === id)
      if (found && method !== 'delete') return ok({ ...(found as object), ...body })
      if (method === 'post' && !path.includes('/release') && !path.includes('/publish')) {
        return ok({ id: `demo-${Date.now()}`, ...body, createTime: new Date().toISOString() })
      }
    }
    return ok(null)
  }

  if (method === 'get') {
    if (path.includes('/export')) {
      return ok({ id: 'demo-export-1', module: 'users', status: 'SUCCESS', fileName: 'export.csv' })
    }
    return page([], params?.page, params?.size)
  }
  return undefined
}
