<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon, NInput, NButton, NPopover, useMessage } from 'naive-ui'
import {
  ImagesOutline,
  FolderOutline,
  HappyOutline,
  PhonePortraitOutline,
  TimeOutline,
  CallOutline,
  VideocamOutline,
  GridOutline,
  AddOutline,
  EllipsisHorizontalOutline,
  MicOutline,
  GiftOutline,
  DocumentOutline,
  LinkOutline
} from '@vicons/ionicons5'
import Avatar from './Avatar.vue'
import PenguinWatermark from './PenguinWatermark.vue'
import GroupChatSidebar from './chat/GroupChatSidebar.vue'
import { useAppState } from '../composables/useAppState'
import { useOverlay } from '../composables/useOverlay'
import { useChatModals } from '../composables/useChatModals'
import type { ChatMessage } from '../types'

const message = useMessage()
const { currentSession, currentMessages, sendMessage } = useAppState()
const { open: openOverlay } = useOverlay()
const {
  openMore,
  openGroupInfo,
  openVoiceCall,
  openVideoCall,
  openAddMembers,
  openGroupFiles,
  openGroupAlbum,
  openGroupEssence,
  openGroupAnnouncement
} = useChatModals()

const groupGridItems = ['群文件', '群相册', '群精华']

function onGroupAppClick(item: string) {
  if (item === '群文件') openGroupFiles()
  else if (item === '群相册') openGroupAlbum()
  else if (item === '群精华') openGroupEssence()
  else if (item === '群公告') openGroupAnnouncement()
  else demoToast(`${item}（演示）`)
}

const isGroupChat = computed(
  () => hasSession.value && !!currentSession.value?.isGroup && !isMyPhone.value
)

const inputValue = ref('')
const showEmoji = ref(false)

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

const emojis = ['��', '��', '��', '❤️', '🎉', '🔥', '✨', '🙏']

function peerAvatarProps(size = 36) {
  const s = currentSession.value
  return {
    text: s?.avatarText || '?',
    color: s?.avatarColor || '#0099ff',
    size,
    imageUrl: s?.avatarUrl,
    icon: isMyPhone.value ? PhonePortraitOutline : undefined
  }
}

function isLinkMsg(msg: ChatMessage) {
  return msg.type === 'link' || /https?:\/\//.test(msg.content) || msg.content.includes('抖音')
}

function toolFile() {
  sendMessage('[文件] project-spec.pdf · 1.2 MB', 'file')
  message.success('文件已发送（演示）')
}

function pickEmoji(e: string) {
  inputValue.value += e
  showEmoji.value = false
}

function send() {
  if (!inputValue.value.trim()) return
  sendMessage(inputValue.value)
  inputValue.value = ''
}

function onEnter(e: KeyboardEvent) {
  if (!e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function openFileView() {
  openOverlay('file-preview', { fileName: 'Screenshot 2026-07-05-18-48.png' })
}

function demoToast(tip: string) {
  message.info(tip)
}
</script>

<template>
  <div class="chat-panel" :class="{ 'chat-panel--group': isGroupChat }">
    <div class="functional-region">
      <!-- QQ 好友顶栏 -->
      <header v-if="isFriendChat" class="chat-header">
        <div class="chat-header-left">
          <Avatar v-bind="peerAvatarProps(32)" />
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
          <button type="button" class="hdr-btn" title="更多" @click="openMore">
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
          <button type="button" class="hdr-btn" title="语音通话" @click="demoToast('群语音（演示）')">
            <n-icon :component="CallOutline" :size="20" />
          </button>
          <button type="button" class="hdr-btn" title="视频通话" @click="demoToast('群视频（演示）')">
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
          <button type="button" class="hdr-btn" title="更多" @click="openGroupInfo">
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
      <div class="message-area" :class="{ 'message-area--friend': isFriendChat }">
        <template v-if="showPhoneDemo">
          <div class="message-time">18:48</div>
          <div class="message-row left">
            <Avatar v-bind="peerAvatarProps(36)" />
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
            <Avatar v-bind="peerAvatarProps(36)" />
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
                <span class="file-view" @click="openFileView">查看</span>
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
              <Avatar v-if="!msg.isSelf" v-bind="peerAvatarProps(36)" />
              <div class="qq-file-card" :class="{ self: msg.isSelf }">
                <div class="qq-file-main">
                  <div class="qq-file-icon apk">
                    <n-icon :component="DocumentOutline" :size="26" color="#fff" />
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
              <Avatar v-if="msg.isSelf" text="我" color="#52c41a" :size="36" />
            </div>

            <!-- 链接 / 长文本 -->
            <div
              v-else
              class="message-row"
              :class="msg.isSelf ? 'right' : 'left'"
            >
              <Avatar v-if="!msg.isSelf" v-bind="peerAvatarProps(36)" />
              <div
                class="qq-bubble"
                :class="{ self: msg.isSelf, link: isLinkMsg(msg) }"
              >
                <p class="qq-bubble-text">{{ msg.content }}</p>
                <n-icon
                  v-if="isLinkMsg(msg)"
                  class="qq-link-ico"
                  :component="LinkOutline"
                  :size="14"
                />
              </div>
              <Avatar v-if="msg.isSelf" text="我" color="#52c41a" :size="36" />
            </div>
          </template>
        </template>

        <PenguinWatermark v-else :hint="hasSession ? '' : '在左侧选择会话开始聊天'" />
      </div>

      <div
        v-if="hasSession"
        class="input-area"
        :class="{ 'input-area--qq': isFriendChat, 'input-area--group': isGroupChat }"
      >
        <div class="input-toolbar">
          <div class="toolbar-left">
            <n-popover v-model:show="showEmoji" trigger="click" placement="top-start">
              <template #trigger>
                <n-icon :component="HappyOutline" :size="22" class="tool-icon" title="表情" />
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
            <n-icon
              :component="FolderOutline"
              :size="22"
              class="tool-icon"
              title="发送文件"
              @click="toolFile"
            />
            <n-icon
              :component="ImagesOutline"
              :size="22"
              class="tool-icon"
              title="图片"
              @click="demoToast('发送图片（演示）')"
            />
            <n-icon
              :component="MicOutline"
              :size="22"
              class="tool-icon"
              title="语音"
              @click="demoToast('语音（演示）')"
            />
            <n-icon
              :component="GiftOutline"
              :size="22"
              class="tool-icon tool-icon--red"
              title="红包"
              @click="demoToast('发红包（演示）')"
            />
          </div>
          <n-icon
            :component="TimeOutline"
            :size="22"
            class="tool-icon"
            title="聊天记录"
            @click="openOverlay('chat-history')"
          />
        </div>
        <div class="input-compose">
          <n-input
            v-model:value="inputValue"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
            placeholder=""
            class="message-input"
            :bordered="false"
            @keydown.enter="onEnter"
          />
          <div class="send-row">
            <n-button
              type="primary"
              class="send-btn"
              :disabled="!inputValue.trim()"
              @click="send"
            >
              发送
            </n-button>
          </div>
        </div>
      </div>
        </div>
        <GroupChatSidebar v-if="isGroupChat" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel, #f5f5f5);
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
}

.chat-body-row {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: row;
  overflow: hidden;
}

.chat-main-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
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
  background: #fff;
  border-radius: 9px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.grid-menu-item {
  border: none;
  background: transparent;
  text-align: left;
  padding: 10px 14px;
  font-size: 14px;
  color: #333;
  cursor: pointer;
  border-radius: 6px;
  margin: 0;
}

.grid-menu-item:hover {
  background: #f5f5f5;
}

/* 好友顶栏 */
.chat-header {
  flex-shrink: 0;
  height: 52px;
  padding: 0 16px 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f5f5f5;
  border-bottom: 1px solid #e0e0e0;
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
  color: #1a1a1a;
  white-space: nowrap;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #52c41a;
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
  border-radius: 6px;
  color: #555;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hdr-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #333;
}

.session-subheader {
  flex-shrink: 0;
  padding: 10px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.35);
}

