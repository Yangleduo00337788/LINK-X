<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 添加好友/群聊搜索模态框。
 * 保留原有综合搜索 UI，用户搜索对接后端 API。
 */
import { ref, computed } from 'vue'
import DOMPurify from 'dompurify'
import Avatar from '../Avatar.vue'
import { useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useContactsStore } from '../../stores/contacts'
import { useNotificationsStore } from '../../stores/notifications'
import * as friendApi from '../../api/friend'
import * as groupApi from '../../api/group'
import * as chatApi from '../../api/chat'
import type { UserSearchResult } from '../../types/friend'
import type { ConversationSummary } from '../../api/group'
import { useI18n } from '../../i18n'
import { LxButton } from '../ui'
import { lxColorHex } from '../../theme/vars'

interface SearchGroupItem {
  id: string
  name: string
  avatarText: string
  lastMessage?: string
}

interface SearchMessageItem {
  messageId: string
  sessionId: string
  sessionName: string
  content: string
  highlight?: string
  type: string
}

interface SearchUserItem {
  id: string
  userId?: string
  name: string
  username?: string
  avatarText: string
  avatarColor: string
  avatarUrl?: string
  online?: boolean
  isRemote: boolean
  isSelf?: boolean
}

const { t } = useI18n()
const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const notificationsStore = useNotificationsStore()
const { comprehensiveSearchOpen } = storeToRefs(chatModalsStore)
const { closeComprehensiveSearch } = chatModalsStore
const { groupSessions, sessions, userProfile } = storeToRefs(appStore)
const { openGroupSession, addFriendSession: addFriendAction, openSessionAtMessage } = appStore

const keyword = ref('')
const mainTab = ref<'all' | 'user' | 'group' | 'message'>('all')
const searched = ref(false)
const searching = ref(false)
const remoteUsers = ref<UserSearchResult[]>([])
const remoteGroups = ref<SearchGroupItem[]>([])
const messageHits = ref<SearchMessageItem[]>([])

const mainTabs = computed(() => [
  { key: 'all' as const, label: t('modals.all') },
  { key: 'user' as const, label: t('modals.user') },
  { key: 'group' as const, label: t('modals.groupChat') },
  { key: 'message' as const, label: t('modals.messages') }
])

function close() {
  closeComprehensiveSearch()
  searched.value = false
  searching.value = false
  remoteUsers.value = []
  remoteGroups.value = []
  messageHits.value = []
}

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) {
    message.warning(t('modals.enterKeyword'))
    return
  }

  searched.value = true
  searching.value = true
  remoteUsers.value = []
  remoteGroups.value = []
  messageHits.value = []

  try {
    const tasks: Promise<void>[] = []

    if (q.length >= 2) {
      tasks.push(
        (async () => {
          const [userRes, groupRes] = await Promise.all([
            friendApi.searchUsers(q),
            groupApi.listGroups()
          ])
          if (userRes.code === 200 && userRes.data) {
            remoteUsers.value = userRes.data
          }
          if (groupRes.code === 200 && groupRes.data) {
            remoteGroups.value = groupRes.data
              .filter(g => (g.name || t('modals.groupChat')).toLowerCase().includes(q.toLowerCase()))
              .map((g: ConversationSummary) => ({
                id: String(g.id),
                name: g.name || t('modals.groupChat'),
                avatarText: (g.name || t('modals.groupChar')).charAt(0),
                lastMessage: g.lastMessage
              }))
          }
        })()
      )
    }

    tasks.push(
      (async () => {
        const msgRes = await chatApi.searchMessages(q, { limit: 30 })
        if (msgRes.code === 200 && msgRes.data) {
          messageHits.value = msgRes.data.map(hit => ({
            messageId: String(hit.messageId),
            sessionId: String(hit.conversationId),
            sessionName:
              hit.conversationName ||
              sessions.value.find(s => s.id === String(hit.conversationId))?.name ||
              t('modals.chat'),
            content: hit.content || hit.fileName || '',
            highlight: hit.highlight || undefined,
            type: hit.type || 'text'
          }))
        }
      })()
    )

    await Promise.all(tasks)
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('modals.searchUserFail'))
  } finally {
    searching.value = false
  }
}

