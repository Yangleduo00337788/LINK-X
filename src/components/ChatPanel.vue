<script setup lang="ts">
import { ref, computed, onUnmounted, watch } from 'vue'
import { NIcon, NInput, NPopover, NDropdown, useMessage, type DropdownOption } from 'naive-ui'
import {
  ImagesOutline,
  FolderOutline,
  HappyOutline,
  PhonePortraitOutline,
  CallOutline,
  VideocamOutline,
  GridOutline,
  AddOutline,
  EllipsisHorizontalOutline,
  MicOutline,
  DocumentOutline,
  LinkOutline,
  CloseOutline,
  CutOutline,
  VolumeHighOutline,
  GiftOutline
} from '@vicons/ionicons5'
import Avatar from './Avatar.vue'
import PenguinWatermark from './PenguinWatermark.vue'
import GroupChatSidebar from './chat/GroupChatSidebar.vue'
import ChatMoreDrawer from './chat/ChatMoreDrawer.vue'
import GroupInfoDrawer from './chat/GroupInfoDrawer.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useOverlayStore } from '../stores/overlay'
import { useChatModalsStore } from '../stores/chatModals'
import { useAppSettingsStore } from '../stores/appSettings'
import { useGroupMetaStore } from '../stores/groupMeta'
import { useContactsStore } from '../stores/contacts'
import { useSecondaryViewStore } from '../stores/secondaryView'
import type { ChatMessage, ContactItem, AppItem } from '../types'
import { CHAT_EMOJIS } from '../constants/emojis'
import { apps as chatApps } from '../data/mockData'
import { useFilesStore } from '../stores/files'
import { useFavoritesStore } from '../stores/favorites'
import { formatFileSize, readFileAsDataUrl, MAX_IMAGE_BYTES } from '../utils/file'

const message = useMessage()
const filesStore = useFilesStore()
const favoritesStore = useFavoritesStore()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const chatModalsStore = useChatModalsStore()
const appSettingsStore = useAppSettingsStore()
const groupMetaStore = useGroupMetaStore()
const contactsStore = useContactsStore()
const secondaryViewStore = useSecondaryViewStore()
const { currentSession, currentMessages, userProfile, currentSessionId, savedLogin } = storeToRefs(appStore)
const { chatBackground } = storeToRefs(appSettingsStore)
const { sendMessage, recallMessage: recallMessageInStore, setNav } = appStore
const { activeApp } = storeToRefs(secondaryViewStore)
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
  openRedPacket,
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

const inputValue = ref('')
const showEmoji = ref(false)
const showApps = ref(false)

const isMyPhone = computed(() => currentSession.value?.name === '我的手机')
const hasSession = computed(() => !!currentSession.value)
const isFriendChat = computed(
  () => hasSession.value && !currentSession.value?.isGroup && !isMyPhone.value
)

const showPhoneDemo = computed(
  () => isMyPhone.value && currentMessages.value.filter(m => m.type !== 'system').length === 0
)

const emojis = [...CHAT_EMOJIS]

const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const chatMessages = computed(() =>
  currentMessages.value.filter(m => m.type !== 'system')
)

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

const isRecording = ref(false)
const playingVoiceId = ref<string | null>(null)
let recordStart = 0
let mediaRecorder: MediaRecorder | null = null
let recordStream: MediaStream | null = null
let voiceChunks: BlobPart[] = []
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

function isLinkMsg(msg: ChatMessage) {
  return msg.type === 'link' || /https?:\/\//.test(msg.content) || msg.content.includes('抖音')
}

function toolFile() {
  fileInputRef.value?.click()
}

