import { describe, it, expect } from 'vitest'
import { APP_CLIENT_CHANNEL, APP_CLIENT_VERSION } from './appVersion'

describe('appVersion', () => {
  it('导出客户端版本号', () => {
    expect(APP_CLIENT_VERSION).toMatch(/^\d+\.\d+\.\d+/)
  })

  it('导出客户端渠道', () => {
    expect(APP_CLIENT_CHANNEL).toMatch(/^(stable|beta|dev)$/)
  })
})
