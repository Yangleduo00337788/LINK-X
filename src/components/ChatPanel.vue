<script setup lang="ts">
import { ref, computed, onUnmounted, watch } from 'vue'
import { NIcon, NPopover, NDropdown, useMessage, type DropdownOption } from 'naive-ui'
import {
  CallOutline,
  VideocamOutline,
  GridOutline,
  AddOutline,
  EllipsisHorizontalOutline,
  PhonePortraitOutline,
  ImagesOutline
} from '@vicons/ionicons5'
import Avatar from './Avatar.vue'
import PenguinWatermark from './PenguinWatermark.vue'
import GroupChatSidebar from './chat/GroupChatSidebar.vue'
import ChatMoreDrawer from './chat/ChatMoreDrawer.vue'
import GroupInfoDrawer from './chat/GroupInfoDrawer.vue'
import ChatMessageItem from './chat/ChatMessageItem.vue'
import ChatInputBox from './chat/ChatInputBox.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useOverlayStore } from '../stores/overlay'
import { useChatModalsStore } from '../stores/chatModals'
import { useAppSettingsStore } from '../stores/appSettings'
import { useContactsStore } from '../stores/contacts'
import type { ChatMessage, ContactItem } from '../types'
import { useFavoritesStore } from '../stores/favorites'

const message = useMessage()
const favoritesStore = useFavoritesStore()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const chatModalsStore = useChatModalsStore()
const appSettingsStore = useAppSettingsStore()
const contactsStore = useContactsStore()
const { currentSession, currentMessages, userProfile, currentSessionId, savedLogin } = storeToRefs(appStore)
const { chatBackground } = storeToRefs(appSettingsStore)
const { recallMessage: recallMessageInStore } = appStore
const { open: openOverlay } = overlayStore
const {
  toggleMore,
  toggleGroupInfo,
  closeMore,
  closeGroupInfo,
  openVoiceCall,
  openVideoCall,
  openAddMembers,
  openGroupFiles,
  openGroupAlbum,
  openGroupEssence,
  openGroupAnnouncement,
  openRedPacketReceive,
  openContactProfile,
  openSelfProfile,
} = chatModalsStore

const groupGridItems = ['群文件', '群相册', '群精华', '群公告']

function onGroupAppClick(item: string) {
  if (item === '群文件') openGroupFiles()
  else if (item === '群相册') openGroupAlbum()
  else if (item === '群精华') openGroupEssence()
  else if (item === '群公告') openGroupAnnouncement()
}

const isGroupChat = computed(
  () => hasSession.value && !!currentSession.value?.isGroup && !isMyPhone.value
)


const isMyPhone = computed(() => currentSession.value?.name === '我的手机')
const hasSession = computed(() => !!currentSession.value)
const isFriendChat = computed(
  () => hasSession.value && !currentSession.value?.isGroup && !isMyPhone.value
)

const showPhoneDemo = computed(
  () => isMyPhone.value && currentMessages.value.filter(m => m.type !== 'system').length === 0
)



const chatMessages = computed(() =>
  currentMessages.value.filter(m => m.type !== 'system')
)

const playingVoiceId = ref<string | null>(null)

const chatBgStyle = computed(() => {
  const id = chatBackground.value
  if (id === 'purple') {
    return { background: 'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)' }
  }
  if (id === 'orange') {
    return { background: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)' }
  }
  return { background: 'var(--lx-bg-panel)' }
})

let voiceAudio: HTMLAudioElement | null = null


function peerAvatarProps(size = 36) {
  const s = currentSession.value
  return {
    text: s?.avatarText || '?',
    color: s?.avatarColor || 'var(--lx-accent)',
    size,
    imageUrl: s?.avatarUrl,
    icon: isMyPhone.value ? PhonePortraitOutline : undefined
  }
}