async function toolScreenshot() {
  if (!ensureCanSend()) return
  if (!navigator.mediaDevices?.getDisplayMedia) {
    message.warning('当前环境不支持屏幕截图')
    return
  }
  try {
    const stream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })
    const video = document.createElement('video')
    video.srcObject = stream
    await video.play()
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d')?.drawImage(video, 0, 0)
    stream.getTracks().forEach(t => t.stop())
    const dataUrl = canvas.toDataURL('image/png')
    if (dataUrl.length > MAX_IMAGE_BYTES * 1.4) {
      message.warning('截图过大，请缩小选区后重试')
      return
    }
    sendMessage(dataUrl, { type: 'image', isImage: true })
    message.success('截图已发送')
    scrollToBottom()
  } catch {
    message.info('已取消截图')
  }
}

function openChatApp(app: AppItem) {
  activeApp.value = app
  setNav('apps')
  showApps.value = false
  message.success(`已打开「${app.name}」`)
}

function toolRedPacket() {
  if (isMyPhone.value) return
  openRedPacket()
}

async function onImagePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !ensureCanSend()) return

  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    return
  }
  if (file.size > MAX_IMAGE_BYTES) {
    message.warning(`图片不能超过 ${formatFileSize(MAX_IMAGE_BYTES)}`)
    return
  }

  try {
    const dataUrl = await readFileAsDataUrl(file)
    sendMessage(dataUrl, { type: 'image', isImage: true })
    message.success('图片已发送')
    scrollToBottom()
  } catch {
    message.error('图片读取失败')
  }
}

function onFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !ensureCanSend()) return

  const fileUrl = URL.createObjectURL(file)
  sendMessage(file.name, {
    type: 'file',
    fileName: file.name,
    fileSize: formatFileSize(file.size),
    fileUrl
  })
  filesStore.addFromChat(file.name, formatFileSize(file.size), '我', fileUrl)
  if (isGroupChat.value && currentSessionId.value) {
    groupMetaStore.addFile(currentSessionId.value, {
      name: file.name,
      size: formatFileSize(file.size),
      user: userProfile.value?.nickname || '我',
      fileUrl
    })
  }
  message.success('文件已发送')
  scrollToBottom()
}

async function toggleVoiceRecord() {
  if (!isRecording.value) {
    if (!ensureCanSend()) return
    try {
      voiceChunks = []
      recordStream = await navigator.mediaDevices.getUserMedia({ audio: true })
      mediaRecorder = new MediaRecorder(recordStream)
      mediaRecorder.ondataavailable = e => {
        if (e.data.size) voiceChunks.push(e.data)
      }
      recordStart = Date.now()
      mediaRecorder.start()
      isRecording.value = true
      message.info('正在录音，再次点击结束')
    } catch {
      sendMessage('', { type: 'voice', voiceDuration: 3 })
      message.success('语音消息已发送')
      scrollToBottom()
    }
    return
  }

  const duration = Math.max(1, Math.round((Date.now() - recordStart) / 1000))
  const recorder = mediaRecorder
  if (recorder && recorder.state !== 'inactive') {
    await new Promise<void>(resolve => {
      recorder.onstop = () => {
        const blob = new Blob(voiceChunks, { type: 'audio/webm' })
        const voiceUrl = URL.createObjectURL(blob)
        sendMessage('', { type: 'voice', voiceDuration: duration, voiceUrl })
        message.success('语音消息已发送')
        scrollToBottom()
        resolve()
      }
      recorder.stop()
    })
  } else {
    sendMessage('', { type: 'voice', voiceDuration: duration })
    message.success('语音消息已发送')
    scrollToBottom()
  }

  recordStream?.getTracks().forEach(t => t.stop())
  mediaRecorder = null
  recordStream = null
  voiceChunks = []
  isRecording.value = false
}

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
  recordStream?.getTracks().forEach(t => t.stop())
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

function pickEmoji(e: string) {
  inputValue.value += e
  showEmoji.value = false
}

