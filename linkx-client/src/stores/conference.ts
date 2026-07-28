/**
 * 多人会议 Store：进出房、本地媒体、mesh WebRTC 远端音视频。
 */
import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import * as conferenceApi from '../api/conference'
import type { ConferenceInfo, ConferenceParticipant } from '../api/conference'
import type { CallEventPayload } from '../api/call'
import { resolveIceServers } from '../utils/iceServers'
import { decideIceRestart, decideWeakNetVideo } from '../utils/callNetworkPolicy'
import { startCallRing, stopCallRing } from '../utils/callSounds'
import { t } from '../i18n'

export type ConferencePhase = 'idle' | 'lobby' | 'waiting' | 'in_room' | 'ended'

/** 会话顶栏进行中摘要：电话 / 会议 分开 */
export type SessionActiveConference = {
  conferenceId: string
  conversationId: string
  title: string
  type: 'voice' | 'video'
  /** call=语音/视频电话 meeting=会议 */
  scene: 'call' | 'meeting'
  hasPassword: boolean
  participantCount: number
}

type PeerSlot = {
  pc: RTCPeerConnection
  pending: RTCIceCandidateInit[]
  makingOffer: boolean
  ignoreOffer: boolean
}

let localStream: MediaStream | null = null
let screenStream: MediaStream | null = null
let qualityTimer: ReturnType<typeof setInterval> | null = null
let meshRetryTimers: number[] = []
let weakNetChecks = 0
/** peerUserId → ICE restart 已尝试次数 */
const iceRestartAttempts = new Map<string, number>()
/** peerUserId → PeerConnection（不入 Pinia 响应式） */
const peers = new Map<string, PeerSlot>()
/** 串行化信令，避免 offer/ICE 交错 */
let signalQueue: Promise<void> = Promise.resolve()
/**
 * 入会竞态缓冲：对端在本端 enterInRoom 完成前发来的 offer/ICE。
 * 若直接丢弃，较小 userId 一侧已发 offer 会永远等不到 answer。
 */
let earlySignals: { peerId: string; raw: CallEventPayload }[] = []

/** admitStatus===0 为等候室；缺省视为已准入 */
function isAdmittedParticipant(p: ConferenceParticipant): boolean {
  return p.admitStatus == null || Number(p.admitStatus) !== 0
}

function shouldInitiateOffer(myId: string, peerId: string): boolean {
  try {
    return BigInt(myId) < BigInt(peerId)
  } catch {
    return myId < peerId
  }
}

/** 按 kind 找 sender（含 replaceTrack(null) 后 track 为空的发送器） */
function findSenderForKind(pc: RTCPeerConnection, kind: string): RTCRtpSender | undefined {
  const live = pc.getSenders().find(s => s.track?.kind === kind)
  if (live) return live
  const trs = pc.getTransceivers()
  for (const t of trs) {
    if (t.receiver?.track?.kind === kind) return t.sender
  }
  // ensurePeer 固定先 audio 后 video 预建 transceiver
  if (kind === 'audio' && trs[0]?.sender) return trs[0].sender
  if (kind === 'video' && trs[1]?.sender) return trs[1].sender
  return trs.find(t => t.sender && !t.sender.track)?.sender
}

