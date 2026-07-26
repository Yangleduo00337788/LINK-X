<script setup lang="ts">
/**
 * 联系人资料卡浮层。
 * <p>
 * 在聊天中点击头像时展示昵称、LinkX ID、友链缩略图，非本人时可发起会话。
 * 本人资料卡支持点击头像直接更换、右侧「编辑资料」打开弹窗。
 * </p>
 */
import { computed, ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import {
  ChatbubbleEllipsesOutline,
  ChevronForwardOutline,
  CameraOutline,
  NotificationsOutline
} from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useContactsStore } from '../../stores/contacts'
import { useAppSettingsStore } from '../../stores/appSettings'
import { useSettingsStore } from '../../stores/settings'
import { useMomentsStore } from '../../stores/moments'
import { useMessage } from 'naive-ui'
import type { ContactItem } from '../../types'
import * as userApi from '../../api/user'
import type { UserProfileData } from '../../api/user'
import { generatePlaceholderImage } from '../../utils/defaultAvatar'
import { useI18n } from '../../i18n'

const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const appSettingsStore = useAppSettingsStore()
const settingsStore = useSettingsStore()
const momentsStore = useMomentsStore()
const message = useMessage()

const { contactProfileOpen, currentContactProfile, profileCardPos, profileCardIsSelf } = storeToRefs(chatModalsStore)
const { closeContactProfile, openEditProfile } = chatModalsStore
const { userProfile, savedLogin, isOffline } = storeToRefs(appStore)
const { onlineFriends } = storeToRefs(contactsStore)
const { notifyFriendOnline } = storeToRefs(appSettingsStore)
const { startChatWithContact, updateAvatar } = appStore
const { posts } = storeToRefs(momentsStore)

const avatarInputRef = ref<HTMLInputElement | null>(null)
const uploadingAvatar = ref(false)
const remoteProfile = ref<UserProfileData | null>(null)
const loadingRemoteProfile = ref(false)

const contact = computed<ContactItem | null>(() => currentContactProfile.value)
const selfOnline = computed(() => !isOffline.value)
const onlineFriendsTitle = computed(() =>
  t('presence.onlineFriendsTitle', { count: onlineFriends.value.length })
)

/** 从联系人 id 或 userId 解析后端用户 ID */
function resolveContactUserId(item: ContactItem): string | null {
  if (item.userId) return String(item.userId)
  const raw = item.id.replace(/^f-/, '')
  if (/^\d+$/.test(raw)) return raw
  return null
}

/** 打开他人资料卡时拉取后端公开资料 */
watch(
  () => [contactProfileOpen.value, profileCardIsSelf.value, contact.value?.id] as const,
  async ([open, isSelf, contactId]) => {
    remoteProfile.value = null
    if (!open || isSelf || !contactId || !contact.value) return

    const userId = resolveContactUserId(contact.value)
    if (!userId) return

    loadingRemoteProfile.value = true
    try {
      const res = await userApi.getUserProfile(userId)
      if (res.code === 200 && res.data) {
        remoteProfile.value = res.data
      }
    } catch {
      // API 失败时回退到本地联系人数据
    } finally {
      loadingRemoteProfile.value = false
    }
  }
)

const displayName = computed(() => {
  if (!contact.value) return ''
  if (profileCardIsSelf.value) return userProfile.value.nickname || contact.value.name
  return remoteProfile.value?.nickname || contact.value.name
})

const displayAvatarUrl = computed(() => {
  if (!contact.value) return undefined
  if (profileCardIsSelf.value) return userProfile.value.avatar || contact.value.avatarUrl
  return remoteProfile.value?.avatar || contact.value.avatarUrl
})

const displayAvatarText = computed(() => {
  if (!contact.value) return ''
  return contact.value.avatarText || displayName.value.charAt(0) || '?'
})

const displayAvatarColor = computed(() => {
  if (displayAvatarUrl.value) return 'transparent'
  return contact.value?.avatarColor || 'var(--lx-accent)'
})

/** 本人资料卡展示时同步最新头像与昵称 */
watch(
  () => [userProfile.value.avatar, userProfile.value.nickname] as const,
  ([avatar, nickname]) => {
    if (!profileCardIsSelf.value || !contact.value) return
    contact.value.avatarUrl = avatar || undefined
    contact.value.avatarColor = avatar ? 'transparent' : 'var(--lx-success)'
    if (nickname) contact.value.name = nickname
  }
)

/** 展示用 LinkX ID：本人取登录名，好友优先取后端 username */
const displayId = computed(() => {
  if (!contact.value) return ''
  if (profileCardIsSelf.value) {
    return savedLogin.value.username || userProfile.value.username || userProfile.value.nickname || '—'
  }
  if (remoteProfile.value?.username) return remoteProfile.value.username
  const id = contact.value.id.replace(/^f-/, '')
  return /^\d+$/.test(id) ? id : `linkx_${id}`
})

