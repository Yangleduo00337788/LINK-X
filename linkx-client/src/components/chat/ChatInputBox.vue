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
import { ref, computed, nextTick, watch, onUnmounted } from 'vue'
// Naive UI 组件与消息提示
import { NIcon, NInput, NPopover, useMessage } from 'naive-ui'
// 工具栏图标（Ionicons5）
import {
  FolderOutline,
  HappyOutline,
  CutOutline,
  VolumeHighOutline,
  GiftOutline,
  MicOutline,
  CloseOutline,
  AtOutline
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
// 消息类型定义
import type { ChatMessage, ContactItem } from '../../types'
// 聊天表情常量列表
import { CHAT_EMOJIS } from '../../constants/emojis'
// 文件工具：大小格式化、DataURL 读取、图片大小上限
import { formatFileSize, MAX_IMAGE_BYTES } from '../../utils/file'
import {
  VOICE_MAX_SECONDS,
  blobToVoiceFile,
  isVoiceDurationValid,
  pickVoiceMimeType
} from '../../utils/voiceRecorder'
import { useI18n } from '../../i18n'
import AtMentionPicker from '../common/AtMentionPicker.vue'

/** @全体成员 的伪 ID，写入正文为「@全体成员」供提醒逻辑识别 */
const AT_ALL_ID = '__all__'

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

const { draftBySession } = storeToRefs(appStore)
let draftSaveTimer: ReturnType<typeof setTimeout> | null = null

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

/** 群成员候选（含置顶的「全体成员」） */
const mentionCandidates = computed<ContactItem[]>(() => {
  if (!props.isGroupChat || !currentSessionId.value) return []
  const q = mentionQuery.value.trim().toLowerCase()
  const me = userProfile.value.userId
  const atAllName = t('extra.atAllMembers')
  const atAll: ContactItem = {
    id: AT_ALL_ID,
    name: atAllName,
    avatarText: '@',
    avatarColor: 'var(--lx-accent)',
    group: t('extra.atAllHint')
  }
  const members: ContactItem[] = (groupMetaStore.members[currentSessionId.value] || [])
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
  let list = [atAll, ...members]
  if (q) {
    list = list.filter(f => f.name.toLowerCase().includes(q))
  }
  return list.slice(0, 30)
})

function detectMentionTrigger() {
  if (!props.isGroupChat || inputDisabled.value) {
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
        if (currentSessionId.value) void groupMetaStore.fetchMembers(currentSessionId.value)
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
  nextTick(() => detectMentionTrigger())
}

function applyMention(id: string | number, name: string) {
  const ta = getTextareaEl()
  const before = inputValue.value.slice(0, mentionStartIndex.value)
  const cursor = ta?.selectionStart ?? mentionStartIndex.value
  const after = inputValue.value.slice(cursor)
  const displayName = String(id) === AT_ALL_ID ? t('extra.atAllMembers') : name
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

/** 工具栏 @：插入 @ 并弹出成员列表 */
function triggerAtMention() {
  if (!props.isGroupChat || inputDisabled.value) return
  const ta = getTextareaEl()
  ta?.focus()
  const cursor = ta?.selectionStart ?? inputValue.value.length
  const before = inputValue.value.slice(0, cursor)
  const after = inputValue.value.slice(cursor)
  const prefix = before.length && !/[\s\u3000]$/.test(before) ? ' ' : ''
  const inserted = `${prefix}@`
  inputValue.value = before + inserted + after
  nextTick(() => {
    const el = getTextareaEl()
    if (!el) return
    const newPos = before.length + inserted.length
    el.focus()
    el.setSelectionRange(newPos, newPos)
    detectMentionTrigger()
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
  console.log('[截图] 按钮点击')
  if (!ensureCanSend()) return

  try {
    let dataUrl: string

    // Electron 环境：使用 captureScreen API
    if (window.electronAPI?.captureScreen) {
      console.log('[截图] 使用 Electron captureScreen')
      const result = await window.electronAPI.captureScreen()
      if (!result) {
        message.warning(t('chat.screenshotFail'))
        return
      }
      dataUrl = result.dataURL
      console.log('[截图] Electron 截图成功, length:', dataUrl.length)
    } else if (navigator.mediaDevices?.getDisplayMedia) {
      // 浏览器环境：使用 getDisplayMedia
      console.log('[截图] 使用浏览器 getDisplayMedia')
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
      console.log('[截图] 浏览器截图成功, length:', dataUrl.length)
    } else {
      message.warning(t('chat.screenshotUnsupported'))
      return
    }

    if (dataUrl.length > MAX_IMAGE_BYTES * 1.4) {
      message.warning(t('chat.screenshotTooLarge'))
      return
    }

    await sendMessage(dataUrl, { type: 'image', isImage: true })
    message.success(t('chat.screenshotSent'))
    emit('scrollToBottom')
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
      await sendMessage('', { type: 'image', isImage: true, rawFile: file })
      message.success(t('chat.imageSent'))
      emit('scrollToBottom')
    } catch {
      message.error(t('chat.imageSendFail'))
    }
  } else {
    const fileUrl = URL.createObjectURL(file)
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
      emit('scrollToBottom')
    } catch {
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

// —— 语音录制 ——
const isRecordingVoice = ref(false)
const voiceRecordSeconds = ref(0)
const voiceSending = ref(false)
let mediaRecorder: MediaRecorder | null = null
let mediaStream: MediaStream | null = null
let voiceChunks: BlobPart[] = []
let voiceStartedAt = 0
let voiceTickTimer: number | null = null
let voiceMaxTimer: number | null = null

function clearVoiceTimers() {
  if (voiceTickTimer != null) {
    window.clearInterval(voiceTickTimer)
    voiceTickTimer = null
  }
  if (voiceMaxTimer != null) {
    window.clearTimeout(voiceMaxTimer)
    voiceMaxTimer = null
  }
}

function stopMediaTracks() {
  mediaStream?.getTracks().forEach(track => track.stop())
  mediaStream = null
}

function resetVoiceRecorderState() {
  clearVoiceTimers()
  stopMediaTracks()
  mediaRecorder = null
  voiceChunks = []
  isRecordingVoice.value = false
  voiceRecordSeconds.value = 0
  voiceStartedAt = 0
}

/**
 * 结束录音并可选发送。cancel=true 时丢弃结果。
 */
async function finishVoiceRecord(cancel: boolean) {
  const recorder = mediaRecorder
  if (!recorder || recorder.state === 'inactive') {
    resetVoiceRecorderState()
    return
  }

  const mimeType = recorder.mimeType || pickVoiceMimeType() || 'audio/webm'
  const startedAt = voiceStartedAt

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

  const durationSec = Math.round((Date.now() - startedAt) / 1000)
  resetVoiceRecorderState()

  if (cancel || !blob || blob.size === 0) {
    if (!cancel) message.warning(t('chat.voiceRecordEmpty'))
    return
  }
  if (!isVoiceDurationValid(durationSec)) {
    message.warning(t('chat.voiceTooShort'))
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
    emit('scrollToBottom')
  } catch {
    URL.revokeObjectURL(voiceUrl)
    message.error(t('chat.voiceSendFail'))
  } finally {
    voiceSending.value = false
  }
}

/**
 * 开始麦克风录音；再次点击结束并发送，超时自动发送。
 */
async function startVoiceRecord() {
  if (!ensureCanSend() || voiceSending.value) return
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
    voiceChunks = []
    voiceStartedAt = Date.now()
    voiceRecordSeconds.value = 0
    isRecordingVoice.value = true

    mediaRecorder.ondataavailable = (ev) => {
      if (ev.data && ev.data.size > 0) voiceChunks.push(ev.data)
    }
    mediaRecorder.start(200)

    voiceTickTimer = window.setInterval(() => {
      voiceRecordSeconds.value = Math.min(
        VOICE_MAX_SECONDS,
        Math.floor((Date.now() - voiceStartedAt) / 1000)
      )
    }, 200)

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
  if (voiceSending.value) return
  if (isRecordingVoice.value) {
    await finishVoiceRecord(false)
    return
  }
  await startVoiceRecord()
}

watch(currentSessionId, () => {
  if (isRecordingVoice.value) {
    void finishVoiceRecord(true)
  }
})

onUnmounted(() => {
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  if (isRecordingVoice.value && mediaRecorder && mediaRecorder.state !== 'inactive') {
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

/**
 * 发送文本或 /img 命令图片。
 * 清空输入并取消回复引用。
 */
function send() {
  if (!inputValue.value.trim()) return
  if (!ensureCanSend()) return

  void (async () => {
    try {
      if (inputValue.value.startsWith('/img ')) {
        const url = inputValue.value.replace('/img ', '').trim()
        await sendMessage(url, { type: 'image', isImage: true })
      } else {
        await sendMessage(inputValue.value, { type: 'text', replyTo: props.replyingTo })
      }
      inputValue.value = ''
      showMentionPicker.value = false
      emit('update:replyingTo', undefined)
      emit('scrollToBottom')
      const sid = currentSessionId.value
      if (sid) void appStore.clearSessionDraft(sid)
    } catch {
      message.error(t('chat.messageSendFail'))
    }
  })()
}

/** Enter 发送，Shift+Enter 保留默认换行；@ 面板打开时 Enter 选中成员 */
function onEnter(e: KeyboardEvent) {
  if (showMentionPicker.value) {
    if (e.shiftKey) return
    e.preventDefault()
    if (mentionCandidates.value.length) {
      const pick = mentionPickerRef.value?.confirm()
      if (pick) applyMention(pick.id, pick.name)
    }
    return
  }
  if (!e.shiftKey) {
    e.preventDefault()
    send()
  }
}

/** 输入框按键：上下选择 @ 候选、Esc 关闭 */
function onInputKeyDown(e: KeyboardEvent) {
  if (!showMentionPicker.value) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    mentionPickerRef.value?.move(1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    mentionPickerRef.value?.move(-1)
  } else if (e.key === 'Tab') {
    e.preventDefault()
    const pick = mentionPickerRef.value?.confirm()
    if (pick) applyMention(pick.id, pick.name)
  } else if (e.key === 'Escape') {
    e.preventDefault()
    showMentionPicker.value = false
  }
}

/** 取消当前回复引用 */
function cancelReply() {
  emit('update:replyingTo', undefined)
}

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
        <div v-if="replyingTo" class="reply-preview">
          <div class="reply-content">
            {{
              t('chat.replyPreview', {
                name: replyingTo.senderName || '',
                content: replyingTo.content
              })
            }}
          </div>
          <n-icon :component="CloseOutline" class="reply-close" @click="cancelReply" />
        </div>

        <div class="input-compose-body">
          <n-input
            ref="messageInputRef"
            :value="inputValue"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            :placeholder="inputPlaceholder"
            :disabled="inputDisabled"
            class="message-input"
            :bordered="false"
            @update:value="onInputUpdate"
            @keydown="onInputKeyDown"
            @keydown.enter="onEnter"
            @paste="onPaste"
          />
          <AtMentionPicker
            v-if="isGroupChat && showMentionPicker"
            ref="mentionPickerRef"
            placement="top"
            :friends="mentionCandidates"
            :title="t('extra.selectMember')"
            :empty-text="t('extra.noMembersToAt')"
            @apply="(p) => applyMention(p.id, p.name)"
            @close="showMentionPicker = false"
          />
        </div>
      </div>

      <!-- 工具栏：表情、应用、文件、截图、红包、语音、通话、发送 -->
      <div class="input-toolbar">
        <div class="toolbar-left">
          <n-popover v-model:show="showEmoji" trigger="click" placement="top-start">
            <template #trigger>
              <button type="button" class="tool-btn" :title="t('chat.emoji')">
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
          <button
            v-if="isGroupChat"
            type="button"
            class="tool-btn"
            :title="t('extra.atMember')"
            :disabled="inputDisabled"
            @click="triggerAtMention"
          >
            <n-icon :component="AtOutline" :size="20" />
          </button>
          <button type="button" class="tool-btn" :title="t('chat.sendFile')" @click="toolFile">
            <n-icon :component="FolderOutline" :size="20" />
          </button>
          <button type="button" class="tool-btn" :title="t('chat.screenshot')" @click="toolScreenshot">
            <n-icon :component="CutOutline" :size="20" />
          </button>
          <button
            v-if="!isMyPhone"
            type="button"
            class="tool-btn"
            :title="t('chat.redPacket')"
            @click="toolRedPacket"
          >
            <n-icon :component="GiftOutline" :size="20" />
          </button>
          <button
            type="button"
            class="tool-btn"
            :class="{ 'tool-btn--recording': isRecordingVoice }"
            :title="
              isRecordingVoice
                ? t('chat.voiceRecording', { n: voiceRecordSeconds })
                : voiceSending
                  ? t('chat.voiceSending')
                  : t('chat.voice')
            "
            :disabled="inputDisabled || voiceSending"
            @click="toggleVoiceRecord"
          >
            <n-icon :component="MicOutline" :size="20" />
          </button>
        </div>

        <div class="toolbar-right">
          <button type="button" class="tool-btn" :title="t('chat.voiceCall')" @click="startVoiceCall">
            <n-icon :component="VolumeHighOutline" :size="20" />
          </button>
          <button
            type="button"
            class="send-btn"
            :disabled="!inputValue.trim()"
            @click="send"
          >
            {{ t('chat.send') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
  overflow: visible;
  min-height: 148px;
}

.input-compose {
  flex: 1;
  min-height: 0;
  padding: 10px 14px 4px;
}

.input-compose-body {
  position: relative;
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

.tool-btn:hover:not(:disabled) {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.tool-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
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

.message-input :deep(.n-input__textarea-el),
.message-input :deep(.n-input__placeholder),
.message-input :deep(.n-input__textarea-mirror) {
  min-height: 72px !important;
  background: transparent !important;
  font-size: 14px;
  line-height: 1.55;
  /* 与 textarea 同步去掉 Naive 默认上下 padding，避免光标与占位文字错位 */
  padding: 0 !important;
  color: var(--lx-text);
  resize: none;
}

.message-input :deep(.n-input__placeholder) {
  color: var(--n-placeholder-color);
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

.hidden-file-input {
  display: none;
}

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

</style>
