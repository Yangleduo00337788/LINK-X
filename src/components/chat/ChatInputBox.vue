<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { NIcon, NInput, NPopover, useMessage } from 'naive-ui'
import {
  FolderOutline,
  HappyOutline,
  CutOutline,
  VolumeHighOutline,
  GiftOutline,
  MicOutline,
  GridOutline,
  CloseOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../stores/app'
import { useChatModalsStore } from '../../stores/chatModals'
import { useSecondaryViewStore } from '../../stores/secondaryView'
import { useFilesStore } from '../../stores/files'
import { useGroupMetaStore } from '../../stores/groupMeta'
import type { ChatMessage, AppItem } from '../../types'
import { CHAT_EMOJIS } from '../../constants/emojis'
import { apps as chatApps } from '../../data/mockData'
import { formatFileSize, readFileAsDataUrl, MAX_IMAGE_BYTES } from '../../utils/file'

const props = defineProps<{
  isMyPhone: boolean
  isFriendChat: boolean
  isGroupChat: boolean
  replyingTo?: ChatMessage
}>()

const emit = defineEmits<{
  (e: 'update:replyingTo', val?: ChatMessage): void
  (e: 'scrollToBottom'): void
}>()

const message = useMessage()
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const secondaryViewStore = useSecondaryViewStore()
const filesStore = useFilesStore()
const groupMetaStore = useGroupMetaStore()

const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)
const { sendMessage, setNav } = appStore
const { activeApp } = storeToRefs(secondaryViewStore)
const { openVoiceCall, openRedPacket } = chatModalsStore

const inputValue = ref('')
const showEmoji = ref(false)
const showApps = ref(false)
const isRecording = ref(false)

const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const emojis = [...CHAT_EMOJIS]

let recordStart = 0
let mediaRecorder: MediaRecorder | null = null
let recordStream: MediaStream | null = null
let voiceChunks: BlobPart[] = []

onUnmounted(() => {
  recordStream?.getTracks().forEach(t => t.stop())
})

function ensureCanSend(): boolean {
  if (currentSession.value?.blocked) {
    message.warning('你已屏蔽该联系人，无法发送消息')
    return false
  }
  return true
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
    emit('scrollToBottom')
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
  if (props.isMyPhone) return
  openRedPacket()
}

async function onImagePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  handleFileSend(file)
}

function onFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  handleFileSend(file)
}

async function handleFileSend(file: File) {
  if (!ensureCanSend()) return

  if (file.type.startsWith('image/')) {
    if (file.size > MAX_IMAGE_BYTES) {
      message.warning(`图片不能超过 ${formatFileSize(MAX_IMAGE_BYTES)}`)
      return
    }
    try {
      const dataUrl = await readFileAsDataUrl(file)
      sendMessage(dataUrl, { type: 'image', isImage: true })
      message.success('图片已发送')
      emit('scrollToBottom')
    } catch {
      message.error('图片读取失败')
    }
  } else {
    const fileUrl = URL.createObjectURL(file)
    sendMessage(file.name, {
      type: 'file',
      fileName: file.name,
      fileSize: formatFileSize(file.size),
      fileUrl
    })
    filesStore.addFromChat(file.name, formatFileSize(file.size), '我', fileUrl)
    if (props.isGroupChat && currentSessionId.value) {
      groupMetaStore.addFile(currentSessionId.value, {
        name: file.name,
        size: formatFileSize(file.size),
        user: userProfile.value?.nickname || '我',
        fileUrl
      })
    }
    message.success('文件已发送')
    emit('scrollToBottom')
  }
}

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
      emit('scrollToBottom')
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
        emit('scrollToBottom')
        resolve()
      }
      recorder.stop()
    })
  } else {
    sendMessage('', { type: 'voice', voiceDuration: duration })
    message.success('语音消息已发送')
    emit('scrollToBottom')
  }

  recordStream?.getTracks().forEach(t => t.stop())
  mediaRecorder = null
  recordStream = null
  voiceChunks = []
  isRecording.value = false
}

function pickEmoji(e: string) {
  inputValue.value += e
  showEmoji.value = false
}

function send() {
  if (!inputValue.value.trim()) return
  if (!ensureCanSend()) return

  if (inputValue.value.startsWith('/img ')) {
    const url = inputValue.value.replace('/img ', '').trim()
    sendMessage(url, { type: 'image', isImage: true })
  } else {
    sendMessage(inputValue.value, { type: 'text', replyTo: props.replyingTo })
  }

  inputValue.value = ''
  emit('update:replyingTo', undefined)
  emit('scrollToBottom')
}

function onEnter(e: KeyboardEvent) {
  if (!e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function cancelReply() {
  emit('update:replyingTo', undefined)
}

defineExpose({
  handleFileSend
})
</script>

<template>
  <div
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
          @paste="onPaste"
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