function openPeerProfile(e: MouseEvent) {
  e.stopPropagation()
  if (!isFriendChat.value || !currentSession.value) return
  const session = currentSession.value
  const found = contactsStore.items.find(c => c.id === session.id || c.name === session.name)
  const contact: ContactItem = found ?? {
    id: session.id,
    name: session.name,
    avatarText: session.avatarText,
    avatarColor: session.avatarColor,
    group: '我的好友',
    online: session.online
  }
  openContactProfile(contact, e)
}

function openSelfProfileClick(e: MouseEvent) {
  e.stopPropagation()
  openSelfProfile(
    {
      nickname: userProfile.value.nickname,
      username: savedLogin.value.username || undefined,
      avatarText: userProfile.value.nickname.charAt(0) || '我'
    },
    e
  )
}

watch(currentSessionId, () => {
  closeMore()
  closeGroupInfo()
})









function playVoice(msg: ChatMessage) {
  if (!msg.voiceUrl) {
    message.info(`语音 ${formatVoiceDuration(msg.voiceDuration)}`)
    return
  }
  if (playingVoiceId.value === msg.id) {
    voiceAudio?.pause()
    playingVoiceId.value = null
    return
  }
  voiceAudio?.pause()
  voiceAudio = new Audio(msg.voiceUrl)
  playingVoiceId.value = msg.id
  voiceAudio.play().catch(() => message.error('无法播放语音'))
  voiceAudio.onended = () => {
    playingVoiceId.value = null
  }
}

function openImageView(msg: ChatMessage) {
  openOverlay('file-preview', {
    filePreview: {
      fileName: '图片消息',
      fileUrl: msg.content,
      isImage: true
    }
  })
}

onUnmounted(() => {
  voiceAudio?.pause()
})

function openFileView(msg?: ChatMessage) {
  const fileName = msg?.fileName || msg?.content || 'Screenshot 2026-07-05-18-48.png'
  openOverlay('file-preview', {
    filePreview: {
      fileName,
      fileUrl: msg?.fileUrl,
      fileSize: msg?.fileSize,
      isImage: msg?.type === 'image' || msg?.isImage
    }
  })
}

function onRedPacketClick(msg: ChatMessage) {
  if (msg.isSelf) {
    message.info('这是您发出的红包')
    return
  }
  openRedPacketReceive(msg.id)
}

function formatVoiceDuration(sec?: number) {
  const s = sec ?? 0
  return s < 60 ? `${s}"` : `${Math.floor(s / 60)}'${s % 60}"`
}

function scrollToBottom() {
  setTimeout(() => {
    const messageArea = document.querySelector('.message-area')
    if (messageArea) {
      messageArea.scrollTo({ top: messageArea.scrollHeight, behavior: 'smooth' })
    }
  }, 100)
}




const replyingTo = ref<ChatMessage | undefined>()

function copyMessage(msg: ChatMessage) {
  const text =
    msg.type === 'file'
      ? msg.fileName || msg.content
      : msg.content
  navigator.clipboard.writeText(text)
  message.success('已复制')
}

function favoriteMessage(msg: ChatMessage) {
  if (msg.type === 'file') {
    favoritesStore.add({
      title: msg.fileName || msg.content,
      preview: msg.fileSize || '',
      type: 'file'
    })
  } else if (msg.type === 'image' || msg.isImage) {
    favoritesStore.add({
      title: '图片消息',
      preview: msg.content.slice(0, 80),
      type: 'image'
    })
  } else if (msg.type === 'link') {
    favoritesStore.add({
      title: msg.content.slice(0, 30),
      preview: msg.content,
      type: 'link'
    })
  } else {
    favoritesStore.add({
      title: msg.content.slice(0, 20) || '消息',
      preview: msg.content,
      type: 'note'
    })
  }
  message.success('已收藏')
}

const ctxMsg = ref<ChatMessage | null>(null)
const ctxShow = ref(false)
const ctxX = ref(0)
const ctxY = ref(0)

