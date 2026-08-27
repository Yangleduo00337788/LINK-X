<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 聊天输入框组件。
 * <p>
 * 提供文本输入、表情、文件/图片发送、截图、语音录制、红包与快捷应用入口。
 * 群聊支持输入 @ 或点击工具栏 @ 按钮提及成员 / 全体成员。
 * 支持回复预览、粘贴图片/文件、Enter 发送（Shift+Enter 换行）。
 * </p>
 */
// Vue 响应式 API
import { ref, computed, nextTick, watch, onUnmounted, onMounted } from 'vue'
// Naive UI 组件与消息提示
import { NIcon, NInput, NPopover, useMessage } from 'naive-ui'
// 工具栏图标（Ionicons5）
import {
  FolderOutline,
  HappyOutline,
  CutOutline,
  VolumeHighOutline,
  VideocamOutline,
  GiftOutline,
  MicOutline,
  LanguageOutline,
  CloseOutline,
  BulbOutline,
  LocationOutline
} from '@vicons/ionicons5'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 主应用状态：会话、用户信息、发送消息
import { useAppStore } from '../../stores/app'
// 聊天弹窗/抽屉状态：红包
import { useChatModalsStore } from '../../stores/chatModals'
// 通话 Store
import { useCallStore } from '../../stores/call'
// 文件列表 store：聊天发送的文件同步记录
import { useFilesStore } from '../../stores/files'
// 群元数据：群文件列表
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useLinkMateStore } from '../../stores/linkmate'
import { useAppSettingsStore } from '../../stores/appSettings'
import * as linkmateApi from '../../api/linkmate'
import { resolveSpeechLanguageHint } from '../../utils/speechLang'
import axios from 'axios'
import { preloadLinkMateLogo } from '../../utils/linkmateLogo'
import { extractLinkMateQuestion, hasLinkMateMention } from '../../utils/linkmateMention'
import { createStringRafBatcher } from '../../utils/linkmateRafBatch'
import { resolveLinkMateErrorMessage } from '../../utils/linkmateErrors'
// 消息类型定义
import type { ChatMessage, ContactItem } from '../../types'
import LocationPickerPage from '../LocationPickerPage.vue'
// 聊天表情常量列表
import { CHAT_EMOJIS } from '../../constants/emojis'
// 文件工具：大小格式化、DataURL 读取、图片大小上限
import { formatFileSize, MAX_IMAGE_BYTES } from '../../utils/file'
import {
  VOICE_MAX_SECONDS,
  blobToVoiceFile,
  elapsedVoiceSeconds,
  isVoiceDurationValid,
  pickVoiceMimeType
} from '../../utils/voiceRecorder'
import { useI18n } from '../../i18n'
import AtMentionPicker from '../common/AtMentionPicker.vue'
import QuoteReplyBar from './QuoteReplyBar.vue'
import { chatMessagePreviewText } from '../../utils/messagePreviewText'
import { sendTypingIndicator } from '../../utils/chatSocket'
import { LxButton, LxIconButton } from '../ui'

/** @全体成员 的伪 ID，写入正文为「@全体成员」供提醒逻辑识别 */
const AT_ALL_ID = '__all__'
/** @灵伴 的伪 ID */
const LINKMATE_AT_ID = '__linkmate__'

// 组件入参：会话类型与可选的回复目标消息
const props = defineProps<{
  isMyPhone: boolean
  isFriendChat: boolean
  isGroupChat: boolean
  replyingTo?: ChatMessage
}>()

// 向父组件抛出：更新回复目标、滚动到底部
const emit = defineEmits<{
  (e: 'update:replyingTo', val?: ChatMessage): void
  (e: 'scrollToBottom'): void
}>()

// Naive UI 全局消息实例
const message = useMessage()
const { t } = useI18n()
// 各 Pinia store 实例
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const filesStore = useFilesStore()
const groupMetaStore = useGroupMetaStore()
const linkMateStore = useLinkMateStore()
const appSettingsStore = useAppSettingsStore()
const { enabled: linkMateEnabled, deepThinking: linkMateDeepThinking, deepThinkingSupported: linkMateDeepThinkingSupported, dailyQuotaExhausted: linkMateQuotaExhausted } = storeToRefs(linkMateStore)

/** 群聊用「群聊小助手」，单聊仍用灵伴 */
const linkMateAtName = computed(() =>
  props.isGroupChat ? t('groupAi.assistantName') : t('linkmate.atName')
)

// 从 appStore 解构响应式会话与用户信息
const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)
// 从 appStore 解构方法（非响应式）
const { sendMessage } = appStore
// 打开红包弹窗
const { openRedPacket } = chatModalsStore
const callStore = useCallStore()

/** 群聊禁言：无法发言时禁用输入 */
const speakForbidden = computed(() => {
  if (!props.isGroupChat || !currentSessionId.value) return false
  return groupMetaStore.isSpeakForbidden(currentSessionId.value, userProfile.value.userId)
})

const inputDisabled = computed(
  () => !!currentSession.value?.blocked || speakForbidden.value
)

const inputPlaceholder = computed(() => {
  if (currentSession.value?.blocked) return t('chat.blocked')
  if (speakForbidden.value) {
    const mute = groupMetaStore.muteStateFor(currentSessionId.value || '')
    if (mute.meMuted) return t('chat.mutedSpeak')
    if (mute.muteAll) return t('chat.muteAllSpeak')
    return t('chat.mutedSpeak')
  }
  return t('chat.inputPlaceholder')
})

/** 从输入栏发起语音通话 */
async function startVoiceCall() {
  const session = currentSession.value
  const sessionId = currentSessionId.value
  if (!session || !sessionId) {
    message.warning(t('chat.selectSessionFirst'))
    return
  }
  if (session.isGroup) {
    message.warning(t('chat.callPrivateOnly'))
    return
  }
  try {
    await callStore.startOutgoing({
      conversationId: sessionId,
      callType: 'voice',
      peerName: session.name,
      peerAvatar: session.avatarUrl,
      peerUserId: session.peerUserId
    })
  } catch (error) {
    const err = error as { message?: string }
    message.error(err.message || t('chat.callFailed'))
  }
}

/** 从输入栏发起视频通话（仅单聊） */
async function startVideoCall() {
  const session = currentSession.value
  const sessionId = currentSessionId.value
  if (!session || !sessionId) {
    message.warning(t('chat.selectSessionFirst'))
    return
  }
  if (session.isGroup) {
    message.warning(t('chat.callPrivateOnly'))
    return
  }
  try {
    await callStore.startOutgoing({
      conversationId: sessionId,
      callType: 'video',
      peerName: session.name,
      peerAvatar: session.avatarUrl,
      peerUserId: session.peerUserId
    })
  } catch (error) {
    const err = error as { message?: string }
    message.error(err.message || t('chat.callFailed'))
  }
}

