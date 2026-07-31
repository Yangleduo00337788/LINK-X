import type { ComposerTranslation } from 'vue-i18n'

type TFunc = ComposerTranslation | ((key: string) => string)

/** Backend menu `name` → i18n key */
const MENU_NAME_KEYS: Record<string, string> = {
  dashboard: 'route.dashboard',
  user: 'route.users',
  blacklist: 'route.blacklist',
  devices: 'route.devices',
  rbac: 'route.rbac',
  role: 'route.roles',
  permission: 'route.permissions',
  menu: 'route.menus',
  log: 'route.logs',
  'audit-log': 'route.auditLogs',
  'login-log': 'route.loginLogs',
  'risk-event': 'route.riskEvents',
  feedback: 'route.feedback',
  review: 'route.review',
  'review-task': 'route.reviews',
  'sensitive-word': 'route.sensitiveWords',
  notices: 'route.notices',
  'notice-inbox': 'route.noticeInbox',
  banners: 'route.banners',
  settings: 'route.settings',
  versions: 'route.versions',
  statistics: 'route.statistics',
}

/** Backend menu path → i18n key (fallback) */
const MENU_PATH_KEYS: Record<string, string> = {
  '/admin/dashboard': 'route.dashboard',
  '/admin/users': 'route.users',
  '/admin/blacklist': 'route.blacklist',
  '/admin/devices': 'route.devices',
  '/admin/rbac': 'route.rbac',
  '/admin/roles': 'route.roles',
  '/admin/permissions': 'route.permissions',
  '/admin/menus': 'route.menus',
  '/admin/logs': 'route.logs',
  '/admin/audit-logs': 'route.auditLogs',
  '/admin/login-logs': 'route.loginLogs',
  '/admin/risk-events': 'route.riskEvents',
  '/admin/feedback': 'route.feedback',
  '/admin/review': 'route.review',
  '/admin/reviews': 'route.reviews',
  '/admin/sensitive-words': 'route.sensitiveWords',
  '/admin/notices': 'route.notices',
  '/admin/notice-inbox': 'route.noticeInbox',
  '/admin/banners': 'route.banners',
  '/admin/settings': 'route.settings',
  '/admin/versions': 'route.versions',
  '/admin/statistics': 'route.statistics',
}

export function resolveMenuLabel(
  t: TFunc,
  menu: { name?: string; path?: string; title?: string },
): string {
  const byName = menu.name && MENU_NAME_KEYS[menu.name]
  if (byName) return String(t(byName))
  const byPath = menu.path && MENU_PATH_KEYS[menu.path]
  if (byPath) return String(t(byPath))
  return menu.title || menu.name || ''
}

