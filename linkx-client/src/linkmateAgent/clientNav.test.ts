/**
 * 作者：yangleduo
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAppStore } from '../stores/app'
import { useSettingsStore } from '../stores/settings'
import { applyAgentNav, openLinkMatePanel } from './clientNav'

vi.mock('../stores/moments', () => ({
  useMomentsStore: () => ({
    ensurePanelReady: vi.fn().mockResolvedValue(undefined),
    openPanel: vi.fn(),
    fetchMoments: vi.fn()
  })
}))

vi.mock('../stores/shortVideo', () => ({
  useShortVideoStore: () => ({
    ensurePanelReady: vi.fn().mockResolvedValue(undefined),
    openPanel: vi.fn(),
    ensurePanelReadyTask: null
  })
}))

vi.mock('../stores/linkmate', () => ({
  useLinkMateStore: () => ({
    ensurePanelReady: vi.fn().mockResolvedValue(undefined),
    openPanel: vi.fn()
  })
}))

vi.mock('../stores/extensionDock', () => ({
  useExtensionDockStore: () => ({
    panelCollapsed: false,
    expandPanel: vi.fn()
  })
}))

describe('clientNav', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('applyAgentNav sets calendar nav', async () => {
    await applyAgentNav('calendar')
    expect(useAppStore().navKey).toBe('calendar')
  })

  it('applyAgentNav opens settings with tab', async () => {
    await applyAgentNav('settings', { settingsTab: 'privacy' })
    expect(useAppStore().navKey).toBe('settings')
    expect(useSettingsStore().settingsActiveTab).toBe('privacy')
  })

  it('applyAgentNav linkmate switches to chat', async () => {
    await applyAgentNav('linkmate')
    expect(useAppStore().navKey).toBe('chat')
  })

  it('openLinkMatePanel switches to chat', async () => {
    await openLinkMatePanel()
    expect(useAppStore().navKey).toBe('chat')
  })
})
