/**
 * 作者：yangleduo
 */
import { avatarMediaPath } from '../../shared/media'

export function demoFeedbackItems() {
  return Array.from({ length: 40 }).map((_, i) => ({
    id: String(3000 + i),
    title: ['登录异常', '消息延迟', '云盘上传失败', '审批流程咨询', '功能建议'][i % 5],
    content: '客户在演示环境提交的典型反馈内容，用于展示列表与详情交互。',
    status: ['pending', 'processing', 'resolved', 'closed'][i % 4],
    priority: i % 3 === 0 ? 'high' : 'normal',
    username: `user_${i + 1}`,
    nickname: `用户 ${i + 1}`,
    createTime: '2026-02-11 14:20:00',
    updateTime: '2026-02-12 09:10:00',
  }))
}

export function demoDevices() {
  return Array.from({ length: 36 }).map((_, i) => ({
    id: String(4000 + i),
    userId: String(10001 + (i % 12)),
    deviceName: ['Windows PC', 'iPhone 15', 'MacBook Pro', 'Android'][i % 4],
    deviceType: ['desktop', 'mobile', 'desktop', 'mobile'][i % 4],
    ip: `10.12.${Math.floor(i / 4)}.${20 + i}`,
    online: i % 4 !== 0,
    lastActive: '2026-02-12 15:02:00',
    banned: i % 17 === 0,
  }))
}

export function demoAuditLogs() {
  return Array.from({ length: 50 }).map((_, i) => ({
    id: String(5000 + i),
    operator: '沈管理',
    operatorAvatar: avatarMediaPath('1'),
    action: ['login', 'update_user', 'export_data', 'approve'][i % 4],
    module: ['auth', 'user', 'report', 'approval'][i % 4],
    ip: `192.168.1.${10 + (i % 40)}`,
    createTime: '2026-02-12 10:00:00',
    detail: '演示审计记录',
  }))
}
