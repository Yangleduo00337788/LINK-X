import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAppStore } from './app'
import * as userApi from '../api/user'

// Mock userApi
vi.mock('../api/user', () => ({
  getCurrentUser: vi.fn(),
  updateProfile: vi.fn(),
  uploadAvatar: vi.fn()
}))

describe('App Store - User Profile', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('updateNickname', () => {
    it('should update nickname locally and sync to backend', async () => {
      // Arrange
      const store = useAppStore()
      vi.mocked(userApi.updateProfile).mockResolvedValue({
        code: 200,
        message: 'success',
        data: {
          id: 123,
          username: 'testuser',
          nickname: 'New Nickname',
          signature: 'Test signature'
        }
      })

      // Act
      await store.updateNickname('New Nickname')

      // Assert
      expect(store.userProfile.nickname).toBe('New Nickname')
      expect(userApi.updateProfile).toHaveBeenCalledWith({ nickname: 'New Nickname' })
    })

    it('should update locally even if backend fails', async () => {
      // Arrange
      const store = useAppStore()
      vi.mocked(userApi.updateProfile).mockRejectedValue(new Error('Network error'))

      // Act
      await store.updateNickname('New Nickname')

      // Assert
      expect(store.userProfile.nickname).toBe('New Nickname')
    })
  })

  describe('updateSignature', () => {
    it('should update signature locally and sync to backend', async () => {
      // Arrange
      const store = useAppStore()
      vi.mocked(userApi.updateProfile).mockResolvedValue({
        code: 200,
        message: 'success',
        data: {
          id: 123,
          username: 'testuser',
          nickname: 'Test',
          signature: 'New Signature'
        }
      })

      // Act
      await store.updateSignature('New Signature')

      // Assert
      expect(store.userProfile.signature).toBe('New Signature')
      expect(userApi.updateProfile).toHaveBeenCalledWith({ signature: 'New Signature' })
    })
  })

  describe('updateAvatar', () => {
    it('should upload avatar and update store', async () => {
      // Arrange
      const store = useAppStore()
      const mockFile = new File(['content'], 'avatar.png', { type: 'image/png' })
      vi.mocked(userApi.uploadAvatar).mockResolvedValue({
        code: 200,
        message: 'success',
        data: 'http://localhost:9000/linkx/avatar/123/456.png'
      })

      // Act
      const result = await store.updateAvatar(mockFile)

      // Assert
      expect(result).toBe('http://localhost:9000/linkx/avatar/123/456.png')
      expect(store.userProfile.avatar).toBe('http://localhost:9000/linkx/avatar/123/456.png')
    })

    it('should throw error when upload fails', async () => {
      // Arrange
      const store = useAppStore()
      const mockFile = new File(['content'], 'avatar.png', { type: 'image/png' })
      vi.mocked(userApi.uploadAvatar).mockResolvedValue({
        code: 500,
        message: '上传失败',
        data: null
      })

      // Act & Assert
      await expect(store.updateAvatar(mockFile)).rejects.toThrow('上传失败')
    })
  })

  describe('fetchCurrentUser', () => {
    it('should fetch and update user profile', async () => {
      // Arrange
      const store = useAppStore()
      vi.mocked(userApi.getCurrentUser).mockResolvedValue({
        code: 200,
        message: 'success',
        data: {
          id: 123,
          username: 'testuser',
          nickname: 'Test Nickname',
          avatar: '/avatar.png',
          signature: 'Test signature',
          createTime: '2024-01-01'
        }
      })

      // Act
      const result = await store.fetchCurrentUser()

      // Assert
      expect(result).not.toBeNull()
      expect(store.userProfile.nickname).toBe('Test Nickname')
      expect(store.userProfile.avatar).toBe('/avatar.png')
      expect(store.userProfile.signature).toBe('Test signature')
      expect(store.userProfile.userId).toBe(123)
    })

    it('should use username when nickname is empty', async () => {
      // Arrange
      const store = useAppStore()
      vi.mocked(userApi.getCurrentUser).mockResolvedValue({
        code: 200,
        message: 'success',
        data: {
          id: 123,
          username: 'testuser',
          nickname: '',
          avatar: '',
          signature: null
        }
      })

      // Act
      await store.fetchCurrentUser()

      // Assert
      expect(store.userProfile.nickname).toBe('testuser')
      expect(store.userProfile.signature).toBe('编辑个性签名')
    })

    it('should handle errors gracefully', async () => {
      // Arrange
      const store = useAppStore()
      vi.mocked(userApi.getCurrentUser).mockRejectedValue(new Error('Network error'))

      // Act
      const result = await store.fetchCurrentUser()

      // Assert
      expect(result).toBeNull()
      // Store should keep default values
      expect(store.userProfile.nickname).toBe('')
    })
  })

  describe('userProfile structure', () => {
    it('should have correct initial structure', () => {
      // Arrange
      const store = useAppStore()

      // Assert
      expect(store.userProfile).toHaveProperty('nickname')
      expect(store.userProfile).toHaveProperty('signature')
      expect(store.userProfile).toHaveProperty('avatar')
      expect(store.userProfile).toHaveProperty('userId')
    })
  })
})
