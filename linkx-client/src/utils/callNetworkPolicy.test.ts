import { describe, expect, it } from 'vitest'
import {
  ICE_RESTART_MAX_ATTEMPTS,
  WEAK_NET_CONFIRM_CHECKS,
  WEAK_NET_LOSS_THRESHOLD,
  computePacketLossRatio,
  decideIceRestart,
  decideWeakNetVideo,
  shouldFallbackToVoiceOnCameraDenied
} from './callNetworkPolicy'

describe('callNetworkPolicy — 丢包率', () => {
  it('样本不足时返回 null', () => {
    expect(computePacketLossRatio(2, 10)).toBeNull()
    expect(computePacketLossRatio(0, 29)).toBeNull()
  })

  it('样本足够时计算丢包率', () => {
    expect(computePacketLossRatio(15, 85)).toBeCloseTo(0.15, 5)
    expect(computePacketLossRatio(0, 100)).toBe(0)
  })
})

describe('callNetworkPolicy — ICE restart 模拟', () => {
  it('非活跃通话不操作', () => {
    expect(
      decideIceRestart({
        attemptsSoFar: 0,
        reason: 'failed',
        callType: 'video',
        cameraOn: true,
        isActive: false
      })
    ).toEqual({ action: 'noop' })
  })

  it('未达上限时发起 restart，视频通话应关摄像头降码率', () => {
    const d = decideIceRestart({
      attemptsSoFar: 0,
      reason: 'disconnected',
      callType: 'video',
      cameraOn: true,
      isActive: true
    })
    expect(d).toEqual({
      action: 'restart',
      nextAttempts: 1,
      disableCamera: true,
      message: '网络波动，正在尝试重连…'
    })
  })

  it('语音通话 restart 不关摄像头', () => {
    const d = decideIceRestart({
      attemptsSoFar: 0,
      reason: 'failed',
      callType: 'voice',
      cameraOn: false,
      isActive: true
    })
    expect(d.action).toBe('restart')
    if (d.action === 'restart') {
      expect(d.disableCamera).toBe(false)
      expect(d.message).toContain('ICE')
    }
  })

  it(`达到 ${ICE_RESTART_MAX_ATTEMPTS} 次后放弃并挂断文案`, () => {
    expect(
      decideIceRestart({
        attemptsSoFar: ICE_RESTART_MAX_ATTEMPTS,
        reason: 'failed',
        callType: 'video',
        cameraOn: true,
        isActive: true
      })
    ).toEqual({ action: 'give_up', message: '通话连接已断开' })
  })

  it('第二次 restart 仍可执行（未达上限）', () => {
    const d = decideIceRestart({
      attemptsSoFar: 1,
      reason: 'failed',
      callType: 'video',
      cameraOn: false,
      isActive: true
    })
    expect(d).toMatchObject({ action: 'restart', nextAttempts: 2 })
  })
})

describe('callNetworkPolicy — 弱网关视频模拟', () => {
  it('丢包样本不足时 noop', () => {
    expect(
      decideWeakNetVideo({
        packetsLost: 1,
        packetsReceived: 10,
        callType: 'video',
        cameraOn: true,
        weakNetChecks: 0
      })
    ).toEqual({ action: 'noop' })
  })

  it('低丢包重置计数', () => {
    expect(
      decideWeakNetVideo({
        packetsLost: 5,
        packetsReceived: 95,
        callType: 'video',
        cameraOn: true,
        weakNetChecks: 1
      })
    ).toEqual({ action: 'reset_checks' })
  })

  it('高丢包首次仅累计', () => {
    // 20/100 = 0.2 > 0.12
    expect(
      decideWeakNetVideo({
        packetsLost: 20,
        packetsReceived: 80,
        callType: 'video',
        cameraOn: true,
        weakNetChecks: 0
      })
    ).toEqual({ action: 'accumulate', nextChecks: 1 })
  })

  it(`连续 ${WEAK_NET_CONFIRM_CHECKS} 次高丢包后关视频`, () => {
    const d = decideWeakNetVideo({
      packetsLost: 30,
      packetsReceived: 70,
      callType: 'video',
      cameraOn: true,
      weakNetChecks: WEAK_NET_CONFIRM_CHECKS - 1
    })
    expect(d).toEqual({
      action: 'disable_camera',
      message: '网络较差，已自动关闭视频以保持通话'
    })
  })

  it('语音通话即使高丢包也不关视频', () => {
    expect(
      decideWeakNetVideo({
        packetsLost: 40,
        packetsReceived: 60,
        callType: 'voice',
        cameraOn: false,
        weakNetChecks: 1
      })
    ).toEqual({ action: 'reset_checks' })
  })

  it(`阈值边界：刚好 ${WEAK_NET_LOSS_THRESHOLD} 不触发`, () => {
    // 12/100 = 0.12，条件是 > 而非 >=
    expect(
      decideWeakNetVideo({
        packetsLost: 12,
        packetsReceived: 88,
        callType: 'video',
        cameraOn: true,
        weakNetChecks: 0
      })
    ).toEqual({ action: 'reset_checks' })
  })
})

describe('callNetworkPolicy — 摄像头权限降级', () => {
  it('视频 + NotAllowedError 应降级语音', () => {
    expect(
      shouldFallbackToVoiceOnCameraDenied('video', { name: 'NotAllowedError' })
    ).toBe(true)
  })

  it('语音通话不降级', () => {
    expect(
      shouldFallbackToVoiceOnCameraDenied('voice', { name: 'NotAllowedError' })
    ).toBe(false)
  })

  it('普通错误不降级', () => {
    expect(shouldFallbackToVoiceOnCameraDenied('video', new Error('x'))).toBe(false)
  })
})
