import { describe, it, expect, vi } from 'vitest'
import {
  isDisplayableMediaUrl,
  isEphemeralMediaUrl,
  stripEphemeralMediaUrl,
  normalizeMediaUrl,
  recoverMediaUrlOnError,
  resolveOpsMediaSrc,
  resolveRecommendSrc,
  resolveActivitySrc
} from './mediaUrl'

describe('mediaUrl', () => {
  it('isDisplayableMediaUrl', () => {
    expect(isDisplayableMediaUrl(null)).toBe(false)
    expect(isDisplayableMediaUrl('')).toBe(false)
    expect(isDisplayableMediaUrl('/default-avatar.svg')).toBe(false)
    expect(isDisplayableMediaUrl('2026/a.jpg')).toBe(false)
    expect(isDisplayableMediaUrl('https://cdn/a.jpg')).toBe(true)
    expect(isDisplayableMediaUrl('data:image/png;base64,xx')).toBe(true)
    expect(isDisplayableMediaUrl('/static/a.png')).toBe(true)
  })

  it('isEphemeralMediaUrl / strip', () => {
    expect(isEphemeralMediaUrl(null)).toBe(false)
    expect(isEphemeralMediaUrl('https://x?X-Amz-Signature=1')).toBe(true)
    expect(isEphemeralMediaUrl('http://localhost:9000/b/a')).toBe(true)
    expect(isEphemeralMediaUrl('https://cdn/a.jpg')).toBe(false)
    expect(isEphemeralMediaUrl('/media/external?u=https%3A%2F%2Fx&e=1&s=ab')).toBe(true)
    expect(
      isEphemeralMediaUrl('http://127.0.0.1:8080/api/media/external?u=https%3A%2F%2Fx&e=1&s=ab')
    ).toBe(true)
    expect(stripEphemeralMediaUrl('https://x?X-Amz-Signature=1')).toBe('')
    expect(stripEphemeralMediaUrl('/media/external?u=1&e=1&s=1')).toBe('')
    expect(stripEphemeralMediaUrl('https://cdn/a.jpg')).toBe('https://cdn/a.jpg')
    expect(stripEphemeralMediaUrl(null)).toBe('')
  })

  it('normalizeMediaUrl', () => {
    expect(normalizeMediaUrl(null)).toBe('')
    expect(normalizeMediaUrl('2026/a.jpg')).toBe('')
    expect(normalizeMediaUrl('http://localhost:9000/a')).toBe('http://127.0.0.1:9000/a')
    expect(normalizeMediaUrl('http://[::1]:9000/a')).toBe('http://127.0.0.1:9000/a')
    expect(normalizeMediaUrl('https://x?X-Amz-Signature=1')).toContain('X-Amz-')
  })

  it('resolveOpsMediaSrc prefixes /media paths', () => {
    expect(resolveRecommendSrc('/media/recommends/1')).toContain('/media/recommends/1')
    expect(resolveActivitySrc('/media/activities/2')).toContain('/media/activities/2')
    expect(resolveOpsMediaSrc('https://cdn.example/a.png')).toBe('https://cdn.example/a.png')
    expect(resolveOpsMediaSrc('')).toBe('')
  })

  it('recoverMediaUrlOnError', async () => {
    expect(await recoverMediaUrlOnError('https://cdn/a.jpg', async () => 'x')).toBe('')
    expect(
      await recoverMediaUrlOnError('https://x?X-Amz-Signature=1', async () => 'https://cdn/b.jpg')
    ).toBe('https://cdn/b.jpg')
    expect(
      await recoverMediaUrlOnError('https://x?X-Amz-Signature=1', async () => {
        throw new Error('fail')
      })
    ).toBe('')
  })
})
