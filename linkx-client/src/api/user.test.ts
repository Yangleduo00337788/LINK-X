import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { getCurrentUser, updateProfile, uploadAvatar, getUserProfile } from './user'
import { apiClient } from './client'

// Mock apiClient
vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn()
  }
}))

describe('User API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('getCurrentUser', () => {
    it('should return user data when request succeeds', async () => {
      // Arrange
      const mockResponse = {
        code: 200,
        message: 'success',
        data: {
          id: 123,
          username: 'testuser',
          nickname: 'Test User',
          avatar: '/avatar.png',
          signature: 'Test signature',
          createTime: '2024-01-01T00:00:00Z'
        }
      }
      vi.mocked(apiClient.get).mockResolvedValue(mockResponse)

      // Act
      const result = await getCurrentUser()

      // Assert
      expect(apiClient.get).toHaveBeenCalledWith('/user/me')
      expect(result).toEqual(mockResponse)
      expect(result.data.nickname).toBe('Test User')
    })

    it('should handle 401 unauthorized error', async () => {
      // Arrange
      const mockError = {
        response: {
          data: {
            code: 401,
            message: '未登录'
          }
        }
      }
      vi.mocked(apiClient.get).mockRejectedValue(mockError)

      // Act & Assert
      await expect(getCurrentUser()).rejects.toEqual(mockError)
    })
  })

  describe('updateProfile', () => {
    it('should update nickname successfully', async () => {
      // Arrange
      const payload = { nickname: 'New Nickname' }
      const mockResponse = {
        code: 200,
        message: 'success',
        data: {
          id: 123,
          username: 'testuser',
          nickname: 'New Nickname',
          signature: 'Original signature'
        }
      }
      vi.mocked(apiClient.put).mockResolvedValue(mockResponse)

      // Act
      const result = await updateProfile(payload)

      // Assert
      expect(apiClient.put).toHaveBeenCalledWith('/user/profile', payload)
      expect(result.data.nickname).toBe('New Nickname')
    })

    it('should update signature successfully', async () => {
      // Arrange
      const payload = { signature: 'New signature' }
      const mockResponse = {
        code: 200,
        message: 'success',
        data: {
          id: 123,
          username: 'testuser',
          nickname: 'Original nickname',
          signature: 'New signature'
        }
      }
      vi.mocked(apiClient.put).mockResolvedValue(mockResponse)

      // Act
      const result = await updateProfile(payload)

      // Assert
      expect(result.data.signature).toBe('New signature')
    })

    it('should update both nickname and signature', async () => {
      // Arrange
      const payload = { nickname: 'New Nick', signature: 'New Sig' }
      const mockResponse = {
        code: 200,
        message: 'success',
        data: {
          id: 123,
          username: 'testuser',
          nickname: 'New Nick',
          signature: 'New Sig'
        }
      }
      vi.mocked(apiClient.put).mockResolvedValue(mockResponse)

      // Act
      const result = await updateProfile(payload)

      // Assert
      expect(result.data.nickname).toBe('New Nick')
      expect(result.data.signature).toBe('New Sig')
    })
  })

  describe('uploadAvatar', () => {
    it('should upload avatar successfully', async () => {
      // Arrange
      const mockFile = new File(['fake image content'], 'avatar.png', { type: 'image/png' })
      const mockResponse = {
        code: 200,
        message: 'success',
        data: 'http://localhost:9000/linkx/avatar/123/456789.png'
      }
      vi.mocked(apiClient.post).mockResolvedValue(mockResponse)

      // Act
      const result = await uploadAvatar(mockFile)

      // Assert
      expect(apiClient.post).toHaveBeenCalledWith(
        '/user/avatar',
        expect.any(FormData),
        { headers: { 'Content-Type': 'multipart/form-data' } }
      )
      expect(result.data).toBe('http://localhost:9000/linkx/avatar/123/456789.png')
    })

    it('should reject non-image files', async () => {
      // Arrange - This is handled by backend, but we test the API call
      const mockFile = new File(['fake content'], 'document.txt', { type: 'text/plain' })
      const mockResponse = {
        code: 400,
        message: '只支持图片文件',
        data: null
      }
      vi.mocked(apiClient.post).mockResolvedValue(mockResponse)

      // Act
      const result = await uploadAvatar(mockFile)

      // Assert
      expect(result.code).toBe(400)
      expect(result.message).toBe('只支持图片文件')
    })

    it('should handle large file upload error', async () => {
      // Arrange
      const mockFile = new File(['x'.repeat(11 * 1024 * 1024)], 'large.png', { type: 'image/png' })
      const mockError = {
        response: {
          data: {
            code: 400,
            message: '文件大小超过限制: 10MB'
          }
        }
      }
      vi.mocked(apiClient.post).mockRejectedValue(mockError)

      // Act & Assert
      await expect(uploadAvatar(mockFile)).rejects.toEqual(mockError)
    })
  })

  describe('getUserProfile', () => {
    it('should get public profile by userId', async () => {
      // Arrange
      const userId = 123
      const mockResponse = {
        code: 200,
        message: 'success',
        data: {
          id: 123,
          username: 'targetuser',
          nickname: 'Target User',
          avatar: '/avatar.png',
          signature: 'Hello!'
        }
      }
      vi.mocked(apiClient.get).mockResolvedValue(mockResponse)

      // Act
      const result = await getUserProfile(userId)

      // Assert
      expect(apiClient.get).toHaveBeenCalledWith('/user/123/profile')
      expect(result.data.username).toBe('targetuser')
    })

    it('should return 404 for non-existent user', async () => {
      // Arrange
      const userId = 99999
      const mockResponse = {
        code: 404,
        message: '用户不存在',
        data: null
      }
      vi.mocked(apiClient.get).mockResolvedValue(mockResponse)

      // Act
      const result = await getUserProfile(userId)

      // Assert
      expect(result.code).toBe(404)
    })
  })
})
