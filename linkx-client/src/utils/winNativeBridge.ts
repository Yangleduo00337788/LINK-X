/**
 * 作者：yangleduo
 */
/**
 * Windows 原生壳桥接：任务栏闪动、通知点击、来电弹出、通话缩略图工具栏。
 */
import { watch } from 'vue'
import { useAppStore } from '../stores/app'
import { useCallStore } from '../stores/call'
import { useMomentsStore } from '../stores/moments'
import { OFFICIAL_NOTIFY_SESSION_ID } from '../types'
import { isWindowInBackground } from './messageNotify'

export type NotificationActionPayload = {
  kind: 'session' | 'official' | 'contacts' | 'calendar' | 'moments' | 'focus'
  sessionId?: string
  notificationId?: string
}

function handleNotificationAction(
  appStore: ReturnType<typeof useAppStore>,
  payload: NotificationActionPayload | undefined
) {
  if (!payload?.kind) return
  switch (payload.kind) {
    case 'session':
      if (!payload.sessionId) return
      appStore.setNav('chat')
      const session = appStore.sessions.find(s => s.id === payload.sessionId)
      if (session) appStore.selectSession(session)
      break
    case 'official':
      appStore.setNav('chat')
      const official = appStore.sessions.find(s => s.id === OFFICIAL_NOTIFY_SESSION_ID)
      if (official) appStore.selectSession(official)
      if (payload.notificationId) {
        void window.electronAPI?.openOfficialNotifyDetail?.(payload.notificationId)
      }
      break
    case 'contacts':
      appStore.setNav('contacts')
      break
    case 'calendar':
      appStore.setNav('calendar')
      break
    case 'moments':
      appStore.setNav('chat')
      void useMomentsStore().ensurePanelReady().then(() => {
        useMomentsStore().openPanel()
      })
      break
    case 'focus':
      break
  }
}

/** 初始化 Win32 原生能力桥接（仅 Windows Electron） */
export function initWinNativeBridge(): void {
  const api = window.electronAPI
  if (!api?.isElectron || api.getPlatform?.() !== 'windows') return

  const appStore = useAppStore()
  const callStore = useCallStore()

  api.onNotificationAction?.(payload => {
    if (!payload?.kind) return
    handleNotificationAction(appStore, payload as NotificationActionPayload)
  })

  api.onCallToolbarAction?.(action => {
    if (action === 'accept') void callStore.acceptIncoming()
    else if (action === 'reject') void callStore.rejectIncoming()
    else if (action === 'hangup') void callStore.hangup()
  })

  watch(
    () => callStore.phase,
    phase => {
      if (phase === 'incoming') {
        void api.showMainWindow?.()
        if (isWindowInBackground()) {
          void api.flashWindow?.(true)
        }
      } else if (phase === 'idle' || phase === 'ended') {
        void api.flashWindow?.(false)
      }
      if (
        phase === 'incoming' ||
        phase === 'outgoing' ||
        phase === 'connecting' ||
        phase === 'connected'
      ) {
        void api.syncCallToolbar?.({ phase })
      } else {
        void api.syncCallToolbar?.({ phase: 'idle' })
      }
    },
    { immediate: true }
  )
}
