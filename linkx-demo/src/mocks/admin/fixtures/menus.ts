/**
 * 作者：yangleduo
 */
import type { MockContext } from '../../types'

type MenuNode = {
  id: number
  parentId: number
  name: string
  title: string
  path: string
  icon?: string
  type?: string
  permission?: string
  sort?: number
  visible?: boolean
  status?: number
  children?: MenuNode[]
}

function leaf(
  id: number,
  parentId: number,
  name: string,
  title: string,
  path: string,
  icon: string,
  permission: string,
  sort: number
): MenuNode {
  return {
    id,
    parentId,
    name,
    title,
    path,
    icon,
    type: 'menu',
    permission,
    sort,
    visible: true,
    status: 1,
  }
}

function group(
  id: number,
  name: string,
  title: string,
  icon: string,
  sort: number,
  children: MenuNode[]
): MenuNode {
  return {
    id,
    parentId: 0,
    name,
    title,
    path: '',
    icon,
    type: 'dir',
    sort,
    visible: true,
    status: 1,
    children: children.map(c => ({ ...c, parentId: id })),
  }
}

/** 与 linkx-admin 路由对齐的演示菜单树 */
export function demoAdminMenus(): MenuNode[] {
  return [
    group(1, 'dashboard', '工作台', 'Grid', 1, [
      leaf(101, 1, 'dashboard', '概览', '/admin/dashboard', 'Home', 'admin:dashboard:view', 1),
    ]),
    group(2, 'user-center', '用户与设备', 'People', 2, [
      leaf(201, 2, 'users', '用户管理', '/admin/users', 'Person', 'admin:user:list', 1),
      leaf(202, 2, 'blacklist', '黑名单', '/admin/blacklist', 'Ban', 'admin:blacklist:list', 2),
      leaf(203, 2, 'devices', '设备管理', '/admin/devices', 'PhonePortrait', 'admin:device:list', 3),
    ]),
    group(3, 'rbac', '权限体系', 'Key', 3, [
      leaf(301, 3, 'roles', '角色管理', '/admin/roles', 'Shield', 'admin:role:list', 1),
      leaf(302, 3, 'permissions', '权限点', '/admin/permissions', 'LockClosed', 'admin:permission:list', 2),
      leaf(303, 3, 'menus', '菜单管理', '/admin/menus', 'Menu', 'admin:menu:list', 3),
      leaf(304, 3, 'depts', '组织架构', '/admin/depts', 'GitNetwork', 'admin:dept:list', 4),
    ]),
    group(4, 'security', '安全风控', 'Alert', 4, [
      leaf(401, 4, 'audit-logs', '审计日志', '/admin/audit-logs', 'DocumentText', 'admin:audit:list', 1),
      leaf(402, 4, 'login-logs', '登录日志', '/admin/login-logs', 'LogIn', 'admin:login-log:list', 2),
      leaf(403, 4, 'abnormal-access', '异常访问', '/admin/abnormal-access', 'AlertCircle', 'admin:abnormal-access:list', 3),
      leaf(404, 4, 'risk-events', '风险事件', '/admin/risk-events', 'Flame', 'admin:risk-event:list', 4),
      leaf(405, 4, 'risk-policies', '风控策略', '/admin/risk-policies', 'Options', 'admin:risk-policy:list', 5),
      leaf(406, 4, 'risk-rules', '风控规则', '/admin/risk-rules', 'Shield', 'admin:risk-rule:list', 6),
      leaf(407, 4, 'rate-limits', '限流策略', '/admin/rate-limits', 'Speedometer', 'admin:rate-limit:list', 7),
    ]),
    group(5, 'ops', '运营与内容', 'Megaphone', 5, [
      leaf(501, 5, 'feedback', '用户反馈', '/admin/feedback', 'Chatbubbles', 'admin:feedback:list', 1),
      leaf(502, 5, 'feedback-dispatch-rules', '反馈分派', '/admin/feedback-dispatch-rules', 'GitBranch', 'admin:feedback-dispatch-rule:list', 2),
      leaf(503, 5, 'duty-schedules', '值班表', '/admin/duty-schedules', 'Time', 'admin:duty-schedule:list', 3),
      leaf(504, 5, 'reviews', '内容审核', '/admin/reviews', 'CheckmarkCircle', 'admin:review:list', 4),
      leaf(505, 5, 'short-videos', '短视频', '/admin/short-videos', 'Videocam', 'admin:short-video:list', 5),
      leaf(506, 5, 'sensitive-words', '敏感词', '/admin/sensitive-words', 'Text', 'admin:sensitive-word:list', 6),
      leaf(507, 5, 'notices', '公告管理', '/admin/notices', 'Notifications', 'admin:notice:list', 7),
      leaf(508, 5, 'notice-inbox', '公告收件箱', '/admin/notice-inbox', 'Mail', 'admin:notice:inbox', 8),
      leaf(509, 5, 'banners', 'Banner', '/admin/banners', 'Image', 'admin:banner:list', 9),
      leaf(510, 5, 'recommends', '推荐位', '/admin/recommends', 'Star', 'admin:recommend:list', 10),
      leaf(511, 5, 'activities', '活动', '/admin/activities', 'Calendar', 'admin:activity:list', 11),
      leaf(512, 5, 'homepage-orchestration', '首页编排', '/admin/homepage-orchestration', 'Layers', 'admin:homepage:list', 12),
    ]),
    group(6, 'approval', '审批', 'CheckmarkDone', 6, [
      leaf(601, 6, 'approval-flows', '审批流程', '/admin/approval-flows', 'GitBranch', 'admin:approval-flow:list', 1),
      leaf(602, 6, 'approval-inbox', '审批待办', '/admin/approval-inbox', 'Inbox', 'admin:approval:inbox', 2),
    ]),
    group(7, 'monitor', '系统监控', 'Pulse', 7, [
      leaf(701, 7, 'monitor-service', '服务监控', '/admin/system-monitor/service', 'Server', 'admin:system-monitor:view', 1),
      leaf(702, 7, 'monitor-cache', '缓存监控', '/admin/system-monitor/cache', 'Cube', 'admin:system-monitor:view', 2),
      leaf(703, 7, 'monitor-api', 'API 统计', '/admin/system-monitor/api-stats', 'StatsChart', 'admin:system-monitor:view', 3),
      leaf(704, 7, 'monitor-tasks', '定时任务', '/admin/system-monitor/tasks', 'Timer', 'admin:system-monitor:view', 4),
      leaf(705, 7, 'monitor-sql', 'SQL 监控', '/admin/system-monitor/sql', 'Code', 'admin:system-monitor:view', 5),
    ]),
    group(8, 'insight', '数据洞察', 'Analytics', 8, [
      leaf(801, 8, 'statistics', '运营统计', '/admin/statistics', 'BarChart', 'admin:statistics:view', 1),
      leaf(802, 8, 'bi-analytics', '高级分析', '/admin/bi-analytics', 'Analytics', 'admin:bi:view', 2),
      leaf(803, 8, 'big-screen', '实时大屏', '/admin/big-screen', 'Tv', 'admin:big-screen:view', 3),
    ]),
    group(9, 'system', '系统配置', 'Settings', 9, [
      leaf(901, 9, 'settings', '配置中心', '/admin/settings', 'Settings', 'admin:setting:view', 1),
      leaf(902, 9, 'versions', '版本发布', '/admin/versions', 'Rocket', 'admin:version:list', 2),
    ]),
  ]
}

export function allDemoPermissions(): string[] {
  const perms = new Set<string>(['*'])
  for (const g of demoAdminMenus()) {
    for (const c of g.children || []) {
      if (c.permission) perms.add(c.permission)
    }
  }
  return [...perms]
}

export function handleAdminMenus(_ctx: MockContext) {
  return demoAdminMenus()
}