const filteredUsers = computed<SearchUserItem[]>(() => {
  if (!searched.value) return []

  const q = keyword.value.trim()
  const qLower = q.toLowerCase()
  const merged = new Map<string, SearchUserItem>()
  const meId = String(userProfile.value.userId || '')
  const meUsername = (userProfile.value.username || '').toLowerCase()

  for (const user of remoteUsers.value) {
    const name = user.nickname || user.username
    const id = String(user.id)
    merged.set(id, {
      id,
      userId: id,
      name,
      username: user.username,
      avatarText: name.charAt(0) || '?',
      avatarColor: lxColorHex.accent,
      avatarUrl: user.avatar,
      online: false,
      isRemote: true,
      isSelf: !!(meId && id === meId)
    })
  }

  for (const contact of contactsStore.searchUsers(keyword.value)) {
    if (merged.has(contact.id)) continue
    if (qLower && !contact.name.toLowerCase().includes(qLower)) continue
    merged.set(contact.id, {
      id: contact.id,
      userId: contact.userId,
      name: contact.name,
      avatarText: contact.avatarText,
      avatarColor: contact.avatarColor,
      avatarUrl: contact.avatarUrl,
      online: contact.online,
      isRemote: false,
      isSelf: !!(meId && String(contact.userId ?? contact.id) === meId)
    })
  }

  // 搜自己时后端会返回本人；本地再兜底一次
  if (
    meId &&
  (q === meId || (meUsername && qLower === meUsername)) &&
    !merged.has(meId)
  ) {
    const name = userProfile.value.nickname || userProfile.value.username || t('common.me')
    merged.set(meId, {
      id: meId,
      userId: meId,
      name,
      username: userProfile.value.username,
      avatarText: name.charAt(0) || '?',
      avatarColor: lxColorHex.accent,
      avatarUrl: userProfile.value.avatar,
      online: false,
      isRemote: true,
      isSelf: true
    })
  }

  return [...merged.values()]
})

const isLikelySelfKeyword = computed(() => {
  if (!searched.value) return false
  const q = keyword.value.trim()
  if (!q) return false
  const meId = String(userProfile.value.userId || '')
  const meUsername = (userProfile.value.username || '').toLowerCase()
  return q === meId || (meUsername && q.toLowerCase() === meUsername)
})

const filteredGroups = computed(() => {
  if (!searched.value) return []
  const q = keyword.value.trim().toLowerCase()
  const merged = new Map<string, SearchGroupItem>()

  for (const group of remoteGroups.value) {
    merged.set(group.id, group)
  }

  for (const session of groupSessions.value) {
    if (!q || session.name.toLowerCase().includes(q)) {
      merged.set(session.id, {
        id: session.id,
        name: session.name,
        avatarText: session.avatarText,
        lastMessage: session.lastMessage
      })
    }
  }

  return [...merged.values()]
})

const showGroups = computed(() => mainTab.value === 'all' || mainTab.value === 'group')
const showUsers = computed(() => mainTab.value === 'all' || mainTab.value === 'user')
const showMessages = computed(() => mainTab.value === 'all' || mainTab.value === 'message')

function openMessageHit(hit: SearchMessageItem) {
  const ok = openSessionAtMessage(hit.sessionId, hit.messageId)
  if (ok) {
    close()
    return
  }
  message.warning(t('overlay.messageNotFound'))
}

async function enterGroup(group: SearchGroupItem) {
  try {
    await openGroupSession(group.id)
    message.success(t('modals.enteredGroup', { name: group.name }))
    close()
  } catch (error) {
    message.error((error as Error).message || t('modals.enterGroupFail'))
  }
}

