import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn()
  }
}))

import { apiClient } from './client'
import {
  getDriveStorage,
  expandDriveStorage,
  listDriveItems,
  getDriveBreadcrumb,
  createDriveFolder,
  uploadDriveFile,
  getDriveFile,
  updateDriveFile,
  deleteDriveFile,
  updateDriveFolder,
  deleteDriveFolder,
  batchDeleteDriveItems,
  batchMoveDriveItems,
  addDriveTag,
  removeDriveTag,
  listDriveActivities,
  createDriveShare,
  revokeDriveShare
} from './drive'

describe('api/drive', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getDriveStorage 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getDriveStorage()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('expandDriveStorage 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await expandDriveStorage()
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('listDriveItems 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listDriveItems()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('getDriveBreadcrumb 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getDriveBreadcrumb()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('createDriveFolder 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await createDriveFolder('x')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('uploadDriveFile 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await uploadDriveFile(new File(['x'], 'a.bin'))
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('getDriveFile 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getDriveFile('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('updateDriveFile 应调用 apiClient', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ code: 200, data: null } as any)
    await updateDriveFile('1')
    expect(apiClient.patch).toHaveBeenCalled()
  })

  it('deleteDriveFile 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await deleteDriveFile('1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('updateDriveFolder 应调用 apiClient', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ code: 200, data: null } as any)
    await updateDriveFolder('1')
    expect(apiClient.patch).toHaveBeenCalled()
  })

  it('deleteDriveFolder 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await deleteDriveFolder('1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('batchDeleteDriveItems 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await batchDeleteDriveItems('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('batchMoveDriveItems 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await batchMoveDriveItems('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('addDriveTag 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await addDriveTag('1', '1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('removeDriveTag 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await removeDriveTag('1', '1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('listDriveActivities 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listDriveActivities()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('createDriveShare 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await createDriveShare()
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('revokeDriveShare 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await revokeDriveShare('1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

})