const ctxOptions = computed<DropdownOption[]>(() => {
  const msg = ctxMsg.value
  if (!msg) return []
  const copyLabel =
    msg.type === 'file' ? '复制文件名' : msg.type === 'image' || msg.isImage ? '复制链接' : '复制'
  const opts: DropdownOption[] = [
    { label: copyLabel, key: 'copy' },
    { label: '收藏', key: 'fav' },
    { label: '回复', key: 'reply' }
  ]
  if (msg.isSelf) {
    opts.push({ type: 'divider', key: 'd' }, { label: '撤回', key: 'recall' })
  }
  return opts
})

function onMsgContext(e: MouseEvent, msg: ChatMessage) {
  e.preventDefault()
  ctxMsg.value = msg
  ctxX.value = e.clientX
  ctxY.value = e.clientY
  ctxShow.value = true
}

function onCtxSelect(key: string) {
  const msg = ctxMsg.value
  if (!msg) return
  if (key === 'copy') copyMessage(msg)
  else if (key === 'fav') favoriteMessage(msg)
  else if (key === 'reply') replyMessage(msg)
  else if (key === 'recall') recallMessage(msg)
  ctxShow.value = false
}

function replyMessage(msg: ChatMessage) {
  replyingTo.value = msg
  document.querySelector<HTMLTextAreaElement>('.message-input textarea')?.focus()
}

function recallMessage(msg: ChatMessage) {
  if (recallMessageInStore(msg.id)) {
    message.success('已撤回')
  }
}



</script>

