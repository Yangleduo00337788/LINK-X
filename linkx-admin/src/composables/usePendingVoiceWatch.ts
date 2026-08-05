import { onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchDashboardSummary } from '@/api/dashboard'
import { notifyPendingTask } from '@/utils/adminNotify'
import { usePreferencesStore } from '@/stores/preferences'
import { useAuthStore } from '@/stores/auth'

const POLL_MS = 12000

/** 轮询待处理反馈数量，SSE 未达时仍能触发语音提醒。 */
export function usePendingVoiceWatch() {
  const { t, locale } = useI18n()
  const prefs = usePreferencesStore()
  const auth = useAuthStore()
  let timer: ReturnType<typeof setInterval> | null = null
  let lastPendingFeedback: number | null = null
  let baselineReady = false

  async function tick() {
    if (document.visibilityState !== 'visible') return
    if (!prefs.voiceNotifyEnabled) return
    if (!auth.hasPermission('admin:feedback:list')) return
    try {
      const summary = await fetchDashboardSummary()
      const current = Number(summary?.pendingFeedback ?? 0)
      if (!baselineReady) {
        lastPendingFeedback = current
        baselineReady = true
        return
      }
      if (lastPendingFeedback !== null && current > lastPendingFeedback) {
        notifyPendingTask(t, locale.value)
      }
      lastPendingFeedback = current
    } catch {
      /* ignore */
    }
  }

  onMounted(() => {
    void tick()
    timer = setInterval(() => void tick(), POLL_MS)
    document.addEventListener('visibilitychange', onVisibility)
  })

  onUnmounted(() => {
    if (timer) clearInterval(timer)
    document.removeEventListener('visibilitychange', onVisibility)
  })

  function onVisibility() {
    if (document.visibilityState === 'visible') void tick()
  }
}
