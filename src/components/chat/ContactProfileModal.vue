<script setup lang="ts">
import { computed } from 'vue'
import { NModal, NButton, NIcon, NDivider, useMessage } from 'naive-ui'
import { 
  ChatbubbleEllipsesOutline, 
  CallOutline, 
  VideocamOutline, 
  EllipsisHorizontalOutline 
} from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { useChatModals } from '../../composables/useChatModals'
import { useAppState } from '../../composables/useAppState'

const { contactProfileOpen, closeContactProfile, currentContactProfile } = useChatModals()
const { ensureSession } = useAppState()
const message = useMessage()

const contact = computed(() => currentContactProfile.value || {})

function handleSendMessage() {
  // Convert contact to session format
  const session = {
    id: contact.value.id,
    name: contact.value.name,
    avatarText: contact.value.avatarText || contact.value.name?.charAt(0) || '?',
    avatarColor: contact.value.avatarColor || '#12b7f5',
    lastMessage: '',
    time: '',
    isGroup: false,
    online: contact.value.online
  }
  ensureSession(session)
  closeContactProfile()
}

function handleVoiceCall() {
  message.info('发起语音通话 (演示)')
  closeContactProfile()
}

function handleVideoCall() {
  message.info('发起视频通话 (演示)')
  closeContactProfile()
}

function handleMore() {
  message.info('更多操作 (演示)')
}
</script>

<template>
  <n-modal
    v-model:show="contactProfileOpen"
    class="contact-profile-modal"
    preset="card"
    :bordered="false"
    :show-icon="false"
    style="width: 320px; border-radius: 12px; padding: 0; overflow: hidden;"
  >
    <div class="profile-header">
      <Avatar 
        :text="contact.avatarText || (contact.name ? contact.name.charAt(0) : '?')" 
        :color="contact.avatarColor || '#12b7f5'" 
        :size="72" 
      />
      <div class="profile-basic">
        <div class="profile-name">
          {{ contact.name }}
          <span v-if="contact.online" class="online-dot" title="在线"></span>
        </div>
        <div class="profile-id">LinkX ID: {{ contact.id || '未知' }}</div>
      </div>
    </div>

    <div class="profile-body">
      <div class="info-row">
        <span class="info-label">地区</span>
        <span class="info-value">中国 广东 深圳</span>
      </div>
      <div class="info-row">
        <span class="info-label">个性签名</span>
        <span class="info-value">{{ contact.signature || '这个人很懒，什么都没写' }}</span>
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
        <n-button class="icon-btn" tertiary circle @click="handleMore" title="更多">
          <template #icon>
            <n-icon :component="EllipsisHorizontalOutline" />
          </template>
        </n-button>
      </div>
    </div>
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
  background: #fff;
}

.profile-basic {
  margin-top: 16px;
}

.profile-name {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #52c41a;
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.2);
}

.profile-id {
  font-size: 13px;
  color: #8e8e93;
  margin-top: 4px;
}

.profile-body {
  padding: 0 24px 24px;
  background: #fff;
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
  color: #8e8e93;
  flex-shrink: 0;
}

.info-value {
  color: #1a1a1a;
  flex: 1;
}

.profile-actions {
  padding: 24px;
  background: #f7f7f7;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-btn {
  width: 100%;
  height: 40px;
  border-radius: 9px;
  font-size: 15px;
  background: #12b7f5 !important;
}

.secondary-actions {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.icon-btn {
  width: 40px;
  height: 40px;
  font-size: 20px;
}
</style>
