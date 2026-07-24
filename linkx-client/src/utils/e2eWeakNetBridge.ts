/**
 * 浏览器弱网专项探针：仅当 URL 带 ?e2eWeakNet=1 时挂到 window，供 Playwright 调用。
 */
import * as policy from './callNetworkPolicy'

export type E2eWeakNetScenario = {
  id: string
  pass: boolean
  detail: string
}

declare global {
  interface Window {
    __e2eWeakNet?: {
      policy: typeof policy
      runAll: () => E2eWeakNetScenario[]
      decideIce: typeof policy.decideIceRestart
      decideWeakNet: typeof policy.decideWeakNetVideo
    }
  }
}

export function installE2eWeakNetBridge(): void {
  if (typeof window === 'undefined') return
  const enabled = new URLSearchParams(window.location.search).has('e2eWeakNet')
  if (!enabled) return

  const runAll = (): E2eWeakNetScenario[] => {
    const scenarios: E2eWeakNetScenario[] = []

    const ice1 = policy.decideIceRestart({
      attemptsSoFar: 0,
      reason: 'disconnected',
      callType: 'video',
      cameraOn: true,
      isActive: true
    })
    scenarios.push({
      id: 'ice-restart-disable-camera',
      pass: ice1.action === 'restart' && ice1.disableCamera === true,
      detail: JSON.stringify(ice1)
    })

    const iceGiveUp = policy.decideIceRestart({
      attemptsSoFar: policy.ICE_RESTART_MAX_ATTEMPTS,
      reason: 'failed',
      callType: 'video',
      cameraOn: true,
      isActive: true
    })
    scenarios.push({
      id: 'ice-give-up-after-max',
      pass: iceGiveUp.action === 'give_up',
      detail: JSON.stringify(iceGiveUp)
    })

    const weakAccum = policy.decideWeakNetVideo({
      packetsLost: 25,
      packetsReceived: 75,
      callType: 'video',
      cameraOn: true,
      weakNetChecks: 0
    })
    scenarios.push({
      id: 'weaknet-accumulate',
      pass: weakAccum.action === 'accumulate',
      detail: JSON.stringify(weakAccum)
    })

    const weakOff = policy.decideWeakNetVideo({
      packetsLost: 40,
      packetsReceived: 60,
      callType: 'video',
      cameraOn: true,
      weakNetChecks: policy.WEAK_NET_CONFIRM_CHECKS - 1
    })
    scenarios.push({
      id: 'weaknet-disable-camera',
      pass: weakOff.action === 'disable_camera',
      detail: JSON.stringify(weakOff)
    })

    const camFallback = policy.shouldFallbackToVoiceOnCameraDenied('video', {
      name: 'NotAllowedError'
    })
    scenarios.push({
      id: 'camera-fallback-voice',
      pass: camFallback === true,
      detail: String(camFallback)
    })

    // 模拟浏览器 getStats 高丢包 → 决策关视频
    const fakeStatsLoss = policy.computePacketLossRatio(35, 65)
    scenarios.push({
      id: 'stats-loss-ratio',
      pass: fakeStatsLoss != null && fakeStatsLoss > policy.WEAK_NET_LOSS_THRESHOLD,
      detail: String(fakeStatsLoss)
    })

    let panel = document.getElementById('e2e-weaknet-log')
    if (!panel) {
      panel = document.createElement('pre')
      panel.id = 'e2e-weaknet-log'
      panel.setAttribute('data-testid', 'e2e-weaknet-log')
      panel.style.cssText =
        'position:fixed;bottom:8px;left:8px;z-index:99999;max-width:90vw;max-height:40vh;overflow:auto;background:#111;color:#0f0;padding:8px;font-size:12px'
      document.body.appendChild(panel)
    }
    panel.textContent = JSON.stringify(scenarios, null, 2)
    document.documentElement.dataset.e2eWeakNet = scenarios.every(s => s.pass)
      ? 'pass'
      : 'fail'

    return scenarios
  }

  window.__e2eWeakNet = {
    policy,
    runAll,
    decideIce: policy.decideIceRestart,
    decideWeakNet: policy.decideWeakNetVideo
  }
  document.documentElement.dataset.e2eWeakNet = 'ready'
}
