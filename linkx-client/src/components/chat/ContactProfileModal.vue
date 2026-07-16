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
import { ChatbubbleEllipsesOutline, ChevronForwardOutline, CameraOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useMomentsStore } from '../../stores/moments'
import { useMessage } from 'naive-ui'
import type { ContactItem } from '../../types'
import * as userApi from '../../api/user'
import type { UserProfileData } from '../../api/user'
import { generatePlaceholderImage } from '../../utils/defaultAvatar'

const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const momentsStore = useMomentsStore()
const message = useMessage()

const { contactProfileOpen, currentContactProfile, profileCardPos, profileCardIsSelf } = storeToRefs(chatModalsStore)
const { closeContactProfile, openEditProfile } = chatModalsStore
const { userProfile, savedLogin } = storeToRefs(appStore)
const { startChatWithContact, updateAvatar } = appStore
const { posts } = storeToRefs(momentsStore)

const avatarInputRef = ref<HTMLInputElement | null>(null)
const uploadingAvatar = ref(false)
const remoteProfile = ref<UserProfileData | null>(null)
const loadingRemoteProfile = ref(false)

const contact = computed<ContactItem | null>(() => currentContactProfile.value)

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
    message.error((error as Error).message || '打开会话失败')
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
    message.error('请选择图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.error('图片大小不能超过 10MB')
    return
  }

  uploadingAvatar.value = true
  try {
    const avatarUrl = await updateAvatar(file)
    contact.value.avatarUrl = avatarUrl
    contact.value.avatarColor = 'transparent'
    message.success('头像已更新')
  } catch (error) {
    message.error('上传失败: ' + (error as Error).message)
  } finally {
    uploadingAvatar.value = false
  }
}

function handleEditProfile() {
  openEditProfile()
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
            :title="profileCardIsSelf ? '点击更换头像' : ''"
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
              LinkX ID: {{ displayId }}
              <span v-if="loadingRemoteProfile" class="profile-loading">加载中…</span>
            </div>
          </div>
          <button
            v-if="profileCardIsSelf"
            type="button"
            class="edit-profile-btn"
            @click="handleEditProfile"
          >
            编辑资料
          </button>
        </section>

        <section class="moments-row">
          <span class="moments-label">友链</span>
          <div class="moments-thumbs">
            <img
              v-for="(img, i) in momentPreviews"
              :key="i"
              :src="img"
              alt=""
              class="thumb"
            />
          </div>
          <n-icon :component="ChevronForwardOutline" :size="16" class="moments-arrow" />
        </section>

        <button v-if="!profileCardIsSelf" type="button" class="send-btn" @click="handleSendMessage">
          <n-icon :component="ChatbubbleEllipsesOutline" :size="18" />
          <span>发消息</span>
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
  background: var(--lx-bg-card);
  border-radius: 14px;
  box-shadow: var(--lx-shadow-modal);
  border: 1px solid var(--lx-border-light);
  overflow: hidden;
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
</style>