function send() {
  if (!inputValue.value.trim()) return
  if (currentSession.value?.blocked) {
    message.warning('你已屏蔽该联系人，无法发送消息')
    return
  }

  if (inputValue.value.startsWith('/img ')) {
    const url = inputValue.value.replace('/img ', '').trim()
    sendMessage(url, { type: 'image', isImage: true })
  } else {
    sendMessage(inputValue.value, { type: 'text', replyTo: replyingTo.value })
  }

  inputValue.value = ''
  replyingTo.value = undefined
  scrollToBottom()
}

function onEnter(e: KeyboardEvent) {
  if (!e.shiftKey) {
    e.preventDefault()
    send()
  }
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
  } else if (isLinkMsg(msg)) {
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

function cancelReply() {
  replyingTo.value = undefined
}


function ensureCanSend(): boolean {
  if (currentSession.value?.blocked) {
    message.warning('你已屏蔽该联系人，无法发送消息')
    return false
  }
  return true
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
          <template v-for="msg in chatMessages" :key="msg.id">
            <!-- 文件消息 -->
            <div
              v-if="msg.type === 'file'"
              class="message-row"
              :class="msg.isSelf ? 'right' : 'left'"
            >
              <button v-if="!msg.isSelf && isFriendChat" type="button" class="avatar-btn" @click="openPeerProfile">
                <Avatar v-bind="peerAvatarProps(36)" />
              </button>
              <Avatar v-else-if="!msg.isSelf" v-bind="peerAvatarProps(36)" />
              <div
                class="qq-file-card"
                :class="{ self: msg.isSelf }"
                @contextmenu="onMsgContext($event, msg)"
                @click="openFileView(msg)"
              >
                <div class="qq-file-main">
                  <div class="qq-file-icon apk">
                    <n-icon :component="DocumentOutline" :size="26" color="var(--lx-bg-card)" />
                  </div>
                  <div class="qq-file-meta">
                    <div class="qq-file-name">{{ msg.fileName || msg.content || '文件' }}</div>
                    <div class="qq-file-size">{{ msg.fileSize || '' }}</div>
                  </div>
                </div>
                <div class="qq-file-bar">
                  {{ msg.fileStatus || (msg.isSelf ? '已发送' : '已接收') }}
                </div>
              </div>
              <button v-if="msg.isSelf" type="button" class="avatar-btn" @click="openSelfProfileClick">
                <Avatar text="我" color="var(--lx-success)" :size="36" />
              </button>
            </div>

            <!-- 图片消息 -->
            <div
              v-else-if="msg.type === 'image' || msg.isImage"
              class="message-row"
              :class="msg.isSelf ? 'right' : 'left'"
            >
              <button v-if="!msg.isSelf && isFriendChat" type="button" class="avatar-btn" @click="openPeerProfile">
                <Avatar v-bind="peerAvatarProps(36)" />
              </button>
              <Avatar v-else-if="!msg.isSelf" v-bind="peerAvatarProps(36)" />
              <div
                class="qq-bubble image-bubble"
                :class="{ self: msg.isSelf }"
                @contextmenu="onMsgContext($event, msg)"
                @click="openImageView(msg)"
              >
                <img :src="msg.content" class="qq-bubble-image" alt="图片消息" />
              </div>
              <button v-if="msg.isSelf" type="button" class="avatar-btn" @click="openSelfProfileClick">
                <Avatar text="我" color="var(--lx-success)" :size="36" />
              </button>
            </div>

            <!-- 语音消息 -->
            <div
              v-else-if="msg.type === 'voice'"
              class="message-row"
              :class="msg.isSelf ? 'right' : 'left'"
            >
              <button v-if="!msg.isSelf && isFriendChat" type="button" class="avatar-btn" @click="openPeerProfile">
                <Avatar v-bind="peerAvatarProps(36)" />
              </button>
              <Avatar v-else-if="!msg.isSelf" v-bind="peerAvatarProps(36)" />
              <div
                class="qq-bubble voice-bubble"
                :class="{ self: msg.isSelf, playing: playingVoiceId === msg.id }"
                @contextmenu="onMsgContext($event, msg)"
                @click="playVoice(msg)"
              >
                <n-icon :component="MicOutline" :size="16" class="voice-ico" />
                <span>{{ formatVoiceDuration(msg.voiceDuration) }}</span>
              </div>
              <button v-if="msg.isSelf" type="button" class="avatar-btn" @click="openSelfProfileClick">
                <Avatar text="我" color="var(--lx-success)" :size="36" />
              </button>
            </div>

            <!-- 红包消息 -->
            <div
              v-else-if="msg.type === 'redPacket'"
              class="message-row"
              :class="msg.isSelf ? 'right' : 'left'"
            >
              <button v-if="!msg.isSelf && isFriendChat" type="button" class="avatar-btn" @click="openPeerProfile">
                <Avatar v-bind="peerAvatarProps(36)" />
              </button>
              <Avatar v-else-if="!msg.isSelf" v-bind="peerAvatarProps(36)" />
              <div
                class="red-packet-card"
                :class="{ self: msg.isSelf, opened: msg.redPacketOpened }"
                @click="onRedPacketClick(msg)"
                @contextmenu="onMsgContext($event, msg)"
              >
                <div class="rp-icon">福</div>
                <div class="rp-text">
                  <div class="rp-title">{{ msg.redPacketGreeting || msg.content }}</div>
                  <div class="rp-sub">
                    {{ msg.redPacketOpened ? '已领取' : msg.isSelf ? '红包' : '领取红包' }}
                  </div>
                </div>
              </div>
              <button v-if="msg.isSelf" type="button" class="avatar-btn" @click="openSelfProfileClick">
                <Avatar text="我" color="var(--lx-success)" :size="36" />
              </button>
            </div>

            <!-- 链接 / 文本 -->
            <div
              v-else
              class="message-row"
              :class="msg.isSelf ? 'right' : 'left'"
            >
              <button v-if="!msg.isSelf && isFriendChat" type="button" class="avatar-btn" @click="openPeerProfile">
                <Avatar v-bind="peerAvatarProps(36)" />
              </button>
              <Avatar v-else-if="!msg.isSelf" v-bind="peerAvatarProps(36)" />
              <div
                class="qq-bubble"
                :class="{ self: msg.isSelf, link: isLinkMsg(msg) }"
                @contextmenu="onMsgContext($event, msg)"
              >
                <div v-if="msg.replyTo" class="qq-bubble-reply">
                  {{ msg.replyTo.senderName }}: {{ msg.replyTo.content }}
                </div>
                <p class="qq-bubble-text">{{ msg.content }}</p>
                <n-icon
                  v-if="isLinkMsg(msg)"
                  class="qq-link-ico"
                  :component="LinkOutline"
                  :size="14"
                />
              </div>
              <button v-if="msg.isSelf" type="button" class="avatar-btn" @click="openSelfProfileClick">
                <Avatar text="我" color="var(--lx-success)" :size="36" />
              </button>
            </div>
          </template>
        </template>

        <PenguinWatermark v-else :hint="hasSession ? '' : '在左侧选择会话开始聊天'" />
      </div>

      <div
        v-if="hasSession"
        class="input-area"
        :class="{ 'input-area--friend': isFriendChat, 'input-area--group': isGroupChat }"
      >
        <input
          ref="imageInputRef"
          type="file"
          accept="image/*"
          class="hidden-file-input"
          @change="onImagePicked"
        />
        <input
          ref="fileInputRef"
          type="file"
          class="hidden-file-input"
          @change="onFilePicked"
        />

        <div class="input-box">
          <div class="input-compose">
            <div v-if="replyingTo" class="reply-preview">
              <div class="reply-content">回复 {{ replyingTo.senderName }}: {{ replyingTo.content }}</div>
              <n-icon :component="CloseOutline" class="reply-close" @click="cancelReply" />
            </div>

            <n-input
              v-model:value="inputValue"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 8 }"
              placeholder=""
              class="message-input"
              :bordered="false"
              @keydown.enter="onEnter"
            />
          </div>

          <div class="input-toolbar">
            <div class="toolbar-left">
              <n-popover v-model:show="showEmoji" trigger="click" placement="top-start">
                <template #trigger>
                  <button type="button" class="tool-btn" title="表情">
                    <n-icon :component="HappyOutline" :size="20" />
                  </button>
                </template>
                <div class="emoji-grid">
                  <button
                    v-for="e in emojis"
                    :key="e"
                    type="button"
                    class="emoji-btn"
                    @click="pickEmoji(e)"
                  >
                    {{ e }}
                  </button>
                </div>
              </n-popover>
              <n-popover v-model:show="showApps" trigger="click" placement="top-start">
                <template #trigger>
                  <button type="button" class="tool-btn" title="应用">
                    <n-icon :component="GridOutline" :size="20" />
                  </button>
                </template>
                <div class="apps-quick-grid">
                  <button
                    v-for="app in chatApps"
                    :key="app.id"
                    type="button"
                    class="apps-quick-item"
                    @click="openChatApp(app)"
                  >
                    <span class="apps-quick-icon" :style="{ background: app.color }">{{ app.icon }}</span>
                    <span>{{ app.name }}</span>
                  </button>
                </div>
              </n-popover>
              <button type="button" class="tool-btn" title="发送文件" @click="toolFile">
                <n-icon :component="FolderOutline" :size="20" />
              </button>
              <button type="button" class="tool-btn" title="截图" @click="toolScreenshot">
                <n-icon :component="CutOutline" :size="20" />
              </button>
              <button
                v-if="!isMyPhone"
                type="button"
                class="tool-btn"
                title="红包"
                @click="toolRedPacket"
              >
                <n-icon :component="GiftOutline" :size="20" />
              </button>
              <button
                type="button"
                class="tool-btn"
                :class="{ 'tool-btn--recording': isRecording }"
                title="语音"
                @click="toggleVoiceRecord"
              >
                <n-icon :component="MicOutline" :size="20" />
              </button>
            </div>

            <div class="toolbar-right">
              <button type="button" class="tool-btn" title="语音通话" @click="openVoiceCall">
                <n-icon :component="VolumeHighOutline" :size="20" />
              </button>
              <button
                type="button"
                class="send-btn"
                :disabled="!inputValue.trim()"
                @click="send"
              >
                发送
              </button>
            </div>
          </div>
        </div>
      </div>
      <ChatMoreDrawer v-if="isFriendChat" />
          </div>
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

/* QQ 气泡 */
.qq-bubble {
  position: relative;
  max-width: min(420px, 72%);
  background: var(--lx-bg-card);
  padding: 10px 12px;
  border-radius: var(--lx-radius);
  font-size: 14px;
  line-height: 1.55;
  color: var(--lx-text);
  box-shadow: 0 1px 2px var(--lx-border-light);
}

.qq-bubble.self {
  background: #c9e7ff;
  border-radius: var(--lx-radius);
}

.qq-bubble.link .qq-bubble-text {
  margin: 0;
  word-break: break-all;
}

.qq-link-ico {
  display: none;
}

.qq-bubble-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

/* QQ 文件卡片 */
.qq-file-card {
  max-width: min(300px, 75%);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: 0 1px 3px var(--lx-bg-active);
}

.qq-file-main {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
}

.qq-file-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.qq-file-icon.apk {
  background: linear-gradient(145deg, #7ed56f 0%, #5cb85c 100%);
}

.qq-file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qq-file-size {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-top: 4px;
}

.qq-file-bar {
  padding: 6px 14px;
  background: #4a4a4a;
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
}

.message-content {
  max-width: 420px;
}

.data-card {
  width: 280px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  padding: 14px;
  box-shadow: var(--lx-shadow-card);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--lx-accent);
  color: var(--lx-bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-info {
  flex: 1;
}

.card-title {
  font-size: 14px;
  font-weight: 500;
}

.card-sub {
  font-size: 11px;
  color: var(--lx-text-muted);
}

.card-tag {
  font-size: 11px;
  color: var(--lx-accent);
  background: var(--lx-accent-bg-soft);
  padding: 2px 8px;
  border-radius: var(--lx-radius);
}

.card-divider {
  height: 1px;
  background: var(--lx-bg-input);
  margin: 12px 0;
}

.card-body .card-label {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.card-value {
  font-size: 26px;
  font-weight: 600;
}

.file-card {
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  width: 260px;
  overflow: hidden;
  box-shadow: var(--lx-shadow-card);
}

.file-body {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
}

.file-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--lx-radius);
  background: var(--lx-accent-bg-soft);
  color: var(--lx-accent);
  display: flex;
  align-items: center;
  justify-content: center;
}

.file-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  font-size: 11px;
  color: var(--lx-text-muted);
}

.file-footer {
  display: flex;
  justify-content: space-between;
  padding: 6px 12px;
  background: #4a4a4a;
  color: var(--lx-bg-card);
  font-size: 11px;
}

.file-view {
  cursor: pointer;
}

/* 输入区 — 微信风格 */
.input-area {
  flex-shrink: 0;
  padding: 10px 14px 14px;
  background: var(--lx-bg-panel);
  border-top: none;
  box-shadow: inset 0 1px 0 var(--lx-separator-fade, rgba(0, 0, 0, 0.04));
}

.input-box {
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: 10px;
  overflow: hidden;
  min-height: 148px;
}

.input-compose {
  flex: 1;
  min-height: 0;
  padding: 10px 14px 4px;
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 10px 10px;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tool-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-nav);
  cursor: pointer;
  padding: 0;
  transition: background 0.15s, color 0.15s;
}

