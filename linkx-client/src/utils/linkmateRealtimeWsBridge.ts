/**
 * 作者：yangleduo
 */
/**
 * 百炼 qwen-audio 语音桥：复用已连接的 IM WebSocket（8081），避免另开 Spring WS 握手失败。
 */

import {
  ensureChatSocketConnected,
  isChatSocketConnected,
  onLinkMateVoiceEvent,
  sendLinkMateVoiceClose,
  sendLinkMateVoiceForward,
  sendLinkMateVoiceOpen
} from './chatSocket'
import type { LinkMateRealtimeBridgeHandle } from './linkmateRealtimeBridge'
import { t } from '../i18n'

export type LinkMateVoiceActivity =
  | 'connecting'
  | 'listening'
  | 'thinking'
  | 'speaking'

export interface LinkMateRealtimeWsConnectOptions {
  callId: string
  localStream: MediaStream
  onRemoteStream: (stream: MediaStream) => void
  onConnected?: () => void
  onActivityChange?: (activity: LinkMateVoiceActivity) => void
  onError?: (message: string) => void
  onDisconnected?: () => void
}

const OUTPUT_SAMPLE_RATE = 24000
const INPUT_SAMPLE_RATE = 16000
const MIC_BUFFER_SIZE = 2048

function floatTo16BitLe(input: Float32Array): Uint8Array {
  const out = new Uint8Array(input.length * 2)
  const view = new DataView(out.buffer)
  for (let i = 0; i < input.length; i++) {
    const sample = Math.max(-1, Math.min(1, input[i]))
    const int16 = sample < 0 ? sample * 0x8000 : sample * 0x7fff
    view.setInt16(i * 2, int16, true)
  }
  return out
}

