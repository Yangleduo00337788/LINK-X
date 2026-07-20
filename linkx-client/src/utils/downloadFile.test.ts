import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('../stores/appSettings', () => ({
  useAppSettingsStore: () => ({
    downloadPath: '',
    downloadAskEveryTime: true
  })
}))

import { downloadFileWithSettings } from './downloadFile'

describe('downloadFileWithSettings', () => {
  beforeEach(() => {
    Reflect.deleteProperty(window, 'electronAPI')
  })

  it('returns error when url is empty', async () => {
    const result = await downloadFileWithSettings('', 'a.txt')
    expect(result.ok).toBe(false)
  })

  it('uses electronAPI.downloadFile for http urls', async () => {
    const downloadFile = vi.fn().mockResolvedValue({ ok: true, path: 'D:\\dl\\a.txt' })
    ;(window as Window & { electronAPI?: unknown }).electronAPI = { downloadFile }

    const result = await downloadFileWithSettings('https://example.com/a.txt', 'a.txt')
    expect(downloadFile).toHaveBeenCalledWith({
      url: 'https://example.com/a.txt',
      fileName: 'a.txt',
      directory: undefined,
      askEveryTime: true
    })
    expect(result).toEqual({ ok: true, path: 'D:\\dl\\a.txt' })
  })
})