async function handleUserAction(user: SearchUserItem) {
  if (user.isSelf) {
    message.info(t('modals.cannotAddSelf'))
    return
  }

  if (user.isRemote && user.username) {
    try {
      const res = await friendApi.sendFriendRequest({
        username: user.username,
        message: t('modals.friendRequestMsg')
      })
      if (res.code === 200) {
        message.success(t('modals.friendRequestSent', { name: user.name }))
        await Promise.all([
          notificationsStore.fetchFriendRequests(),
          contactsStore.fetchFriends()
        ])
        close()
        return
      }
      message.error(res.message || t('modals.friendRequestFail'))
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || t('modals.friendRequestFail'))
    }
    return
  }

  if (!user.userId) {
    message.warning(t('modals.searchByIdHint'))
    return
  }

  try {
    await addFriendAction({
      userId: user.userId,
      name: user.name,
      avatarUrl: user.avatarUrl
    })
    message.success(t('modals.openedSession', { name: user.name }))
    close()
  } catch (error) {
    message.error((error as Error).message || t('modals.openSessionFail'))
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="comprehensiveSearchOpen" class="modal-root" @click.self="close">
      <div class="search-window" @click.stop>
        <header class="win-title">{{ t('modals.addFriend') }}</header>
        <div class="search-row">
          <input
            v-model="keyword"
            type="text"
            class="search-input"
            :placeholder="t('modals.searchKeywordPh')"
            @keydown.enter="doSearch"
          />
          <LxButton variant="search" :disabled="searching" @click="doSearch">
            {{ searching ? t('modals.searching') : t('modals.search') }}
          </LxButton>
        </div>
        <div class="main-tabs">
          <button
            v-for="tab in mainTabs"
            :key="tab.key"
            type="button"
            class="main-tab"
            :class="{ 'is-active': mainTab === tab.key }"
            @click="mainTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>
        <div class="result-list">
          <template v-if="!searched">
            <p class="empty-tip">{{ t('modals.searchHint') }}</p>
          </template>
          <template v-else>
            <template v-if="showUsers">
              <h4 v-if="filteredUsers.length" class="section-label">{{ t('modals.user') }}</h4>
              <article v-for="u in filteredUsers" :key="u.id" class="group-card user-card">
                <Avatar
                  :text="u.avatarText"
                  :color="u.avatarColor"
                  :size="48"
                  :image-url="u.avatarUrl"
                />
                <div class="g-body">
                  <h3 class="g-name">{{ u.name }}</h3>
                  <p class="g-meta">
                    <span v-if="u.username">{{ t('modals.linkxId', { id: u.username }) }}</span>
                    <span v-else>{{ u.online ? t('chat.online') : t('chat.offline') }}</span>
                  </p>
                </div>
                <LxButton
                  v-if="u.isSelf"
                  variant="join-self"
                  disabled
                >
                  {{ t('modals.selfAccount') }}
                </LxButton>
                <LxButton v-else variant="join" @click="handleUserAction(u)">
                  {{ u.isRemote ? t('modals.addFriendBtn') : t('modals.sendMessage') }}
                </LxButton>
              </article>
              <p
                v-if="showUsers && !filteredUsers.length && isLikelySelfKeyword"
                class="empty-tip"
              >
                {{ t('modals.searchSelfMismatch') }}
              </p>
              <p v-else-if="showUsers && !filteredUsers.length && mainTab !== 'group'" class="empty-tip">
                {{ t('modals.noMatchUser') }}
              </p>
              <p
                v-if="showUsers && !filteredUsers.length && userProfile.username"
                class="empty-tip sub"
              >
                {{ t('modals.searchIdHint', { id: userProfile.username }) }}
              </p>
            </template>
            <template v-if="showGroups">
              <h4 v-if="filteredGroups.length" class="section-label">{{ t('modals.groupChat') }}</h4>
              <article v-for="g in filteredGroups" :key="g.id" class="group-card">
                <div class="g-avatar">{{ g.avatarText.charAt(0) }}</div>
                <div class="g-body">
                  <h3 class="g-name">{{ g.name }}</h3>
                  <p class="g-meta">
                    <span>{{ g.lastMessage || t('modals.noMessages') }}</span>
                  </p>
                </div>
                <LxButton variant="join" @click="enterGroup(g)">{{ t('modals.enter') }}</LxButton>
              </article>
              <p v-if="showGroups && !filteredGroups.length && mainTab !== 'user' && mainTab !== 'message'" class="empty-tip">
                {{ t('modals.noMatchGroup') }}
              </p>
            </template>
            <template v-if="showMessages">
              <h4 v-if="messageHits.length" class="section-label">{{ t('modals.messages') }}</h4>
              <article
                v-for="m in messageHits"
                :key="m.messageId"
                class="group-card message-hit"
                @click="openMessageHit(m)"
              >
                <div class="g-avatar">💬</div>
                <div class="g-body">
                  <h3 class="g-name">{{ m.sessionName }}</h3>
                  <p
                    v-if="m.highlight"
                    class="g-meta hit-highlight"
                    v-html="DOMPurify.sanitize(m.highlight)"
                  />
                  <p v-else class="g-meta">{{ m.content || m.type }}</p>
                </div>
                <LxButton variant="join" @click.stop="openMessageHit(m)">
                  {{ t('modals.locate') }}
                </LxButton>
              </article>
              <p v-if="showMessages && !messageHits.length && mainTab === 'message'" class="empty-tip">
                {{ t('modals.noMatchMessage') }}
              </p>
            </template>
          </template>
        </div>
        <button type="button" class="close-fab" :title="t('modals.close')" @click="close">×</button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-dialog-search);
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-3xl);
}