// 文本输入框绑定值
const inputValue = ref('')
// 表情面板是否展开
const showEmoji = ref(false)
// NInput 实例，用于定位 textarea 光标
const messageInputRef = ref<InstanceType<typeof NInput> | null>(null)

// 隐藏的图片选择 input 引用
const imageInputRef = ref<HTMLInputElement | null>(null)
// 隐藏的文件选择 input 引用
const fileInputRef = ref<HTMLInputElement | null>(null)

// 表情列表副本（供模板 v-for）
const emojis = [...CHAT_EMOJIS]

// —— 群聊 @ 提及 ——
const showMentionPicker = ref(false)
const mentionQuery = ref('')
const mentionStartIndex = ref(0)
const mentionPickerRef = ref<InstanceType<typeof AtMentionPicker> | null>(null)
const mentionAnchorStyle = ref<Record<string, string>>({ visibility: 'hidden' })
let mentionAnchorRaf = 0

function syncMentionAnchor() {
  if (!showMentionPicker.value) return
  const ta = getTextareaEl()
  if (!ta) return
  const rect = ta.getBoundingClientRect()
  mentionAnchorStyle.value = {
    position: 'fixed',
    left: `${Math.max(8, rect.left)}px`,
    top: `${Math.max(8, rect.top - 8)}px`,
    transform: 'translateY(-100%)',
    zIndex: '10000',
    width: '240px'
  }
}

function scheduleMentionAnchorSync() {
  if (!showMentionPicker.value) return
  if (mentionAnchorRaf) cancelAnimationFrame(mentionAnchorRaf)
  mentionAnchorRaf = requestAnimationFrame(() => {
    mentionAnchorRaf = 0
    syncMentionAnchor()
  })
}

function bindMentionAnchorListeners() {
  window.addEventListener('resize', scheduleMentionAnchorSync)
  window.addEventListener('scroll', scheduleMentionAnchorSync, true)
}

function unbindMentionAnchorListeners() {
  window.removeEventListener('resize', scheduleMentionAnchorSync)
  window.removeEventListener('scroll', scheduleMentionAnchorSync, true)
}

watch(currentSessionId, () => {
  if (linkMateImStreaming.value) {
    stopLinkMateImReply()
  }
})

watch(showMentionPicker, (open) => {
  if (open) {
    bindMentionAnchorListeners()
    nextTick(scheduleMentionAnchorSync)
  } else {
    unbindMentionAnchorListeners()
    mentionAnchorStyle.value = { visibility: 'hidden' }
  }
})

onMounted(() => {
  if (props.isGroupChat || props.isFriendChat) {
    void linkMateStore.loadStatus()
    preloadLinkMateLogo()
  }
})

const { draftBySession } = storeToRefs(appStore)
let draftSaveTimer: ReturnType<typeof setTimeout> | null = null
/** [P2] 收集本组件创建的 Object URL，组件卸载时统一释放，避免内存泄漏 */
const objectUrls: string[] = []

watch(
  currentSessionId,
  (id) => {
    showMentionPicker.value = false
    mentionQuery.value = ''
    if (draftSaveTimer) {
      clearTimeout(draftSaveTimer)
      draftSaveTimer = null
    }
    inputValue.value = id ? draftBySession.value[id] || '' : ''
  },
  { immediate: true }
)

watch(
  () => (currentSessionId.value ? draftBySession.value[currentSessionId.value] : ''),
  (draft) => {
    const id = currentSessionId.value
    if (!id) return
    if (!inputValue.value || inputValue.value === draft) {
      inputValue.value = draft || ''
    }
  }
)

function scheduleDraftSave() {
  const id = currentSessionId.value
  if (!id || !appStore.sessions.find(s => s.id === id)?.isReal) return
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  draftSaveTimer = setTimeout(() => {
    void appStore.saveSessionDraft(id, inputValue.value)
  }, 800)
}

watch(inputValue, () => {
  scheduleDraftSave()
})

function getTextareaEl(): HTMLTextAreaElement | null {
  const inst = messageInputRef.value as unknown as {
    textareaElRef?: HTMLTextAreaElement
  } | null
  return inst?.textareaElRef ?? null
}

/** 群成员候选（含灵伴、置顶的「全体成员」）；单聊仅灵伴 */
const mentionCandidates = computed<ContactItem[]>(() => {
  if (!currentSessionId.value) return []
  if (!props.isGroupChat && !props.isFriendChat) return []
  const q = mentionQuery.value.trim().toLowerCase()
  const linkMate: ContactItem = {
    id: LINKMATE_AT_ID,
    name: linkMateAtName.value,
    avatarText: '',
    avatarColor: 'transparent',
    group: props.isGroupChat ? t('groupAi.assistantName') : t('linkmate.atHintPrivate')
  }
  if (props.isFriendChat) {
    if (!linkMateEnabled.value) return []
    if (q && !linkMate.name.toLowerCase().includes(q)) return []
    return [linkMate]
  }
  const me = userProfile.value.userId
  const atAllName = t('extra.atAllMembers')
  const atAll: ContactItem = {
    id: AT_ALL_ID,
    name: atAllName,
    avatarText: '@',
    avatarColor: 'var(--lx-accent)',
    group: t('extra.atAllHint')
  }
  const members: ContactItem[] = groupMetaStore
    .membersFor(currentSessionId.value)
    .filter(m => !me || m.id !== me)
    .map(m => ({
      id: m.id,
      userId: m.id,
      name: m.name,
      avatarText: m.avatarText,
      avatarColor: m.avatarColor,
      avatarUrl: m.avatarUrl,
      group: m.badge || t('extra.groupMember')
    }))
  let list: ContactItem[] = []
  const groupLinkmateOn =
    !props.isGroupChat ||
    (groupMetaStore.linkmateStateLoaded(currentSessionId.value || '')
      ? groupMetaStore.linkmateEnabledFor(currentSessionId.value || '')
      : false)
  if (linkMateEnabled.value && groupLinkmateOn) list.push(linkMate)
  list.push(atAll, ...members)
  if (q) {
    list = list.filter(f => f.name.toLowerCase().includes(q))
  }
  return list.slice(0, 30)
})

const canAtMention = computed(() => {
  if (inputDisabled.value) return false
  if (props.isGroupChat) return true
  return props.isFriendChat && linkMateEnabled.value
})

/** 输入 @ 触发提及面板时，在输入框内展示深度思考开关 */
const showDeepThinkingWhenAt = computed(
  () =>
    showMentionPicker.value &&
    linkMateEnabled.value &&
    linkMateDeepThinkingSupported.value &&
    (!props.isGroupChat ||
      (groupMetaStore.linkmateStateLoaded(currentSessionId.value || '') &&
        groupMetaStore.linkmateEnabledFor(currentSessionId.value || '')))
)

