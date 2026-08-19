/**
 * 作者：yangleduo
 */
/**
 * 音视频通话 Store：信令状态 + WebRTC PeerConnection
 */

import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import * as callApi from '../api/call'
import type { CallEventPayload } from '../api/call'
import * as linkmateApi from '../api/linkmate'
import { resolveApiErrorMessage } from '../api/client'
import { startCallRing, stopCallRing, playCallConnect, playCallEnd } from '../utils/callSounds'
import {
  decideIceRestart,
  decideWeakNetVideo,
  shouldFallbackToVoiceOnCameraDenied
} from '../utils/callNetworkPolicy'
import { resolveIceServers } from '../utils/iceServers'
import {
  connectLinkMateRealtime,
  type LinkMateRealtimeBridgeHandle
} from '../utils/linkmateRealtimeBridge'
import { getLinkMateLogoUrl } from '../utils/linkmateLogo'
import { t } from '../i18n'

export type CallPhase = 'idle' | 'outgoing' | 'incoming' | 'connecting' | 'connected' | 'ended'
export type CallRole = 'caller' | 'callee' | null
export type CallType = 'voice' | 'video'
export type CallPeerKind = 'human' | 'linkmate'

/** PeerConnection 不适合放入 Pinia 响应式 state，用模块变量持有 */
let peerConnection: RTCPeerConnection | null = null
let pendingCandidates: RTCIceCandidateInit[] = []
/** 串行化 getUserMedia，避免接听与收 offer 并发抢占摄像头 */
let mediaChain: Promise<unknown> = Promise.resolve()
/** 串行化信令处理，避免 offer/ICE 交错 */
let signalQueue: Promise<void> = Promise.resolve()
let iceRestartAttempts = 0
let weakNetChecks = 0
let statsTimer: ReturnType<typeof setInterval> | null = null
/** 振铃超时：超时后主叫按未接听取消，被叫按拒绝 */
let ringTimer: ReturnType<typeof setTimeout> | null = null
const RING_TIMEOUT_MS = 55_000
/** 呼叫灵伴时短振铃，模拟接听感 */
const LINKMATE_RING_MS = 900
let linkMateBridge: LinkMateRealtimeBridgeHandle | null = null
let linkMateConnectToken = 0

function clearRingTimer() {
  if (ringTimer) {
    clearTimeout(ringTimer)
    ringTimer = null
  }
}

function stopLinkMateBridge() {
  if (linkMateBridge) {
    try {
      linkMateBridge.stop()
    } catch {
      /* ignore */
    }
    linkMateBridge = null
  }
}

interface NormalizedCallEvent {
  callId: string
  conversationId: string
  callType: CallType
  fromUserId: string
  fromNickname: string
  fromAvatar: string
  signalType?: 'offer' | 'answer' | 'ice-candidate'
  sdp?: string
  candidate?: string
}

function normalizeEvent(raw: CallEventPayload): NormalizedCallEvent {
  return {
    callId: String(raw.callId || ''),
    conversationId: String(raw.conversationId || ''),
    callType: raw.callType === 'video' ? 'video' : 'voice',
    fromUserId: String(raw.fromUserId || ''),
    fromNickname: raw.fromNickname || '',
    fromAvatar: raw.fromAvatar || '',
    signalType: raw.signalType,
    sdp: raw.sdp,
    candidate: raw.candidate
  }
}

