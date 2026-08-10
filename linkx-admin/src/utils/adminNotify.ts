/**
 * 作者：yangleduo
 */
import type { AdminRealtimeEvent } from '@/api/realtime'
import type { Composer } from 'vue-i18n'
import { usePreferencesStore } from '@/stores/preferences'
import { voiceForAdminEvent, voicePendingTask } from '@/utils/adminRealtimeVoice'

/** 实时事件：仅语音朗读。 */
export function notifyAdminEvent(
  evt: AdminRealtimeEvent,
  t: Composer['t'],
  locale: string
) {
  const prefs = usePreferencesStore()
  if (!prefs.voiceNotifyEnabled) return
  voiceForAdminEvent(evt, t, locale)
}

/** 新待办：语音朗读。 */
export function notifyPendingTask(t: Composer['t'], locale: string) {
  const prefs = usePreferencesStore()
  if (!prefs.voiceNotifyEnabled) return
  voicePendingTask(t, locale)
}