function detectMentionTrigger() {
  if (!canAtMention.value) {
    showMentionPicker.value = false
    return
  }
  const ta = getTextareaEl()
  const value = inputValue.value
  const cursor = ta?.selectionStart ?? value.length
  let i = cursor - 1
  while (i >= 0) {
    const ch = value[i]
    if (ch === '@') {
      const segment = value.slice(i + 1, cursor)
      if (/^\S{0,32}$/.test(segment) && !segment.includes('\n')) {
        mentionStartIndex.value = i
        mentionQuery.value = segment
        showMentionPicker.value = true
        if (props.isGroupChat && currentSessionId.value) {
          void groupMetaStore.fetchMembers(currentSessionId.value)
        }
        scheduleMentionAnchorSync()
      } else {
        showMentionPicker.value = false
      }
      return
    }
    if (ch === ' ' || ch === '\n' || ch === '\t' || ch === '\u3000') break
    i--
  }
  showMentionPicker.value = false
}

function onInputUpdate(val: string) {
  inputValue.value = val
  notifyTyping()
  nextTick(() => {
    detectMentionTrigger()
    scheduleMentionAnchorSync()
  })
}

let lastTypingSent = 0
function notifyTyping() {
  const sid = currentSessionId.value
  if (!sid || inputDisabled.value) return
  const now = Date.now()
  if (now - lastTypingSent < 2000) return
  lastTypingSent = now
  try {
    sendTypingIndicator(sid)
  } catch {
    /* WS 未连接时忽略 */
  }
}

function applyMention(id: string | number, name: string) {
  const ta = getTextareaEl()
  const before = inputValue.value.slice(0, mentionStartIndex.value)
  const cursor = ta?.selectionStart ?? mentionStartIndex.value
  const after = inputValue.value.slice(cursor)
  const displayName =
    String(id) === AT_ALL_ID
      ? t('extra.atAllMembers')
      : String(id) === LINKMATE_AT_ID
        ? linkMateAtName.value
        : name
  const inserted = `@${displayName} `
  inputValue.value = before + inserted + after
  showMentionPicker.value = false
  mentionQuery.value = ''
  nextTick(() => {
    const el = getTextareaEl()
    if (!el) return
    const newPos = before.length + inserted.length
    el.focus()
    el.setSelectionRange(newPos, newPos)
  })
}

/**
 * 发送前校验：当前会话是否已屏蔽对方。
 *
 * @returns 允许发送返回 true，否则提示并返回 false
 */
function ensureCanSend(): boolean {
  if (currentSession.value?.blocked) {
    message.warning(t('chat.blockedSend'))
    return false
  }
  if (speakForbidden.value) {
    const mute = groupMetaStore.muteStateFor(currentSessionId.value || '')
    message.warning(mute.meMuted ? t('chat.mutedSpeak') : t('chat.muteAllSpeak'))
    return false
  }
  return true
}

/** 触发隐藏的文件选择器（图片/任意文件共用同一 input 逻辑入口） */
function toolFile() {
  fileInputRef.value?.click()
}

/**
 * 屏幕截图并作为图片消息发送。
 * Electron 环境优先使用 desktopCapturer，浏览器环境使用 getDisplayMedia。
 */
async function toolScreenshot() {
  if (!ensureCanSend()) return

  try {
    let dataUrl: string

    // Electron 环境：使用 captureScreen API
    if (window.electronAPI?.captureScreen) {
      const result = await window.electronAPI.captureScreen()
      if (!result) {
        message.warning(t('chat.screenshotFail'))
        return
      }
      dataUrl = result.dataURL
    } else if (navigator.mediaDevices?.getDisplayMedia) {
      // 浏览器环境：使用 getDisplayMedia
      const stream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })
      const video = document.createElement('video')
      video.srcObject = stream
      await video.play()
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      canvas.getContext('2d')?.drawImage(video, 0, 0)
      stream.getTracks().forEach(t => t.stop())
      dataUrl = canvas.toDataURL('image/png')
    } else {
      message.warning(t('chat.screenshotUnsupported'))
      return
    }

    if (dataUrl.length > MAX_IMAGE_BYTES * 1.4) {
      message.warning(t('chat.screenshotTooLarge'))
      return
    }

    const shotName = `截图_${new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)}.png`
    await sendMessage(dataUrl, {
      type: 'image',
      isImage: true,
      fileName: shotName,
      fileSize: formatFileSize(Math.round(dataUrl.length * 0.75))
    })
    message.success(t('chat.screenshotSent'))
  } catch (e) {
    console.error('[截图] 失败:', e)
    message.info(t('chat.screenshotCancel'))
  }
}

/** 触发红包弹窗：真实会话与非真实会话都允许发红包，由 RedPacketModal 决定是否调用后端 */
function toolRedPacket() {
  if (props.isMyPhone) return
  openRedPacket()
}

const showLocationPicker = ref(false)

function toolLocation() {
  if (inputDisabled.value) return
  showLocationPicker.value = true
}

async function onLocationSelect(location: string) {
  showLocationPicker.value = false
  const text = location?.trim()
  if (!text) return
  try {
    await sendMessage(text, { type: 'location' })
  } catch (e) {
    const err = e as { message?: string }
    message.error(err.message || t('common.fail'))
  }
}

/** 图片 input change：取首个文件后交给 handleFileSend */
async function onImagePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  handleFileSend(file)
}

/** 文件 input change：逻辑与图片选择相同 */
function onFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  handleFileSend(file)
}

/**
 * 统一处理文件/图片发送。
 * 图片转 DataURL；其他文件用 Object URL，并写入 filesStore 与群文件列表。
 */