.session-subheader-title {
  font-size: 15px;
  font-weight: 500;
  color: #333;
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
  color: #999;
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

/* QQ 气泡 */
.qq-bubble {
  position: relative;
  max-width: min(420px, 72%);
  background: #fff;
  padding: 10px 12px;
  border-radius: 4px 10px 10px 10px;
  font-size: 14px;
  line-height: 1.55;
  color: #1a1a1a;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.qq-bubble.self {
  background: #c9e7ff;
  border-radius: 10px 4px 10px 10px;
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
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
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
  border-radius: 8px;
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
  color: #222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qq-file-size {
  font-size: 12px;
  color: #999;
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
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
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
  background: #0099ff;
  color: #fff;
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
  color: #999;
}

.card-tag {
  font-size: 11px;
  color: #0099ff;
  background: #e6f2ff;
  padding: 2px 8px;
  border-radius: 10px;
}

.card-divider {
  height: 1px;
  background: #f0f0f0;
  margin: 12px 0;
}

.card-body .card-label {
  font-size: 12px;
  color: #999;
}

.card-value {
  font-size: 26px;
  font-weight: 600;
}

.file-card {
  background: #fff;
  border-radius: 10px;
  width: 260px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
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
  border-radius: 8px;
  background: #e6f2ff;
  color: #0099ff;
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
  color: #999;
}

.file-footer {
  display: flex;
  justify-content: space-between;
  padding: 6px 12px;
  background: #4a4a4a;
  color: #fff;
  font-size: 11px;
}

.file-view {
  cursor: pointer;
}

/* 输入区 QQ */
.input-area {
  flex-shrink: 0;
  background: transparent;
  border-top: 1px solid var(--lx-border, #d8d8d8);
}

.input-area--qq {
  background: transparent;
}

.input-area--group {
  background: #fff;
  border-top: 1px solid #e8e8e8;
}

.input-area--group .input-compose {
  background: #fff;
}

.input-area--group .message-input :deep(.n-input__textarea-el) {
  background: #fff !important;
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px 0;
}

.input-area--group .input-toolbar {
  background: #fff;
  padding: 10px 14px 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.tool-spacer {
  display: none;
}

.tool-icon {
  color: #5c5c5c;
  cursor: pointer;
}

.tool-icon:hover {
  color: #222;
}

.input-compose {
  padding: 4px 14px 12px;
}

.message-input {
  flex: 1;
}

.message-input :deep(.n-input-wrapper) {
  padding: 0 !important;
}

.message-input :deep(.n-input) {
  background: transparent !important;
  --n-border: transparent !important;
  --n-border-hover: transparent !important;
  --n-border-focus: transparent !important;
  --n-box-shadow-focus: none !important;
}

.message-input :deep(.n-input-wrapper) {
  background: transparent !important;
  padding: 0 !important;
}

.message-input :deep(.n-input__border),
.message-input :deep(.n-input__state-border) {
  display: none !important;
}

.message-input :deep(.n-input__textarea-el) {
  min-height: 88px !important;
  background: transparent !important;
  font-size: 14px;
  line-height: 1.55;
  padding: 0 2px !important;
  color: #1a1a1a;
}

.tool-icon--red {
  color: #e34d59 !important;
}

.tool-icon--red:hover {
  color: #c93d48 !important;
}

.send-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.send-btn {
  min-width: 72px;
  height: 32px;
  border-radius: 9px;
  background: #12b7f5 !important;
  border: none !important;
  font-size: 13px;
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