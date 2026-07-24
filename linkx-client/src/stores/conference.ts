/**
 * 多人会议 Store：进出房、成员状态、本地预览媒体。
 */
import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import * as conferenceApi from '../api/conference'
import type { ConferenceInfo, ConferenceParticipant } from '../api/conference'

export type ConferencePhase = 'idle' | 'lobby' | 'in_room' | 'ended'

let localStream: MediaStream | null = null
let qualityTimer: ReturnType<typeof setInterval> | null = null

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
    networkHint: '' as string,
    invitePrompt: null as {
      conferenceId: string
      title: string
      conversationId: string
      callId?: string
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
        userId: String(p.userId)
      }))
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
      if (
        action === 'conference_join' ||
        action === 'conference_leave' ||
        action === 'conference_mute' ||
        action === 'conference_host'
      ) {
        void this.refreshInfo()
      }
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
      } catch (e) {
        const name = (e as DOMException)?.name
        if (wantVideo && (name === 'NotAllowedError' || name === 'NotFoundError' || name === 'NotReadableError')) {
          // 权限/设备异常：降级仅语音
          this.cameraOn = false
          this.networkHint = '摄像头不可用，已降级为语音'
          localStream = await navigator.mediaDevices.getUserMedia({
            audio: this.selectedAudioId ? { deviceId: { exact: this.selectedAudioId } } : true,
            video: false
          })
          localStream.getAudioTracks().forEach(t => {
            t.enabled = this.micOn
          })
          this.localStream = markRaw(localStream)
          if (this.conferenceId) {
            void conferenceApi.setVideo(this.conferenceId, true)
          }
          return
        }
        throw e
      }
    },

    async toggleMic() {
      this.micOn = !this.micOn
      this.localStream?.getAudioTracks().forEach(t => {
        t.enabled = this.micOn
      })
      if (this.conferenceId) {
        await conferenceApi.mute(this.conferenceId, this.myUserId, !this.micOn)
        await this.refreshInfo()
      }
    },

    async toggleCamera() {
      this.cameraOn = !this.cameraOn
      if (this.type === 'video') {
        await this.ensureLocalMedia()
      }
      if (this.conferenceId) {
        await conferenceApi.setVideo(this.conferenceId, !this.cameraOn)
        await this.refreshInfo()
      }
    },

    /**
     * 转发 WebRTC 信令（offer/answer/ice），供后续 mesh 或对端协商使用。
     * 当前会议房以管理面+本地预览为主，仍完整对接 /conference/signal。
     */
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
      await conferenceApi.mute(this.conferenceId, userId, muted)
      await this.refreshInfo()
    },

    async removeTarget(userId: string) {
      if (!this.conferenceId) return
      await conferenceApi.removeMember(this.conferenceId, userId)
      await this.refreshInfo()
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
        // 弱网启发式：无视频轨或 video 被禁用时提示；有轨则根据 muted/ended 判断
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
    }
  }
})
