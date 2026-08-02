import { describe, expect, it } from 'vitest'
import { resolveMenuLabel, resolvePermissionDesc, resolvePermissionName } from './menuI18n'

const t = (key: string) => `t:${key}`

describe('menuI18n', () => {
  describe('resolveMenuLabel', () => {
    it('prefers backend menu name mapping', () => {
      expect(resolveMenuLabel(t, { name: 'dashboard' })).toBe('t:route.dashboard')
    })

    it('falls back to path mapping', () => {
      expect(resolveMenuLabel(t, { path: '/admin/users' })).toBe('t:route.users')
    })

    it('falls back to title, then name', () => {
      expect(resolveMenuLabel(t, { title: 'Custom' })).toBe('Custom')
      expect(resolveMenuLabel(t, { name: 'unknown-menu' })).toBe('unknown-menu')
    })
  })

  describe('resolvePermissionName', () => {
    it('maps known permission codes', () => {
      expect(resolvePermissionName(t, 'admin:user:list')).toBe('t:perm.adminUserList')
    })

    it('falls back when code is unknown or missing', () => {
      expect(resolvePermissionName(t, 'custom:perm', 'Fallback')).toBe('Fallback')
      expect(resolvePermissionName(t, undefined)).toBe('-')
    })
  })

  describe('resolvePermissionDesc', () => {
    it('maps known permission descriptions', () => {
      expect(resolvePermissionDesc(t, 'admin:user:list')).toBe('t:perm.adminUserListDesc')
    })

    it('falls back when description is missing', () => {
      expect(resolvePermissionDesc(t, 'unknown:perm', 'Desc')).toBe('Desc')
      expect(resolvePermissionDesc(t, undefined)).toBe('-')
    })
  })
})
