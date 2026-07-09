<script setup lang="ts">
/**
 * 聊天主面板组件。
 * <p>
 * 展示当前会话的消息列表、输入框与顶栏操作，
 * 支持好友/群聊/我的手机等不同会话类型，以及语音播放、
 * 文件拖拽、消息右键菜单（复制/收藏/回复/撤回）等功能。
 * </p>
 */
// Vue 响应式、计算属性、生命周期、侦听器与 nextTick
import { ref, computed, onUnmounted, watch, nextTick } from 'vue'
// Naive UI 图标、气泡、下拉菜单、虚拟列表与消息提示
import { NIcon, NPopover, NDropdown, NVirtualList, useMessage, type DropdownOption } from 'naive-ui'
// Ionicons5 通话、视频、网格、添加、更多、手机、图片图标
import {
  CallOutline,
  VideocamOutline,
  GridOutline,
  AddOutline,
  EllipsisHorizontalOutline,
  PhonePortraitOutline,
  ImagesOutline
} from '@vicons/ionicons5'
// 头像组件
import Avatar from './Avatar.vue'
// 企鹅水印占位
import PenguinWatermark from './PenguinWatermark.vue'
// 群聊侧边栏
import GroupChatSidebar from './chat/GroupChatSidebar.vue'
// 好友聊天更多抽屉
import ChatMoreDrawer from './chat/ChatMoreDrawer.vue'
// 群信息抽屉
import GroupInfoDrawer from './chat/GroupInfoDrawer.vue'
// 单条消息组件
import ChatMessageItem from './chat/ChatMessageItem.vue'
// 聊天输入框
import ChatInputBox from './chat/ChatInputBox.vue'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 全屏 Overlay Store
import { useOverlayStore } from '../stores/overlay'
// 聊天弹窗 Store
import { useChatModalsStore } from '../stores/chatModals'
// 应用设置 Store
import { useAppSettingsStore } from '../stores/appSettings'
// 联系人 Store
import { useContactsStore } from '../stores/contacts'
// 消息与联系人类型
import type { ChatMessage, ContactItem } from '../types'
// 收藏 Store
import { useFavoritesStore } from '../stores/favorites'

// 获取 Naive UI 消息提示实例
const message = useMessage()
// 获取收藏 Store 实例
const favoritesStore = useFavoritesStore()
// 获取应用 Store 实例
const appStore = useAppStore()
// 获取 Overlay Store 实例
const overlayStore = useOverlayStore()
// 获取聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 获取应用设置 Store 实例
const appSettingsStore = useAppSettingsStore()
// 获取联系人 Store 实例
const contactsStore = useContactsStore()
// 解构当前会话、消息、用户资料、会话 ID、已保存登录信息
const { currentSession, currentMessages, userProfile, currentSessionId, savedLogin } = storeToRefs(appStore)
// 解构聊天背景设置
const { chatBackground } = storeToRefs(appSettingsStore)
// 解构撤回消息方法
const { recallMessage: recallMessageInStore } = appStore
// 解构打开 Overlay 方法
const { open: openOverlay } = overlayStore
// 解构聊天弹窗相关操作方法
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

// 群应用快捷菜单项
const groupGridItems = ['群文件', '群相册', '群精华', '群公告']

// 群应用菜单项点击：打开对应群功能弹窗
function onGroupAppClick(item: string) {
  if (item === '群文件') openGroupFiles()
  else if (item === '群相册') openGroupAlbum()
  else if (item === '群精华') openGroupEssence()
  else if (item === '群公告') openGroupAnnouncement()
}

// 是否为群聊（有会话、是群、且非「我的手机」）
const isGroupChat = computed(
  () => hasSession.value && !!currentSession.value?.isGroup && !isMyPhone.value
)


// 是否为「我的手机」会话
const isMyPhone = computed(() => currentSession.value?.name === '我的手机')
// 是否有选中的会话
const hasSession = computed(() => !!currentSession.value)
// 是否为好友单聊（有会话、非群、非我的手机）
const isFriendChat = computed(
  () => hasSession.value && !currentSession.value?.isGroup && !isMyPhone.value
)

// 过滤掉系统消息，仅展示用户可见消息
const chatMessages = computed(() =>
  currentMessages.value.filter(m => m.type !== 'system')
)

// 当前正在播放的语音消息 ID
const playingVoiceId = ref<string | null>(null)

