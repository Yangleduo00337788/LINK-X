import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  resolveActivitySrc,
  resolveAvatarSrc,
  resolveBannerSrc,
  resolveRecommendSrc,
} from './mediaUrl'

describe('mediaUrl', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', '/api')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  describe('resolveAvatarSrc', () => {
    it('returns data and blob URLs unchanged', () => {
      expect(resolveAvatarSrc('data:image/png;base64,abc')).toBe('data:image/png;base64,abc')
      expect(resolveAvatarSrc('blob:http://localhost/uuid')).toBe('blob:http://localhost/uuid')
    })

    it('returns external CDN URLs unchanged', () => {
      expect(resolveAvatarSrc('https://cdn.example.com/avatar.png')).toBe(
        'https://cdn.example.com/avatar.png'
      )
    })

    it('proxies avatars via userId when url is present', () => {
      expect(resolveAvatarSrc('https://cdn.example.com/a.png', 42)).toBe(
        'https://cdn.example.com/a.png'
      )
      expect(resolveAvatarSrc('/media/custom.png', 42)).toBe('/api/media/custom.png')
    })

    it('respects forceProxy when url is empty', () => {
      expect(resolveAvatarSrc('', 7, true)).toBe('/api/media/avatars/7')
      expect(resolveAvatarSrc('', null, true)).toBe('')
    })

    it('resolves /media paths without userId', () => {
      expect(resolveAvatarSrc('/media/avatars/x.png')).toBe('/api/media/avatars/x.png')
    })

    it('returns empty for unsupported local paths without userId', () => {
      expect(resolveAvatarSrc('relative.png')).toBe('')
    })

    it('does not treat presigned MinIO URLs as CDN', () => {
      const presigned = 'http://127.0.0.1:9000/bucket/key?X-Amz-Algorithm=AWS4-HMAC-SHA256'
      expect(resolveAvatarSrc(presigned, 5)).toBe('/api/media/avatars/5')
    })
  })

  describe('resolveOpsMediaSrc helpers', () => {
    it('resolveBannerSrc resolves /media paths', () => {
      expect(resolveBannerSrc('/media/banners/1.png')).toBe('/api/media/banners/1.png')
    })

    it('resolveRecommendSrc returns empty for blank input', () => {
      expect(resolveRecommendSrc(null)).toBe('')
      expect(resolveRecommendSrc('  ')).toBe('')
    })

    it('resolveActivitySrc passes through blob URLs', () => {
      expect(resolveActivitySrc('blob:http://localhost/act')).toBe('blob:http://localhost/act')
    })

    it('resolveActivitySrc returns external https URLs', () => {
      expect(resolveActivitySrc('https://img.example.com/a.jpg')).toBe(
        'https://img.example.com/a.jpg'
      )
    })
  })
})
