import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider } from 'naive-ui'
import { h } from 'vue'

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(async () => ({ code: 200, data: null })),
    post: vi.fn(async () => ({ code: 200, data: null })),
    put: vi.fn(async () => ({ code: 200, data: null })),
    delete: vi.fn(async () => ({ code: 200, data: null })),
    patch: vi.fn(async () => ({ code: 200, data: null }))
  }
}))

vi.mock('@/utils/chatSocket', () => ({
  connectChatSocket: vi.fn(),
  disconnectChatSocket: vi.fn(),
  sendChatSocket: vi.fn(),
  onChatEvent: vi.fn(() => () => {}),
  isChatSocketConnected: vi.fn(() => false)
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<div/>' } }]
})

const NaiveUIProviders = {
  setup(_: unknown, { slots }: { slots: { default?: () => unknown } }) {
    return () =>
      h(NConfigProvider, {}, {
        default: () =>
          h(NMessageProvider, {}, {
            default: () =>
              h(NDialogProvider, {}, {
                default: () =>
                  h(NNotificationProvider, {}, {
                    default: () => slots.default?.(),
                  }),
              }),
          }),
      })
  },
}

const baseProps = {
  msg: { type: 'text', content: 'test', isSelf: false },
  playing: false,
  items: [],
  friends: [],
  sessionId: '',
  isOwner: false,
  groupId: '',
  groupName: '',
  visible: false,
  file: null,
  imageUrl: '',
  modelValue: '',
  nav: 'chat',
  color: 'var(--lx-accent)',
  isMyPhone: false,
  isFriendChat: false,
  isGroupChat: false,
  onKeydown: () => undefined,
} satisfies Record<string, unknown>

function mountWithProviders(component: unknown, options: { props?: Record<string, unknown> } = {}) {
  return mount(NaiveUIProviders, {
    global: {
      plugins: [createPinia(), router],
      stubs: true,
    },
    slots: {
      default: () => h(component as never, { ...baseProps, ...options.props }),
    },
    attachTo: document.body,
  })
}


describe('all vue components mount smoke', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('mount components/AppShell.vue', async () => {
    const mod = await import('@/components/AppShell.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  }, 15000)

  it('mount components/Avatar.vue', async () => {
    const mod = await import('@/components/Avatar.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)

    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/CalendarMainView.vue', async () => {
    const mod = await import('@/components/CalendarMainView.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/CalendarPanel.vue', async () => {
    const mod = await import('@/components/CalendarPanel.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/AddGroupMembersModal.vue', async () => {
    const mod = await import('@/components/chat/AddGroupMembersModal.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/bubbles/DataCardBubble.vue', async () => {
    const mod = await import('@/components/chat/bubbles/DataCardBubble.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/bubbles/FileBubble.vue', async () => {
    const mod = await import('@/components/chat/bubbles/FileBubble.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/bubbles/ImageBubble.vue', async () => {
    const mod = await import('@/components/chat/bubbles/ImageBubble.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/bubbles/RedPacketBubble.vue', async () => {
    const mod = await import('@/components/chat/bubbles/RedPacketBubble.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/bubbles/TextBubble.vue', async () => {
    const mod = await import('@/components/chat/bubbles/TextBubble.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/bubbles/VoiceBubble.vue', async () => {
    const mod = await import('@/components/chat/bubbles/VoiceBubble.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/ChatInputBox.vue', async () => {
    const mod = await import('@/components/chat/ChatInputBox.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/ChatMessageItem.vue', async () => {
    const mod = await import('@/components/chat/ChatMessageItem.vue')
    const Comp = mod.default
    const wrapper = mountWithProviders(Comp)
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/ChatMoreDrawer.vue', async () => {
    const mod = await import('@/components/chat/ChatMoreDrawer.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/ComprehensiveSearchModal.vue', async () => {
    const mod = await import('@/components/chat/ComprehensiveSearchModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/ContactProfileModal.vue', async () => {
    const mod = await import('@/components/chat/ContactProfileModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/CreateGroupModal.vue', async () => {
    const mod = await import('@/components/chat/CreateGroupModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/GroupAlbumModal.vue', async () => {
    const mod = await import('@/components/chat/GroupAlbumModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/GroupAnnouncementModal.vue', async () => {
    const mod = await import('@/components/chat/GroupAnnouncementModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/GroupChatSidebar.vue', async () => {
    const mod = await import('@/components/chat/GroupChatSidebar.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/GroupEssenceModal.vue', async () => {
    const mod = await import('@/components/chat/GroupEssenceModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/GroupFilesModal.vue', async () => {
    const mod = await import('@/components/chat/GroupFilesModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/GroupInfoDrawer.vue', async () => {
    const mod = await import('@/components/chat/GroupInfoDrawer.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/GroupMutePanel.vue', async () => {
    const mod = await import('@/components/chat/GroupMutePanel.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/GroupReportPanel.vue', async () => {
    const mod = await import('@/components/chat/GroupReportPanel.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/IncomingCallModal.vue', async () => {
    const mod = await import('@/components/chat/IncomingCallModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/MessageVirtualList.vue', async () => {
    const mod = await import('@/components/chat/MessageVirtualList.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/RedPacketModal.vue', async () => {
    const mod = await import('@/components/chat/RedPacketModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/RedPacketReceiveModal.vue', async () => {
    const mod = await import('@/components/chat/RedPacketReceiveModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/VideoCallModal.vue', async () => {
    const mod = await import('@/components/chat/VideoCallModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/chat/VoiceCallModal.vue', async () => {
    const mod = await import('@/components/chat/VoiceCallModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/ChatList.vue', async () => {
    const mod = await import('@/components/ChatList.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/ChatPanel.vue', async () => {
    const mod = await import('@/components/ChatPanel.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/common/AtMentionPicker.vue', async () => {
    const mod = await import('@/components/common/AtMentionPicker.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: { friends: [] },
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/common/BannerCropModal.vue', async () => {
    const mod = await import('@/components/common/BannerCropModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/common/EmptyState.vue', async () => {
    const mod = await import('@/components/common/EmptyState.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/contacts/FriendNotifications.vue', async () => {
    const mod = await import('@/components/contacts/FriendNotifications.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/contacts/GroupNotifications.vue', async () => {
    const mod = await import('@/components/contacts/GroupNotifications.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/ContactsMainView.vue', async () => {
    const mod = await import('@/components/ContactsMainView.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/ContactsPanel.vue', async () => {
    const mod = await import('@/components/ContactsPanel.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/EditProfileModal.vue', async () => {
    const mod = await import('@/components/EditProfileModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/FavoritesMainView.vue', async () => {
    const mod = await import('@/components/FavoritesMainView.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/FavoritesPanel.vue', async () => {
    const mod = await import('@/components/FavoritesPanel.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/FilesMainView.vue', async () => {
    const mod = await import('@/components/FilesMainView.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/FilesPanel.vue', async () => {
    const mod = await import('@/components/FilesPanel.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/GroupAvatar.vue', async () => {
    const mod = await import('@/components/GroupAvatar.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/icons/PinIcon.vue', async () => {
    const mod = await import('@/components/icons/PinIcon.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/InAppToastBridge.vue', async () => {
    const mod = await import('@/components/InAppToastBridge.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/LocationPickerPage.vue', async () => {
    const mod = await import('@/components/LocationPickerPage.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/LockScreen.vue', async () => {
    const mod = await import('@/components/LockScreen.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/LoginView.vue', async () => {
    const mod = await import('@/components/LoginView.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/MainStatusBar.vue', async () => {
    const mod = await import('@/components/MainStatusBar.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/MomentsComposerModal.vue', async () => {
    const mod = await import('@/components/MomentsComposerModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/MomentsModal.vue', async () => {
    const mod = await import('@/components/MomentsModal.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/MomentsNotificationsPage.vue', async () => {
    const mod = await import('@/components/MomentsNotificationsPage.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/MomentsPanel.vue', async () => {
    const mod = await import('@/components/MomentsPanel.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/MomentsPublishPage.vue', async () => {
    const mod = await import('@/components/MomentsPublishPage.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/NoteEditor.vue', async () => {
    const mod = await import('@/components/NoteEditor.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/overlay/OverlayHost.vue', async () => {
    const mod = await import('@/components/overlay/OverlayHost.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/overlay/pages/ChatHistoryPage.vue', async () => {
    const mod = await import('@/components/overlay/pages/ChatHistoryPage.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/overlay/pages/FilePreviewPage.vue', async () => {
    const mod = await import('@/components/overlay/pages/FilePreviewPage.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/overlay/pages/HelpPage.vue', async () => {
    const mod = await import('@/components/overlay/pages/HelpPage.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/PanelSearchBar.vue', async () => {
    const mod = await import('@/components/PanelSearchBar.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/PenguinWatermark.vue', async () => {
    const mod = await import('@/components/PenguinWatermark.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/PlaceholderMainView.vue', async () => {
    const mod = await import('@/components/PlaceholderMainView.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/RegisterView.vue', async () => {
    const mod = await import('@/components/RegisterView.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/settings/AboutSettings.vue', async () => {
    const mod = await import('@/components/settings/AboutSettings.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/settings/AccountSettings.vue', async () => {
    const mod = await import('@/components/settings/AccountSettings.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/settings/AppearanceSettings.vue', async () => {
    const mod = await import('@/components/settings/AppearanceSettings.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/settings/ChatSettings.vue', async () => {
    const mod = await import('@/components/settings/ChatSettings.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/settings/FilesSettings.vue', async () => {
    const mod = await import('@/components/settings/FilesSettings.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/settings/GeneralSettings.vue', async () => {
    const mod = await import('@/components/settings/GeneralSettings.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/settings/NotificationsSettings.vue', async () => {
    const mod = await import('@/components/settings/NotificationsSettings.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/settings/PrivacySettings.vue', async () => {
    const mod = await import('@/components/settings/PrivacySettings.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/settings/ShortcutsSettings.vue', async () => {
    const mod = await import('@/components/settings/ShortcutsSettings.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/SettingsMainView.vue', async () => {
    const mod = await import('@/components/SettingsMainView.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/SettingsPanel.vue', async () => {
    const mod = await import('@/components/SettingsPanel.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/Sidebar.vue', async () => {
    const mod = await import('@/components/Sidebar.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount components/SystemNotifyPanel.vue', async () => {
    const mod = await import('@/components/SystemNotifyPanel.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount App.vue', async () => {
    const mod = await import('@/App.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount AppRoot.vue', async () => {
    const mod = await import('@/AppRoot.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('mount views/HomeView.vue', async () => {
    const mod = await import('@/views/HomeView.vue')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

})