// 根据聊天背景设置计算消息区背景样式
const chatBgStyle = computed(() => {
  const id = chatBackground.value
  if (id === 'purple') {
    return { background: 'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)' }
  }
  if (id === 'orange') {
    return { background: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)' }
  }
  return { background: 'var(--lx-bg-panel)' } // 默认背景
})

// 语音播放 Audio 实例引用
let voiceAudio: HTMLAudioElement | null = null


// 生成对方头像组件 props
function peerAvatarProps(size = 36) {
  const s = currentSession.value
  return {
    text: s?.avatarText || '?',
    color: s?.avatarColor || 'var(--lx-accent)',
    size,
    imageUrl: s?.avatarUrl,
    icon: isMyPhone.value ? PhonePortraitOutline : undefined // 我的手机显示手机图标
  }
}

// 点击对方头像打开联系人资料卡
function openPeerProfile(e: MouseEvent) {
  e.stopPropagation()
  if (!isFriendChat.value || !currentSession.value) return
  const session = currentSession.value
  // 从联系人 Store 查找匹配联系人，找不到则构造临时 ContactItem
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

// 点击自己头像打开个人资料卡
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

// 切换会话时关闭更多抽屉与群信息抽屉
watch(currentSessionId, () => {
  closeMore()
  closeGroupInfo()
})










// 播放或暂停语音消息
function playVoice(msg: ChatMessage) {
  if (!msg.voiceUrl) {
    message.info(`语音 ${formatVoiceDuration(msg.voiceDuration)}`) // 无 URL 时仅提示时长
    return
  }
  if (playingVoiceId.value === msg.id) {
    voiceAudio?.pause() // 再次点击则暂停
    playingVoiceId.value = null
    return
  }
  voiceAudio?.pause() // 停止上一段语音
  voiceAudio = new Audio(msg.voiceUrl)
  playingVoiceId.value = msg.id
  voiceAudio.play().catch(() => message.error('无法播放语音'))
  voiceAudio.onended = () => {
    playingVoiceId.value = null // 播放结束清除状态
  }
}

// 打开图片预览 Overlay
function openImageView(msg: ChatMessage) {
  openOverlay('file-preview', {
    filePreview: {
      fileName: '图片消息',
      fileUrl: msg.content,
      isImage: true
    }
  })
}

// 组件卸载时停止语音播放
onUnmounted(() => {
  voiceAudio?.pause()
})

// 打开文件预览 Overlay
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

// 点击红包消息：自己发的提示，他人发的打开领取弹窗
function onRedPacketClick(msg: ChatMessage) {
  if (msg.isSelf) {
    message.info('这是您发出的红包')
    return
  }
  openRedPacketReceive(msg.id)
}

// 格式化语音时长显示
function formatVoiceDuration(sec?: number) {
  const s = sec ?? 0
  return s < 60 ? `${s}"` : `${Math.floor(s / 60)}'${s % 60}"`
}

// 当前正在回复的消息
const replyingTo = ref<ChatMessage | undefined>()
// 虚拟列表组件引用
const virtualListRef = ref<InstanceType<typeof NVirtualList> | null>(null)
// 聊天输入框组件引用
const chatInputRef = ref<InstanceType<typeof ChatInputBox> | null>(null)

// 滚动消息列表到底部
function scrollToBottom() {
  nextTick(() => {
    if (virtualListRef.value) {
      virtualListRef.value.scrollTo({ position: 'bottom', behavior: 'smooth' })
    } else {
      // 降级：直接操作 DOM 滚动
      const messageArea = document.querySelector('.message-area')
      if (messageArea) {
        messageArea.scrollTo({ top: messageArea.scrollHeight, behavior: 'smooth' })
      }
    }
  })
}

// 复制消息内容到剪贴板
function copyMessage(msg: ChatMessage) {
  const text =
    msg.type === 'file'
      ? msg.fileName || msg.content // 文件消息复制文件名
      : msg.content
  navigator.clipboard.writeText(text)
  message.success('已复制')
}

// 收藏消息到收藏夹
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

// 消息右键菜单相关状态
const ctxMsg = ref<ChatMessage | null>(null) // 右键选中的消息
const ctxShow = ref(false) // 是否显示菜单
const ctxX = ref(0) // 菜单 X 坐标
const ctxY = ref(0) // 菜单 Y 坐标

// 根据消息类型与是否自己发送动态生成右键菜单选项
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
    opts.push({ type: 'divider', key: 'd' }, { label: '撤回', key: 'recall' }) // 自己发的消息可撤回
  }
  return opts
})

