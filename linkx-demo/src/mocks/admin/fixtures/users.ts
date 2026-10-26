/**
 * 作者：yangleduo
 */
import { avatarMediaPath } from '../../shared/media'

const DEPTS = ['研发中心', '产品部', '运营中心', '客户成功', '安全合规']
const ROLES = [['member'], ['editor'], ['admin'], ['member', 'editor']]

export function demoAdminUsers() {
  return Array.from({ length: 80 }).map((_, i) => ({
    id: String(10001 + i),
    username: `user_${String(i + 1).padStart(2, '0')}`,
    nickname: ['林雨桐', '王浩然', '赵思琪', '李泽远', '周婉清', '吴俊杰'][i % 6] + (i > 5 ? ` ${i}` : ''),
    avatar: avatarMediaPath(10001 + i),
    email: `user${i + 1}@linkx.io`,
    phone: `138${String(10000000 + i).slice(-8)}`,
    deptName: DEPTS[i % DEPTS.length],
    status: i % 11 === 0 ? 0 : 1,
    roles: ROLES[i % ROLES.length],
    createTime: '2026-01-08 09:30:00',
    updateTime: '2026-02-12 18:20:00',
  }))
}

export function demoAdminUserDetail(id: string) {
  const list = demoAdminUsers()
  const found = list.find(u => u.id === id) || list[0]
  return {
    ...found,
    signature: '专注企业协作与数字化办公',
    permissions: ['chat:send', 'drive:upload'],
    deviceBindingEnabled: false,
  }
}

export const DEMO_ADMIN_PROFILE = {
  id: 1,
  username: 'demo-admin',
  nickname: '沈管理',
  avatar: avatarMediaPath('1'),
  email: 'admin@linkx.io',
  roles: ['super_admin'],
  permissions: [] as string[],
  totpEnabled: false,
}
