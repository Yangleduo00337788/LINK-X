/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import { useLinkMateStore } from './linkmate'
import { useMomentsStore, type MomentsPanelTabId } from './moments'
import { useShortVideoStore, type ShortVideoPanelTabId } from './shortVideo'
import { useNoteStore, type NotePanelTabId } from './note'
import { t } from '../i18n'

export type ExtensionDockState = 'closed' | 'open' | 'collapsed'
export type ExtensionTabKey =
  | `linkmate:${string}`
  | `moments:${MomentsPanelTabId}`
  | `shortVideo:${ShortVideoPanelTabId}`
  | `notes:${NotePanelTabId}`

const PANEL_WIDTH_STORAGE_KEY = 'linkx-extension-dock-width'
export const EXTENSION_DOCK_WIDTH_MIN = 320
export const EXTENSION_DOCK_WIDTH_MAX = 720
export const EXTENSION_DOCK_WIDTH_DEFAULT = 420

function clampPanelWidth(width: number): number {
  return Math.min(EXTENSION_DOCK_WIDTH_MAX, Math.max(EXTENSION_DOCK_WIDTH_MIN, width))
}

function loadPanelWidth(): number {
  try {
    const raw = localStorage.getItem(PANEL_WIDTH_STORAGE_KEY)
    const parsed = raw ? Number(raw) : NaN
    if (Number.isFinite(parsed)) {
      return clampPanelWidth(parsed)
    }
  } catch {
    /* ignore */
  }
  return EXTENSION_DOCK_WIDTH_DEFAULT
}

function persistPanelWidth(width: number) {
  try {
    localStorage.setItem(PANEL_WIDTH_STORAGE_KEY, String(width))
  } catch {
    /* ignore */
  }
}

export interface ExtensionDockTab {
  key: ExtensionTabKey
  kind: 'linkmate' | 'moments' | 'shortVideo' | 'notes'
  title: string
  tabId: string
}

export const useExtensionDockStore = defineStore('extensionDock', {
  state: () => ({
    panelState: 'closed' as ExtensionDockState,
    panelWidth: loadPanelWidth(),
    activeTabKey: null as ExtensionTabKey | null
  }),

  getters: {
    panelExpanded(state): boolean {
      return state.panelState === 'open'
    },
    panelCollapsed(state): boolean {
      return state.panelState === 'collapsed'
    },
    isVisible(state): boolean {
      return state.panelState !== 'closed'
    },
    allTabs(): ExtensionDockTab[] {
      const linkMate = useLinkMateStore()
      const moments = useMomentsStore()
      const shortVideo = useShortVideoStore()
      const notes = useNoteStore()
      const tabs: ExtensionDockTab[] = []
      for (const id of linkMate.openTabIds) {
        const session = linkMate.sessions.find(s => s.id === id)
        tabs.push({
          key: `linkmate:${id}`,
          kind: 'linkmate',
          title: session?.title || t('linkmate.newChat'),
          tabId: id
        })
      }
      for (const tab of moments.openTabs) {
        tabs.push({
          key: `moments:${tab.id}`,
          kind: 'moments',
          title: tab.title,
          tabId: tab.id
        })
      }
      for (const tab of shortVideo.openTabs) {
        tabs.push({
          key: `shortVideo:${tab.id}`,
          kind: 'shortVideo',
          title: tab.title,
          tabId: tab.id
        })
      }
      for (const tab of notes.openTabs) {
        tabs.push({
          key: `notes:${tab.id}`,
          kind: 'notes',
          title: tab.title,
          tabId: tab.id
        })
      }
      return tabs
    },
    activeKind(state): 'linkmate' | 'moments' | 'shortVideo' | 'notes' | null {
      if (!state.activeTabKey) return null
      if (state.activeTabKey.startsWith('linkmate:')) return 'linkmate'
      if (state.activeTabKey.startsWith('moments:')) return 'moments'
      if (state.activeTabKey.startsWith('shortVideo:')) return 'shortVideo'
      if (state.activeTabKey.startsWith('notes:')) return 'notes'
      return null
    },
    hasOpenTabs(): boolean {
      const linkMate = useLinkMateStore()
      const moments = useMomentsStore()
      const shortVideo = useShortVideoStore()
      const notes = useNoteStore()
      return (
        linkMate.openTabIds.length > 0 ||
        moments.openTabIds.length > 0 ||
        shortVideo.openTabIds.length > 0 ||
        notes.openTabIds.length > 0
      )
    }
  },

  actions: {
    setPanelWidth(width: number) {
      const next = clampPanelWidth(width)
      this.panelWidth = next
      persistPanelWidth(next)
    },

    activateTab(key: ExtensionTabKey) {
      this.activeTabKey = key
      this.panelState = 'open'
    },

    collapsePanel() {
      if (this.panelState === 'open') {
        this.panelState = 'collapsed'
      }
    },

    expandPanel() {
      if (this.panelState === 'collapsed') {
        this.panelState = 'open'
      }
    },

    syncAfterTabsChanged() {
      const tabs = this.allTabs
      if (tabs.length === 0) {
        this.panelState = 'closed'
        this.activeTabKey = null
        return
      }
      const exists = tabs.some(tab => tab.key === this.activeTabKey)
      if (!exists) {
        this.activeTabKey = tabs[tabs.length - 1].key
      }
      if (this.panelState === 'closed') {
        this.panelState = 'open'
      }
    },

    async selectTab(key: ExtensionTabKey) {
      this.activeTabKey = key
      this.panelState = 'open'
      if (key.startsWith('linkmate:')) {
        const sessionId = key.slice('linkmate:'.length)
        await useLinkMateStore().selectSession(sessionId)
        return
      }
      if (key.startsWith('moments:')) {
        const tabId = key.slice('moments:'.length) as MomentsPanelTabId
        await useMomentsStore().selectTab(tabId)
        return
      }
      if (key.startsWith('shortVideo:')) {
        const tabId = key.slice('shortVideo:'.length) as ShortVideoPanelTabId
        await useShortVideoStore().selectTab(tabId)
        return
      }
      const tabId = key.slice('notes:'.length) as NotePanelTabId
      await useNoteStore().selectTab(tabId)
    }
  }
})
