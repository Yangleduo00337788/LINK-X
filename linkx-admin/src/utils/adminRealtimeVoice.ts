/**
 * 作者：yangleduo
 */
import type { AdminRealtimeEvent } from '@/api/realtime'
import type { Composer } from 'vue-i18n'
import { speakText } from '@/utils/voiceNotify'

let lastPendingVoiceAt = 0
const PENDING_VOICE_DEDUPE_MS = 15000

const PENDING_EVENT_TYPES = new Set([
  'review_created',
  'risk_created',
  'feedback_created',
  'feedback_escalated',
  'review_escalated',
])

function cleanText(value: unknown, max = 240) {
  const s = typeof value === 'string' ? value.trim().replace(/\s+/g, ' ') : ''
  if (!s) return ''
  return s.length <= max ? s : `${s.slice(0, max)}…`
}

/** 待处理事项：固定提示语（15s 内去重）。 */
export function voicePendingTask(t: Composer['t'], locale: string) {
  const now = Date.now()
  if (now - lastPendingVoiceAt < PENDING_VOICE_DEDUPE_MS) return
  lastPendingVoiceAt = now
  speakText(t('voiceNotify.pendingTask'), locale)
}

/** 根据实时事件生成并朗读对应文案。 */
export function voiceForAdminEvent(
  evt: AdminRealtimeEvent,
  t: Composer['t'],
  locale: string
) {
  const type = String(evt?.type || '')
  if (!type) return

  if (PENDING_EVENT_TYPES.has(type)) {
    voicePendingTask(t, locale)
    return
  }

  let text = ''
  switch (type) {
    case 'admin_notice_published': {
      const title = cleanText(evt.title, 80)
      const content = cleanText(evt.content, 200)
      if (title && content) text = `${title}。${content}`
      else text = content || title || t('notice.adminBulletinGeneric')
      break
    }
    case 'admin_notice_unpublished': {
      const title = cleanText(evt.title, 80)
      text = title
        ? t('notice.adminBulletinRecalled', { title })
        : t('notice.adminBulletinRecalledGeneric')
      break
    }
    case 'export_ready':
      text = t('common.exportReady')
      break
    case 'export_failed': {
      const err = cleanText(evt.error, 120)
      text = err || t('common.exportFailed')
      break
    }
    default:
      return
  }

  speakText(text, locale)
}