.search-window {
  position: relative;
  width: min(920px, 96vw);
  height: min(640px, 90vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: 0 16px 56px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.win-title {
  margin: 0;
  padding: var(--lx-space-2xl) var(--lx-space-3xl) var(--lx-space);
  font-size: var(--lx-font-xl);
  font-weight: 600;
  color: var(--lx-text-body);
}

.search-row {
  display: flex;
  gap: var(--lx-space-md);
  padding: var(--lx-space) var(--lx-space-3xl) var(--lx-space-lg);
}

.search-input {
  flex: 1;
  height: 36px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 0 var(--lx-space-xl);
  font-size: var(--lx-font);
  outline: none;
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
}

.search-input:focus {
  border-color: var(--lx-accent);
}

.main-tabs {
  display: flex;
  gap: var(--lx-space-4xl);
  padding: 0 var(--lx-space-3xl);
  border-bottom: 1px solid var(--lx-border-light);
}

.main-tab {
  border: none;
  background: none;
  padding: var(--lx-space-md) 0;
  font-size: var(--lx-font);
  color: var(--lx-text-secondary);
  cursor: pointer;
  position: relative;
}

.main-tab.active {
  color: var(--lx-accent);
  font-weight: 600;
}

.main-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--lx-accent);
  border-radius: var(--lx-radius-hair);
}

.result-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--lx-space-3xl) var(--lx-space-3xl);
}

.section-label {
  margin: var(--lx-space-lg) 0 var(--lx-space);
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
  font-weight: 600;
}

.empty-tip {
  padding: var(--lx-space-5xl) 0;
  text-align: center;
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}

.user-card .g-avatar {
  display: none;
}

.group-card {
  display: flex;
  align-items: flex-start;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-xl) 0;
  border-bottom: 1px solid var(--lx-border-light);
}

.g-avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--lx-radius);
  background: linear-gradient(135deg, var(--lx-accent-light), var(--lx-accent));
  color: var(--lx-bg-card);
  font-size: var(--lx-font-3xl);
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.g-body {
  flex: 1;
  min-width: 0;
}

.g-name {
  margin: 0 0 var(--lx-space-sm);
  font-size: var(--lx-font-lg);
  font-weight: 600;
  color: var(--lx-text-body);
}

.g-meta {
  margin: 0 0 var(--lx-space-xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  display: flex;
  flex-wrap: wrap;
  gap: var(--lx-space);
  align-items: center;
}

.empty-tip.sub {
  margin-top: -var(--lx-space-xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.close-fab {
  position: absolute;
  top: 12px;
  right: 14px;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: var(--lx-font-5xl);
  color: var(--lx-text-muted);
  cursor: pointer;
  line-height: var(--lx-leading-none);
}

.close-fab:hover {
  color: var(--lx-text-body);
}
</style>