.tool-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.tool-btn--recording {
  color: var(--lx-danger) !important;
  animation: pulse-rec 1s infinite;
}

.message-input {
  flex: 1;
}

.message-input :deep(.n-input-wrapper) {
  padding: 0 !important;
  background: transparent !important;
}

.message-input :deep(.n-input) {
  background: transparent !important;
  --n-border: transparent !important;
  --n-border-hover: transparent !important;
  --n-border-focus: transparent !important;
  --n-box-shadow-focus: none !important;
}

.message-input :deep(.n-input__border),
.message-input :deep(.n-input__state-border) {
  display: none !important;
}

.message-input :deep(.n-input__textarea-el) {
  min-height: 72px !important;
  background: transparent !important;
  font-size: 14px;
  line-height: 1.55;
  padding: 0 !important;
  color: var(--lx-text);
  resize: none;
}

.send-btn {
  min-width: 64px;
  height: 28px;
  padding: 0 14px;
  border-radius: 6px;
  border: none;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  background: #e9e9e9;
  color: #ffffff;
  transition: background 0.15s;
}

.send-btn:not(:disabled) {
  background: var(--lx-accent);
  color: var(--lx-text-on-accent);
}

.send-btn:not(:disabled):hover {
  background: var(--lx-accent-hover);
}

.send-btn:disabled {
  cursor: not-allowed;
}

@keyframes pulse-rec {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

.voice-bubble {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 72px;
  cursor: pointer;
}

.voice-bubble.playing {
  color: var(--lx-accent);
}

.voice-ico {
  flex-shrink: 0;
}

.red-packet-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 200px;
  max-width: 260px;
  padding: 12px 14px;
  border-radius: var(--lx-radius);
  background: linear-gradient(135deg, #e84c3d, #c0392b);
  color: var(--lx-text-on-accent);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(232, 76, 61, 0.35);
}

.red-packet-card.opened {
  opacity: 0.85;
}

.rp-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
}

.rp-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}

.rp-sub {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 2px;
}

.qq-file-card {
  cursor: pointer;
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