async function handleFileSend(file: File) {
  if (!ensureCanSend()) return

  // 校验文件大小，防止读取失败的空文件（0 字节）发送到后端报错
  if (file.size === 0) {
    message.error(t('chat.cannotReadFile'))
    return
  }

  if (file.type.startsWith('image/')) {
    if (file.size > MAX_IMAGE_BYTES) {
      message.warning(t('chat.imageTooLarge', { size: formatFileSize(MAX_IMAGE_BYTES) }))
      return
    }
    try {
      await sendMessage('', {
        type: 'image',
        isImage: true,
        rawFile: file,
        fileName: file.name,
        fileSize: formatFileSize(file.size)
      })
      message.success(t('chat.imageSent'))
    } catch {
      message.error(t('chat.imageSendFail'))
    }
  } else {
    const fileUrl = URL.createObjectURL(file)
    objectUrls.push(fileUrl)
    try {
      await sendMessage(file.name, {
        type: 'file',
        fileName: file.name,
        fileSize: formatFileSize(file.size),
        fileUrl,
        rawFile: file
      })
      // 同步到全局文件传输列表
      filesStore.addFromChat(file.name, formatFileSize(file.size), t('chat.me'), fileUrl)
      // 群聊时追加到群文件元数据
      if (props.isGroupChat && currentSessionId.value) {
        groupMetaStore.addFile(currentSessionId.value, {
          name: file.name,
          size: formatFileSize(file.size),
          user: userProfile.value?.nickname || t('chat.me'),
          fileUrl
        })
      }
      message.success(t('chat.fileSent'))
      // [P2-1] 发送成功后释放 Object URL，避免内存泄漏
      URL.revokeObjectURL(fileUrl)
    } catch {
      // [P2-1] 发送失败也要释放 Object URL，避免内存泄漏
      URL.revokeObjectURL(fileUrl)
      message.error(t('chat.fileSendFail'))
    }
  }
}

/**
 * 粘贴事件：剪贴板中的图片或文件自动发送。
 */
function onPaste(e: ClipboardEvent) {
  if (!e.clipboardData) return
  const items = e.clipboardData.items
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        e.preventDefault()
        handleFileSend(file)
      }
    } else if (item.kind === 'file') {
      const file = item.getAsFile()
      if (file) {
        e.preventDefault()
        handleFileSend(file)
      }
    }
  }
}

// —— 语音录制 / 语音转文字 ——
const isRecordingVoice = ref(false)
const voiceRecordSeconds = ref(0)
const voiceSending = ref(false)
const isDictating = ref(false)
const dictatingSeconds = ref(0)
const dictatingBusy = ref(false)
/** 听写进行中：边录边分段调用服务端转写，把文字持续写入输入框 */
const dictateStreaming = ref(false)
let mediaRecorder: MediaRecorder | null = null
let mediaStream: MediaStream | null = null
let voiceChunks: BlobPart[] = []
let voiceStartedAt = 0
let voiceTickTimer: number | null = null
let voiceMaxTimer: number | null = null
let dictatePartialTimer: number | null = null
let recordMode: 'voice' | 'dictate' = 'voice'
/** 开始听写前输入框已有内容 */
let dictateBaseText = ''
/** 本轮听写已识别出的全文（随分段转写刷新） */
let dictateFinalText = ''
/** 分段转写序号，用于丢弃过期响应 */
let dictateTranscribeSeq = 0
let dictatePartialInFlight = false
const DICTATE_PARTIAL_INTERVAL_MS = 1800
const DICTATE_PARTIAL_MIN_BYTES = 1200

function clearVoiceTimers() {
  if (voiceTickTimer != null) {
    window.clearInterval(voiceTickTimer)
    voiceTickTimer = null
  }
  if (voiceMaxTimer != null) {
    window.clearTimeout(voiceMaxTimer)
    voiceMaxTimer = null
  }
  if (dictatePartialTimer != null) {
    window.clearInterval(dictatePartialTimer)
    dictatePartialTimer = null
  }
}

function stopMediaTracks() {
  mediaStream?.getTracks().forEach(track => track.stop())
  mediaStream = null
}

function joinDictateParts(base: string, spoken: string): string {
  const part = spoken.trim()
  if (!part) return base
  if (!base) return part
  if (base.endsWith(' ') || base.endsWith('\n') || base.endsWith('\u3000')) {
    return base + part
  }
  const needSpace = /[A-Za-z0-9]$/.test(base) && /^[A-Za-z0-9]/.test(part)
  return needSpace ? `${base} ${part}` : base + part
}

function applyStreamingDictateText() {
  inputValue.value = joinDictateParts(dictateBaseText, dictateFinalText)
}

function resetVoiceRecorderState() {
  clearVoiceTimers()
  stopMediaTracks()
  mediaRecorder = null
  voiceChunks = []
  isRecordingVoice.value = false
  isDictating.value = false
  dictateStreaming.value = false
  voiceRecordSeconds.value = 0
  dictatingSeconds.value = 0
  voiceStartedAt = 0
  dictateBaseText = ''
  dictateFinalText = ''
  dictateTranscribeSeq = 0
  dictatePartialInFlight = false
}

/**
 * 结束录音并可选发送。cancel=true 时丢弃结果。
 */
async function finishVoiceRecord(cancel: boolean) {
  const recorder = mediaRecorder
  const mode = recordMode
  const progressive = dictateStreaming.value
  if (!recorder || recorder.state === 'inactive') {
    resetVoiceRecorderState()
    return
  }

  const mimeType = recorder.mimeType || pickVoiceMimeType() || 'audio/webm'
  const startedAt = voiceStartedAt
  const baseSnapshot = dictateBaseText

  // 停止分段转写定时器，避免 stop 前后并发
  if (dictatePartialTimer != null) {
    window.clearInterval(dictatePartialTimer)
    dictatePartialTimer = null
  }

  const blob = await new Promise<Blob>((resolve, reject) => {
    recorder.ondataavailable = (ev) => {
      if (ev.data && ev.data.size > 0) voiceChunks.push(ev.data)
    }
    recorder.onerror = () => reject(new Error('voice_record_error'))
    recorder.onstop = () => {
      resolve(new Blob(voiceChunks, { type: mimeType }))
    }
    try {
      recorder.stop()
    } catch (e) {
      reject(e)
    }
  }).catch(() => null)

  const durationSec = elapsedVoiceSeconds(startedAt)
  // 先清录音资源，但保留 progressive 听写时已写入的 base/识别文本语义
  clearVoiceTimers()
  stopMediaTracks()
  mediaRecorder = null
  voiceChunks = []
  isRecordingVoice.value = false
  voiceRecordSeconds.value = 0
  dictatingSeconds.value = 0
  voiceStartedAt = 0

  if (cancel || !blob || blob.size === 0) {
    if (mode === 'dictate') {
      inputValue.value = baseSnapshot
    }
    isDictating.value = false
    dictateStreaming.value = false
    dictateBaseText = ''
    dictateFinalText = ''
    if (!cancel) message.warning(t('chat.voiceRecordEmpty'))
    return
  }
  if (!isVoiceDurationValid(durationSec)) {
    if (mode === 'dictate') {
      inputValue.value = baseSnapshot
    }
    isDictating.value = false
    dictateStreaming.value = false
    dictateBaseText = ''
    dictateFinalText = ''
    message.warning(t('chat.voiceTooShort'))
    return
  }

  if (mode === 'dictate') {
    await finishDictation(blob, mimeType, durationSec, {
      progressive,
      baseText: baseSnapshot
    })
    return
  }

  const file = blobToVoiceFile(blob, mimeType, durationSec)
  const voiceUrl = URL.createObjectURL(blob)
  voiceSending.value = true
  try {
    await sendMessage('[语音]', {
      type: 'voice',
      voiceDuration: durationSec,
      voiceUrl,
      fileUrl: voiceUrl,
      fileName: file.name,
      fileSize: formatFileSize(file.size),
      rawFile: file
    })
    message.success(t('chat.voiceSent'))
    URL.revokeObjectURL(voiceUrl)
  } catch {
    URL.revokeObjectURL(voiceUrl)
    message.error(t('chat.voiceSendFail'))
  } finally {
    voiceSending.value = false
  }
}