function uint8ToBase64(bytes: Uint8Array): string {
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

function base64ToFloat32Pcm16Le(base64: string): Float32Array | null {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  const sampleCount = Math.floor(bytes.length / 2)
  if (sampleCount <= 0) return null
  // 拷贝到对齐缓冲，避免 byteOffset 非 2 对齐时 Int16Array 抛错/静音
  const aligned = new ArrayBuffer(sampleCount * 2)
  new Uint8Array(aligned).set(bytes.subarray(0, sampleCount * 2))
  const int16 = new Int16Array(aligned)
  const floats = new Float32Array(sampleCount)
  for (let i = 0; i < sampleCount; i++) {
    floats[i] = int16[i] / 32768
  }
  return floats
}

function extractRealtimeErrorMessage(event: Record<string, unknown>): string {
  const err = event.error
  if (err && typeof err === 'object') {
    const nested = err as Record<string, unknown>
    if (typeof nested.message === 'string' && nested.message.trim()) {
      return nested.message.trim()
    }
  }
  if (typeof event.message === 'string' && event.message.trim()) {
    return event.message.trim()
  }
  return t('linkmate.realtimeVoiceError')
}

function shouldReportRealtimeError(event: Record<string, unknown>, sessionReady: boolean): boolean {
  if (!sessionReady) return true
  const err = event.error
  if (!err || typeof err !== 'object') return true
  const code = String((err as Record<string, unknown>).code || '').toLowerCase()
  const msg = String((err as Record<string, unknown>).message || '').toLowerCase()
  // 会话已就绪时，忽略「已有进行中响应」这类竞态噪声
  if (code.includes('already') || msg.includes('already') || msg.includes('active response')) {
    return false
  }
  return true
}

class Pcm24kPlayer {
  private readonly ctx: AudioContext
  private readonly destination: MediaStreamAudioDestinationNode
  private nextPlayTime = 0
  private readonly activeSources = new Set<AudioBufferSourceNode>()

  constructor(ctx: AudioContext, destination: MediaStreamAudioDestinationNode) {
    this.ctx = ctx
    this.destination = destination
  }

  addBase64Pcm(base64: string) {
    const floats = base64ToFloat32Pcm16Le(base64)
    if (!floats) return
    const buffer = this.ctx.createBuffer(1, floats.length, OUTPUT_SAMPLE_RATE)
    buffer.copyToChannel(floats, 0)
    const source = this.ctx.createBufferSource()
    source.buffer = buffer
    source.connect(this.destination)
    const startAt = Math.max(this.nextPlayTime, this.ctx.currentTime)
    source.onended = () => {
      this.activeSources.delete(source)
      try {
        source.disconnect()
      } catch {
        /* ignore */
      }
    }
    this.activeSources.add(source)
    source.start(startAt)
    this.nextPlayTime = startAt + buffer.duration
  }

  /** 打断时必须停掉已调度的 BufferSource，否则新旧音频叠成双声 */
  cancel() {
    for (const source of this.activeSources) {
      try {
        source.stop(0)
      } catch {
        /* already stopped */
      }
      try {
        source.disconnect()
      } catch {
        /* ignore */
      }
    }
    this.activeSources.clear()
    this.nextPlayTime = this.ctx.currentTime
  }
}

/**
 * 通过 IM WebSocket 中继连接百炼 Realtime。
 */
export async function connectLinkMateRealtimeWs(
  options: LinkMateRealtimeWsConnectOptions
): Promise<LinkMateRealtimeBridgeHandle> {
  let stopped = false
  let micStop: (() => void) | null = null
  let sessionReady = false
  let greetingDone = false
  let greetingSent = false
  let offVoiceEvent: (() => void) | null = null
  let isResponding = false
  let hasAudioDelta = false
  let micAllowed = false

  const setActivity = (activity: LinkMateVoiceActivity) => {
    options.onActivityChange?.(activity)
  }

  const playbackCtx = new AudioContext({ sampleRate: OUTPUT_SAMPLE_RATE })
  const destination = playbackCtx.createMediaStreamDestination()
  const player = new Pcm24kPlayer(playbackCtx, destination)

  const stop = () => {
    if (stopped) return
    stopped = true
    player.cancel()
    micStop?.()
    micStop = null
    offVoiceEvent?.()
    offVoiceEvent = null
    try {
      sendLinkMateVoiceClose(options.callId)
    } catch {
      /* ignore */
    }
    playbackCtx.close().catch(() => {
      /* ignore */
    })
  }

  const forwardToUpstream = (payload: Record<string, unknown>) => {
    sendLinkMateVoiceForward(options.callId, JSON.stringify(payload))
  }

  const sendGreeting = () => {
    if (greetingSent || stopped) return
    greetingSent = true
    setActivity('thinking')
    // 注入一条用户文本再触发回复，比空 response.create 更稳
    forwardToUpstream({
      type: 'conversation.item.create',
      item: {
        type: 'message',
        role: 'user',
        content: [{ type: 'input_text', text: t('linkmate.voiceGreeting') }]
      }
    })
    forwardToUpstream({
      type: 'response.create',
      response: {
        modalities: ['audio', 'text']
      }
    })
  }

  const markGreetingDoneAndOpenMic = () => {
    if (greetingDone) return
    greetingDone = true
    micAllowed = true
    startMicPump()
  }

  const playAudioDelta = (delta: string) => {
    if (!hasAudioDelta) {
      hasAudioDelta = true
      setActivity('speaking')
    }
    player.addBase64Pcm(delta)
  }

  const startMicPump = () => {
    if (stopped || micStop || !micAllowed) return
    const captureCtx = new AudioContext({ sampleRate: INPUT_SAMPLE_RATE })
    const source = captureCtx.createMediaStreamSource(options.localStream)
    const processor = captureCtx.createScriptProcessor(MIC_BUFFER_SIZE, 1, 1)
    // 必须进图才会回调，但增益置 0，避免麦克风回放到扬声器造成回声/双声
    const silent = captureCtx.createGain()
    silent.gain.value = 0
    processor.onaudioprocess = evt => {
      if (stopped || !sessionReady || !micAllowed || !isChatSocketConnected()) return
      const input = evt.inputBuffer.getChannelData(0)
      const pcm = floatTo16BitLe(input)
      try {
        forwardToUpstream({
          type: 'input_audio_buffer.append',
          audio: uint8ToBase64(pcm)
        })
      } catch {
        /* ignore */
      }
    }
    source.connect(processor)
    processor.connect(silent)
    silent.connect(captureCtx.destination)
    micStop = () => {
      processor.disconnect()
      source.disconnect()
      silent.disconnect()
      captureCtx.close().catch(() => {
        /* ignore */
      })
    }
  }

  setActivity('connecting')
  await ensureChatSocketConnected()
  if (!isChatSocketConnected()) {
    throw new Error(t('linkmate.voiceWsNotConnected'))
  }

  if (playbackCtx.state === 'suspended') {
    await playbackCtx.resume()
  }

  offVoiceEvent = onLinkMateVoiceEvent(event => {
    if (stopped) return
    const type = typeof event.type === 'string' ? event.type : ''

    if (type === 'session.updated') {
      sessionReady = true
      options.onRemoteStream(destination.stream)
      options.onConnected?.()
      // 先问候，问候完成后再开麦，避免环境音打断开场白
      sendGreeting()
      return
    }

    if (type === 'response.created') {
      isResponding = true
      hasAudioDelta = false
      setActivity('thinking')
      return
    }

    if (type === 'response.done') {
      isResponding = false
      hasAudioDelta = false
      markGreetingDoneAndOpenMic()
      setActivity('listening')
      return
    }

    if (type === 'input_audio_buffer.speech_started') {
      // 开场白完成前忽略用户打断，避免问候被取消后卡住
      if (!greetingDone) return
      player.cancel()
      if (isResponding) {
        try {
          forwardToUpstream({ type: 'response.cancel' })
        } catch {
          /* ignore */
        }
      }
      setActivity('listening')
      return
    }

    if (type === 'input_audio_buffer.speech_stopped') {
      if (!greetingDone) return
      if (!hasAudioDelta) {
        setActivity('thinking')
      }
      return
    }

    if (
      (type === 'response.audio.delta' || type === 'response.output_audio.delta') &&
      typeof event.delta === 'string'
    ) {
      playAudioDelta(event.delta)
      return
    }

    if (type === 'error') {
      const msg = extractRealtimeErrorMessage(event)
      console.warn('[LinkMate voice]', msg, event)
      if (shouldReportRealtimeError(event, sessionReady)) {
        options.onError?.(msg)
      }
      // 问候失败时仍打开麦克风，避免一直卡在「正在思考」
      if (!greetingDone) {
        markGreetingDoneAndOpenMic()
        setActivity('listening')
      }
      return
    }

    if (type === 'session.closed') {
      options.onDisconnected?.()
    }
  })

  sendLinkMateVoiceOpen(options.callId)

  return {
    peerConnection: null as unknown as RTCPeerConnection,
    dataChannel: null,
    stop
  }
}