/** 友链缩略图：优先取该用户动态图片，不足 4 张用默认渐变图补齐 */
const momentPreviews = computed(() => {
  if (!contact.value) return [] as string[]
  const images: string[] = []
  for (const post of posts.value) {
    if (post.user !== contact.value.name) continue
    if (post.images?.length) images.push(...post.images)
    if (images.length >= 4) break
  }
  if (images.length) return images.slice(0, 4)
  const name = contact.value!.name
  return Array.from({ length: 4 }, (_, i) =>
    generatePlaceholderImage(`${name}-${i}`, 120)
  )
})

/** 从资料卡发起与该联系人的聊天 */
async function handleSendMessage() {
  if (!contact.value) return
  try {
    await startChatWithContact(contact.value)
    closeContactProfile()
  } catch (error) {
    message.error((error as Error).message || t('modals.openSessionFail'))
  }
}

/** 点击头像：本人直接选择图片上传 */
function handleAvatarClick() {
  if (!contact.value || !profileCardIsSelf.value || uploadingAvatar.value) return
  avatarInputRef.value?.click()
}

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !contact.value) return

  if (!file.type.startsWith('image/')) {
    message.error(t('modals.pickImage'))
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.error(t('modals.imageTooLarge'))
    return
  }

  uploadingAvatar.value = true
  try {
    const avatarUrl = await updateAvatar(file)
    contact.value.avatarUrl = avatarUrl
    contact.value.avatarColor = 'transparent'
    message.success(t('modals.avatarUpdated'))
  } catch (error) {
    message.error(t('modals.uploadFail', { message: (error as Error).message }))
  } finally {
    uploadingAvatar.value = false
  }
}

function handleEditProfile() {
  openEditProfile()
}

async function chatWithOnlineFriend(friend: ContactItem) {
  try {
    await startChatWithContact(friend)
    closeContactProfile()
  } catch (error) {
    message.error((error as Error).message || t('modals.openSessionFail'))
  }
}

function goNotifySettings() {
  closeContactProfile()
  settingsStore.openSettings('notifications')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="contactProfileOpen && contact"
      class="profile-overlay"
      @click.self="closeContactProfile"
    >
      <div
        class="profile-card"
        :style="{ left: `${profileCardPos.x}px`, top: `${profileCardPos.y}px` }"
        @click.stop
      >
        <section class="card-head">
          <div
            class="avatar-clickable"
            :class="{ uploading: uploadingAvatar }"
            @click="handleAvatarClick"
            :title="profileCardIsSelf ? t('modals.changeAvatar') : ''"
          >
            <Avatar
              :text="displayAvatarText"
              :color="displayAvatarColor"
              :size="64"
              :image-url="displayAvatarUrl || undefined"
            />
            <div v-if="profileCardIsSelf" class="avatar-edit-hint">
              <n-icon :component="CameraOutline" :size="16" />
            </div>
          </div>
          <div class="head-meta">
            <div class="profile-name">{{ displayName }}</div>
            <div class="profile-id">
              {{ t('modals.linkxId', { id: displayId }) }}
              <span v-if="loadingRemoteProfile" class="profile-loading">{{ t('common.loading') }}</span>
            </div>
            <div v-if="profileCardIsSelf" class="self-status">
              <span class="status-dot" :class="{ on: selfOnline }" />
              <span>{{ selfOnline ? t('chat.online') : t('chat.offline') }}</span>
            </div>
          </div>
          <button
            v-if="profileCardIsSelf"
            type="button"
            class="edit-profile-btn"
            @click="handleEditProfile"
          >
            {{ t('modals.editProfile') }}
          </button>
        </section>

        <section v-if="profileCardIsSelf" class="online-section">
          <div class="online-section-head">
            <span class="online-section-title">{{ onlineFriendsTitle }}</span>
            <span class="online-section-hint">{{ t('presence.onlineFriendsHint') }}</span>
          </div>
          <button type="button" class="notify-hint" @click="goNotifySettings">
            <n-icon :component="NotificationsOutline" :size="13" />
            <span>
              {{
                notifyFriendOnline ? t('presence.notifyEnabled') : t('presence.notifyDisabled')
              }}
            </span>
            <span class="link">{{ t('presence.goSettings') }}</span>
          </button>
          <div class="online-list">
            <button
              v-for="f in onlineFriends"
              :key="f.id"
              type="button"
              class="online-row"
              @click="chatWithOnlineFriend(f)"
            >
              <Avatar
                :text="f.avatarText"
                :color="f.avatarColor"
                :size="36"
                :image-url="f.avatarUrl"
              />
              <div class="online-row-info">
                <div class="online-row-name">{{ f.name }}</div>
                <div class="online-row-status">
                  <span class="status-dot on" />
                  <span>{{ t('chat.online') }}</span>
                </div>
              </div>
              <n-icon
                :component="ChatbubbleEllipsesOutline"
                :size="16"
                class="online-row-chat"
                :title="t('presence.sendMessage')"
              />
            </button>
            <div v-if="onlineFriends.length === 0" class="online-empty">
              {{ t('presence.noOnlineFriends') }}
            </div>
          </div>
        </section>

        <section v-if="!profileCardIsSelf" class="moments-row">
          <span class="moments-label">{{ t('modals.moments') }}</span>
          <div class="moments-thumbs">
            <img
              v-for="(img, i) in momentPreviews"
              :key="i"
              :src="img"
              alt=""
              class="thumb"
              referrerpolicy="no-referrer"
            />
          </div>
          <n-icon :component="ChevronForwardOutline" :size="16" class="moments-arrow" />
        </section>

        <button v-if="!profileCardIsSelf" type="button" class="send-btn" @click="handleSendMessage">
          <n-icon :component="ChatbubbleEllipsesOutline" :size="18" />
          <span>{{ t('modals.sendMessage') }}</span>
        </button>

        <input
          v-if="profileCardIsSelf"
          ref="avatarInputRef"
          type="file"
          accept="image/*"
          hidden
          @change="handleAvatarChange"
        />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.profile-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: transparent;
}

