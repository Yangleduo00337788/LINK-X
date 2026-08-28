/**
 * 作者：yangleduo
 */
import type { NavKey, SettingsTab } from '../types'
import { useAppStore } from '../stores/app'
import { useExtensionDockStore } from '../stores/extensionDock'
import { useMomentsStore } from '../stores/moments'
import { useShortVideoStore } from '../stores/shortVideo'
import { useLinkMateStore } from '../stores/linkmate'
import { useSettingsStore } from '../stores/settings'
import { useContactsStore } from '../stores/contacts'
import { useFavoritesStore } from '../stores/favorites'
import { useDriveStore } from '../stores/drive'
import { useCalendarStore } from '../stores/calendar'
import { useNotificationsStore } from '../stores/notifications'

export interface ApplyAgentNavOptions {
  settingsTab?: SettingsTab
}

/** 与 Sidebar.handleClick 对齐的导航逻辑 */
export async function applyAgentNav(key: NavKey, options?: ApplyAgentNavOptions): Promise<void> {
  const app = useAppStore()
  const extensionDock = useExtensionDockStore()

  if (key === 'moments') {
    app.setNav('chat')
    if (extensionDock.panelCollapsed) extensionDock.expandPanel()
    await useMomentsStore().ensurePanelReady()
    useMomentsStore().openPanel()
    refreshNavData('moments')
    return
  }

  if (key === 'shortVideo') {
    app.setNav('chat')
    if (extensionDock.panelCollapsed) extensionDock.expandPanel()
    await useShortVideoStore().ensurePanelReady()
    useShortVideoStore().openPanel()
    refreshNavData('shortVideo')
    return
  }

  if (key === 'linkmate') {
    await openLinkMatePanel()
    return
  }

  if (key === 'settings') {
    useSettingsStore().openSettings(options?.settingsTab ?? 'account')
    return
  }

  app.setNav(key)
  refreshNavData(key)
}

export async function openLinkMatePanel(): Promise<void> {
  const app = useAppStore()
  const extensionDock = useExtensionDockStore()
  app.setNav('chat')
  if (extensionDock.panelCollapsed) extensionDock.expandPanel()
  const linkMate = useLinkMateStore()
  await linkMate.ensurePanelReady()
  linkMate.openPanel()
}

function refreshNavData(key: NavKey) {
  switch (key) {
    case 'chat':
      void useAppStore().loadChatSessions()
      break
    case 'contacts':
      void useContactsStore().fetchFriends()
      void useNotificationsStore().fetchFriendRequests()
      void useNotificationsStore().fetchGroupInvitations()
      break
    case 'favorites':
      void useFavoritesStore().refreshAll()
      break
    case 'files':
      void useDriveStore().refreshAll()
      break
    case 'calendar':
      void useCalendarStore().fetchEvents()
      break
    case 'moments':
      void useMomentsStore().fetchMoments()
      break
    case 'shortVideo':
      void useShortVideoStore().ensurePanelReady()
      break
    default:
      break
  }
}
