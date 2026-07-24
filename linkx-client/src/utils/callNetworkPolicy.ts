/**
 * 通话弱网 / ICE 重连策略（纯函数，便于单测模拟）。
 * 真实 RTCPeerConnection 行为由 call store 调用本模块决策后执行。
 */

export const ICE_RESTART_MAX_ATTEMPTS = 2
export const WEAK_NET_LOSS_THRESHOLD = 0.12
export const WEAK_NET_CONFIRM_CHECKS = 2
export const WEAK_NET_MIN_PACKETS = 30

export type CallMediaType = 'voice' | 'video'

export type IceRestartDecision =
  | { action: 'noop' }
  | { action: 'give_up'; message: string }
  | {
      action: 'restart'
      nextAttempts: number
      disableCamera: boolean
      message: string
    }

export type WeakNetDecision =
  | { action: 'noop' }
  | { action: 'reset_checks' }
  | { action: 'accumulate'; nextChecks: number }
  | { action: 'disable_camera'; message: string }

/** 根据 inbound-rtp 统计计算丢包率；样本不足返回 null。 */
export function computePacketLossRatio(
  packetsLost: number,
  packetsReceived: number,
  minPackets = WEAK_NET_MIN_PACKETS
): number | null {
  const lost = Math.max(0, packetsLost)
  const received = Math.max(0, packetsReceived)
  const total = lost + received
  if (total < minPackets) return null
  return lost / total
}

/**
 * ICE 连接 failed/disconnected 时的决策。
 * @param attemptsSoFar 已尝试次数（调用前累计值，不含本次）
 */
export function decideIceRestart(input: {
  attemptsSoFar: number
  reason: string
  callType: CallMediaType
  cameraOn: boolean
  isActive: boolean
}): IceRestartDecision {
  if (!input.isActive) return { action: 'noop' }
  if (input.attemptsSoFar >= ICE_RESTART_MAX_ATTEMPTS) {
    return { action: 'give_up', message: '通话连接已断开' }
  }
  const nextAttempts = input.attemptsSoFar + 1
  const disableCamera =
    input.callType === 'video' && input.cameraOn && nextAttempts >= 1
  const message =
    input.reason === 'disconnected'
      ? '网络波动，正在尝试重连…'
      : '连接失败，正在尝试 ICE 重连…'
  return { action: 'restart', nextAttempts, disableCamera, message }
}

/**
 * 弱网检测：连续确认高丢包后关视频降码率。
 * @param weakNetChecks 当前已累计的高丢包次数
 */
export function decideWeakNetVideo(input: {
  packetsLost: number
  packetsReceived: number
  callType: CallMediaType
  cameraOn: boolean
  weakNetChecks: number
}): WeakNetDecision {
  const loss = computePacketLossRatio(input.packetsLost, input.packetsReceived)
  if (loss == null) return { action: 'noop' }

  const highLoss =
    loss > WEAK_NET_LOSS_THRESHOLD && input.callType === 'video' && input.cameraOn
  if (!highLoss) return { action: 'reset_checks' }

  const nextChecks = input.weakNetChecks + 1
  if (nextChecks >= WEAK_NET_CONFIRM_CHECKS) {
    return {
      action: 'disable_camera',
      message: '网络较差，已自动关闭视频以保持通话'
    }
  }
  return { action: 'accumulate', nextChecks }
}

/** 摄像头权限拒绝时是否应降级为语音。 */
export function shouldFallbackToVoiceOnCameraDenied(
  callType: CallMediaType,
  cameraError: unknown
): boolean {
  if (callType !== 'video') return false
  const name =
    cameraError && typeof cameraError === 'object' && 'name' in cameraError
      ? String((cameraError as { name?: string }).name || '')
      : ''
  return (
    name === 'NotAllowedError' ||
    name === 'NotFoundError' ||
    name === 'NotReadableError' ||
    name === 'OverconstrainedError'
  )
}