async function finishDictation(
  blob: Blob,
  mimeType: string,
  durationSec: number,
  opts?: { progressive?: boolean; baseText?: string }
) {
  dictatingBusy.value = true
  const progressive = opts?.progressive === true
  const baseText = opts?.baseText ?? ''
  try {
    const file = blobToVoiceFile(blob, mimeType, durationSec)
    const language = resolveSpeechLanguageHint(appSettingsStore.translateTargetLang)
    const res = await linkmateApi.transcribeAudio(file, language, file.name)
    if (res.code !== 200 || !res.data?.text?.trim()) {
      throw new Error(res.message || t('chat.transcribeFail'))
    }
    const text = res.data.text.trim()
    if (progressive) {
      inputValue.value = joinDictateParts(baseText, text)
    } else {
      const current = inputValue.value
      inputValue.value = current
        ? current.endsWith(' ') || current.endsWith('\n')
          ? current + text
          : `${current} ${text}`
        : text
    }
    message.success(t('chat.dictateDone'))
    await nextTick()
    document.querySelector<HTMLTextAreaElement>('.message-input textarea')?.focus()
  } catch (err) {
    const ax = err as { response?: { data?: { message?: string } }; message?: string }
    const raw = ax.response?.data?.message || ax.message || ''
    // 分段听写过程中若已有文字，结束时失败则保留已填内容
    if (!(progressive && dictateFinalText.trim())) {
      if (progressive) inputValue.value = baseText
      message.error(resolveLinkMateErrorMessage(raw, t) || t('chat.transcribeFail'))
    } else {
      message.success(t('chat.dictateDone'))
    }
  } finally {
    isDictating.value = false
    dictateStreaming.value = false
    dictateBaseText = ''
    dictateFinalText = ''
    dictateTranscribeSeq = 0
    dictatePartialInFlight = false
    dictatingBusy.value = false
  }
}

/** 听写中周期性把当前录音送到服务端转写，刷新输入框 */
async function flushDictatePartial() {
  if (!isDictating.value || !dictateStreaming.value || dictatePartialInFlight) return
  const recorder = mediaRecorder
  if (!recorder || recorder.state !== 'recording') return

  try {
    recorder.requestData()
  } catch {
    /* ignore */
  }
  // 等一次 timeslice 回调把数据推进 voiceChunks
  await new Promise<void>((resolve) => window.setTimeout(resolve, 80))

  const mimeType = recorder.mimeType || pickVoiceMimeType() || 'audio/webm'
  const blob = new Blob(voiceChunks, { type: mimeType })
  if (blob.size < DICTATE_PARTIAL_MIN_BYTES) return

  const seq = ++dictateTranscribeSeq
  dictatePartialInFlight = true
  try {
    const durationSec = Math.max(1, Math.floor((Date.now() - voiceStartedAt) / 1000))
    const file = blobToVoiceFile(blob, mimeType, durationSec)
    const language = resolveSpeechLanguageHint(appSettingsStore.translateTargetLang)
    const res = await linkmateApi.transcribeAudio(file, language, file.name)
    if (seq !== dictateTranscribeSeq) return
    if (!isDictating.value || !dictateStreaming.value) return
    const text = res.data?.text?.trim()
    if (res.code === 200 && text) {
      dictateFinalText = text
      applyStreamingDictateText()
    }
  } catch (err) {
    // 分段失败不打断听写，等下一次或结束时最终转写
    console.warn('[听写] 分段转写失败', err)
  } finally {
    if (seq === dictateTranscribeSeq) {
      dictatePartialInFlight = false
    }
  }
}

/**
 * 边录边转写：用已配置的 Whisper 兼容服务，边说边更新输入框。
 */
async function startProgressiveDictation() {
  if (!ensureCanSend() || voiceSending.value || dictatingBusy.value) return
  if (isRecordingVoice.value || isDictating.value) return
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    message.warning(t('chat.voiceUnsupported'))
    return
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaStream = stream
    const mimeType = pickVoiceMimeType()
    mediaRecorder = mimeType
      ? new MediaRecorder(stream, { mimeType })
      : new MediaRecorder(stream)
    recordMode = 'dictate'
    voiceChunks = []
    voiceStartedAt = Date.now()
    voiceRecordSeconds.value = 0
    dictatingSeconds.value = 0
    dictateBaseText = inputValue.value
    dictateFinalText = ''
    dictateTranscribeSeq = 0
    dictatePartialInFlight = false
    dictateStreaming.value = true
    isDictating.value = true

    mediaRecorder.ondataavailable = (ev) => {
      if (ev.data && ev.data.size > 0) voiceChunks.push(ev.data)
    }
    mediaRecorder.start(200)

    voiceTickTimer = window.setInterval(() => {
      dictatingSeconds.value = elapsedVoiceSeconds(voiceStartedAt)
    }, 100)

    dictatePartialTimer = window.setInterval(() => {
      void flushDictatePartial()
    }, DICTATE_PARTIAL_INTERVAL_MS)

    // 稍等首段音频再立刻转一次，减少「说了半天没字」的感觉
    window.setTimeout(() => {
      void flushDictatePartial()
    }, 1100)

    voiceMaxTimer = window.setTimeout(() => {
      void finishVoiceRecord(false)
    }, VOICE_MAX_SECONDS * 1000)
  } catch (e) {
    console.error('[听写] 无法开始录音:', e)
    resetVoiceRecorderState()
    message.error(t('chat.voiceMicDenied'))
  }
}

/**
 * 开始麦克风录音；再次点击结束并发送，超时自动发送。
 */