.profile-card {
  position: fixed;
  width: 320px;
  max-height: min(520px, 80vh);
  background: var(--lx-bg-card);
  border-radius: 14px;
  box-shadow: var(--lx-shadow-modal);
  border: 1px solid var(--lx-border-light);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  animation: card-in 0.18s ease-out;
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(-6px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.card-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 20px 18px 16px;
}

.head-meta {
  min-width: 0;
  flex: 1;
  padding-top: 4px;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--lx-text-body);
  line-height: 1.3;
}

.profile-id {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.profile-loading {
  margin-left: 6px;
  font-size: 11px;
  color: var(--lx-text-muted);
}

.edit-profile-btn {
  flex-shrink: 0;
  border: 1px solid var(--lx-border);
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  margin-top: 6px;
}

.edit-profile-btn:hover {
  background: var(--lx-bg-panel);
  border-color: var(--lx-accent);
  color: var(--lx-accent);
}

.moments-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border-top: 1px solid var(--lx-border-light);
  border-bottom: 1px solid var(--lx-border-light);
  cursor: default;
}

.moments-label {
  font-size: 14px;
  color: var(--lx-text-body);
  flex-shrink: 0;
}

.moments-thumbs {
  flex: 1;
  display: flex;
  gap: 4px;
  justify-content: flex-end;
  min-width: 0;
}

.thumb {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  object-fit: cover;
  background: var(--lx-bg-panel-deep);
  flex-shrink: 0;
}

.moments-arrow {
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.send-btn {
  width: 100%;
  border: none;
  background: var(--lx-bg-card);
  color: var(--lx-accent);
  font-size: 15px;
  font-weight: 500;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.send-btn:hover {
  background: var(--lx-accent-soft);
}

.avatar-clickable {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar-clickable.uploading {
  opacity: 0.7;
  pointer-events: none;
}

.avatar-clickable:hover .avatar-edit-hint {
  opacity: 1;
}

.avatar-edit-hint {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  opacity: 0;
  transition: opacity 0.2s;
  border-radius: 50%;
}

.self-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--lx-border-strong, #c0c4cc);
  flex-shrink: 0;
}

.status-dot.on {
  background: var(--lx-success, #52c41a);
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.18);
}

.online-section {
  border-top: 1px solid var(--lx-border-light);
  padding: 12px 0 8px;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.online-section-head {
  padding: 0 18px 6px;
}

.online-section-title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.online-section-hint {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--lx-text-muted);
}

.notify-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0 18px 8px;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: var(--lx-text-secondary);
  cursor: pointer;
  text-align: left;
}

.notify-hint .link {
  color: var(--lx-accent);
}

.notify-hint:hover .link {
  text-decoration: underline;
}

.online-list {
  max-height: 240px;
  overflow-y: auto;
}

.online-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 18px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.online-row:hover {
  background: var(--lx-bg-hover);
}

.online-row-info {
  flex: 1;
  min-width: 0;
}

.online-row-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.online-row-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
  font-size: 11px;
  color: var(--lx-text-secondary);
}

.online-row-chat {
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.online-row:hover .online-row-chat {
  color: var(--lx-accent);
}

.online-empty {
  padding: 16px 18px 12px;
  font-size: 12px;
  color: var(--lx-text-muted);
  text-align: center;
}
</style>
