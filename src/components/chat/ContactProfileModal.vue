<script setup lang="ts">
import { computed } from 'vue'
import { NModal, NButton, NIcon, NDivider, NPopover, useMessage } from 'naive-ui'
import {
  ChatbubbleEllipsesOutline,
  CallOutline,
  VideocamOutline,
  EllipsisHorizontalOutline,
  ShareOutline,
  BanOutline,
  PersonRemoveOutline
} from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useContactsStore } from '../../stores/contacts'
import type { ContactItem } from '../../types'

const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const { contactProfileOpen, currentContactProfile } = storeToRefs(chatModalsStore)
const { closeContactProfile, openVoiceCall, openVideoCall } = chatModalsStore
const { startChatWithContact, deleteSession, toggleSessionBlock } = appStore
const { remove: removeContact } = contactsStore
const message = useMessage()

const contact = computed<ContactItem | null>(() => currentContactProfile.value)

function handleSendMessage() {
  if (!contact.value) return
  startChatWithContact(contact.value)
  closeContactProfile()
}

function handleVoiceCall() {
  openVoiceCall()
  closeContactProfile()
}

function handleVideoCall() {
  openVideoCall()
  closeContactProfile()
}

function shareCard() {
  if (!contact.value) return
  navigator.clipboard.writeText(`LinkX 名片：${contact.value.name} (${contact.value.id})`)
  message.success('名片链接已复制')
}

function blockContact() {
  if (!contact.value) return
  const session = appStore.sessions.find(
    s => !s.isGroup && (s.id === contact.value!.id || s.name === contact.value!.name)
  )
  if (session) toggleSessionBlock(session.id)
  message.success(`已屏蔽「${contact.value.name}」`)
  closeContactProfile()
}

function deleteFriend() {
  if (!contact.value) return
  const c = contact.value
  const session = appStore.sessions.find(s => !s.isGroup && (s.id === c.id || s.name === c.name))
  if (session) deleteSession(session.id)
  removeContact(c.id)
  message.success(`已删除好友「${c.name}」`)
  closeContactProfile()
}
</script>

<template>
  <n-modal
    v-model:show="contactProfileOpen"
    class="contact-profile-modal"
    preset="card"
    :bordered="false"
    :show-icon="false"
    style="width: 320px; border-radius: var(--lx-radius); padding: 0; overflow: hidden;"
  >
    <template v-if="contact">
    <div class="profile-header">
      <Avatar
        :text="contact.avatarText || contact.name.charAt(0)"
        :color="contact.avatarColor || 'var(--lx-accent)'"
        :size="72"
      />
      <div class="profile-basic">
        <div class="profile-name">
          {{ contact.name }}
          <span v-if="contact.online" class="online-dot" title="在线"></span>
        </div>
        <div class="profile-id">LinkX ID: {{ contact.id }}</div>
      </div>
    </div>

    <div class="profile-body">
      <div class="info-row">
        <span class="info-label">地区</span>
        <span class="info-value">中国 广东 深圳</span>
      </div>
      <div class="info-row">
        <span class="info-label">个性签名</span>
        <span class="info-value">这个人很懒，什么都没写</span>
      </div>
    </div>

    <n-divider style="margin: 0;" />

    <div class="profile-actions">
      <n-button class="action-btn" type="primary" @click="handleSendMessage">
        <template #icon>
          <n-icon :component="ChatbubbleEllipsesOutline" />
        </template>
        发消息
      </n-button>
      <div class="secondary-actions">
        <n-button class="icon-btn" tertiary circle @click="handleVoiceCall" title="语音通话">
          <template #icon>
            <n-icon :component="CallOutline" />
          </template>
        </n-button>
        <n-button class="icon-btn" tertiary circle @click="handleVideoCall" title="视频通话">
          <template #icon>
            <n-icon :component="VideocamOutline" />
          </template>
        </n-button>
        <n-popover trigger="click" placement="bottom">
          <template #trigger>
            <n-button class="icon-btn" tertiary circle title="更多">
              <template #icon>
                <n-icon :component="EllipsisHorizontalOutline" />
              </template>
            </n-button>
          </template>
          <div class="more-menu">
            <button type="button" class="more-item" @click="shareCard">
              <n-icon :component="ShareOutline" /> 分享名片
            </button>
            <button type="button" class="more-item" @click="blockContact">
              <n-icon :component="BanOutline" /> 屏蔽此人
            </button>
            <button type="button" class="more-item danger" @click="deleteFriend">
              <n-icon :component="PersonRemoveOutline" /> 删除好友
            </button>
          </div>
        </n-popover>
      </div>
    </div>
    </template>
  </n-modal>
</template>

<style scoped>
.contact-profile-modal :deep(.n-card-header) {
  display: none !important;
}

.profile-header {
  padding: 32px 24px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  background: var(--lx-bg-card);
}

.profile-basic {
  margin-top: 16px;
}

.profile-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--lx-text-body);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--lx-success);
}

.profile-id {
  font-size: 13px;
  color: var(--lx-text-muted);
  margin-top: 4px;
}

.profile-body {
  padding: 0 24px 24px;
  background: var(--lx-bg-card);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-row {
  display: flex;
  align-items: flex-start;
  font-size: 14px;
  line-height: 1.5;
}

.info-label {
  width: 72px;
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.info-value {
  color: var(--lx-text-body);
  flex: 1;
}

.profile-actions {
  padding: 24px;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-btn {
  width: 100%;
  height: 40px;
}

.secondary-actions {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.more-menu {
  display: flex;
  flex-direction: column;
  min-width: 140px;
}

.more-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--lx-text-body);
  border-radius: var(--lx-radius);
}

.more-item:hover {
  background: var(--lx-bg-hover);
}

.more-item.danger {
  color: #e34d59;
}
</style>
