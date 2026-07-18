<script setup lang="ts">
/**
 * 聊天输入框组件。
 * <p>
 * 提供文本输入、表情、文件/图片发送、截图、语音录制、红包与快捷应用入口。
 * 支持回复预览、粘贴图片/文件、Enter 发送（Shift+Enter 换行）。
 * </p>
 */
// Vue 响应式 API
import { ref } from 'vue'
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
  CloseOutline
} from '@vicons/ionicons5'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 主应用状态：会话、用户信息、发送消息
import { useAppStore } from '../../stores/app'
// 聊天弹窗/抽屉状态：语音通话、红包
import { useChatModalsStore } from '../../stores/chatModals'
// 文件列表 store：聊天发送的文件同步记录
import { useFilesStore } from '../../stores/files'
// 群元数据：群文件列表
import { useGroupMetaStore } from '../../stores/groupMeta'
// 消息类型定义
import type { ChatMessage } from '../../types'
// 聊天表情常量列表
import { CHAT_EMOJIS } from '../../constants/emojis'
// 文件工具：大小格式化、DataURL 读取、图片大小上限
import { formatFileSize, MAX_IMAGE_BYTES } from '../../utils/file'

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
// 各 Pinia store 实例
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const filesStore = useFilesStore()
const groupMetaStore = useGroupMetaStore()

// 从 appStore 解构响应式会话与用户信息
const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)
// 从 appStore 解构方法（非响应式）
const { sendMessage } = appStore
// 打开语音通话、红包弹窗
const { openRedPacket } = chatModalsStore

// 文本输入框绑定值
const inputValue = ref('')
// 表情面板是否展开
const showEmoji = ref(false)

// 隐藏的图片选择 input 引用
const imageInputRef = ref<HTMLInputElement | null>(null)
// 隐藏的文件选择 input 引用
const fileInputRef = ref<HTMLInputElement | null>(null)

// 表情列表副本（供模板 v-for）
const emojis = [...CHAT_EMOJIS]

/**
 * 发送前校验：当前会话是否已屏蔽对方。
 *
 * @returns 允许发送返回 true，否则提示并返回 false
 */
function ensureCanSend(): boolean {
  if (currentSession.value?.blocked) {
    message.warning('你已屏蔽该联系人，无法发送消息')
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
        message.warning('截图失败，请重试')
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
      message.warning('当前环境不支持屏幕截图')
      return
    }

    if (dataUrl.length > MAX_IMAGE_BYTES * 1.4) {
      message.warning('截图过大，请缩小选区后重试')
      return
    }

    await sendMessage(dataUrl, { type: 'image', isImage: true })
    message.success('截图已发送')
    emit('scrollToBottom')
  } catch (e) {
    console.error('[截图] 失败:', e)
    message.info('已取消截图')
  }
}

/** 真实会话暂不支持的功能提示 */
function warnUnsupportedOnRealSession(feature: string): boolean {
  if (currentSession.value?.isReal) {
    message.warning(`真实会话暂不支持${feature}`)
    return true
  }
  return false
}

/** 暂不支持的功能提示 */
function showUnsupported(feature: string) {
  message.info(`${feature}功能正在开发中，敬请期待`)
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
    message.error('无法读取文件内容，请尝试重新选择或直接拖拽文件')
    return
  }

  if (file.type.startsWith('image/')) {
    if (file.size > MAX_IMAGE_BYTES) {
      message.warning(`图片不能超过 ${formatFileSize(MAX_IMAGE_BYTES)}`)
      return
    }
    try {
      await sendMessage('', { type: 'image', isImage: true, rawFile: file })
      message.success('图片已发送')
      emit('scrollToBottom')
    } catch {
      message.error('图片发送失败')
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
      filesStore.addFromChat(file.name, formatFileSize(file.size), '我', fileUrl)
      // 群聊时追加到群文件元数据
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
    } catch {
      message.error('文件发送失败')
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

/**
 * 语音录制（暂不支持，提示用户）
 */
function toggleVoiceRecord() {
  showUnsupported('语音')
}

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
      emit('update:replyingTo', undefined)
      emit('scrollToBottom')
    } catch {
      message.error('消息发送失败')
    }
  })()
}

/** Enter 发送，Shift+Enter 保留默认换行 */
function onEnter(e: KeyboardEvent) {
  if (!e.shiftKey) {
    e.preventDefault()
    send()
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

      <!-- 工具栏：表情、应用、文件、截图、红包、语音、通话、发送 -->
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
          <button type="button" class="tool-btn" title="语音" @click="toggleVoiceRecord">
            <n-icon :component="MicOutline" :size="20" />
          </button>
        </div>

        <div class="toolbar-right">
          <button type="button" class="tool-btn" title="语音通话" @click="showUnsupported('语音通话')">
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

</style>
