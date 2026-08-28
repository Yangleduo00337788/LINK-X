/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import { resolveMomentsBackgroundUrl, resolveUserAvatarUrl } from './defaultAvatar'

describe('resolveUserAvatarUrl', () => {
  it('preserves ?v= cache buster from full upload URL', () => {
    const uploaded = 'http://127.0.0.1:8080/api/media/avatars/888212?v=1700000000000'
    const resolved = resolveUserAvatarUrl(uploaded, 888212)
    expect(resolved).toContain('/media/avatars/888212')
    expect(resolved).toContain('v=1700000000000')
  })

  it('preserves ?v= on relative proxy path', () => {
    const resolved = resolveUserAvatarUrl('/media/avatars/888212?v=1700000000000', 888212)
    expect(resolved).toContain('v=1700000000000')
  })

  it('returns empty when avatar is empty (no proxy probe)', () => {
    expect(resolveUserAvatarUrl('', 888212)).toBe('')
    expect(resolveUserAvatarUrl(null, 888212)).toBe('')
  })
})

describe('resolveMomentsBackgroundUrl', () => {
  it('preserves ?v= cache buster from full upload URL', () => {
    const uploaded = 'http://127.0.0.1:8080/api/media/moments-background/888212?v=1700000000000'
    const resolved = resolveMomentsBackgroundUrl(uploaded, 888212)
    expect(resolved).toContain('/media/moments-background/888212')
    expect(resolved).toContain('v=1700000000000')
  })

  it('returns empty when background is empty', () => {
    expect(resolveMomentsBackgroundUrl('', 888212)).toBe('')
  })
})