async function startVoiceRecord(mode: 'voice' | 'dictate' = 'voice') {
  if (!ensureCanSend() || voiceSending.value || dictatingBusy.value) return
  if (isRecordingVoice.value || isDictating.value) return
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    message.warning(t('chat.voiceUnsupported'))
    return
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaStream = stream
    const mimeType = pickVoiceMimeType()
    mediaRecorder = mimeType
      ? new MediaRecorder(stream, { mimeType })
      : new MediaRecorder(stream)
    recordMode = mode
    voiceChunks = []
    voiceStartedAt = Date.now()
    voiceRecordSeconds.value = 0
    dictatingSeconds.value = 0
    dictateStreaming.value = false
    if (mode === 'dictate') {
      isDictating.value = true
    } else {
      isRecordingVoice.value = true
    }

    mediaRecorder.ondataavailable = (ev) => {
      if (ev.data && ev.data.size > 0) voiceChunks.push(ev.data)
    }
    mediaRecorder.start(200)

    voiceTickTimer = window.setInterval(() => {
      const sec = elapsedVoiceSeconds(voiceStartedAt)
      if (mode === 'dictate') {
        dictatingSeconds.value = sec
      } else {
        voiceRecordSeconds.value = sec
      }
    }, 100)

    voiceMaxTimer = window.setTimeout(() => {
      void finishVoiceRecord(false)
    }, VOICE_MAX_SECONDS * 1000)
  } catch (e) {
    console.error('[语音] 无法开始录音:', e)
    resetVoiceRecorderState()
    message.error(t('chat.voiceMicDenied'))
  }
}

/**
 * 切换语音录制：未录制时开始，录制中再点则停止并发送。
 */
async function toggleVoiceRecord() {
  if (voiceSending.value || dictatingBusy.value || isDictating.value) return
  if (isRecordingVoice.value) {
    await finishVoiceRecord(false)
    return
  }
  await startVoiceRecord('voice')
}

/** 语音转文字：边说边填入；再次点击结束并做最终转写 */
async function toggleDictation() {
  if (voiceSending.value || dictatingBusy.value || isRecordingVoice.value) return
  if (isDictating.value) {
    await finishVoiceRecord(false)
    return
  }
  await startProgressiveDictation()
}

watch(currentSessionId, () => {
  if (isRecordingVoice.value || isDictating.value) {
    void finishVoiceRecord(true)
  }
})

onUnmounted(() => {
  linkMateReplyAbort?.abort()
  linkMateReplyAbort = null
  unbindMentionAnchorListeners()
  if (mentionAnchorRaf) cancelAnimationFrame(mentionAnchorRaf)
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  // [P2-1] 释放未清理的 Object URL（如发送失败后组件卸载）
  for (const url of objectUrls) {
    URL.revokeObjectURL(url)
  }
  objectUrls.length = 0
  if ((isRecordingVoice.value || isDictating.value) && mediaRecorder && mediaRecorder.state !== 'inactive') {
    try {
      mediaRecorder.stop()
    } catch {
      /* ignore */
    }
  }
  resetVoiceRecorderState()
})

/** 选中表情追加到输入框并关闭表情面板 */
function pickEmoji(e: string) {
  inputValue.value += e
  showEmoji.value = false
}

function mentionHasLinkMate(text: string): boolean {
  return hasLinkMateMention(text, linkMateAtName.value)
}

function mentionExtractLinkMateQuestion(text: string): string | null {
  return extractLinkMateQuestion(text, linkMateAtName.value)
}

function buildLinkMateQuestion(text: string): string | null {
  const base = mentionExtractLinkMateQuestion(text)
  if (!base) return null
  if (props.replyingTo) {
    const quote = chatMessagePreviewText(props.replyingTo)
    if (quote) {
      return `${base}\n\n${t('linkmate.quotePrefix')}\n${quote}`
    }
  }
  return base
}

function toggleLinkMateDeepThinking() {
  if (!linkMateDeepThinkingSupported.value) {
    message.info(t('linkmate.deepThinkingUnsupported'))
    return
  }
  linkMateStore.setDeepThinking(!linkMateDeepThinking.value)
}

let linkMateReplyAbort: AbortController | null = null
const linkMateImStreaming = ref(false)

function stopLinkMateImReply() {
  linkMateReplyAbort?.abort()
}

let linkMateScrollRaf = 0
function scheduleLinkMateScroll() {
  if (linkMateScrollRaf) return
  linkMateScrollRaf = requestAnimationFrame(() => {
    linkMateScrollRaf = 0
    emit('scrollToBottom')
  })
}

async function requestLinkMateImReply(conversationId: string, question: string) {
  if (linkMateQuotaExhausted.value) {
    message.warning(t('linkmate.dailyQuotaExhausted'))
    return
  }
  linkMateReplyAbort?.abort()
  const abortController = new AbortController()
  linkMateReplyAbort = abortController
  linkMateImStreaming.value = true
  const tempId = `temp-linkmate-${Date.now()}`
  let content = ''
  let assistantReasoning = ''
  const batchReasoning = createStringRafBatcher(chunk => {
    assistantReasoning += chunk
    appStore.updateStreamingLinkMateReasoning(conversationId, tempId, assistantReasoning)
    scheduleLinkMateScroll()
  })
  const batchContent = createStringRafBatcher(chunk => {
    content += chunk
    appStore.updateStreamingLinkMateMessage(conversationId, tempId, content)
    scheduleLinkMateScroll()
  })

  appStore.ensureStreamingLinkMateMessage(conversationId, tempId, linkMateAtName.value)
  emit('scrollToBottom')

  try {
    await linkmateApi.streamImReply(
      conversationId,
      question,
      {
        onReasoningDelta: chunk => {
          batchReasoning.push(chunk)
        },
        onDelta: chunk => {
          batchContent.push(chunk)
        },
        onDone: payload => {
          batchReasoning.flush()
          batchContent.flush()
          const messages =
            payload.messages && payload.messages.length > 0
              ? payload.messages
              : [{ id: payload.messageId, content }]
          appStore.finalizeStreamingLinkMateMessages(
            conversationId,
            tempId,
            messages,
            linkMateAtName.value
          )
          emit('scrollToBottom')
        },
        onError: errMsg => {
          appStore.removeStreamingLinkMateMessage(conversationId, tempId)
          message.error(resolveLinkMateErrorMessage(errMsg, t))
        }
      },
      abortController.signal,
      linkMateDeepThinking.value && linkMateDeepThinkingSupported.value
    )
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      appStore.removeStreamingLinkMateMessage(conversationId, tempId)
      return
    }
    appStore.removeStreamingLinkMateMessage(conversationId, tempId)
    let errMsg = t('linkmate.sendFailed')
    if (axios.isAxiosError(error)) {
      errMsg = (error.response?.data as { message?: string } | undefined)?.message || errMsg
    } else if (error instanceof Error && error.message) {
      errMsg = error.message
    }
    message.error(resolveLinkMateErrorMessage(errMsg, t))
  } finally {
    batchReasoning.flush()
    batchContent.flush()
    linkMateImStreaming.value = false
    if (linkMateReplyAbort === abortController) {
      linkMateReplyAbort = null
    }
    void linkMateStore.loadStatus()
  }
}

/**
 * 发送文本或 /img 命令图片。
 * 清空输入并取消回复引用。
 */
