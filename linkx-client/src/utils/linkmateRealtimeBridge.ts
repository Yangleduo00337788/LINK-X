/**
 * 作者：yangleduo
 */
/**
 * 灵伴 Realtime 语音桥：OpenAI 直连或百炼 DashScope（经 LinkX 服务端 SDP 代理）。
 */

import { API_BASE_URL } from '../config/endpoints'
import { getToken, isWebEnvironment } from '../utils/tokenStorage'
import { getDeviceName, getDeviceType, getOrCreateDeviceId } from '../utils/deviceId'

export type LinkMateRealtimeProvider = 'openai' | 'dashscope'

export interface LinkMateRealtimeConnectOptions {
  ephemeralKey?: string
  realtimeCallsUrl: string
  provider?: LinkMateRealtimeProvider
  voice?: string
  localStream: MediaStream
  onRemoteStream: (stream: MediaStream) => void
  onConnected?: () => void
  onError?: (message: string) => void
  onDisconnected?: () => void
}

export interface LinkMateRealtimeBridgeHandle {
  peerConnection: RTCPeerConnection
  dataChannel: RTCDataChannel | null
  stop: () => void
}

function resolveCallsUrl(url: string, provider?: LinkMateRealtimeProvider): string {
  if (provider === 'dashscope' && url.startsWith('/')) {
    return `${API_BASE_URL}${url}`
  }
  return url
}

async function buildAuthHeaders(
  provider?: LinkMateRealtimeProvider,
  ephemeralKey?: string
): Promise<Record<string, string>> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/sdp',
    'X-Device-Id': getOrCreateDeviceId(),
    'X-Device-Name': getDeviceName(),
    'X-Device-Type': getDeviceType()
  }
  if (provider === 'dashscope') {
    if (!isWebEnvironment()) {
      const token = await getToken('accessToken')
      if (token) {
        headers.Authorization = `Bearer ${token}`
      }
    }
    return headers
  }
  if (!ephemeralKey) {
    throw new Error('缺少 Realtime 临时密钥')
  }
  headers.Authorization = `Bearer ${ephemeralKey}`
  return headers
}

function normalizeSdp(sdp: string): string {
  let normalized = sdp.trim().replace(/\r?\n/g, '\r\n')
  if (!normalized.endsWith('\r\n')) {
    normalized += '\r\n'
  }
  return normalized
}

async function waitForIceGathering(pc: RTCPeerConnection, timeoutMs = 8000): Promise<void> {
  if (pc.iceGatheringState === 'complete') {
    return
  }
  await new Promise<void>(resolve => {
    const timer = window.setTimeout(() => {
      pc.removeEventListener('icegatheringstatechange', onChange)
      resolve()
    }, timeoutMs)
    const onChange = () => {
      if (pc.iceGatheringState === 'complete') {
        window.clearTimeout(timer)
        pc.removeEventListener('icegatheringstatechange', onChange)
        resolve()
      }
    }
    pc.addEventListener('icegatheringstatechange', onChange)
  })
}

function parseSdpExchangeError(text: string, status: number): string {
  const trimmed = text.trim()
  if (!trimmed) {
    return `Realtime SDP 交换失败 (${status})`
  }
  try {
    const json = JSON.parse(trimmed) as { message?: string }
    if (typeof json.message === 'string' && json.message.trim()) {
      return json.message.trim()
    }
  } catch {
    /* not json */
  }
  return trimmed
}

function sendDashScopeSessionUpdate(channel: RTCDataChannel, voice?: string) {
  if (channel.readyState !== 'open') {
    return
  }
  const resolvedVoice = voice?.trim() || 'longanqian'
  channel.send(
    JSON.stringify({
      type: 'session.update',
      session: {
        modalities: ['text', 'audio'],
        voice: resolvedVoice,
        input_audio_format: 'pcm',
        output_audio_format: 'pcm',
        turn_detection: {
          type: 'server_vad',
          threshold: 0.5,
          prefix_padding_ms: 500,
          silence_duration_ms: 800
        }
      }
    })
  )
}

/**
 * 建立与 Realtime 的 WebRTC 连接，把麦克风送入模型并回传远端音轨。
 */
export async function connectLinkMateRealtime(
  options: LinkMateRealtimeConnectOptions
): Promise<LinkMateRealtimeBridgeHandle> {
  const pc = new RTCPeerConnection()
  let dataChannel: RTCDataChannel | null = null
  let stopped = false

  const stop = () => {
    if (stopped) return
    stopped = true
    try {
      dataChannel?.close()
    } catch {
      /* ignore */
    }
    dataChannel = null
    try {
      pc.close()
    } catch {
      /* ignore */
    }
  }

  pc.ontrack = evt => {
    if (stopped) return
    const stream = evt.streams[0] || new MediaStream([evt.track])
    options.onRemoteStream(stream)
  }

  pc.onconnectionstatechange = () => {
    if (stopped) return
    const state = pc.connectionState
    if (state === 'connected') {
      options.onConnected?.()
    } else if (state === 'failed') {
      options.onError?.(`Realtime 连接${state}`)
      options.onDisconnected?.()
    } else if (state === 'disconnected') {
      options.onError?.(`Realtime 连接${state}`)
    } else if (state === 'closed') {
      options.onDisconnected?.()
    }
  }

  const audioTrack = options.localStream.getAudioTracks()[0]
  if (!audioTrack) {
    stop()
    throw new Error('缺少麦克风音轨')
  }
  pc.addTrack(audioTrack, options.localStream)

  dataChannel = pc.createDataChannel('oai-events')
  const onDataChannelMessage = (data: string) => {
    if (stopped || options.provider !== 'dashscope') return
    try {
      const event = JSON.parse(data) as { type?: string }
      if (event.type === 'session.created') {
        sendDashScopeSessionUpdate(dataChannel!, options.voice)
      }
    } catch {
      /* ignore */
    }
  }
  dataChannel.onmessage = evt => onDataChannelMessage(String(evt.data))
  pc.ondatachannel = evt => {
    const channel = evt.channel
    channel.onmessage = e => onDataChannelMessage(String(e.data))
  }

  const offer = await pc.createOffer()
  await pc.setLocalDescription(offer)
  await waitForIceGathering(pc)
  const localSdp = pc.localDescription?.sdp
  if (!localSdp) {
    stop()
    throw new Error('创建 SDP offer 失败')
  }

  const callsUrl = resolveCallsUrl(options.realtimeCallsUrl, options.provider)
  const headers = await buildAuthHeaders(options.provider, options.ephemeralKey)

  const response = await fetch(callsUrl, {
    method: 'POST',
    headers,
    body: localSdp,
    credentials: isWebEnvironment() ? 'include' : 'same-origin'
  })

  if (!response.ok) {
    const errText = await response.text().catch(() => '')
    stop()
    throw new Error(parseSdpExchangeError(errText, response.status))
  }

  const answerSdp = await response.text()
  if (!answerSdp.trim()) {
    stop()
    throw new Error('Realtime 未返回 SDP answer')
  }
  await pc.setRemoteDescription({ type: 'answer', sdp: normalizeSdp(answerSdp) })

  return { peerConnection: pc, dataChannel, stop }
}
