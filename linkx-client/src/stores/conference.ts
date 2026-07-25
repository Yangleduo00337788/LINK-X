/**
 * 多人会议 Store：进出房、本地媒体、mesh WebRTC 远端音视频。
 */
import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import * as conferenceApi from '../api/conference'
import type { ConferenceInfo, ConferenceParticipant } from '../api/conference'
import type { CallEventPayload } from '../api/call'
import { resolveIceServers } from '../utils/iceServers'

export type ConferencePhase = 'idle' | 'lobby' | 'in_room' | 'ended'

type PeerSlot = {
  pc: RTCPeerConnection
  pending: RTCIceCandidateInit[]
  makingOffer: boolean
  ignoreOffer: boolean
}

let localStream: MediaStream | null = null
let qualityTimer: ReturnType<typeof setInterval> | null = null
/** peerUserId → PeerConnection（不入 Pinia 响应式） */
const peers = new Map<string, PeerSlot>()
/** 串行化信令，避免 offer/ICE 交错 */
let signalQueue: Promise<void> = Promise.resolve()

function shouldInitiateOffer(myId: string, peerId: string): boolean {
  try {
    return BigInt(myId) < BigInt(peerId)
  } catch {
    return myId < peerId
  }
}

export const useConferenceStore = defineStore('conference', {
  state: () => ({
    phase: 'idle' as ConferencePhase,
    conferenceId: null as string | null,
    callId: null as string | null,
    conversationId: null as string | null,
    title: '',
    type: 'video' as 'voice' | 'video',
    creatorId: '' as string,
    myUserId: '' as string,
    participants: [] as ConferenceParticipant[],
    micOn: true,
    cameraOn: true,
    audioInputs: [] as MediaDeviceInfo[],
    videoInputs: [] as MediaDeviceInfo[],
    selectedAudioId: '' as string,
    selectedVideoId: '' as string,
    localStream: null as MediaStream | null,
    /** peerUserId → 远端合流（音视频轨） */
    remoteStreams: {} as Record<string, MediaStream>,
    networkHint: '' as string,
    invitePrompt: null as {
      conferenceId: string
      title: string
      conversationId: string
      callId?: string
      /** refresh 后发现仍在会中 */
      restore?: boolean
    } | null,
    errorMessage: '' as string
  }),

  getters: {
    visible(state): boolean {
      return state.phase === 'lobby' || state.phase === 'in_room'
    },
    isHost(state): boolean {
      const me = state.participants.find(p => String(p.userId) === state.myUserId)
      return me?.role === 'host' || state.creatorId === state.myUserId
    }
  },

  actions: {
    async openCreated(info: ConferenceInfo, myUserId: string) {
      this.applyInfo(info, myUserId)
      this.phase = 'in_room'
      await this.refreshDevices()
      await this.ensureLocalMedia()
      await this.syncMeshPeers()
      this.startQualityWatch()
    },

    async joinExisting(conferenceId: string, myUserId: string, password?: string) {
      const res = await conferenceApi.join(conferenceId, password)
      if (res.code !== 200 || !res.data) {
        throw new Error(res.message || '加入会议失败')
      }
      this.applyInfo(res.data, myUserId)
      this.phase = 'in_room'
      this.invitePrompt = null
      await this.refreshDevices()
      await this.ensureLocalMedia()
      await this.syncMeshPeers()
      this.startQualityWatch()
    },

    applyInfo(info: ConferenceInfo, myUserId: string) {
      this.conferenceId = String(info.id)
      this.callId = info.callId ? String(info.callId) : null
      this.conversationId = info.conversationId != null ? String(info.conversationId) : null
      this.title = info.title || '多人会议'
      this.type = info.type === 'voice' ? 'voice' : 'video'
      this.creatorId = info.creatorId != null ? String(info.creatorId) : ''
      this.myUserId = myUserId
      this.participants = (info.participants || []).map(p => ({
        ...p,
        userId: String(p.userId),
        muted: !!p.muted,
        videoOff: !!p.videoOff
      }))
      // 与服务端成员状态对齐，避免刷新后底栏/小窗图标不一致
      const me = this.participants.find(p => String(p.userId) === myUserId)
      if (me) {
        this.micOn = !me.muted
        this.cameraOn = !me.videoOff
        localStream?.getAudioTracks().forEach(t => {
          t.enabled = this.micOn
        })
      }
    },

    /**
     * 登录后探测是否仍在 ACTIVE 会议（刷新/重开客户端）。
     * 有则进入 lobby 确认层，由用户选择重新加入。
     */
    async tryRestoreActive(myUserId: string) {
      if (!myUserId || this.phase === 'in_room') return
      try {
        const res = await conferenceApi.active()
        if (res.code !== 200 || !res.data?.length) return
        const info = res.data[0]
        const conferenceId = String(info.id)
        // 已在同会或已有邀请弹层则不覆盖
        if (this.conferenceId === conferenceId && this.phase === 'in_room') return
        if (this.invitePrompt && !this.invitePrompt.restore) return
        this.invitePrompt = {
          conferenceId,
          title: info.title || '多人会议',
          conversationId: info.conversationId != null ? String(info.conversationId) : '',
          callId: info.callId ? String(info.callId) : undefined,
          restore: true
        }
        if (this.phase === 'idle') this.phase = 'lobby'
      } catch (e) {
        console.warn('[conference] tryRestoreActive failed', e)
      }
    },

    /** 从通知中心打开会议邀请 */
    openInviteFromNotification(payload: {
      conferenceId: string
      title?: string
      conversationId?: string
    }) {
      if (!payload.conferenceId) return
      this.invitePrompt = {
        conferenceId: String(payload.conferenceId),
        title: payload.title || '多人会议',
        conversationId: payload.conversationId ? String(payload.conversationId) : '',
        restore: false
      }
      if (this.phase === 'idle') this.phase = 'lobby'
    },

    async refreshInfo() {
      if (!this.conferenceId) return
      const res = await conferenceApi.info(this.conferenceId)
      if (res.code === 200 && res.data) {
        this.applyInfo(res.data, this.myUserId)
      }
    },

    handleRemoteEvent(action: string, data: Record<string, unknown>) {
      if (action === 'conference_invite') {
        const conferenceId = data.conferenceId != null ? String(data.conferenceId) : ''
        if (!conferenceId || conferenceId === '0') return
        this.invitePrompt = {
          conferenceId,
          title: String(data.title || '多人会议'),
          conversationId: String(data.conversationId || ''),
          callId: data.callId != null ? String(data.callId) : undefined
        }
        this.phase = this.phase === 'idle' ? 'lobby' : this.phase
        return
      }
      if (action === 'conference_end' || action === 'conference_remove') {
        const cid = data.conferenceId != null ? String(data.conferenceId) : ''
        if (cid && cid === this.conferenceId) {
          this.errorMessage = action === 'conference_remove' ? '你已被移出会议' : '会议已结束'
          this.cleanupLocal()
          this.phase = 'ended'
          setTimeout(() => {
            if (this.phase === 'ended') this.phase = 'idle'
          }, 1200)
        }
        return
      }
      if (action === 'conference_join' || action === 'conference_leave') {
        void this.refreshInfo().then(() => this.syncMeshPeers())
        return
      }
      if (action === 'conference_mute') {
        const uid = data.userId != null ? String(data.userId) : ''
        if (uid) {
          const muted = !!data.muted
          this.participants = this.participants.map(p =>
            String(p.userId) === uid ? { ...p, muted } : p
          )
          if (uid === this.myUserId) {
            this.micOn = !muted
            localStream?.getAudioTracks().forEach(t => {
              t.enabled = this.micOn
            })
          }
        }
        void this.refreshInfo()
        return
      }
      if (action === 'conference_video') {
        const uid = data.userId != null ? String(data.userId) : ''
        if (uid) {
          const videoOff = !!data.videoOff
          this.participants = this.participants.map(p =>
            String(p.userId) === uid ? { ...p, videoOff } : p
          )
          if (uid === this.myUserId) {
            this.cameraOn = !videoOff
          } else if (videoOff) {
            // 对端关摄像头：立刻去掉远端 video 轨，小窗切头像+图标
            this.stripRemoteVideoTracks(uid)
          }
        }
        void this.refreshInfo()
        return
      }
      if (action === 'conference_host') {
        const prev = data.previousHostId != null ? String(data.previousHostId) : ''
        const next = data.newHostId != null ? String(data.newHostId) : ''
        if (next) {
          this.creatorId = next
          this.participants = this.participants.map(p => {
            const id = String(p.userId)
            if (id === next) return { ...p, role: 'host' }
            if (prev && id === prev) return { ...p, role: 'member' }
            if (p.role === 'host' && id !== next) return { ...p, role: 'member' }
            return p
          })
        }
        void this.refreshInfo()
      }
    },

    stripRemoteVideoTracks(peerId: string) {
      const cur = this.remoteStreams[peerId]
      if (!cur) return
      const kept = cur.getTracks().filter(t => t.kind !== 'video')
      cur.getVideoTracks().forEach(t => {
        try {
          cur.removeTrack(t)
        } catch {
          /* ignore */
        }
      })
      if (kept.length === 0) {
        const { [peerId]: _, ...rest } = this.remoteStreams
        this.remoteStreams = rest
      } else {
        this.remoteStreams = { ...this.remoteStreams, [peerId]: markRaw(new MediaStream(kept)) }
      }
    },

    /**
     * 会议态下的 WebRTC 信令（WS action=call_signal，callId 匹配本会）。
     */
    handleCallSignal(raw: CallEventPayload) {
      if (this.phase !== 'in_room' || !this.callId) return
      if (String(raw.callId || '') !== this.callId) return
      const from = String(raw.fromUserId || '')
      if (!from || from === this.myUserId || !raw.signalType) return

      signalQueue = signalQueue
        .then(() => this.processPeerSignal(from, raw))
        .catch(err => console.warn('[conference] signal error', err))
    },

    async processPeerSignal(peerId: string, raw: CallEventPayload) {
      const slot = await this.ensurePeer(peerId)
      const pc = slot.pc

      if (raw.signalType === 'offer' && raw.sdp) {
        const offerCollision = slot.makingOffer || pc.signalingState !== 'stable'
        // 较大 userId 为 polite：冲突时回滚并接受对端 offer
        const polite = !shouldInitiateOffer(this.myUserId, peerId)
        if (offerCollision) {
          if (!polite) {
            slot.ignoreOffer = true
            return
          }
          try {
            await pc.setLocalDescription({ type: 'rollback' })
          } catch {
            /* ignore */
          }
        }
        slot.ignoreOffer = false
        await pc.setRemoteDescription({ type: 'offer', sdp: raw.sdp })
        await this.flushPendingCandidates(peerId)
        this.attachLocalTracksTo(pc)
        const answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)
        await this.sendSignal({
          signalType: 'answer',
          sdp: answer.sdp,
          targetUserId: peerId
        })
        return
      }

      if (raw.signalType === 'answer' && raw.sdp) {
        if (slot.ignoreOffer) return
        if (!pc.currentRemoteDescription) {
          await pc.setRemoteDescription({ type: 'answer', sdp: raw.sdp })
          await this.flushPendingCandidates(peerId)
        }
        return
      }

      if (raw.signalType === 'ice-candidate' && raw.candidate) {
        try {
          const init = JSON.parse(raw.candidate) as RTCIceCandidateInit
          if (!pc.remoteDescription) {
            slot.pending.push(init)
          } else {
            await pc.addIceCandidate(init)
          }
        } catch (e) {
          console.warn('[conference] bad ICE candidate', e)
        }
      }
    },

    async ensurePeer(peerId: string): Promise<PeerSlot> {
      const existing = peers.get(peerId)
      if (existing) return existing

      const pc = new RTCPeerConnection({ iceServers: resolveIceServers() })
      const slot: PeerSlot = {
        pc,
        pending: [],
        makingOffer: false,
        ignoreOffer: false
      }
      peers.set(peerId, slot)

      this.attachLocalTracksTo(pc)

      pc.onicecandidate = evt => {
        if (!evt.candidate || this.phase !== 'in_room') return
        void this.sendSignal({
          signalType: 'ice-candidate',
          candidate: JSON.stringify(evt.candidate.toJSON()),
          targetUserId: peerId
        })
      }

      pc.ontrack = evt => {
        const prev = this.remoteStreams[peerId]
        const stream = prev || markRaw(new MediaStream())
        if (!stream.getTrackById(evt.track.id)) {
          stream.addTrack(evt.track)
        }
        evt.track.onended = () => {
          const cur = this.remoteStreams[peerId]
          if (!cur) return
          const next = markRaw(new MediaStream(cur.getTracks().filter(t => t.id !== evt.track.id && t.readyState !== 'ended')))
          if (next.getTracks().length === 0) {
            const { [peerId]: _, ...rest } = this.remoteStreams
            this.remoteStreams = rest
          } else {
            this.remoteStreams = { ...this.remoteStreams, [peerId]: next }
          }
        }
        this.remoteStreams = { ...this.remoteStreams, [peerId]: markRaw(stream) }
      }

      pc.onconnectionstatechange = () => {
        const state = pc.connectionState
        if (state === 'failed') {
          void this.recoverPeer(peerId)
        } else if (state === 'closed') {
          this.closePeer(peerId)
        }
      }

      return slot
    },

    async recoverPeer(peerId: string) {
      if (this.phase !== 'in_room') return
      this.closePeer(peerId)
      if (!this.participants.some(p => String(p.userId) === peerId)) return
      await this.ensurePeer(peerId)
      if (shouldInitiateOffer(this.myUserId, peerId)) {
        await this.createOfferTo(peerId)
      }
    },

    attachLocalTracksTo(pc: RTCPeerConnection) {
      const stream = localStream
      if (!stream) return
      const senders = pc.getSenders()
      for (const track of stream.getTracks()) {
        const exists = senders.some(s => s.track?.id === track.id)
        if (!exists) {
          pc.addTrack(track, stream)
        }
      }
    },

    async replaceTracksOnPeers() {
      const stream = localStream
      if (!stream) return
      for (const [, slot] of peers) {
        const senders = slot.pc.getSenders()
        for (const track of stream.getTracks()) {
          const sender = senders.find(s => s.track?.kind === track.kind)
          if (sender) {
            try {
              await sender.replaceTrack(track)
            } catch {
              /* ignore */
            }
          } else {
            slot.pc.addTrack(track, stream)
          }
        }
        // 摄像头关闭时，清空 video sender，避免对端仍看到冻结画面
        if (!this.cameraOn || this.type !== 'video') {
          const videoSender = slot.pc.getSenders().find(s => s.track?.kind === 'video')
          if (videoSender?.track) {
            try {
              await videoSender.replaceTrack(null)
            } catch {
              /* ignore */
            }
          }
        }
      }
    },

    async createOfferTo(peerId: string) {
      const slot = await this.ensurePeer(peerId)
      if (slot.pc.signalingState !== 'stable') return
      slot.makingOffer = true
      try {
        this.attachLocalTracksTo(slot.pc)
        const offer = await slot.pc.createOffer()
        await slot.pc.setLocalDescription(offer)
        await this.sendSignal({
          signalType: 'offer',
          sdp: offer.sdp,
          targetUserId: peerId
        })
      } finally {
        slot.makingOffer = false
      }
    },

    async flushPendingCandidates(peerId: string) {
      const slot = peers.get(peerId)
      if (!slot) return
      const pending = slot.pending.splice(0)
      for (const c of pending) {
        try {
          await slot.pc.addIceCandidate(c)
        } catch (e) {
          console.warn('[conference] addIceCandidate failed', e)
        }
      }
    },

    /**
     * 与当前成员列表对齐：建连 / 发 offer / 拆离开的 peer。
     * 仅较小 userId 一侧主动 createOffer，避免 glare。
     */
    async syncMeshPeers() {
      if (this.phase !== 'in_room' || !this.myUserId) return
      const others = this.participants
        .map(p => String(p.userId))
        .filter(id => id && id !== this.myUserId)

      for (const peerId of others) {
        await this.ensurePeer(peerId)
        if (!shouldInitiateOffer(this.myUserId, peerId)) continue
        const slot = peers.get(peerId)
        // 仅在稳定且尚未协商时主动发 offer，避免重复协商
        if (slot && slot.pc.signalingState === 'stable' && !slot.pc.currentRemoteDescription) {
          await this.createOfferTo(peerId)
        }
      }

      for (const peerId of [...peers.keys()]) {
        if (!others.includes(peerId)) {
          this.closePeer(peerId)
        }
      }
    },

    closePeer(peerId: string) {
      const slot = peers.get(peerId)
      if (slot) {
        slot.pc.onicecandidate = null
        slot.pc.ontrack = null
        slot.pc.onconnectionstatechange = null
        try {
          slot.pc.close()
        } catch {
          /* ignore */
        }
        peers.delete(peerId)
      }
      if (this.remoteStreams[peerId]) {
        const { [peerId]: _, ...rest } = this.remoteStreams
        this.remoteStreams = rest
      }
    },

    closeAllPeers() {
      for (const peerId of [...peers.keys()]) {
        this.closePeer(peerId)
      }
      this.remoteStreams = {}
    },

    async refreshDevices() {
      if (!navigator.mediaDevices?.enumerateDevices) return
      const devices = await navigator.mediaDevices.enumerateDevices()
      this.audioInputs = devices.filter(d => d.kind === 'audioinput')
      this.videoInputs = devices.filter(d => d.kind === 'videoinput')
      if (!this.selectedAudioId && this.audioInputs[0]) {
        this.selectedAudioId = this.audioInputs[0].deviceId
      }
      if (!this.selectedVideoId && this.videoInputs[0]) {
        this.selectedVideoId = this.videoInputs[0].deviceId
      }
    },

    async ensureLocalMedia() {
      const wantVideo = this.type === 'video' && this.cameraOn
      try {
        if (localStream) {
          localStream.getTracks().forEach(t => t.stop())
          localStream = null
          this.localStream = null
        }
        const constraints: MediaStreamConstraints = {
          audio: this.selectedAudioId
            ? { deviceId: { exact: this.selectedAudioId } }
            : true,
          video: wantVideo
            ? this.selectedVideoId
              ? { deviceId: { exact: this.selectedVideoId } }
              : true
            : false
        }
        localStream = await navigator.mediaDevices.getUserMedia(constraints)
        localStream.getAudioTracks().forEach(t => {
          t.enabled = this.micOn
        })
        this.localStream = markRaw(localStream)
        await this.replaceTracksOnPeers()
      } catch (e) {
        const name = (e as DOMException)?.name
        if (wantVideo && (name === 'NotAllowedError' || name === 'NotFoundError' || name === 'NotReadableError')) {
          this.cameraOn = false
          this.networkHint = '摄像头不可用，已降级为语音'
          this.participants = this.participants.map(p =>
            String(p.userId) === this.myUserId ? { ...p, videoOff: true } : p
          )
          localStream = await navigator.mediaDevices.getUserMedia({
            audio: this.selectedAudioId ? { deviceId: { exact: this.selectedAudioId } } : true,
            video: false
          })
          localStream.getAudioTracks().forEach(t => {
            t.enabled = this.micOn
          })
          this.localStream = markRaw(localStream)
          await this.replaceTracksOnPeers()
          if (this.conferenceId) {
            try {
              await conferenceApi.setVideo(this.conferenceId, true)
            } catch {
              /* 本地已降级；同步失败不阻断会议 */
            }
          }
          return
        }
        throw e
      }
    },

    async toggleMic() {
      const prev = this.micOn
      this.micOn = !this.micOn
      this.localStream?.getAudioTracks().forEach(t => {
        t.enabled = this.micOn
      })
      this.participants = this.participants.map(p =>
        String(p.userId) === this.myUserId ? { ...p, muted: !this.micOn } : p
      )
      if (!this.conferenceId) return
      try {
        await conferenceApi.mute(this.conferenceId, this.myUserId, !this.micOn)
      } catch (e) {
        this.micOn = prev
        this.localStream?.getAudioTracks().forEach(t => {
          t.enabled = this.micOn
        })
        this.participants = this.participants.map(p =>
          String(p.userId) === this.myUserId ? { ...p, muted: !this.micOn } : p
        )
        throw e
      }
    },

    async toggleCamera() {
      const prev = this.cameraOn
      this.cameraOn = !this.cameraOn
      this.participants = this.participants.map(p =>
        String(p.userId) === this.myUserId ? { ...p, videoOff: !this.cameraOn } : p
      )
      try {
        if (this.type === 'video') {
          await this.ensureLocalMedia()
        } else {
          await this.replaceTracksOnPeers()
        }
        if (this.conferenceId) {
          await conferenceApi.setVideo(this.conferenceId, !this.cameraOn)
        }
      } catch (e) {
        this.cameraOn = prev
        this.participants = this.participants.map(p =>
          String(p.userId) === this.myUserId ? { ...p, videoOff: !this.cameraOn } : p
        )
        if (this.type === 'video') {
          try {
            await this.ensureLocalMedia()
          } catch {
            /* ignore */
          }
        }
        throw e
      }
    },

    async sendSignal(payload: {
      signalType: 'offer' | 'answer' | 'ice-candidate'
      sdp?: string
      candidate?: string
      targetUserId?: string | number
    }) {
      if (!this.conferenceId) return
      await conferenceApi.signal({
        conferenceId: this.conferenceId,
        signalType: payload.signalType,
        sdp: payload.sdp,
        candidate: payload.candidate,
        targetUserId: payload.targetUserId
      })
    },

    async switchAudioDevice(deviceId: string) {
      this.selectedAudioId = deviceId
      await this.ensureLocalMedia()
    },

    async switchVideoDevice(deviceId: string) {
      this.selectedVideoId = deviceId
      await this.ensureLocalMedia()
    },

    async muteTarget(userId: string, muted: boolean) {
      if (!this.conferenceId) return
      const uid = String(userId)
      this.participants = this.participants.map(p =>
        String(p.userId) === uid ? { ...p, muted } : p
      )
      await conferenceApi.mute(this.conferenceId, userId, muted)
      await this.refreshInfo()
    },

    async removeTarget(userId: string) {
      if (!this.conferenceId) return
      await conferenceApi.removeMember(this.conferenceId, userId)
      this.closePeer(String(userId))
      await this.refreshInfo()
      await this.syncMeshPeers()
    },

    async leave() {
      if (this.conferenceId) {
        try {
          await conferenceApi.leave(this.conferenceId)
        } catch {
          /* ignore */
        }
      }
      this.cleanupLocal()
      this.phase = 'idle'
    },

    async endAsHost() {
      if (!this.conferenceId) return
      await conferenceApi.end(this.conferenceId)
      this.cleanupLocal()
      this.phase = 'idle'
    },

    dismissInvite() {
      this.invitePrompt = null
      if (this.phase === 'lobby') this.phase = 'idle'
    },

    startQualityWatch() {
      this.stopQualityWatch()
      qualityTimer = setInterval(() => {
        const stream = this.localStream
        if (!stream) return
        const vt = stream.getVideoTracks()[0]
        if (this.cameraOn && vt && (vt.muted || vt.readyState !== 'live')) {
          this.networkHint = '视频链路不稳定，可尝试关闭摄像头'
        } else if (this.networkHint.includes('不稳定')) {
          this.networkHint = ''
        }
      }, 5000)
    },

    stopQualityWatch() {
      if (qualityTimer) {
        clearInterval(qualityTimer)
        qualityTimer = null
      }
    },

    cleanupLocal() {
      this.stopQualityWatch()
      this.closeAllPeers()
      if (localStream) {
        localStream.getTracks().forEach(t => t.stop())
        localStream = null
      }
      this.localStream = null
      this.conferenceId = null
      this.callId = null
      this.participants = []
      this.networkHint = ''
    },

    clearError() {
      this.errorMessage = ''
    },

    remoteStreamFor(userId: string): MediaStream | null {
      return this.remoteStreams[String(userId)] || null
    }
  }
})
