import { test, expect } from '@playwright/test'

/**
 * 浏览器弱网专项：
 * 1) Chromium 内跑与单测同策略的场景（真浏览器环境）
 * 2) CDP 模拟慢网，确认登录壳层仍可用
 * 3) 注入 Fake RTCPeerConnection.getStats 高丢包，验证策略决策
 */
test.describe('Call weak-net (browser)', () => {
  test('e2eWeakNet bridge scenarios all pass in Chromium', async ({ page }) => {
    await page.goto('/?e2eWeakNet=1')
    await expect(page.locator('html')).toHaveAttribute('data-e2e-weak-net', 'ready', {
      timeout: 20000
    })

    const scenarios = await page.evaluate(() => {
      return window.__e2eWeakNet!.runAll()
    })

    expect(scenarios.length).toBeGreaterThanOrEqual(5)
    const failed = scenarios.filter(s => !s.pass)
    expect(failed, JSON.stringify(failed, null, 2)).toEqual([])
    await expect(page.locator('html')).toHaveAttribute('data-e2e-weak-net', 'pass')
    await expect(page.getByTestId('e2e-weaknet-log')).toBeVisible()
  })

  test('CDP Slow-3G: login shell still interactive', async ({ page, context }) => {
    const client = await context.newCDPSession(page)
    await client.send('Network.enable')
    // ~Slow 3G
    await client.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: 400,
      downloadThroughput: (500 * 1024) / 8,
      uploadThroughput: (500 * 1024) / 8,
      connectionType: 'cellular3g'
    })

    await page.goto('/?e2eWeakNet=1', { waitUntil: 'domcontentloaded', timeout: 60000 })
    await expect(page.locator('.login-page')).toBeVisible({ timeout: 30000 })
    await expect(page.locator('.lx-login-btn')).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('data-e2e-weak-net', 'ready', {
      timeout: 30000
    })
  })

  test('Fake getStats high loss → disable camera decision', async ({ page }) => {
    await page.addInitScript(() => {
      class FakeRTCPeerConnection {
        connectionState = 'connected'
        iceConnectionState = 'connected'
        onconnectionstatechange: ((this: FakeRTCPeerConnection) => void) | null = null
        createOffer() {
          return Promise.resolve({ type: 'offer', sdp: 'v=0' })
        }
        setLocalDescription() {
          return Promise.resolve()
        }
        getStats() {
          const map = new Map()
          map.set('inbound', {
            type: 'inbound-rtp',
            packetsLost: 45,
            packetsReceived: 55
          })
          return Promise.resolve(map)
        }
        close() {
          this.connectionState = 'closed'
        }
        addEventListener() {}
        removeEventListener() {}
      }
      // override for e2e
      window.RTCPeerConnection = FakeRTCPeerConnection as unknown as typeof RTCPeerConnection
    })

    await page.goto('/?e2eWeakNet=1')
    await expect(page.locator('html')).toHaveAttribute('data-e2e-weak-net', 'ready', {
      timeout: 20000
    })

    const decision = await page.evaluate(async () => {
      const pc = new RTCPeerConnection()
      const stats = await pc.getStats()
      let lost = 0
      let received = 0
      stats.forEach(r => {
        if (r.type === 'inbound-rtp') {
          lost += Number((r as { packetsLost?: number }).packetsLost || 0)
          received += Number((r as { packetsReceived?: number }).packetsReceived || 0)
        }
      })
      return window.__e2eWeakNet!.decideWeakNet({
        packetsLost: lost,
        packetsReceived: received,
        callType: 'video',
        cameraOn: true,
        weakNetChecks: 1
      })
    })

    expect(decision.action).toBe('disable_camera')
  })

  test('Fake ICE failed → restart then give up path', async ({ page }) => {
    await page.goto('/?e2eWeakNet=1')
    await expect(page.locator('html')).toHaveAttribute('data-e2e-weak-net', 'ready', {
      timeout: 20000
    })

    const path = await page.evaluate(() => {
      const first = window.__e2eWeakNet!.decideIce({
        attemptsSoFar: 0,
        reason: 'failed',
        callType: 'video',
        cameraOn: true,
        isActive: true
      })
      const second = window.__e2eWeakNet!.decideIce({
        attemptsSoFar: 1,
        reason: 'failed',
        callType: 'video',
        cameraOn: false,
        isActive: true
      })
      const third = window.__e2eWeakNet!.decideIce({
        attemptsSoFar: 2,
        reason: 'failed',
        callType: 'video',
        cameraOn: false,
        isActive: true
      })
      return { first, second, third }
    })

    expect(path.first.action).toBe('restart')
    expect(path.second.action).toBe('restart')
    expect(path.third.action).toBe('give_up')
  })
})