/** permission_code → { nameKey, descKey } under `perm.*` */
const PERM_KEYS: Record<string, { name: string; desc?: string }> = {
  '*': { name: 'perm.all', desc: 'perm.allDesc' },
  'rbac:role:create': { name: 'perm.rbacRoleCreate', desc: 'perm.rbacRoleCreateDesc' },
  'rbac:role:list': { name: 'perm.rbacRoleList', desc: 'perm.rbacRoleListDesc' },
  'rbac:user:grant': { name: 'perm.rbacUserGrant', desc: 'perm.rbacUserGrantDesc' },
  'rbac:user:revoke': { name: 'perm.rbacUserRevoke', desc: 'perm.rbacUserRevokeDesc' },
  'rbac:user:permissions': { name: 'perm.rbacUserPerms', desc: 'perm.rbacUserPermsDesc' },
  'admin:dashboard:view': { name: 'perm.adminDashboardView', desc: 'perm.adminDashboardViewDesc' },
  'admin:user:list': { name: 'perm.adminUserList', desc: 'perm.adminUserListDesc' },
  'admin:user:view': { name: 'perm.adminUserView', desc: 'perm.adminUserViewDesc' },
  'admin:user:edit': { name: 'perm.adminUserEdit', desc: 'perm.adminUserEditDesc' },
  'admin:user:freeze': { name: 'perm.adminUserFreeze', desc: 'perm.adminUserFreezeDesc' },
  'admin:user:unfreeze': { name: 'perm.adminUserUnfreeze', desc: 'perm.adminUserUnfreezeDesc' },
  'admin:user:ban': { name: 'perm.adminUserBan', desc: 'perm.adminUserBanDesc' },
  'admin:user:unban': { name: 'perm.adminUserUnban', desc: 'perm.adminUserUnbanDesc' },
  'admin:user:reset-password': { name: 'perm.adminUserResetPassword', desc: 'perm.adminUserResetPasswordDesc' },
  'admin:user:device:list': { name: 'perm.adminUserDeviceList', desc: 'perm.adminUserDeviceListDesc' },
  'admin:user:login:list': { name: 'perm.adminUserLoginList', desc: 'perm.adminUserLoginListDesc' },
  'admin:blacklist:list': { name: 'perm.adminBlacklistList', desc: 'perm.adminBlacklistListDesc' },
  'admin:blacklist:add': { name: 'perm.adminBlacklistAdd', desc: 'perm.adminBlacklistAddDesc' },
  'admin:blacklist:remove': { name: 'perm.adminBlacklistRemove', desc: 'perm.adminBlacklistRemoveDesc' },
  'admin:device:list': { name: 'perm.adminDeviceList', desc: 'perm.adminDeviceListDesc' },
  'admin:device:kick': { name: 'perm.adminDeviceKick', desc: 'perm.adminDeviceKickDesc' },
  'admin:role:list': { name: 'perm.adminRoleList', desc: 'perm.adminRoleListDesc' },
  'admin:role:create': { name: 'perm.adminRoleCreate', desc: 'perm.adminRoleCreateDesc' },
  'admin:role:edit': { name: 'perm.adminRoleEdit', desc: 'perm.adminRoleEditDesc' },
  'admin:role:delete': { name: 'perm.adminRoleDelete', desc: 'perm.adminRoleDeleteDesc' },
  'admin:role:assign-menu': { name: 'perm.adminRoleAssignMenu', desc: 'perm.adminRoleAssignMenuDesc' },
  'admin:role:assign-user': { name: 'perm.adminRoleAssignUser', desc: 'perm.adminRoleAssignUserDesc' },
  'admin:permission:list': { name: 'perm.adminPermissionList', desc: 'perm.adminPermissionListDesc' },
  'admin:permission:create': { name: 'perm.adminPermissionCreate', desc: 'perm.adminPermissionCreateDesc' },
  'admin:permission:edit': { name: 'perm.adminPermissionEdit', desc: 'perm.adminPermissionEditDesc' },
  'admin:permission:delete': { name: 'perm.adminPermissionDelete', desc: 'perm.adminPermissionDeleteDesc' },
  'admin:menu:list': { name: 'perm.adminMenuList', desc: 'perm.adminMenuListDesc' },
  'admin:menu:create': { name: 'perm.adminMenuCreate', desc: 'perm.adminMenuCreateDesc' },
  'admin:menu:edit': { name: 'perm.adminMenuEdit', desc: 'perm.adminMenuEditDesc' },
  'admin:menu:delete': { name: 'perm.adminMenuDelete', desc: 'perm.adminMenuDeleteDesc' },
  'admin:audit:list': { name: 'perm.adminAuditList', desc: 'perm.adminAuditListDesc' },
  'admin:audit:export': { name: 'perm.adminAuditExport', desc: 'perm.adminAuditExportDesc' },
  'admin:login-log:list': { name: 'perm.adminLoginLogList', desc: 'perm.adminLoginLogListDesc' },
  'admin:login-log:export': { name: 'perm.adminLoginLogExport', desc: 'perm.adminLoginLogExportDesc' },
  'admin:risk-event:list': { name: 'perm.adminRiskEventList', desc: 'perm.adminRiskEventListDesc' },
  'admin:risk-event:view': { name: 'perm.adminRiskEventView', desc: 'perm.adminRiskEventViewDesc' },
  'admin:risk-event:handle': { name: 'perm.adminRiskEventHandle', desc: 'perm.adminRiskEventHandleDesc' },
  'admin:risk-event:batch': { name: 'perm.adminRiskEventBatch', desc: 'perm.adminRiskEventBatchDesc' },
  'admin:risk-event:export': { name: 'perm.adminRiskEventExport', desc: 'perm.adminRiskEventExportDesc' },
  'admin:feedback:list': { name: 'perm.adminFeedbackList', desc: 'perm.adminFeedbackListDesc' },
  'admin:feedback:reply': { name: 'perm.adminFeedbackReply', desc: 'perm.adminFeedbackReplyDesc' },
  'admin:feedback:close': { name: 'perm.adminFeedbackClose', desc: 'perm.adminFeedbackCloseDesc' },
  'admin:feedback:export': { name: 'perm.adminFeedbackExport', desc: 'perm.adminFeedbackExportDesc' },
  'admin:review:list': { name: 'perm.adminReviewList', desc: 'perm.adminReviewListDesc' },
  'admin:review:approve': { name: 'perm.adminReviewApprove', desc: 'perm.adminReviewApproveDesc' },
  'admin:review:reject': { name: 'perm.adminReviewReject', desc: 'perm.adminReviewRejectDesc' },
  'admin:review:batch': { name: 'perm.adminReviewBatch', desc: 'perm.adminReviewBatchDesc' },
  'admin:review:export': { name: 'perm.adminReviewExport', desc: 'perm.adminReviewExportDesc' },
  'admin:user:export': { name: 'perm.adminUserExport', desc: 'perm.adminUserExportDesc' },
  'admin:sensitive-word:list': { name: 'perm.adminSensitiveList', desc: 'perm.adminSensitiveListDesc' },
  'admin:sensitive-word:create': { name: 'perm.adminSensitiveCreate', desc: 'perm.adminSensitiveCreateDesc' },
  'admin:sensitive-word:edit': { name: 'perm.adminSensitiveEdit', desc: 'perm.adminSensitiveEditDesc' },
  'admin:sensitive-word:delete': { name: 'perm.adminSensitiveDelete', desc: 'perm.adminSensitiveDeleteDesc' },
  'admin:notice:list': { name: 'perm.adminNoticeList', desc: 'perm.adminNoticeListDesc' },
  'admin:notice:view': { name: 'perm.adminNoticeView', desc: 'perm.adminNoticeViewDesc' },
  'admin:notice:create': { name: 'perm.adminNoticeCreate', desc: 'perm.adminNoticeCreateDesc' },
  'admin:notice:edit': { name: 'perm.adminNoticeEdit', desc: 'perm.adminNoticeEditDesc' },
  'admin:notice:delete': { name: 'perm.adminNoticeDelete', desc: 'perm.adminNoticeDeleteDesc' },
  'admin:notice:publish': { name: 'perm.adminNoticePublish', desc: 'perm.adminNoticePublishDesc' },
  'admin:notice:unpublish': { name: 'perm.adminNoticeUnpublish', desc: 'perm.adminNoticeUnpublishDesc' },
  'admin:notice:inbox': { name: 'perm.adminNoticeInbox', desc: 'perm.adminNoticeInboxDesc' },
  'admin:banner:list': { name: 'perm.adminBannerList', desc: 'perm.adminBannerListDesc' },
  'admin:banner:view': { name: 'perm.adminBannerView', desc: 'perm.adminBannerViewDesc' },
  'admin:banner:create': { name: 'perm.adminBannerCreate', desc: 'perm.adminBannerCreateDesc' },
  'admin:banner:edit': { name: 'perm.adminBannerEdit', desc: 'perm.adminBannerEditDesc' },
  'admin:banner:delete': { name: 'perm.adminBannerDelete', desc: 'perm.adminBannerDeleteDesc' },
  'admin:banner:publish': { name: 'perm.adminBannerPublish', desc: 'perm.adminBannerPublishDesc' },
  'admin:banner:unpublish': { name: 'perm.adminBannerUnpublish', desc: 'perm.adminBannerUnpublishDesc' },
  'admin:setting:view': { name: 'perm.adminSettingView', desc: 'perm.adminSettingViewDesc' },
  'admin:setting:edit': { name: 'perm.adminSettingEdit', desc: 'perm.adminSettingEditDesc' },
  'admin:version:list': { name: 'perm.adminVersionList', desc: 'perm.adminVersionListDesc' },
  'admin:statistics:view': { name: 'perm.adminStatisticsView', desc: 'perm.adminStatisticsViewDesc' },
  'admin:statistics:export': { name: 'perm.adminStatisticsExport', desc: 'perm.adminStatisticsExportDesc' },
}

export function resolvePermissionName(
  t: TFunc,
  code?: string,
  fallback?: string,
): string {
  if (!code) return fallback || '-'
  const keys = PERM_KEYS[code]
  if (keys) return String(t(keys.name))
  return fallback || code
}

export function resolvePermissionDesc(
  t: TFunc,
  code?: string,
  fallback?: string,
): string {
  if (!code) return fallback || '-'
  const keys = PERM_KEYS[code]
  if (keys?.desc) return String(t(keys.desc))
  return fallback || '-'
}
