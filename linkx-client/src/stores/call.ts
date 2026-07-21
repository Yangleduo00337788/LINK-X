/**
 * 音视频通话 Store：信令状态 + WebRTC PeerConnection
 */

import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import * as callApi from '../api/call'
import type { CallEventPayload } from '../api/call'

export type CallPhase = 'idle' | 'outgoing' | 'incoming' | 'connecting' | 'connected' | 'ended'
export type CallRole = 'caller' | 'callee' | null
export type CallType = 'voice' | 'video'

const ICE_SERVERS: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' }
]

/** PeerConnection 不适合放入 Pinia 响应式 state，用模块变量持有 */
let peerConnection: RTCPeerConnection | null = null
let pendingCandidates: RTCIceCandidateInit[] = []
/** 串行化 getUserMedia，避免接听与收 offer 并发抢占摄像头 */
let mediaChain: Promise<unknown> = Promise.resolve()
/** 串行化信令处理，避免 offer/ICE 交错 */
let signalQueue: Promise<void> = Promise.resolve()

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
        throw new Error('当前已有通话进行中')
      }
      const res = await callApi.inviteCall({
        conversationId: opts.conversationId,
        callType: opts.callType
      })
      if (res.code !== 200 || !res.data?.callId) {
        throw new Error(res.message || '发起通话失败')
      }
      this.role = 'caller'
      this.callId = res.data.callId
      this.conversationId = String(res.data.conversationId ?? opts.conversationId)
      this.callType = opts.callType
      this.peerName = res.data.peerNickname || opts.peerName
      this.peerAvatar = res.data.peerAvatar || opts.peerAvatar || ''
      this.peerUserId = String(res.data.peerUserId || opts.peerUserId || '')
      this.phase = 'outgoing'
      this.errorMessage = ''
      this.micOn = true
      this.cameraOn = opts.callType === 'video'
      // 主叫在振铃阶段即打开本地媒体：视频可预览，接通后立刻有音轨
      void this.ensureLocalMedia().catch(e => {
        const name = (e as DOMException)?.name
        this.errorMessage =
          name === 'NotAllowedError'
            ? '请允许使用摄像头/麦克风'
            : name === 'NotReadableError'
              ? '摄像头或麦克风被占用，请关闭其他占用设备的应用后重试'
              : (e as Error).message || '无法打开摄像头/麦克风'
      })
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
          this.onRemoteEnd('对方已拒绝')
          break
        case 'call_cancel':
          this.onRemoteEnd('对方已取消')
          break
        case 'call_hangup':
          this.onRemoteEnd('对方已挂断')
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
                    ? '请允许使用摄像头/麦克风'
                    : '摄像头或麦克风被占用，请关闭其他占用设备的应用后重试'
              }
            })
          break
        default:
          break
      }
    },

    async acceptIncoming() {
      if (this.phase !== 'incoming' || !this.callId) return
      try {
        const res = await callApi.acceptCall(this.callId)
        if (res.code !== 200) {
          throw new Error(res.message || '接听失败')
        }
        this.phase = 'connecting'
        // 被叫只建 PeerConnection；媒体在收到 offer 后再采集，避免与 ensureLocalMedia 并发抢设备
        await this.ensurePeerConnection()
      } catch (e) {
        this.errorMessage = (e as Error).message || '接听失败'
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
      const wasRingingCaller = this.role === 'caller' && this.phase === 'outgoing'
      if (callId) {
        try {
          if (wasRingingCaller) {
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
    },

    async toggleCamera() {
      if (this.callType !== 'video') return
      this.cameraOn = !this.cameraOn
      const videoTracks = this.localStream?.getVideoTracks() ?? []
      if (this.cameraOn && videoTracks.length === 0) {
        await this.ensureLocalMedia()
        return
      }
      videoTracks.forEach(t => {
        t.enabled = this.cameraOn
      })
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
      this.peerName = event.fromNickname || '好友'
      this.peerAvatar = event.fromAvatar || ''
      this.peerUserId = event.fromUserId
      this.phase = 'incoming'
      this.micOn = true
      this.cameraOn = event.callType === 'video'
      this.errorMessage = ''
    },

    async onAccept(event: NormalizedCallEvent) {
      if (this.role !== 'caller' || this.callId !== event.callId) return
      if (this.phase !== 'outgoing') return
      this.phase = 'connecting'
      try {
        await this.ensurePeerConnection()
        await this.ensureLocalMedia()
        await this.createAndSendOffer()
      } catch (e) {
        this.errorMessage = (e as Error).message || '建立通话失败'
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
        if (!pc.currentRemoteDescription) {
          await pc.setRemoteDescription({ type: 'answer', sdp: event.sdp })
          await this.flushPendingCandidates()
        }
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
      const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS })
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
        } else if (state === 'failed' || state === 'closed') {
          if (this.isActive) {
            this.errorMessage = '通话连接已断开'
            this.cleanupLocal()
          }
        }
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
          if (name === 'NotReadableError') {
            throw new DOMException(
              '摄像头或麦克风被占用，请关闭其他占用设备的应用后重试',
              'NotReadableError'
            )
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
      this.connectedAt = 0
    },

    clearError() {
      this.errorMessage = ''
    }
  }
})