// 消息右键：记录坐标与目标消息
function onMsgContext(e: MouseEvent, msg: ChatMessage) {
  e.preventDefault()
  ctxMsg.value = msg
  ctxX.value = e.clientX
  ctxY.value = e.clientY
  ctxShow.value = true
}

// 处理消息右键菜单选项选中
function onCtxSelect(key: string) {
  const msg = ctxMsg.value
  if (!msg) return
  if (key === 'copy') copyMessage(msg)
  else if (key === 'fav') favoriteMessage(msg)
  else if (key === 'reply') replyMessage(msg)
  else if (key === 'recall') recallMessage(msg)
  ctxShow.value = false
}

// 设置回复目标并聚焦输入框
function replyMessage(msg: ChatMessage) {
  replyingTo.value = msg
  document.querySelector<HTMLTextAreaElement>('.message-input textarea')?.focus()
}

// 撤回消息
function recallMessage(msg: ChatMessage) {
  if (recallMessageInStore(msg.id)) {
    message.success('已撤回')
  }
}

// 是否正在拖拽文件到聊天区
const isDraggingFile = ref(false)

// 文件拖入聊天区：显示拖放提示
function onDragOver(e: DragEvent) {
  e.preventDefault()
  if (hasSession.value) {
    isDraggingFile.value = true
  }
}

// 文件拖离聊天区：隐藏拖放提示
function onDragLeave(e: DragEvent) {
  e.preventDefault()
  isDraggingFile.value = false
}

// 文件放下：通过输入框组件发送文件
function onDrop(e: DragEvent) {
  e.preventDefault()
  isDraggingFile.value = false
  if (!hasSession.value) return
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    for (let i = 0; i < files.length; i++) {
      chatInputRef.value?.handleFileSend(files[i])
    }
  }
}

</script>

<template>
  <!-- 聊天面板根容器，群聊附加样式类，支持文件拖放 -->
  <div class="chat-panel" :class="{ 'chat-panel--group': isGroupChat }"
       @dragover="onDragOver"
       @dragleave="onDragLeave"
       @drop="onDrop">
    <!-- 文件拖放提示遮罩 -->
    <div v-if="isDraggingFile" class="drag-overlay">
      <div class="drag-overlay-content">
        <n-icon :component="ImagesOutline" :size="48" />
        <span>松开以发送文件</span>
      </div>
    </div>
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

      <!-- 聊天主体：消息区 + 输入框 + 侧边抽屉 -->
      <div class="chat-body-row">
        <div class="chat-main-col">
          <div class="chat-content-stack">
      <!-- 消息列表区域 -->
      <div class="message-area" :class="{ 'message-area--friend': isFriendChat }" :style="chatBgStyle">

        <n-virtual-list
          v-if="hasSession && chatMessages.length"
          ref="virtualListRef"
          :items="chatMessages"
          :item-size="80"
          item-resizable
          item-key="id"
          style="flex: 1; height: 100%; min-height: 0;"
        >
          <template #default="{ item: msg }">
            <div style="padding-bottom: 14px;">
              <ChatMessageItem
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
            </div>
          </template>
        </n-virtual-list>

        <!-- 无消息或未选会话时的占位水印 -->
        <PenguinWatermark v-else :hint="hasSession ? '' : '在左侧选择会话开始聊天'" />
      </div>

      <!-- 聊天输入框 -->
      <ChatInputBox
        ref="chatInputRef"
        v-if="hasSession"
        :is-my-phone="isMyPhone"
        :is-friend-chat="isFriendChat"
        :is-group-chat="isGroupChat"
        v-model:replying-to="replyingTo"
        @scroll-to-bottom="scrollToBottom"
      />
          </div>
          <!-- 好友聊天更多抽屉 -->
          <ChatMoreDrawer v-if="isFriendChat" />
        </div>
        <!-- 群聊右侧成员侧栏 -->
        <GroupChatSidebar v-if="isGroupChat" />
        <!-- 群信息抽屉 -->
        <GroupInfoDrawer v-if="isGroupChat" />
      </div>
    </div>

    <!-- 消息右键菜单 -->
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

.drag-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.drag-overlay-content {
  background: var(--lx-bg-panel);
  padding: 32px 48px;
  border-radius: var(--lx-radius);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  color: var(--lx-accent);
  font-size: 18px;
  font-weight: 600;
  box-shadow: var(--lx-shadow-dropdown);
  pointer-events: none;
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