export const useCallStore = defineStore('call', {
  state: () => ({
    phase: 'idle' as CallPhase,
    role: null as CallRole,
    callId: null as string | null,
    conversationId: null as string | null,
    callType: 'voice' as CallType,
    peerKind: 'human' as CallPeerKind,
    peerName: '',
    peerAvatar: '',
    peerUserId: '' as string,
    micOn: true,
    cameraOn: true,
    errorMessage: '' as string,
    connectedAt: 0,
    localStream: null as MediaStream | null,
    remoteStream: null as MediaStream | null
  }),

  getters: {
    isActive(state): boolean {
      return state.phase !== 'idle' && state.phase !== 'ended'
    },
    showVoiceUi(state): boolean {
      return (
        state.callType === 'voice' &&
        (state.phase === 'outgoing' ||
          state.phase === 'connecting' ||
          state.phase === 'connected')
      )
    },
    showVideoUi(state): boolean {
      return (
        state.callType === 'video' &&
        (state.phase === 'outgoing' ||
          state.phase === 'connecting' ||
          state.phase === 'connected')
      )
    },
    showIncomingUi(state): boolean {
      return state.phase === 'incoming'
    }
  },

  actions: {
    async startOutgoing(opts: {
      conversationId: string
      callType: CallType
      peerName: string
      peerAvatar?: string
      peerUserId?: string
    }) {
      if (this.isActive) {
        throw new Error(t('errors.callInProgress'))
      }
      const res = await callApi.inviteCall({
        conversationId: opts.conversationId,
        callType: opts.callType
      })
      if (res.code !== 200 || !res.data?.callId) {
        throw new Error(res.message || t('errors.callInviteFail'))
      }
      this.role = 'caller'
      this.callId = res.data.callId
      this.conversationId = String(res.data.conversationId ?? opts.conversationId)
      this.callType = opts.callType
      this.peerKind = 'human'
      this.peerName = res.data.peerNickname || opts.peerName
      this.peerAvatar = res.data.peerAvatar || opts.peerAvatar || ''
      this.peerUserId = String(res.data.peerUserId || opts.peerUserId || '')
      this.phase = 'outgoing'
      this.errorMessage = ''
      this.micOn = true
      this.cameraOn = opts.callType === 'video'
      startCallRing()
      this.armRingTimeout()
      // 主叫在振铃阶段即打开本地媒体：视频可预览，接通后立刻有音轨
      void this.ensureLocalMedia().catch(e => {
        const name = (e as DOMException)?.name
        this.errorMessage =
          name === 'NotAllowedError'
            ? t('errors.mediaPermissionDenied')
            : name === 'NotReadableError'
              ? t('errors.mediaDeviceBusy')
              : (e as Error).message || t('errors.mediaOpenFail')
      })
    },

    /**
     * 打给灵伴：复用 VoiceCallModal，媒体走 OpenAI Realtime WebRTC。
     */
    async startLinkMateVoiceCall() {
      if (this.isActive) {
        throw new Error(t('errors.callInProgress'))
      }
      let res: Awaited<ReturnType<typeof linkmateApi.startVoiceCall>>
      try {
        res = await linkmateApi.startVoiceCall()
      } catch (err) {
        throw new Error(resolveApiErrorMessage(err, t('linkmate.voiceCallFail')))
      }
      if (res.code !== 200 || !res.data?.callId || !res.data.realtimeCallsUrl) {
        throw new Error(res.message || t('linkmate.voiceCallFail'))
      }
      const provider = res.data.provider
      if (provider !== 'dashscope' && !res.data.ephemeralKey) {
        throw new Error(res.message || t('linkmate.voiceCallFail'))
      }

      const token = ++linkMateConnectToken
      this.role = 'caller'
      this.callId = res.data.callId
      this.conversationId = null
      this.callType = 'voice'
      this.peerKind = 'linkmate'
      this.peerName = res.data.peerNickname || t('linkmate.name')
      this.peerAvatar = getLinkMateLogoUrl()
      this.peerUserId = '0'
      this.phase = 'outgoing'
      this.errorMessage = ''
      this.micOn = true
      this.cameraOn = false
      this.connectedAt = 0
      startCallRing()

      try {
        await this.ensureLocalMedia()
        if (token !== linkMateConnectToken || this.callId !== res.data.callId) {
          return
        }
        await new Promise<void>(resolve => {
          window.setTimeout(resolve, LINKMATE_RING_MS)
        })
        if (token !== linkMateConnectToken || this.callId !== res.data.callId) {
          return
        }
        clearRingTimer()
        stopCallRing()
        this.phase = 'connecting'
        playCallConnect()

        const local = this.localStream
        if (!local) {
          throw new Error(t('errors.mediaOpenFail'))
        }

        const bridge = await connectLinkMateRealtime({
          ephemeralKey: res.data.ephemeralKey,
          realtimeCallsUrl: res.data.realtimeCallsUrl,
          provider: provider === 'dashscope' ? 'dashscope' : 'openai',
          voice: res.data.voice,
          localStream: local,
          onRemoteStream: stream => {
            if (token !== linkMateConnectToken) return
            this.remoteStream = markRaw(stream)
            this.phase = 'connected'
            if (!this.connectedAt) this.connectedAt = Date.now()
          },
          onConnected: () => {
            if (token !== linkMateConnectToken) return
            this.phase = 'connected'
            if (!this.connectedAt) this.connectedAt = Date.now()
          },
          onError: message => {
            if (token !== linkMateConnectToken) return
            this.errorMessage = message || t('linkmate.voiceCallFail')
          },
          onDisconnected: () => {
            if (token !== linkMateConnectToken) return
            if (this.peerKind === 'linkmate' && this.isActive) {
              void this.hangup()
            }
          }
        })
        if (token !== linkMateConnectToken) {
          bridge.stop()
          return
        }
        linkMateBridge = bridge
        if (this.phase !== 'connected') {
          this.phase = 'connected'
          if (!this.connectedAt) this.connectedAt = Date.now()
        }
      } catch (e) {
        const callId = this.callId
        this.errorMessage = (e as Error).message || t('linkmate.voiceCallFail')
        if (callId) {
          void linkmateApi.hangupVoiceCall(callId, 0).catch(() => {
            /* ignore */
          })
        }
        this.cleanupLocal()
        throw e
      }
    },

    handleRemoteEvent(action: string, raw: CallEventPayload) {
      const event = normalizeEvent(raw)
      if (!event.callId) return

      switch (action) {
        case 'call_invite':
          void this.onInvite(event)
          break
        case 'call_accept':
          void this.onAccept(event)
          break
        case 'call_reject':
          this.onRemoteEnd(t('errors.peerRejected'))
          break
        case 'call_cancel':
          this.onRemoteEnd(t('errors.peerCancelled'))
          break
        case 'call_hangup':
          this.onRemoteEnd(t('errors.peerHungUp'))
          break
        case 'call_signal':
          signalQueue = signalQueue
            .then(() => this.onSignal(event))
            .catch(err => {
              console.error('处理通话信令失败:', err)
              const name = (err as DOMException)?.name
              if (name === 'NotReadableError' || name === 'NotAllowedError') {
                this.errorMessage =
                  name === 'NotAllowedError'
                    ? t('errors.mediaPermissionDenied')
                    : t('errors.mediaDeviceBusy')
              }
            })
          break
        case 'call_reconnect':
          if (this.callId === event.callId && this.isActive) {
            void this.tryIceRestart('peer_reconnect')
          }
          break
        case 'call_device_switch': {
          if (this.callId !== event.callId) break
          const rawMap = raw as unknown as Record<string, unknown>
          const deviceType = String(rawMap.deviceType || '')
          const enabled = rawMap.enabled === true
          if (deviceType === 'video' && !enabled) {
            this.errorMessage = t('errors.peerCameraOff')
          } else if (deviceType === 'audio' && !enabled) {
            this.errorMessage = t('errors.peerMicOff')
          } else {
            this.errorMessage = ''
          }
          break
        }
        default:
          break
      }
    },

    async acceptIncoming() {
      if (this.phase !== 'incoming' || !this.callId) return
      clearRingTimer()
      try {
        const res = await callApi.acceptCall(this.callId)
        if (res.code !== 200) {
          throw new Error(res.message || t('errors.acceptCallFail'))
        }
        this.phase = 'connecting'
        playCallConnect()
        // 被叫只建 PeerConnection；媒体在收到 offer 后再采集，避免与 ensureLocalMedia 并发抢设备
        await this.ensurePeerConnection()
      } catch (e) {
        this.errorMessage = (e as Error).message || t('errors.acceptCallFail')
        this.cleanupLocal()
      }
    },

    async rejectIncoming() {
      const callId = this.callId
      if (!callId) {
        this.cleanupLocal()
        return
      }
      try {
        await callApi.rejectCall(callId)
      } catch {
        /* ignore */
      }
      this.cleanupLocal()
    },

    async hangup() {
      const callId = this.callId
      const peerKind = this.peerKind
      const wasRingingCaller = this.role === 'caller' && this.phase === 'outgoing'
      const durationSec =
        this.connectedAt > 0 ? Math.max(1, Math.floor((Date.now() - this.connectedAt) / 1000)) : 0
      if (callId) {
        try {
          if (peerKind === 'linkmate') {
            await linkmateApi.hangupVoiceCall(callId, durationSec || undefined)
          } else if (wasRingingCaller) {
            await callApi.cancelCall(callId)
          } else {
            await callApi.hangupCall(callId)
          }
        } catch {
          /* ignore */
        }
      }
      this.cleanupLocal()
    },

    toggleMic() {
      this.micOn = !this.micOn
      this.localStream?.getAudioTracks().forEach(t => {
        t.enabled = this.micOn
      })
      if (this.peerKind === 'linkmate') return
      if (this.callId && this.phase === 'connected') {
        void callApi.switchCallDevice(this.callId, 'audio', this.micOn).catch(() => {
          /* ignore */
        })
      }
    },

    async toggleCamera() {
      if (this.callType !== 'video') return
      this.cameraOn = !this.cameraOn
      const videoTracks = this.localStream?.getVideoTracks() ?? []
      if (this.cameraOn && videoTracks.length === 0) {
        await this.ensureLocalMedia()
      } else {
        videoTracks.forEach(t => {
          t.enabled = this.cameraOn
        })
      }
      if (this.callId && (this.phase === 'connected' || this.phase === 'connecting')) {
        void callApi.switchCallDevice(this.callId, 'video', this.cameraOn).catch(() => {
          /* ignore */
        })
      }
    },

    async onInvite(event: NormalizedCallEvent) {
      if (this.isActive) {
        try {
          await callApi.rejectCall(event.callId)
        } catch {
          /* ignore */
        }
        return
      }
      this.role = 'callee'
      this.callId = event.callId
      this.conversationId = event.conversationId
      this.callType = event.callType
      this.peerKind = 'human'
      this.peerName = event.fromNickname || t('defaults.friend')
      this.peerAvatar = event.fromAvatar || ''
      this.peerUserId = event.fromUserId
      this.phase = 'incoming'
      this.micOn = true
      this.cameraOn = event.callType === 'video'
      this.errorMessage = ''
      startCallRing()
      this.armRingTimeout()
      if (event.callType === 'video') {
        void this.ensureLocalMedia().catch(() => {
          /* 预览失败仍可语音接听 */
        })
      }
    },

    armRingTimeout() {
      clearRingTimer()
      ringTimer = setTimeout(() => {
        ringTimer = null
        if (this.phase !== 'outgoing' && this.phase !== 'incoming') return
        const callId = this.callId
        if (!callId) {
          this.cleanupLocal()
          return
        }
        void (async () => {
          try {
            if (this.role === 'caller') {
              await callApi.cancelCall(callId, 'timeout')
              this.errorMessage = t('errors.peerNoAnswer')
            } else {
              await callApi.rejectCall(callId)
              this.errorMessage = t('errors.callNotAnswered')
            }
          } catch {
            /* ignore */
          }
          this.cleanupLocal()
        })()
      }, RING_TIMEOUT_MS)
    },

    async onAccept(event: NormalizedCallEvent) {
      if (this.role !== 'caller' || this.callId !== event.callId) return
      if (this.phase !== 'outgoing') return
      clearRingTimer()
      this.phase = 'connecting'
      playCallConnect()
      try {
        await this.ensurePeerConnection()
        await this.ensureLocalMedia()
        await this.createAndSendOffer()
      } catch (e) {
        this.errorMessage = (e as Error).message || t('errors.connectCallFail')
        await this.hangup()
      }
    },

    onRemoteEnd(message: string) {
      if (!this.isActive && this.phase !== 'incoming') return
      this.errorMessage = message
      this.cleanupLocal()
    },

    async onSignal(event: NormalizedCallEvent) {
      if (!this.callId || this.callId !== event.callId) return
      if (!event.signalType) return

      await this.ensurePeerConnection()
      const pc = peerConnection!

      if (event.signalType === 'offer' && event.sdp) {
        await pc.setRemoteDescription({ type: 'offer', sdp: event.sdp })
        await this.flushPendingCandidates()
        if (this.role === 'callee') {
          await this.ensureLocalMedia()
          const answer = await pc.createAnswer()
          await pc.setLocalDescription(answer)
          await callApi.signalCall({
            callId: this.callId!,
            signalType: 'answer',
            sdp: answer.sdp
          })
          if (this.phase === 'connecting' || this.phase === 'outgoing') {
            this.phase = 'connecting'
          }
        }
        return
      }

      if (event.signalType === 'answer' && event.sdp) {
        // 重协商时已有 remoteDescription，必须按 signalingState 接受 answer
        if (pc.signalingState !== 'have-local-offer') return
        await pc.setRemoteDescription({ type: 'answer', sdp: event.sdp })
        await this.flushPendingCandidates()
        return
      }

      if (event.signalType === 'ice-candidate' && event.candidate) {
        const init = JSON.parse(event.candidate) as RTCIceCandidateInit
        if (!pc.remoteDescription) {
          pendingCandidates.push(init)
        } else {
          await pc.addIceCandidate(init)
        }
      }
    },

    async ensurePeerConnection() {
      if (peerConnection) return
      const pc = new RTCPeerConnection({ iceServers: resolveIceServers() })
      peerConnection = pc
      pendingCandidates = []

      pc.onicecandidate = evt => {
        if (!evt.candidate || !this.callId) return
        void callApi.signalCall({
          callId: this.callId,
          signalType: 'ice-candidate',
          candidate: JSON.stringify(evt.candidate.toJSON())
        })
      }

      pc.ontrack = evt => {
        // 音视频可能分多次 ontrack；始终合并到同一 MediaStream，避免后到的轨覆盖先到的轨
        if (!this.remoteStream) {
          this.remoteStream = markRaw(new MediaStream())
        }
        const stream = this.remoteStream
        if (!stream.getTrackById(evt.track.id)) {
          stream.addTrack(evt.track)
        }
        // 重新赋值以触发 video/audio 的 watch
        this.remoteStream = markRaw(stream)
        this.phase = 'connected'
        if (!this.connectedAt) this.connectedAt = Date.now()
      }

      pc.onconnectionstatechange = () => {
        const state = pc.connectionState
        if (state === 'connected') {
          this.phase = 'connected'
          if (!this.connectedAt) this.connectedAt = Date.now()
          iceRestartAttempts = 0
          this.startWeakNetWatch()
        } else if (state === 'failed' || state === 'disconnected') {
          void this.tryIceRestart(state)
        } else if (state === 'closed') {
          if (this.isActive) {
            this.errorMessage = t('errors.callDisconnected')
            this.cleanupLocal()
          }
        }
      }
    },

    async tryIceRestart(reason: string) {
      const pc = peerConnection
      if (!pc || !this.callId) return
      const decision = decideIceRestart({
        attemptsSoFar: iceRestartAttempts,
        reason,
        callType: this.callType,
        cameraOn: this.cameraOn,
        isActive: this.isActive
      })
      if (decision.action === 'noop') return
      if (decision.action === 'give_up') {
        this.errorMessage = decision.message
        this.cleanupLocal()
        return
      }
      iceRestartAttempts = decision.nextAttempts
      this.errorMessage = decision.message
      try {
        if (this.callId) {
          void callApi.reconnectCall(this.callId).catch(() => {
            /* ignore */
          })
        }
        if (decision.disableCamera) {
          this.cameraOn = false
          this.localStream?.getVideoTracks().forEach(t => {
            t.enabled = false
          })
          if (this.callId) {
            void callApi.switchCallDevice(this.callId, 'video', false).catch(() => {
              /* ignore */
            })
          }
        }
        const offer = await pc.createOffer({ iceRestart: true })
        await pc.setLocalDescription(offer)
        await callApi.signalCall({
          callId: this.callId,
          signalType: 'offer',
          sdp: offer.sdp
        })
      } catch (e) {
        console.warn('ICE restart 失败', e)
        this.errorMessage = t('errors.callDisconnected')
        this.cleanupLocal()
      }
    },

    startWeakNetWatch() {
      this.stopWeakNetWatch()
      weakNetChecks = 0
      statsTimer = setInterval(() => {
        void this.checkConnectionQuality()
      }, 4000)
    },

    stopWeakNetWatch() {
      if (statsTimer) {
        clearInterval(statsTimer)
        statsTimer = null
      }
    },

    async checkConnectionQuality() {
      const pc = peerConnection
      if (!pc || this.phase !== 'connected') return
      try {
        const stats = await pc.getStats()
        let packetsLost = 0
        let packetsReceived = 0
        stats.forEach(r => {
          if (r.type === 'inbound-rtp' && 'packetsLost' in r) {
            packetsLost += Number(r.packetsLost || 0)
            packetsReceived += Number((r as { packetsReceived?: number }).packetsReceived || 0)
          }
        })
        const decision = decideWeakNetVideo({
          packetsLost,
          packetsReceived,
          callType: this.callType,
          cameraOn: this.cameraOn,
          weakNetChecks
        })
        if (decision.action === 'noop') return
        if (decision.action === 'reset_checks') {
          weakNetChecks = 0
          return
        }
        if (decision.action === 'accumulate') {
          weakNetChecks = decision.nextChecks
          return
        }
        this.cameraOn = false
        this.localStream?.getVideoTracks().forEach(t => {
          t.enabled = false
        })
        this.errorMessage = decision.message
        weakNetChecks = 0
      } catch {
        /* ignore */
      }
    },

    async ensureLocalMedia() {
      const run = async () => {
        const wantVideo = this.callType === 'video' && this.cameraOn
        if (this.localStream) {
          const hasVideo = this.localStream.getVideoTracks().length > 0
          if ((wantVideo && hasVideo) || !wantVideo) {
            if (!wantVideo) {
              this.localStream.getVideoTracks().forEach(t => {
                t.enabled = false
              })
            }
            this.localStream.getAudioTracks().forEach(t => {
              t.enabled = this.micOn
            })
            this.localStream.getVideoTracks().forEach(t => {
              if (wantVideo) t.enabled = this.cameraOn
            })
            this.attachLocalTracks()
            return
          }
          this.localStream.getTracks().forEach(t => t.stop())
          this.localStream = null
        }

        try {
          const stream = await navigator.mediaDevices.getUserMedia({
            audio: true,
            video: wantVideo
          })
          stream.getAudioTracks().forEach(t => {
            t.enabled = this.micOn
          })
          this.localStream = markRaw(stream)
          this.attachLocalTracks()
        } catch (e) {
          const name = (e as DOMException)?.name
          if (wantVideo && shouldFallbackToVoiceOnCameraDenied(this.callType, e)) {
            // 摄像头权限/设备异常：降级仅语音，不直接结束通话
            this.cameraOn = false
            this.errorMessage =
              name === 'NotAllowedError'
                ? t('errors.cameraDeniedVoiceFallback')
                : t('errors.cameraUnavailableVoiceFallback')
            const audioOnly = await navigator.mediaDevices.getUserMedia({ audio: true, video: false })
            audioOnly.getAudioTracks().forEach(t => {
              t.enabled = this.micOn
            })
            this.localStream = markRaw(audioOnly)
            this.attachLocalTracks()
            if (this.callId) {
              void callApi.switchCallDevice(this.callId, 'video', false).catch(() => {
                /* ignore */
              })
            }
            return
          }
          if (name === 'NotReadableError') {
            throw new DOMException(t('errors.mediaDeviceBusy'), 'NotReadableError')
          }
          throw e
        }
      }

      const next = mediaChain.then(run, run)
      mediaChain = next.then(
        () => undefined,
        () => undefined
      )
      await next
    },

    attachLocalTracks() {
      const pc = peerConnection
      const stream = this.localStream
      if (!pc || !stream) return
      const senders = pc.getSenders()
      for (const track of stream.getTracks()) {
        const exists = senders.some(s => s.track?.id === track.id)
        if (!exists) {
          pc.addTrack(track, stream)
        }
      }
    },

    async createAndSendOffer() {
      const pc = peerConnection
      if (!pc || !this.callId) return
      const offer = await pc.createOffer()
      await pc.setLocalDescription(offer)
      await callApi.signalCall({
        callId: this.callId,
        signalType: 'offer',
        sdp: offer.sdp
      })
    },

    async flushPendingCandidates() {
      const pc = peerConnection
      if (!pc) return
      const pending = pendingCandidates.splice(0)
      for (const c of pending) {
        try {
          await pc.addIceCandidate(c)
        } catch (e) {
          console.warn('添加 ICE candidate 失败', e)
        }
      }
    },

    cleanupLocal() {
      const shouldPlayEnd = this.phase !== 'idle'
      linkMateConnectToken += 1
      clearRingTimer()
      stopCallRing()
      stopLinkMateBridge()
      if (shouldPlayEnd) playCallEnd()
      this.stopWeakNetWatch()
      iceRestartAttempts = 0
      weakNetChecks = 0
      if (peerConnection) {
        peerConnection.onicecandidate = null
        peerConnection.ontrack = null
        peerConnection.onconnectionstatechange = null
        try {
          peerConnection.close()
        } catch {
          /* ignore */
        }
        peerConnection = null
      }
      this.localStream?.getTracks().forEach(t => t.stop())
      this.localStream = null
      this.remoteStream = null
      pendingCandidates = []
      mediaChain = Promise.resolve()
      this.phase = 'idle'
      this.role = null
      this.callId = null
      this.conversationId = null
      this.peerKind = 'human'
      this.connectedAt = 0
    },

    clearError() {
      this.errorMessage = ''
    }
  }
})