<template>
  <div class="chat-panel" :class="{ 'chat-panel--group': isGroupChat }">
    <div class="functional-region">
      <!-- QQ 好友顶栏 -->
      <header v-if="isFriendChat" class="chat-header">
        <div class="chat-header-left">
          <button v-if="isFriendChat" type="button" class="avatar-btn" @click="openPeerProfile">
            <Avatar v-bind="peerAvatarProps(32)" />
          </button>
          <Avatar v-else v-bind="peerAvatarProps(32)" />
          <span class="chat-peer-name">{{ currentSession?.name }}</span>
          <span v-if="currentSession?.online" class="online-dot" title="在线" />
        </div>
        <div class="chat-header-actions">
          <button type="button" class="hdr-btn" title="语音通话" @click="openVoiceCall">
            <n-icon :component="CallOutline" :size="20" />
          </button>
          <button type="button" class="hdr-btn" title="视频通话" @click="openVideoCall">
            <n-icon :component="VideocamOutline" :size="20" />
          </button>
          <button type="button" class="hdr-btn" title="更多" @click="toggleMore">
            <n-icon :component="EllipsisHorizontalOutline" :size="20" />
          </button>
        </div>
      </header>

      <!-- 群聊顶栏 -->
      <header v-else-if="isGroupChat" class="chat-header chat-header--group">
        <div class="chat-header-left">
          <span class="chat-peer-name chat-peer-name--group">{{ currentSession?.name }}</span>
        </div>
        <div class="chat-header-actions">
          <button type="button" class="hdr-btn" title="语音通话" @click="openVoiceCall">
            <n-icon :component="CallOutline" :size="20" />
          </button>
          <button type="button" class="hdr-btn" title="视频通话" @click="openVideoCall">
            <n-icon :component="VideocamOutline" :size="20" />
          </button>
          <n-popover
            trigger="click"
            placement="bottom-end"
            :show-arrow="false"
            raw
            class="group-apps-popover"
          >
            <template #trigger>
              <button type="button" class="hdr-btn" title="群应用">
                <n-icon :component="GridOutline" :size="20" />
              </button>
            </template>
            <div class="group-grid-menu">
              <button
                v-for="item in groupGridItems"
                :key="item"
                type="button"
                class="grid-menu-item"
                @click="onGroupAppClick(item)"
              >
                {{ item }}
              </button>
            </div>
          </n-popover>
          <button type="button" class="hdr-btn" title="邀请" @click="openAddMembers">
            <n-icon :component="AddOutline" :size="20" />
          </button>
          <button type="button" class="hdr-btn" title="更多" @click="toggleGroupInfo">
            <n-icon :component="EllipsisHorizontalOutline" :size="20" />
          </button>
        </div>
      </header>

      <!-- 我的手机等 -->
      <div v-else-if="hasSession && !isFriendChat" class="session-subheader">
        <span class="session-subheader-title">{{ currentSession?.name }}</span>
      </div>

      <div class="chat-body-row">
        <div class="chat-main-col">
          <div class="chat-content-stack">
      <div class="message-area" :class="{ 'message-area--friend': isFriendChat }" :style="chatBgStyle">
        <template v-if="showPhoneDemo">
          <div class="message-time">18:48</div>
          <div class="message-row left">
            <button v-if="isFriendChat" type="button" class="avatar-btn" @click="openPeerProfile">
              <Avatar v-bind="peerAvatarProps(36)" />
            </button>
            <Avatar v-else v-bind="peerAvatarProps(36)" />
            <div class="message-content">
              <div class="data-card">
                <div class="card-header">
                  <div class="card-icon">
                    <n-icon :component="PhonePortraitOutline" :size="20" />
                  </div>
                  <div class="card-info">
                    <div class="card-title">知流</div>
                    <div class="card-sub">中国移动流量</div>
                  </div>
                  <div class="card-tag">套餐</div>
                </div>
                <div class="card-divider" />
                <div class="card-body">
                  <div class="card-label">已用移动流量</div>
                  <div class="card-value">14.79 GB</div>
                </div>
              </div>
            </div>
          </div>
          <div class="message-row left">
            <button v-if="isFriendChat" type="button" class="avatar-btn" @click="openPeerProfile">
              <Avatar v-bind="peerAvatarProps(36)" />
            </button>
            <Avatar v-else v-bind="peerAvatarProps(36)" />
            <div class="file-card">
              <div class="file-body">
                <div class="file-icon">
                  <n-icon :component="ImagesOutline" :size="28" />
                </div>
                <div class="file-info">
                  <div class="file-name">Screenshot 2026-07-05-18-...</div>
                  <div class="file-meta">355.33 KB</div>
                </div>
              </div>
              <div class="file-footer">
                <span>已下载</span>
                <span class="file-view" @click="openFileView()">查看</span>
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="hasSession && chatMessages.length">
          <ChatMessageItem
            v-for="msg in chatMessages"
            :key="msg.id"
            :msg="msg"
            :playing-voice-id="playingVoiceId"
            @contextmenu="onMsgContext"
            @play-voice="playVoice"
            @open-file-view="openFileView"
            @open-image-view="openImageView"
            @click-red-packet="onRedPacketClick"
            @open-peer-profile="openPeerProfile"
            @open-self-profile="openSelfProfileClick"
          />
        </template>

        <PenguinWatermark v-else :hint="hasSession ? '' : '在左侧选择会话开始聊天'" />
      </div>

      <ChatInputBox
        v-if="hasSession"
        :is-my-phone="isMyPhone"
        :is-friend-chat="isFriendChat"
        :is-group-chat="isGroupChat"
        v-model:replying-to="replyingTo"
        @scroll-to-bottom="scrollToBottom"
      />
          </div>
          <ChatMoreDrawer v-if="isFriendChat" />
        </div>
        <GroupChatSidebar v-if="isGroupChat" />
        <GroupInfoDrawer v-if="isGroupChat" />
      </div>
    </div>

    <n-dropdown
      trigger="manual"
      placement="bottom-start"
      :show="ctxShow"
      :x="ctxX"
      :y="ctxY"
      :options="ctxOptions"
      @select="onCtxSelect"
      @clickoutside="ctxShow = false"
    />
  </div>
</template>

<style scoped>
.chat-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.functional-region {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: transparent;
  position: relative;
}

.chat-body-row {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: row;
  overflow: hidden;
  position: relative;
}

.chat-main-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.chat-content-stack {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  isolation: isolate;
}

.chat-peer-name--group {
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: min(420px, 45vw);
}