function send() {
  if (!inputValue.value.trim()) return
  if (!ensureCanSend()) return
  if (linkMateImStreaming.value) {
    message.warning(t('linkmate.streamingBusy'))
    return
  }

  void (async () => {
    try {
      if (inputValue.value.startsWith('/img ')) {
        const url = inputValue.value.replace('/img ', '').trim()
        if (!/^https?:\/\//i.test(url)) {
          message.warning(t('chat.imageUrlInvalid'))
          return
        }
        let imgName = t('chat.imageMessage')
        try {
          const path = new URL(url).pathname
          const base = path.split('/').pop()
          if (base) imgName = decodeURIComponent(base)
        } catch {
          /* keep fallback name */
        }
        await sendMessage(url, { type: 'image', isImage: true, fileName: imgName })
      } else {
        const text = inputValue.value
        if (
          mentionHasLinkMate(text) &&
          props.isGroupChat &&
          currentSessionId.value &&
          !groupMetaStore.linkmateEnabledFor(currentSessionId.value)
        ) {
          message.warning(t('linkmate.groupDisabled'))
          return
        }
        if (
          mentionHasLinkMate(text) &&
          linkMateEnabled.value &&
          (props.isGroupChat || props.isFriendChat) &&
          !mentionExtractLinkMateQuestion(text)
        ) {
          message.warning(t('linkmate.emptyAtPrompt'))
          return
        }
        const linkMateQuestion = buildLinkMateQuestion(text)
        await sendMessage(text, { type: 'text', replyTo: props.replyingTo })
        if (
          linkMateQuestion &&
          linkMateEnabled.value &&
          !linkMateQuotaExhausted.value &&
          (props.isGroupChat || props.isFriendChat) &&
          currentSessionId.value &&
          (!props.isGroupChat || groupMetaStore.linkmateEnabledFor(currentSessionId.value))
        ) {
          void requestLinkMateImReply(currentSessionId.value, linkMateQuestion)
        }
      }
      inputValue.value = ''
      showMentionPicker.value = false
      emit('update:replyingTo', undefined)
      const sid = currentSessionId.value
      if (sid) void appStore.clearSessionDraft(sid)
    } catch {
      message.error(t('chat.messageSendFail'))
    }
  })()
}

/**
 * 输入框按键：合并 Enter / @ 导航到同一 handler，
 * 避免同时绑 @keydown 与 @keydown.enter 导致 NInput 收到 Array。
 */
function onInputKeyDown(e: KeyboardEvent) {
  if (showMentionPicker.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      mentionPickerRef.value?.move(1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      mentionPickerRef.value?.move(-1)
      return
    }
    if (e.key === 'Tab') {
      e.preventDefault()
      const pick = mentionPickerRef.value?.confirm()
      if (pick) applyMention(pick.id, pick.name)
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      showMentionPicker.value = false
      return
    }
    if (e.key === 'Enter') {
      if (e.shiftKey) return
      e.preventDefault()
      if (mentionCandidates.value.length) {
        const pick = mentionPickerRef.value?.confirm()
        if (pick) applyMention(pick.id, pick.name)
      }
      return
    }
    return
  }
  // Enter 发送，Shift+Enter 换行
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    if (linkMateImStreaming.value) {
      message.warning(t('linkmate.streamingBusy'))
      return
    }
    send()
  }
}

/** 取消当前回复引用 */
function cancelReply() {
  emit('update:replyingTo', undefined)
}

const replyPreviewContent = computed(() =>
  props.replyingTo ? chatMessagePreviewText(props.replyingTo) : ''
)

const replySenderLabel = computed(() => {
  const name = props.replyingTo?.senderName?.trim()
  return name || t('chat.messageFallback')
})

/** 有回复预览时压缩输入区，避免工具栏/发送按钮被挤出可视区域 */
const textareaAutosize = computed(() =>
  props.replyingTo ? { minRows: 1, maxRows: 5 } : { minRows: 2, maxRows: 8 }
)

// 暴露给父组件：拖拽/外部调用文件发送
defineExpose({
  handleFileSend
})
</script>

