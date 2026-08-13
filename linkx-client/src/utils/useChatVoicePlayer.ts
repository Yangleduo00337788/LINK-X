/**
 * 作者：yangleduo
 */
/**
 * 聊天语音播放（单例）：优先鉴权中转，预签名仅作回退。
 */
import { ref } from 'vue'
import type { ChatMessage } from '../types'
import * as chatApi from '../api/chat'
import { recoverMediaUrlOnError } from './mediaUrl'
import { resolveChatVoicePlaySrc } from './chatMediaAccess'
import { t } from '../i18n'

const playingVoiceId = ref<string | null>(null)
let voiceAudio: HTMLAudioElement | null = null
let voicePlaybackBlobUrl: string | null = null

function formatVoiceDuration(sec?: number) {
  const s = sec ?? 0
  return s < 60 ? `${s}"` : `${Math.floor(s / 60)}'${s % 60}"`
}

function revokeVoicePlaybackBlob() {
  if (voicePlaybackBlobUrl) {
    URL.revokeObjectURL(voicePlaybackBlobUrl)
    voicePlaybackBlobUrl = null
  }
}

function stopVoicePlayback() {
  voiceAudio?.pause()
  voiceAudio = null
  playingVoiceId.value = null
  revokeVoicePlaybackBlob()
}

export function useChatVoicePlayer(options?: {
  onInfo?: (text: string) => void
  onError?: (text: string) => void
}) {
  const notifyInfo = options?.onInfo
  const notifyError = options?.onError

  async function playVoice(msg: ChatMessage) {
    if (!msg.voiceUrl && !msg.fileUrl) {
      notifyInfo?.(`${t('chat.voice')} ${formatVoiceDuration(msg.voiceDuration)}`)
      return
    }
    if (playingVoiceId.value === msg.id) {
      stopVoicePlayback()
      return
    }
    stopVoicePlayback()

    const resolved = await resolveChatVoicePlaySrc(msg)
    if (!resolved.src) {
      notifyError?.(t('chat.voicePlayFail'))
      return
    }
    if (resolved.blobUrlToRevoke) {
      voicePlaybackBlobUrl = resolved.blobUrlToRevoke
    }

    const tryPlay = (url: string) => {
      voiceAudio = new Audio(url)
      playingVoiceId.value = msg.id
      voiceAudio.onended = () => {
        playingVoiceId.value = null
        revokeVoicePlaybackBlob()
      }
      voiceAudio.onerror = () => {
        void (async () => {
          const next = await recoverMediaUrlOnError(url, async () => {
            const res = await chatApi.refreshMessageMediaUrl(msg.id)
            if (res.code === 200 && res.data?.url) return res.data.url
            return null
          })
          if (next && next !== url) {
            revokeVoicePlaybackBlob()
            msg.voiceUrl = next
            msg.fileUrl = next
            tryPlay(next)
          } else {
            playingVoiceId.value = null
            revokeVoicePlaybackBlob()
            notifyError?.(t('chat.voicePlayFail'))
          }
        })()
      }
      voiceAudio.play().catch(() => {
        playingVoiceId.value = null
        revokeVoicePlaybackBlob()
        notifyError?.(t('chat.voicePlayFail'))
      })
    }
    tryPlay(resolved.src)
  }

  return {
    playingVoiceId,
    playVoice,
    stopVoicePlayback
  }
}