export const useConferenceStore = defineStore('conference', {
  state: () => ({
    phase: 'idle' as ConferencePhase,
    /** 会中收起窗口（仍在会，聊天顶栏可「返回」） */
    uiMinimized: false,
    conferenceId: null as string | null,
    callId: null as string | null,
    conversationId: null as string | null,
    title: '',
    type: 'video' as 'voice' | 'video',
    scene: 'meeting' as 'call' | 'meeting',
    creatorId: '' as string,
    myUserId: '' as string,
    participants: [] as ConferenceParticipant[],
    micOn: true,
    cameraOn: true,
    hasPassword: false,
    lobbyEnabled: false,
    maxParticipants: 16,
    raisedHands: {} as Record<string, boolean>,
    handRaised: false,
    screenSharing: false,
    activeSpeakerId: '' as string,
    chatOpen: false,
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
      hasPassword?: boolean
      /** refresh 后发现仍在会中 */
      restore?: boolean
    } | null,
    /** conversationId → 该会话进行中会议（顶栏条） */
    sessionActives: {} as Record<string, SessionActiveConference>,
    errorMessage: '' as string
  }),

  getters: {
    visible(state): boolean {
      if (state.uiMinimized && state.phase === 'in_room') return false
      return state.phase === 'lobby' || state.phase === 'waiting' || state.phase === 'in_room'
    },
    isHost(state): boolean {
      const me = state.participants.find(p => String(p.userId) === state.myUserId)
      return me?.role === 'host' || state.creatorId === state.myUserId
    },
    isHostOrCoHost(state): boolean {
      const me = state.participants.find(p => String(p.userId) === state.myUserId)
      const role = me?.role
      return role === 'host' || role === 'co-host' || state.creatorId === state.myUserId
    },
    sessionActiveFor: (state) => {
      return (conversationId: string | null | undefined): SessionActiveConference | null => {
        if (!conversationId) return null
        return state.sessionActives[String(conversationId)] || null
      }
    }
  },

  actions: {
    async enterInRoom() {
      this.uiMinimized = false
      this.phase = 'in_room'
      await this.refreshDevices()
      await this.ensureLocalMedia()
      if (this.conferenceId && this.type === 'video') {
        void conferenceApi.setVideo(this.conferenceId, !this.cameraOn).catch(() => {
          /* ignore */
        })
      }
      // 先消化入会前缓冲的 offer/ICE，再主动建连
      await this.flushEarlySignals()
      await this.syncMeshPeers()
      // 入会后若本端有摄像头，稍后强制再发布一次（覆盖「先协商后开视频」时序）
      if (this.cameraOn && this.type === 'video') {
        window.setTimeout(() => {
          void this.replaceTracksOnPeers().catch(e => {
            console.warn('[conference] delayed video publish failed', e)
          })
        }, 800)
      }
      this.scheduleMeshRetries()
      this.startQualityWatch()
    },

    /** 消化 enterInRoom 前收到的信令 */
    async flushEarlySignals() {
      const batch = earlySignals.splice(0)
      for (const item of batch) {
        signalQueue = signalQueue
          .then(() => this.processPeerSignal(item.peerId, item.raw))
          .catch(err => console.warn('[conference] early signal error', err))
      }
      await signalQueue
    },

    clearMeshRetries() {
      for (const t of meshRetryTimers) clearTimeout(t)
      meshRetryTimers = []
    },

    /** 入会后多次补建连，覆盖「对端尚未就绪 / offer 丢失」 */
    scheduleMeshRetries() {
      this.clearMeshRetries()
      for (const ms of [1200, 3000, 6000]) {
        meshRetryTimers.push(
          window.setTimeout(() => {
            void this.retryMeshIfNeeded().catch(e =>
              console.warn('[conference] mesh retry failed', e)
            )
          }, ms)
        )
      }
    },

    async retryMeshIfNeeded() {
      if (this.phase !== 'in_room' || !this.myUserId) return
      await this.syncMeshPeers()
      for (const peerId of [...peers.keys()]) {
        if (!shouldInitiateOffer(this.myUserId, peerId)) continue
        const slot = peers.get(peerId)
        if (!slot) continue
        const pc = slot.pc
        const connected =
          pc.connectionState === 'connected' ||
          pc.iceConnectionState === 'connected' ||
          pc.iceConnectionState === 'completed'
        if (connected && pc.currentRemoteDescription) continue

        // 未连通 / 卡在 have-local-offer（对端入会前 offer 已丢）：重建后再发
        this.closePeer(peerId)
        await this.ensurePeer(peerId)
        await this.createOfferTo(peerId)
      }
    },

    async openCreated(info: ConferenceInfo, myUserId: string) {
      this.applyInfo(info, myUserId)
      this.upsertSessionActiveFromInfo(info)
      if (info.waitingAdmit) {
        this.phase = 'waiting'
        return
      }
      await this.enterInRoom()
    },

    async joinExisting(conferenceId: string, myUserId: string, password?: string) {
      const res = await conferenceApi.join(conferenceId, password)
      if (res.code !== 200 || !res.data) {
        const err = new Error(res.message || '加入会议失败') as Error & { code?: number }
        err.code = res.code
        throw err
      }
      stopCallRing()
      this.applyInfo(res.data, myUserId)
      this.upsertSessionActiveFromInfo(res.data)
      this.invitePrompt = null
      if (res.data.waitingAdmit) {
        this.phase = 'waiting'
        return
      }
      await this.enterInRoom()
    },

    upsertSessionActive(payload: Partial<SessionActiveConference> & {
      conferenceId: string
      conversationId: string
    }) {
      const cid = String(payload.conversationId)
      if (!cid || !payload.conferenceId) return
      const prev = this.sessionActives[cid]
      const next: SessionActiveConference = {
        conferenceId: String(payload.conferenceId),
        conversationId: cid,
        title: payload.title || prev?.title || '多人会议',
        type: payload.type === 'voice' || payload.type === 'video'
          ? payload.type
          : prev?.type || 'video',
        scene:
          payload.scene === 'call' || payload.scene === 'meeting'
            ? payload.scene
            : prev?.scene || 'meeting',
        hasPassword: payload.hasPassword ?? prev?.hasPassword ?? false,
        participantCount:
          payload.participantCount != null
            ? Number(payload.participantCount)
            : prev?.participantCount || 1
      }
      this.sessionActives = { ...this.sessionActives, [cid]: next }
    },

    upsertSessionActiveFromInfo(info: ConferenceInfo) {
      if (info.conversationId == null || info.id == null) return
      const admitted = (info.participants || []).filter(p => {
        const a = p.admitStatus
        return a == null || Number(a) !== 0
      }).length
      this.upsertSessionActive({
        conferenceId: String(info.id),
        conversationId: String(info.conversationId),
        title: info.title || '多人会议',
        type: info.type === 'voice' ? 'voice' : 'video',
        scene: info.scene === 'call' ? 'call' : 'meeting',
        hasPassword: !!info.hasPassword,
        participantCount: admitted || 1
      })
    },

    clearSessionActive(conversationId?: string | null, conferenceId?: string | null) {
      if (conversationId) {
        const cid = String(conversationId)
        const cur = this.sessionActives[cid]
        if (conferenceId && cur && String(cur.conferenceId) !== String(conferenceId)) return
        if (!cur) return
        const next = { ...this.sessionActives }
        delete next[cid]
        this.sessionActives = next
        return
      }
      if (conferenceId) {
        const target = String(conferenceId)
        const next = { ...this.sessionActives }
        for (const key of Object.keys(next)) {
          if (String(next[key].conferenceId) === target) delete next[key]
        }
        this.sessionActives = next
      }
    },

    /** 进入会话时拉取是否有进行中会议；失败时可用消息里的 conferenceId 兜底探测 */
    async fetchSessionActive(conversationId: string, hintConferenceId?: string) {
      if (!conversationId) return
      const localBusy =
        (this.phase === 'in_room' || this.phase === 'waiting') &&
        this.conversationId === conversationId &&
        !!this.conferenceId
      try {
        const res = await conferenceApi.activeInConversation(conversationId)
        if (res.code === 200 && res.data?.id != null) {
          this.upsertSessionActiveFromInfo(res.data)
          return
        }
      } catch (e) {
        console.warn('[conference] fetchSessionActive failed', e)
      }
      // 新接口不可用或暂无记录时：用最近一条会议消息的 id 查 info
      const hint = hintConferenceId || this.sessionActives[conversationId]?.conferenceId
      if (hint) {
        try {
          const infoRes = await conferenceApi.info(hint)
          if (infoRes.code === 200 && infoRes.data?.id != null && Number(infoRes.data.status) === 1) {
            this.upsertSessionActiveFromInfo({
              ...infoRes.data,
              conversationId: infoRes.data.conversationId ?? conversationId
            })
            return
          }
        } catch (e) {
          console.warn('[conference] fetchSessionActive hint info failed', e)
        }
      }
      // 本端仍在会中时不要清顶栏
      if (!localBusy) {
        this.clearSessionActive(conversationId)
      }
    },

    /** 收起会议窗，保持在会（聊天顶栏可返回） */
    minimizeUi() {
      if (this.phase === 'in_room') {
        this.uiMinimized = true
      }
    },

    restoreUi() {
      this.uiMinimized = false
    },

    toggleChatOpen() {
      this.chatOpen = !this.chatOpen
    },

    setChatOpen(open: boolean) {
      this.chatOpen = open
    },

    /** 收到/加载到会议邀请消息时同步顶栏（先乐观展示，再校验 ACTIVE） */
    noteConferenceInviteMessage(payload: {
      conversationId: string
      conferenceId: string
      title?: string
      type?: string
      scene?: 'call' | 'meeting'
      hasPassword?: boolean
    }) {
      const conversationId = String(payload.conversationId || '')
      const conferenceId = String(payload.conferenceId || '')
      if (!conversationId || !conferenceId || conferenceId === '0') return
      this.upsertSessionActive({
        conferenceId,
        conversationId,
        title: payload.title || '多人会议',
        type: payload.type === 'voice' ? 'voice' : 'video',
        scene: payload.scene === 'call' ? 'call' : 'meeting',
        hasPassword: !!payload.hasPassword,
        participantCount: this.sessionActives[conversationId]?.participantCount || 1
      })
      void this.fetchSessionActive(conversationId, conferenceId)
    },

    applyInfo(info: ConferenceInfo, myUserId: string) {
      this.conferenceId = String(info.id)
      this.callId = info.callId ? String(info.callId) : null
      this.conversationId = info.conversationId != null ? String(info.conversationId) : null
      this.title = info.title || '多人会议'
      this.type = info.type === 'voice' ? 'voice' : 'video'
      this.scene = info.scene === 'call' ? 'call' : 'meeting'
      this.creatorId = info.creatorId != null ? String(info.creatorId) : ''
      this.myUserId = myUserId
      this.hasPassword = !!info.hasPassword
      this.lobbyEnabled = !!info.lobbyEnabled
      if (info.maxParticipants != null) this.maxParticipants = Number(info.maxParticipants) || 16
      this.participants = (info.participants || []).map(p => ({
        ...p,
        userId: String(p.userId),
        muted: !!p.muted,
        videoOff: !!p.videoOff,
        admitStatus: p.admitStatus != null ? Number(p.admitStatus) : undefined
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
      this.upsertSessionActiveFromInfo(info)
    },

    /**
     * 登录后探测是否仍在 ACTIVE 会议（刷新/重开客户端）。
     * 有则进入 lobby 确认层，由用户选择重新加入。
     */
    async tryRestoreActive(myUserId: string) {
      if (!myUserId || this.phase === 'in_room' || this.phase === 'waiting') return
      try {
        const res = await conferenceApi.active()
        if (res.code !== 200 || !res.data?.length) return
        const info = res.data[0]
        const conferenceId = String(info.id)
        // 已有邀请弹层则不覆盖
        if (this.invitePrompt && !this.invitePrompt.restore) return
        if (this.conferenceId === conferenceId && this.phase === 'lobby') return
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
      if (action === 'conference_invite' || action === 'conference_presence') {
        const conferenceId = data.conferenceId != null ? String(data.conferenceId) : ''
        const conversationId = data.conversationId != null ? String(data.conversationId) : ''
        if (!conferenceId || conferenceId === '0') return
        if (conversationId) {
          this.upsertSessionActive({
            conferenceId,
            conversationId,
            title: String(data.title || this.sessionActives[conversationId]?.title || '多人会议'),
            type: data.type === 'voice' || data.callType === 'voice' ? 'voice' : 'video',
            scene: data.scene === 'call' ? 'call' : 'meeting',
            hasPassword:
              data.hasPassword === true || data.hasPassword === 1 || data.hasPassword === 'true',
            participantCount:
              data.participantCount != null
                ? Number(data.participantCount)
                : this.sessionActives[conversationId]?.participantCount || 1
          })
        }
        if (action === 'conference_presence') return
        // 已在会中则忽略新邀请弹层（避免打断）
        if (this.phase === 'in_room' || this.phase === 'waiting') return
        this.invitePrompt = {
          conferenceId,
          title: String(data.title || '多人会议'),
          conversationId,
          callId: data.callId != null ? String(data.callId) : undefined,
          hasPassword: data.hasPassword === true || data.hasPassword === 1 || data.hasPassword === 'true'
        }
        // 提前记下 callId，便于入会瞬间缓冲对端 offer（否则 phase≠in_room 时会被丢弃）
        if (data.callId != null) this.callId = String(data.callId)
        this.phase = this.phase === 'idle' ? 'lobby' : this.phase
        startCallRing()
        this.notifyConferenceInvite(String(data.title || '多人会议'), String(data.creatorName || ''))
        return
      }
      if (action === 'conference_end' || action === 'conference_remove') {
        const cid = data.conferenceId != null ? String(data.conferenceId) : ''
        const conversationId = data.conversationId != null ? String(data.conversationId) : ''
        this.clearSessionActive(conversationId || null, cid || null)
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
        const conversationId = data.conversationId != null ? String(data.conversationId) : ''
        const conferenceId = data.conferenceId != null ? String(data.conferenceId) : ''
        if (conversationId && conferenceId && data.participantCount != null) {
          this.upsertSessionActive({
            conferenceId,
            conversationId,
            participantCount: Number(data.participantCount)
          })
        }
        // join 推送时对端可能仍在 getUserMedia；稍延迟再建连，并靠 mesh retry 兜底
        void this.refreshInfo().then(async () => {
          if (action === 'conference_join') {
            await new Promise<void>(r => window.setTimeout(r, 600))
          }
          await this.syncMeshPeers()
        })
        return
      }
      if (action === 'conference_waiting') {
        void this.refreshInfo()
        return
      }
      if (action === 'conference_admit') {
        const uid = data.userId != null ? String(data.userId) : ''
        const cid = data.conferenceId != null ? String(data.conferenceId) : ''
        if (cid && this.conferenceId && cid !== this.conferenceId) return
        void this.refreshInfo().then(async () => {
          if (uid === this.myUserId && this.phase === 'waiting') {
            await this.enterInRoom()
          } else {
            await this.syncMeshPeers()
          }
        })
        return
      }
      if (action === 'conference_raise') {
        const uid = data.userId != null ? String(data.userId) : ''
        if (!uid) return
        const raised = data.raised === true || data.raised === 1 || data.raised === 'true'
        this.raisedHands = { ...this.raisedHands, [uid]: raised }
        if (uid === this.myUserId) this.handRaised = raised
        return
      }
      if (action === 'conference_role') {
        const uid = data.userId != null ? String(data.userId) : ''
        const role = data.role != null ? String(data.role) : ''
        if (uid && role) {
          this.participants = this.participants.map(p =>
            String(p.userId) === uid ? { ...p, role } : p
          )
        }
        void this.refreshInfo()
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
      if (!this.callId) return
      if (String(raw.callId || '') !== this.callId) return
      const from = String(raw.fromUserId || '')
      if (!from || from === this.myUserId || !raw.signalType) return

      // 对端常在本端 getUserMedia / enterInRoom 完成前就发 offer；必须缓冲，不能丢
      if (this.phase !== 'in_room') {
        earlySignals.push({ peerId: from, raw })
        return
      }

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
        await this.attachLocalTracksTo(pc)
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
        // answer 不能被 ignoreOffer 挡住：glare 时 impolite 方仍须接受对本端 offer 的应答
        // 也不能用 !currentRemoteDescription：重协商时已有 remoteDescription，否则 answer 会被静默丢弃
        if (pc.signalingState !== 'have-local-offer') return
        await pc.setRemoteDescription({ type: 'answer', sdp: raw.sdp })
        await this.flushPendingCandidates(peerId)
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

      // 预先建立 audio/video sendrecv transceiver：
      // 即使本端关摄像头，SDP 也带 video m-line，对端开摄像头后 replaceTrack 即可被收到
      pc.addTransceiver('audio', { direction: 'sendrecv' })
      pc.addTransceiver('video', { direction: 'sendrecv' })
      await this.attachLocalTracksTo(pc)

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
        evt.track.onunmute = () => {
          const cur = this.remoteStreams[peerId]
          if (cur) this.remoteStreams = { ...this.remoteStreams, [peerId]: markRaw(cur) }
        }
        evt.track.onended = () => {
          const cur = this.remoteStreams[peerId]
          if (!cur) return
          const next = markRaw(
            new MediaStream(cur.getTracks().filter(t => t.id !== evt.track.id && t.readyState !== 'ended'))
          )
          if (next.getTracks().length === 0) {
            const { [peerId]: _, ...rest } = this.remoteStreams
            this.remoteStreams = rest
          } else {
            this.remoteStreams = { ...this.remoteStreams, [peerId]: next }
          }
        }
        this.remoteStreams = { ...this.remoteStreams, [peerId]: markRaw(stream) }
        // 对端开了摄像头：纠正本地可能过期的 videoOff 标记
        if (evt.track.kind === 'video') {
          this.participants = this.participants.map(p =>
            String(p.userId) === peerId ? { ...p, videoOff: false } : p
          )
        }
      }

      pc.onconnectionstatechange = () => {
        const state = pc.connectionState
        if (state === 'connected') {
          iceRestartAttempts.delete(peerId)
          if (
            this.networkHint.includes('连接') ||
            this.networkHint.includes('重建') ||
            this.networkHint.includes('connection') ||
            this.networkHint.includes('rebuild') ||
            this.networkHint.includes('retry')
          ) {
            this.networkHint = ''
          }
        } else if (state === 'failed' || state === 'disconnected') {
          this.networkHint = t('conference.networkRetry', { state })
          void this.recoverPeer(peerId)
        } else if (state === 'closed') {
          this.closePeer(peerId)
        }
      }

      return slot
    },

    async recoverPeer(peerId: string) {
      if (this.phase !== 'in_room') return
      if (!this.participants.some(p => String(p.userId) === peerId && isAdmittedParticipant(p))) {
        return
      }
      const attemptsSoFar = iceRestartAttempts.get(peerId) || 0
      const decision = decideIceRestart({
        attemptsSoFar,
        reason: 'failed',
        callType: this.type,
        cameraOn: this.cameraOn && !this.screenSharing,
        isActive: this.phase === 'in_room'
      })
      if (decision.action === 'noop') return

      // 超限：close + 重建 PC
      if (decision.action === 'give_up') {
        iceRestartAttempts.delete(peerId)
        this.networkHint = t('conference.networkRebuild')
        this.closePeer(peerId)
        await this.ensurePeer(peerId)
        if (shouldInitiateOffer(this.myUserId, peerId)) {
          await this.createOfferTo(peerId)
        }
        return
      }

      iceRestartAttempts.set(peerId, decision.nextAttempts)
      this.networkHint = decision.message
      if (decision.disableCamera && this.cameraOn && !this.screenSharing) {
        this.cameraOn = false
        this.participants = this.participants.map(p =>
          String(p.userId) === this.myUserId ? { ...p, videoOff: true } : p
        )
        localStream?.getVideoTracks().forEach(t => {
          t.enabled = false
        })
        await this.replaceTracksOnPeers()
        if (this.conferenceId) {
          void conferenceApi.setVideo(this.conferenceId, true).catch(() => {
            /* ignore */
          })
        }
      }

      const slot = peers.get(peerId)
      if (!slot) {
        await this.ensurePeer(peerId)
        if (shouldInitiateOffer(this.myUserId, peerId)) {
          await this.createOfferTo(peerId)
        }
        return
      }

      try {
        if (typeof slot.pc.restartIce === 'function') {
          slot.pc.restartIce()
        }
        if (!shouldInitiateOffer(this.myUserId, peerId)) return
        if (slot.pc.signalingState !== 'stable' && slot.pc.signalingState !== 'have-local-offer') {
          return
        }
        slot.makingOffer = true
        await this.attachLocalTracksTo(slot.pc)
        const offer = await slot.pc.createOffer({ iceRestart: true })
        await slot.pc.setLocalDescription(offer)
        await this.sendSignal({
          signalType: 'offer',
          sdp: offer.sdp,
          targetUserId: peerId
        })
      } catch (e) {
        console.warn('[conference] ICE restart failed, rebuilding peer', e)
        this.closePeer(peerId)
        await this.ensurePeer(peerId)
        if (shouldInitiateOffer(this.myUserId, peerId)) {
          await this.createOfferTo(peerId)
        }
      } finally {
        const s = peers.get(peerId)
        if (s) s.makingOffer = false
      }
    },

    attachLocalTracksTo(pc: RTCPeerConnection) {
      return this.applyLocalTracks(pc, false)
    },

    /** @param renegotiateHint 是否在视频从无到有时标记需要重协商（由调用方处理） */
    async applyLocalTracks(pc: RTCPeerConnection, _renegotiateHint: boolean): Promise<boolean> {
      const stream = localStream
      let videoActivated = false
      if (stream) {
        for (const track of stream.getTracks()) {
          if (track.kind === 'video' && (!this.cameraOn || this.type !== 'video') && !this.screenSharing) {
            continue
          }
          const sender = findSenderForKind(pc, track.kind)
          if (sender) {
            const hadNoTrack = !sender.track
            const different = sender.track?.id !== track.id
            if (hadNoTrack || different) {
              try {
                await sender.replaceTrack(track)
                if (track.kind === 'video' && (hadNoTrack || different)) videoActivated = true
              } catch {
                /* ignore */
              }
            }
            // 轨已在 sender 上且未变化：不强制重协商（delayed publish 仅在首次挂轨时触发）
          } else {
            pc.addTrack(track, stream)
            if (track.kind === 'video') videoActivated = true
          }
        }
      }
      if ((!this.cameraOn || this.type !== 'video') && !this.screenSharing) {
        const videoSender = findSenderForKind(pc, 'video')
        if (videoSender?.track) {
          try {
            await videoSender.replaceTrack(null)
          } catch {
            /* ignore */
          }
        }
        videoActivated = false
      }
      return videoActivated
    },

    async replaceTracksOnPeers() {
      const stream = localStream
      if (!stream && !this.cameraOn) {
        // 仍可能需要清空各 peer 的 video
      }
      const needOffer: string[] = []
      for (const [peerId, slot] of peers) {
        const videoActivated = await this.applyLocalTracks(slot.pc, true)
        // 视频轨刚挂上必须重协商，否则对端可能收不到
        if (videoActivated) {
          needOffer.push(peerId)
        }
      }
      for (const peerId of needOffer) {
        try {
          await this.createOfferTo(peerId)
        } catch (e) {
          console.warn('[conference] renegotiate after video publish failed', peerId, e)
        }
      }
    },

    async createOfferTo(peerId: string) {
      const slot = await this.ensurePeer(peerId)
      // caller（syncMeshPeers / replaceTracksOnPeers）已在上层或 ensurePeer 中调用 attachLocalTracksTo，
      // 此处不再重复挂载，避免 replaceTracksOnPeers 中 applyLocalTracks 与 createOfferTo 各挂一次
      if (slot.pc.signalingState !== 'stable') return
      slot.makingOffer = true
      try {
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
     * 只连 admitStatus!==0 的参与者（缺省视为已准入）。
     */
    async syncMeshPeers() {
      if (this.phase !== 'in_room' || !this.myUserId) return
      const others = this.participants
        .filter(p => isAdmittedParticipant(p))
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
      iceRestartAttempts.delete(peerId)
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
        if (wantVideo && localStream.getVideoTracks().some(t => t.readyState === 'live')) {
          // 成功拿到摄像头时清掉「已降级」提示
          if (this.networkHint.includes('摄像头') || this.networkHint.includes('降级') || this.networkHint.includes('Camera') || this.networkHint.includes('audio')) {
            this.networkHint = ''
          }
        }
        await this.replaceTracksOnPeers()
      } catch (e) {
        const name = (e as DOMException)?.name
        if (wantVideo && (name === 'NotAllowedError' || name === 'NotFoundError' || name === 'NotReadableError')) {
          this.cameraOn = false
          this.networkHint = t('conference.cameraFallback')
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
        // 恢复状态后重试获取流；如果摄像头不可用，降级到仅音频不应算「失败」
        if (this.type === 'video') {
          try {
            await this.ensureLocalMedia()
            // 恢复成功（可能已降级为音频），不再把原始异常往上抛
            return
          } catch {
            /* 降级也失败，继续向外抛原始异常 */
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
      try {
        await conferenceApi.signal({
          conferenceId: this.conferenceId,
          signalType: payload.signalType,
          sdp: payload.sdp,
          candidate: payload.candidate,
          targetUserId: payload.targetUserId
        })
      } catch (e) {
        console.warn('[conference] sendSignal failed', payload.signalType, payload.targetUserId, e)
        throw e
      }
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

    async transferHostTo(userId: string) {
      if (!this.conferenceId) return
      await conferenceApi.transferHost(this.conferenceId, userId)
      await this.refreshInfo()
    },

    async admitUser(userId: string) {
      if (!this.conferenceId) return
      await conferenceApi.admit(this.conferenceId, userId)
      await this.refreshInfo()
      await this.syncMeshPeers()
    },

    async setCoHost(userId: string, enable: boolean) {
      if (!this.conferenceId) return
      await conferenceApi.setRole(this.conferenceId, userId, enable ? 'co-host' : 'member')
      await this.refreshInfo()
    },

    async toggleRaise() {
      if (!this.conferenceId) return
      const next = !this.handRaised
      this.handRaised = next
      this.raisedHands = { ...this.raisedHands, [this.myUserId]: next }
      try {
        await conferenceApi.raise(this.conferenceId, next)
      } catch (e) {
        this.handRaised = !next
        this.raisedHands = { ...this.raisedHands, [this.myUserId]: !next }
        throw e
      }
    },

    async toggleScreenShare() {
      if (this.phase !== 'in_room') return
      if (this.screenSharing) {
        this.stopScreenShareTracks()
        this.screenSharing = false
        try {
          if (this.type === 'video' && this.cameraOn) {
            await this.ensureLocalMedia()
          } else {
            await this.replaceTracksOnPeers()
          }
        } catch (e) {
          console.warn('[conference] restore camera after screen share failed', e)
        }
        return
      }
      try {
        const display = await navigator.mediaDevices.getDisplayMedia({
          video: true,
          audio: false
        })
        const track = display.getVideoTracks()[0]
        if (!track) {
          display.getTracks().forEach(t => t.stop())
          return
        }
        screenStream = display
        track.onended = () => {
          if (this.screenSharing) {
            void this.toggleScreenShare()
          }
        }
        // 用屏幕轨替换各 peer 的 video sender
        for (const [, slot] of peers) {
          const videoSender = slot.pc.getSenders().find(s => s.track?.kind === 'video')
          if (videoSender) {
            try {
              await videoSender.replaceTrack(track)
            } catch {
              /* ignore */
            }
          } else {
            slot.pc.addTrack(track, display)
          }
        }
        // 本地预览也切到屏幕
        if (localStream) {
          const oldVideo = localStream.getVideoTracks()[0]
          if (oldVideo) {
            localStream.removeTrack(oldVideo)
            oldVideo.stop()
          }
          localStream.addTrack(track)
          this.localStream = markRaw(new MediaStream(localStream.getTracks()))
        } else {
          localStream = markRaw(new MediaStream([track]))
          this.localStream = localStream
        }
        this.screenSharing = true
      } catch (e) {
        const name = (e as DOMException)?.name
        if (name === 'NotAllowedError' || name === 'AbortError') return
        throw e
      }
    },

    stopScreenShareTracks() {
      if (screenStream) {
        screenStream.getTracks().forEach(t => {
          try {
            t.stop()
          } catch {
            /* ignore */
          }
        })
        screenStream = null
      }
    },

    async leave() {
      const cid = this.conversationId
      const confId = this.conferenceId
      if (this.conferenceId) {
        try {
          await conferenceApi.leave(this.conferenceId)
        } catch {
          /* ignore */
        }
      }
      this.cleanupLocal()
      this.phase = 'idle'
      // 离开不散会：刷新顶栏人数；会议仍 ACTIVE 时其他人还能加入
      if (cid) void this.fetchSessionActive(cid, confId || undefined)
    },

    async endAsHost() {
      if (!this.conferenceId) return
      const cid = this.conversationId
      const confId = this.conferenceId
      await conferenceApi.end(this.conferenceId)
      this.cleanupLocal()
      this.phase = 'idle'
      if (cid) this.clearSessionActive(cid, confId)
    },

    dismissInvite() {
      stopCallRing()
      this.invitePrompt = null
      if (this.phase === 'lobby') this.phase = 'idle'
    },

    notifyConferenceInvite(title: string, creatorName: string) {
      try {
        if (typeof Notification === 'undefined') return
        const body = creatorName
          ? `${creatorName} 邀请你加入「${title}」`
          : `邀请你加入「${title}」`
        if (Notification.permission === 'granted') {
          new Notification('会议邀请', { body, silent: true })
        } else if (Notification.permission === 'default') {
          void Notification.requestPermission().then(p => {
            if (p === 'granted') new Notification('会议邀请', { body, silent: true })
          })
        }
      } catch {
        /* ignore */
      }
    },

    startQualityWatch() {
      this.stopQualityWatch()
      weakNetChecks = 0
      qualityTimer = setInterval(() => {
        void this.tickQualityAndSpeaker()
      }, 3000)
    },

    stopQualityWatch() {
      if (qualityTimer) {
        clearInterval(qualityTimer)
        qualityTimer = null
      }
    },

    async tickQualityAndSpeaker() {
      if (this.phase !== 'in_room') return

      // 弱网：聚合各 peer inbound-rtp 丢包，决定是否关摄像头
      let packetsLost = 0
      let packetsReceived = 0
      for (const [, slot] of peers) {
        try {
          const stats = await slot.pc.getStats()
          stats.forEach(r => {
            if (r.type === 'inbound-rtp' && 'packetsLost' in r) {
              packetsLost += Number(r.packetsLost || 0)
              packetsReceived += Number((r as { packetsReceived?: number }).packetsReceived || 0)
            }
          })
        } catch {
          /* ignore */
        }
      }
      if (!this.screenSharing) {
        const decision = decideWeakNetVideo({
          packetsLost,
          packetsReceived,
          callType: this.type,
          cameraOn: this.cameraOn,
          weakNetChecks
        })
        if (decision.action === 'reset_checks') {
          weakNetChecks = 0
        } else if (decision.action === 'accumulate') {
          weakNetChecks = decision.nextChecks
        } else if (decision.action === 'disable_camera') {
          weakNetChecks = 0
          this.cameraOn = false
          this.networkHint = decision.message
          this.participants = this.participants.map(p =>
            String(p.userId) === this.myUserId ? { ...p, videoOff: true } : p
          )
          localStream?.getVideoTracks().forEach(t => {
            t.enabled = false
          })
          await this.replaceTracksOnPeers()
          if (this.conferenceId) {
            void conferenceApi.setVideo(this.conferenceId, true).catch(() => {
              /* ignore */
            })
          }
        }
      }

      // 简易 activeSpeaker：远端音频 level，无则回退 host
      let bestId = ''
      let bestLevel = 0.01
      for (const [peerId, slot] of peers) {
        try {
          const stats = await slot.pc.getStats()
          stats.forEach(r => {
            if (r.type === 'inbound-rtp' && (r as { kind?: string }).kind === 'audio') {
              const level = Number((r as { audioLevel?: number }).audioLevel || 0)
              if (level > bestLevel) {
                bestLevel = level
                bestId = peerId
              }
            }
          })
        } catch {
          /* ignore */
        }
        // audioLevel 从 getStats() inbound-rtp 获取，兜底不再需要
      }
      if (!bestId) {
        const host =
          this.participants.find(p => p.role === 'host') ||
          this.participants.find(p => String(p.userId) === this.creatorId)
        bestId = host ? String(host.userId) : this.myUserId
      }
      if (bestId && bestId !== this.activeSpeakerId) {
        this.activeSpeakerId = bestId
      }
    },

    cleanupLocal() {
      stopCallRing()
      this.stopQualityWatch()
      this.clearMeshRetries()
      this.stopScreenShareTracks()
      this.closeAllPeers()
      iceRestartAttempts.clear()
      earlySignals = []
      signalQueue = Promise.resolve()
      weakNetChecks = 0
      if (localStream) {
        localStream.getTracks().forEach(t => t.stop())
        localStream = null
      }
      this.localStream = null
      this.conferenceId = null
      this.callId = null
      this.conversationId = null
      this.participants = []
      this.networkHint = ''
      this.raisedHands = {}
      this.handRaised = false
      this.screenSharing = false
      this.activeSpeakerId = ''
      this.chatOpen = false
      this.hasPassword = false
      this.lobbyEnabled = false
      this.uiMinimized = false
    },

    clearError() {
      this.errorMessage = ''
    },

    remoteStreamFor(userId: string): MediaStream | null {
      return this.remoteStreams[String(userId)] || null
    }
  }
})