<template>
  <!-- 聊天底部输入区域：好友/群聊样式区分 -->
  <div
    class="input-area"
    :class="{ 'input-area--friend': isFriendChat, 'input-area--group': isGroupChat }"
  >
    <!-- 隐藏的文件选择器：图片与通用文件 -->
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
      <!-- 回复预览 + 多行文本输入 -->
      <div class="input-compose">
        <div v-if="replyingTo" class="reply-compose">
          <div class="reply-compose-label">{{ replySenderLabel }}</div>
          <div class="reply-compose-row">
            <QuoteReplyBar variant="input" :content="replyPreviewContent" />
            <button type="button" class="lx-close-btn lx-close-btn--reply" :title="t('common.close')" @click="cancelReply">
              <n-icon :component="CloseOutline" :size="14" />
            </button>
          </div>
        </div>

        <div class="input-compose-body">
          <button
            v-if="showDeepThinkingWhenAt"
            type="button"
            class="mention-deep-toggle"
            :class="{ 'is-active': linkMateDeepThinking }"
            :title="
              linkMateDeepThinking ? t('linkmate.deepThinkingOn') : t('linkmate.deepThinkingOff')
            "
            :disabled="inputDisabled || linkMateImStreaming"
            @click="toggleLinkMateDeepThinking"
          >
            <n-icon :component="BulbOutline" :size="16" />
            <span>{{ t('linkmate.deepThinking') }}</span>
          </button>
          <n-input
            ref="messageInputRef"
            :value="inputValue"
            type="textarea"
            :autosize="textareaAutosize"
            :placeholder="inputPlaceholder"
            :disabled="inputDisabled"
            class="message-input"
            data-lm-chat-input
            :class="{
              'message-input--with-reply': replyingTo,
              'message-input--with-deep': showDeepThinkingWhenAt
            }"
            :bordered="false"
            @update:value="onInputUpdate"
            @keydown="onInputKeyDown"
            @paste="onPaste"
          />
        </div>
      </div>

      <Teleport to="body">
        <div
          v-if="showMentionPicker && canAtMention"
          class="chat-mention-anchor"
          :style="mentionAnchorStyle"
        >
          <AtMentionPicker
            ref="mentionPickerRef"
            placement="top"
            :friends="mentionCandidates"
            :title="t('extra.selectMember')"
            :empty-text="t('extra.noMembersToAt')"
            @apply="(p) => applyMention(p.id, p.name)"
            @close="showMentionPicker = false"
          />
        </div>
      </Teleport>

      <!-- 工具栏：表情、应用、文件、截图、红包、语音、通话、发送 -->
      <div class="input-toolbar">
        <div class="toolbar-left">
          <n-popover v-model:show="showEmoji" trigger="click" placement="top-start">
            <template #trigger>
              <LxIconButton variant="chat-tool" :title="t('chat.emoji')">
                <n-icon :component="HappyOutline" :size="20" />
              </LxIconButton>
            </template>
            <div class="emoji-grid">
              <button
                v-for="e in emojis"
                :key="e"
                type="button"
                class="lx-btn--emoji"
                @click="pickEmoji(e)"
              >
                {{ e }}
              </button>
            </div>
          </n-popover>
          <LxIconButton variant="chat-tool" :title="t('chat.sendFile')" @click="toolFile">
            <n-icon :component="FolderOutline" :size="20" />
          </LxIconButton>
          <LxIconButton variant="chat-tool" :title="t('chat.screenshot')" @click="toolScreenshot">
            <n-icon :component="CutOutline" :size="20" />
          </LxIconButton>
          <LxIconButton
            v-if="!isMyPhone"
            variant="chat-tool"
            :title="t('chat.redPacket')"
            @click="toolRedPacket"
          >
            <n-icon :component="GiftOutline" :size="20" />
          </LxIconButton>
          <LxIconButton
            variant="chat-tool"
            :title="t('chat.location')"
            :disabled="inputDisabled"
            @click="toolLocation"
          >
            <n-icon :component="LocationOutline" :size="20" />
          </LxIconButton>
          <LxIconButton
            variant="chat-tool"
            :class="{ 'is-recording': isRecordingVoice }"
            :title="
              isRecordingVoice
                ? t('chat.voiceRecording', { n: voiceRecordSeconds })
                : voiceSending
                  ? t('chat.voiceSending')
                  : t('chat.voice')
            "
            :disabled="inputDisabled || voiceSending || dictatingBusy || isDictating"
            @click="toggleVoiceRecord"
          >
            <n-icon :component="MicOutline" :size="20" />
          </LxIconButton>
          <LxIconButton
            variant="chat-tool"
            :class="{ 'is-recording': isDictating }"
            :title="
              isDictating
                ? t('chat.dictateRecording', { n: dictatingSeconds })
                : dictatingBusy
                  ? t('chat.transcribing')
                  : t('chat.dictate')
            "
            :disabled="inputDisabled || voiceSending || dictatingBusy || isRecordingVoice"
            @click="toggleDictation"
          >
            <n-icon :component="LanguageOutline" :size="20" />
          </LxIconButton>
        </div>

        <div class="toolbar-right">
          <template v-if="!isGroupChat">
            <LxIconButton variant="chat-tool" :title="t('chat.voiceCall')" @click="startVoiceCall">
              <n-icon :component="VolumeHighOutline" :size="20" />
            </LxIconButton>
            <LxIconButton variant="chat-tool" :title="t('chat.videoCall')" @click="startVideoCall">
              <n-icon :component="VideocamOutline" :size="20" />
            </LxIconButton>
          </template>
          <LxButton
            v-if="linkMateImStreaming"
            variant="send"
            @click="stopLinkMateImReply"
          >
            {{ t('linkmate.stop') }}
          </LxButton>
          <LxButton
            v-else
            variant="send"
            data-lm-send-btn
            :disabled="!inputValue.trim()"
            @click="send"
          >
            {{ t('chat.send') }}
          </LxButton>
        </div>
      </div>
    </div>
  </div>

  <Teleport to="body">
    <div v-if="showLocationPicker" class="location-overlay" @click.self="showLocationPicker = false">
      <div class="location-panel" @click.stop>
        <LocationPickerPage @select="onLocationSelect" @back="showLocationPicker = false" />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.input-area {
  flex-shrink: 0;
  padding: var(--lx-space-md) var(--lx-space-xl) var(--lx-space-xl);
  background: var(--lx-bg-panel);
  border-top: none;
  box-shadow: inset 0 1px 0 var(--lx-separator-fade);
  max-height: min(220px, 38vh);
}

.input-box {
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-xl);
  overflow: hidden;
  min-height: 108px;
  max-height: 100%;
}

.input-compose {
  flex: 1 1 auto;
  min-height: 0;
  max-height: 140px;
  overflow: hidden;
  padding: var(--lx-space) var(--lx-space-lg) var(--lx-space-xs);
}

.input-compose-body {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
}

.mention-deep-toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-2xs);
  padding: var(--lx-space-2xs) var(--lx-space-sm);
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-full);
  background: var(--lx-bg-panel);
  color: var(--lx-text-secondary);
  font-size: var(--lx-font-sm);
  line-height: 1.2;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

.mention-deep-toggle:hover:not(:disabled) {
  color: var(--lx-accent);
  border-color: var(--lx-accent-soft);
}

.mention-deep-toggle.is-active {
  color: var(--lx-accent);
  border-color: var(--lx-accent-soft);
  background: var(--lx-accent-bg);
}

.mention-deep-toggle:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.chat-mention-anchor :deep(.at-mention-popover) {
  position: relative;
  top: auto;
  bottom: auto;
  left: 0;
  width: 100%;
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lx-space-xs) var(--lx-space-md) var(--lx-space-md);
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--lx-space-2xs);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
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

.message-input :deep(.n-input__textarea-el),
.message-input :deep(.n-input__placeholder),
.message-input :deep(.n-input__textarea-mirror) {
  min-height: 56px !important;
  background: transparent !important;
  font-size: var(--lx-font);
  line-height: var(--lx-leading-normal);
  /* 与 textarea 同步去掉 Naive 默认上下 padding，避免光标与占位文字错位 */
  padding: 0 !important;
  color: var(--lx-text);
  resize: none;
}

.message-input--with-reply :deep(.n-input__textarea-el),
.message-input--with-reply :deep(.n-input__placeholder),
.message-input--with-reply :deep(.n-input__textarea-mirror) {
  min-height: 36px !important;
}

.message-input--with-deep :deep(.n-input__textarea-el),
.message-input--with-deep :deep(.n-input__placeholder),
.message-input--with-deep :deep(.n-input__textarea-mirror) {
  min-height: 44px !important;
}

.message-input :deep(.n-input__placeholder) {
  color: var(--n-placeholder-color);
}

.hidden-file-input {
  display: none;
}

.reply-compose {
  margin-bottom: var(--lx-space-sm);
}

.reply-compose-label {
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-snug);
  color: var(--lx-text-body, var(--lx-conf-surface));
  margin-bottom: var(--lx-space-xs);
}

.reply-compose-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--lx-space);
  padding: var(--lx-space-xs);
}

.location-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-dialog-input);
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-4xl);
}
.location-panel {
  width: min(420px, 96vw);
  height: min(640px, 90vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-lg);
  overflow: hidden;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.28);
}
</style>