.group-grid-menu {
  display: flex;
  flex-direction: column;
  min-width: 148px;
  padding: 6px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: var(--lx-shadow-dropdown);
  border: 1px solid var(--lx-border-light);
}

.grid-menu-item {
  border: none;
  background: transparent;
  text-align: left;
  padding: 10px 14px;
  font-size: 14px;
  color: var(--lx-text-body);
  cursor: pointer;
  border-radius: var(--lx-radius);
  margin: 0;
}

.grid-menu-item:hover {
  background: var(--lx-bg-panel);
}

/* 好友顶栏 */
.chat-header {
  flex-shrink: 0;
  height: 52px;
  padding: 0 16px 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--lx-bg-panel);
  border-bottom: none;
  box-shadow: 0 1px 0 var(--lx-separator-fade, rgba(0, 0, 0, 0.04));
  position: relative;
  z-index: 31;
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.chat-peer-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text);
  white-space: nowrap;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--lx-success);
  flex-shrink: 0;
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.25);
}

.chat-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.hdr-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  color: var(--lx-text-nav);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hdr-btn:hover {
  background: var(--lx-border-light);
  color: var(--lx-text-body);
}

.session-subheader {
  flex-shrink: 0;
  padding: 10px 20px;
  border-bottom: none;
  box-shadow: 0 1px 0 var(--lx-separator-fade, rgba(0, 0, 0, 0.04));
  background: rgba(255, 255, 255, 0.35);
}

.session-subheader-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.message-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: transparent;
}

.message-area--friend {
  padding: 12px 16px 16px;
}

.message-time {
  text-align: center;
  color: var(--lx-text-muted);
  font-size: 12px;
}

.message-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.message-row.left {
  justify-content: flex-start;
}

.message-row.right {
  justify-content: flex-end;
}

.avatar-btn {
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  cursor: pointer;
  border-radius: var(--lx-avatar-radius);
  flex-shrink: 0;
  line-height: 0;
}

.avatar-btn:hover {
  opacity: 0.88;
}

.avatar-btn:focus-visible {
  outline: 2px solid var(--lx-accent);
  outline-offset: 2px;
}

/* 右键菜单 */
.msg-context-menu {
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  min-width: 100px;
  padding: 4px;
}

.menu-item {
  padding: 8px 16px;
  font-size: 13px;
  color: var(--lx-text-body);
  cursor: pointer;
  border-radius: var(--lx-radius);
}

.menu-item:hover {
  background: var(--lx-bg-panel);
}

.menu-item.danger {
  color: var(--lx-danger);
}

.menu-item.danger:hover {
  background: var(--lx-danger-bg);
}

/* 引用消息展示 */
.qq-bubble-reply {
  font-size: 12px;
  color: var(--lx-text-secondary);
  background: var(--lx-bg-hover);
  padding: 4px 8px;
  border-radius: var(--lx-radius);
  margin-bottom: 6px;
  border-left: 2px solid var(--lx-accent);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.qq-bubble-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: var(--lx-radius);
  object-fit: cover;
  cursor: pointer;
  display: block;
}

.image-bubble {
  padding: 4px;
  background: var(--lx-bg-card);
}

.hidden-file-input {
  display: none;
}

/* 输入框引用预览 */
.reply-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  margin-bottom: 8px;
  border-left: 3px solid var(--lx-accent);
}

.reply-content {
  font-size: 12px;
  color: var(--lx-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.reply-close {
  cursor: pointer;
  color: var(--lx-text-muted);
  padding: 2px;
}

.reply-close:hover {
  color: var(--lx-danger);
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 4px;
}

.emoji-btn {
  border: none;
  background: transparent;
  font-size: 22px;
  cursor: pointer;
}

.apps-quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 4px;
  min-width: 200px;
}

.apps-quick-item {
  border: none;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  padding: 10px 8px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--lx-text-body);
}

.apps-quick-item:hover {
  background: var(--lx-bg-hover);
}

.apps-quick-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
}
</style>