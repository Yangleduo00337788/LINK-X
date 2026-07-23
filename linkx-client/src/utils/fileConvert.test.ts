import { describe, it, expect } from 'vitest'
import { dataUrlToFile } from './fileConvert'

describe('fileConvert', () => {
  it('dataUrlToFile 解析 mime 与文件名', () => {
    const dataUrl = 'data:image/png;base64,aGVsbG8='
    const file = dataUrlToFile(dataUrl, 'x.png')
    expect(file.name).toBe('x.png')
    expect(file.type).toBe('image/png')
    expect(file.size).toBeGreaterThan(0)
  })
})
