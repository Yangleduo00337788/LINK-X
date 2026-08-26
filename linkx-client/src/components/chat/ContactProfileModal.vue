<!-- 作者：yangleduo -->
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
import { useShortVideoStore } from '../../stores/shortVideo'
import { useMessage } from 'naive-ui'
import type { ContactItem } from '../../types'
import * as userApi from '../../api/user'
import type { UserProfileData } from '../../api/user'
import { resolveUserAvatarUrl } from '../../utils/defaultAvatar'
import { normalizeMediaUrl } from '../../utils/mediaUrl'
import { useI18n } from '../../i18n'
import { PROFILE_GENDER_FEMALE, PROFILE_GENDER_MALE } from '../../types/profileGender'
import { formatFriendDisplayName, friendAvatarText } from '../../utils/friendDisplay'
import { LxButton } from '../ui'
import ModalOverlayCaption from '../ModalOverlayCaption.vue'

const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const appSettingsStore = useAppSettingsStore()
const settingsStore = useSettingsStore()
const momentsStore = useMomentsStore()
const shortVideoStore = useShortVideoStore()
const message = useMessage()

const { contactProfileOpen, currentContactProfile, profileCardPos, profileCardIsSelf } = storeToRefs(chatModalsStore)
const { closeContactProfile, openEditProfile } = chatModalsStore
const { userProfile, savedLogin, isOffline } = storeToRefs(appStore)
const { onlineFriends } = storeToRefs(contactsStore)
const { notifyFriendOnline } = storeToRefs(appSettingsStore)
const { fetchUserMoments } = momentsStore

const avatarInputRef = ref<HTMLInputElement | null>(null)
const uploadingAvatar = ref(false)
const remoteProfile = ref<UserProfileData | null>(null)
const loadingRemoteProfile = ref(false)
const remarkDraft = ref('')
const groupDraft = ref('')
const savingRemark = ref(false)
const savingGroup = ref(false)
/** 资料卡友链真实缩略图（最多 4 张图片，不含占位） */
const momentPreviewImages = ref<string[]>([])
const loadingMomentPreviews = ref(false)

const contact = computed<ContactItem | null>(() => currentContactProfile.value)
const friendUserId = computed(() => (contact.value ? resolveContactUserId(contact.value) : null))
const existingGroupNames = computed(() => contactsStore.friendGroupNames)
const isFriendContact = computed(() => {
  const uid = friendUserId.value
  if (!uid) return false
  return contactsStore.items.some(c => String(c.userId ?? c.id) === uid)
})
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

/** 打开他人资料卡时拉取公开资料 + 友链缩略图 */
watch(
  () => [contactProfileOpen.value, profileCardIsSelf.value, contact.value?.id] as const,
  async ([open, isSelf, contactId]) => {
    remoteProfile.value = null
    remarkDraft.value = ''
    groupDraft.value = ''
    momentPreviewImages.value = []
    if (!open || isSelf || !contactId || !contact.value) return

    remarkDraft.value = contact.value.remark || ''
    groupDraft.value = contact.value.group || t('contacts.myFriends')

    const userId = resolveContactUserId(contact.value)
    if (!userId) return

    loadingRemoteProfile.value = true
    loadingMomentPreviews.value = true
    try {
      const [profileRes, userPosts] = await Promise.all([
        userApi.getUserProfile(userId).catch(() => null),
        fetchUserMoments(userId).catch(() => [] as Awaited<ReturnType<typeof fetchUserMoments>>)
      ])
      if (profileRes && profileRes.code === 200 && profileRes.data) {
        remoteProfile.value = profileRes.data
      }
      const images: string[] = []
      for (const post of userPosts || []) {
        for (const raw of post.images || []) {
          const url = normalizeMediaUrl(raw) || raw
          if (!url) continue
          const path = url.split('?')[0].toLowerCase()
          if (/\.(mp4|webm|mov|m4v)$/i.test(path)) continue
          images.push(url)
          if (images.length >= 4) break
        }
        if (images.length >= 4) break
      }
      momentPreviewImages.value = images
    } catch {
      // API 失败时回退到本地联系人数据
    } finally {
      loadingRemoteProfile.value = false
      loadingMomentPreviews.value = false
    }
  }
)

const displayName = computed(() => {
  if (!contact.value) return ''
  if (profileCardIsSelf.value) return userProfile.value.nickname || contact.value.name
  const nickname =
    contact.value.nickname || remoteProfile.value?.nickname || contact.value.name || t('defaults.friend')
  return formatFriendDisplayName(nickname, contact.value.remark)
})

const displayAvatarUrl = computed(() => {
  if (!contact.value) return undefined
  if (profileCardIsSelf.value) {
    return resolveUserAvatarUrl(userProfile.value.avatar, userProfile.value.userId)
  }
  const remoteId = remoteProfile.value?.id ?? friendUserId.value
  return resolveUserAvatarUrl(remoteProfile.value?.avatar || contact.value.avatarUrl, remoteId)
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
    if (!profileCardIsSelf.value) return
    const profileContact = currentContactProfile.value
    if (!profileContact) return
    profileContact.avatarUrl = avatar || undefined
    profileContact.avatarColor = avatar ? 'transparent' : 'var(--lx-success)'
    if (nickname) profileContact.name = nickname
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

function formatBirthday(ts: number | string | null | undefined): string {
  if (ts == null || ts === '') return ''
  const n = typeof ts === 'number' ? ts : Number(String(ts).trim())
  const d = Number.isFinite(n) ? new Date(n) : new Date(String(ts))
  if (Number.isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function joinLocation(country?: string | null, province?: string | null, region?: string | null): string {
  return [country, province, region]
    .map(p => (p || '').trim())
    .filter(p => p && p !== '请选择')
    .join(' · ')
}

/** 性别 / 生日 / 地区：本人读 store，他人读公开资料（生日属 PII，仅本人可见） */
const displayGender = computed(() => {
  if (profileCardIsSelf.value) return userProfile.value.gender || ''
  return remoteProfile.value?.gender || ''
})

const genderLabel = computed(() => {
  const g = displayGender.value
  if (g === PROFILE_GENDER_FEMALE) return t('modals.female')
  if (g === PROFILE_GENDER_MALE) return t('modals.male')
  return t('modals.notFilled')
})

const displayBirthdayText = computed(() => {
  if (!profileCardIsSelf.value) return ''
  return formatBirthday(userProfile.value.birthday)
})

const displayLocationText = computed(() => {
  if (profileCardIsSelf.value) {
    return joinLocation(userProfile.value.country, userProfile.value.province, userProfile.value.region)
  }
  return joinLocation(
    remoteProfile.value?.country,
    remoteProfile.value?.province,
    remoteProfile.value?.region
  )
})

const showProfileDetails = computed(
  () =>
    !!(displayGender.value || displayBirthdayText.value || displayLocationText.value) ||
    profileCardIsSelf.value
)

/** 友链缩略图：仅展示真实图片，无图时留空由 UI 提示 */
const momentPreviews = computed(() => momentPreviewImages.value)

/** 打开对方友链：侧栏扩展面板；独立窗通过标签弹出 */
function openContactMoments() {
  const userId = friendUserId.value
  if (!userId || !contact.value) return
  const name = displayName.value
  closeContactProfile()
  appStore.setNav('chat')
  void (async () => {
    await momentsStore.ensurePanelReady({ userId, userName: name })
    momentsStore.openPanel({ userId, userName: name })
  })()
}

function openContactShortVideo() {
  const userId = friendUserId.value
  if (!userId || !contact.value) return
  const name = displayName.value
  const avatar = contact.value.avatarUrl
  closeContactProfile()
  void shortVideoStore.openAuthorPanel(userId, { nickname: name, avatar })
}

/** 从资料卡发起与该联系人的聊天 */
async function handleSendMessage() {
  if (!contact.value) return
  try {
    await appStore.startChatWithContact(contact.value)
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
    const avatarUrl = await appStore.updateAvatar(file)
    const profileContact = currentContactProfile.value
    if (profileContact) {
      profileContact.avatarUrl = avatarUrl
      profileContact.avatarColor = 'transparent'
    }
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
    await appStore.startChatWithContact(friend)
    closeContactProfile()
  } catch (error) {
    message.error((error as Error).message || t('modals.openSessionFail'))
  }
}

function goNotifySettings() {
  closeContactProfile()
  settingsStore.openSettings('notifications')
}

async function saveRemark() {
  const userId = friendUserId.value
  if (!userId || !contact.value) return
  savingRemark.value = true
  try {
    const value = await contactsStore.updateFriendRemark(userId, remarkDraft.value)
    const nickname =
      contact.value.nickname || remoteProfile.value?.nickname || t('defaults.friend')
    contact.value.remark = value || undefined
    contact.value.nickname = nickname
    contact.value.name = formatFriendDisplayName(nickname, value)
    contact.value.avatarText = friendAvatarText(nickname, value)
    // 同步会话显示名
    const sid = appStore.sessions.find(
      s => !s.isGroup && (String(s.peerUserId) === userId || s.id === contact.value!.id)
    )?.id
    if (sid) {
      const session = appStore.sessions.find(s => s.id === sid)
      if (session) session.name = contact.value.name
    }
    message.success(t('modals.friendRemarkSaved'))
  } catch (e) {
    message.error((e as Error).message || t('common.fail'))
  } finally {
    savingRemark.value = false
  }
}

async function saveGroup() {
  const userId = friendUserId.value
  if (!userId || !contact.value) return
  const next = groupDraft.value.trim()
  savingGroup.value = true
  try {
    const value = await contactsStore.updateFriendGroup(userId, next)
    contact.value.group = value
    groupDraft.value = value
    message.success(t('modals.friendGroupSaved'))
  } catch (e) {
    message.error((e as Error).message || t('common.fail'))
  } finally {
    savingGroup.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="contactProfileOpen && contact"
      class="profile-overlay"
      @click.self="closeContactProfile"
    >
      <ModalOverlayCaption />
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
          <LxButton
            v-if="profileCardIsSelf"
            variant="outline"
            class="edit-profile-btn"
            @click="handleEditProfile"
          >
            {{ t('modals.editProfile') }}
          </LxButton>
        </section>

        <section v-if="showProfileDetails" class="profile-details">
          <div class="detail-row">
            <span class="detail-label">{{ t('modals.gender') }}</span>
            <span class="detail-value" :class="{ muted: !displayGender }">
              {{ genderLabel }}
            </span>
          </div>
          <div v-if="profileCardIsSelf" class="detail-row">
            <span class="detail-label">{{ t('modals.birthday') }}</span>
            <span class="detail-value" :class="{ muted: !displayBirthdayText }">
              {{ displayBirthdayText || t('modals.notFilled') }}
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('modals.location') }}</span>
            <span class="detail-value" :class="{ muted: !displayLocationText }">
              {{ displayLocationText || t('modals.notFilled') }}
            </span>
          </div>
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

        <section v-if="!profileCardIsSelf && isFriendContact" class="friend-edit">
          <label class="edit-label">{{ t('modals.friendRemark') }}</label>
          <div class="edit-row">
            <input
              v-model="remarkDraft"
              class="edit-input"
              :placeholder="t('modals.friendRemarkPh')"
              maxlength="64"
            />
            <LxButton variant="sm-primary" class="edit-save" :disabled="savingRemark" @click="saveRemark">
              {{ t('common.save') }}
            </LxButton>
          </div>
          <label class="edit-label">{{ t('modals.friendGroup') }}</label>
          <div class="edit-row">
            <input
              v-model="groupDraft"
              class="edit-input"
              list="friend-group-suggestions"
              :placeholder="t('modals.friendGroupPh')"
              maxlength="32"
            />
            <datalist id="friend-group-suggestions">
              <option v-for="g in existingGroupNames" :key="g" :value="g" />
            </datalist>
            <LxButton variant="sm-primary" class="edit-save" :disabled="savingGroup" @click="saveGroup">
              {{ t('common.save') }}
            </LxButton>
          </div>
          <p class="edit-hint">{{ t('modals.friendGroupHint') }}</p>
        </section>

        <button
          v-if="!profileCardIsSelf"
          type="button"
          class="moments-row"
          @click="openContactMoments"
        >
          <span class="moments-label">{{ t('modals.moments') }}</span>
          <div class="moments-thumbs">
            <template v-if="momentPreviews.length">
              <img
                v-for="(img, i) in momentPreviews"
                :key="i"
                :src="img"
                alt=""
                class="thumb"
                referrerpolicy="no-referrer"
              />
            </template>
            <span v-else class="moments-empty">
              {{ loadingMomentPreviews ? t('common.loading') : t('modals.noMomentsPreview') }}
            </span>
          </div>
          <n-icon :component="ChevronForwardOutline" :size="16" class="moments-arrow" />
        </button>

        <button
          v-if="!profileCardIsSelf"
          type="button"
          class="moments-row"
          @click="openContactShortVideo"
        >
          <span class="moments-label">{{ t('modals.shortVideo') }}</span>
          <span class="moments-empty">{{ t('shortVideo.viewAuthor') }}</span>
          <n-icon :component="ChevronForwardOutline" :size="16" class="moments-arrow" />
        </button>

        <LxButton v-if="!profileCardIsSelf" variant="profile-action" @click="handleSendMessage">
          <n-icon :component="ChatbubbleEllipsesOutline" :size="18" />
          <span>{{ t('modals.sendMessage') }}</span>
        </LxButton>

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
  z-index: var(--lx-z-dialog-profile);
  background: transparent;
}

.profile-card {
  position: fixed;
  width: 320px;
  max-height: min(520px, 80vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-card);
  box-shadow: var(--lx-shadow-modal);
  border: 1px solid var(--lx-border-light);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  animation: card-in var(--lx-duration-md) ease-out;
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
  gap: var(--lx-space-xl);
  padding: var(--lx-space-3xl) var(--lx-space-2xl) var(--lx-space-2xl);
}

.head-meta {
  min-width: 0;
  flex: 1;
  padding-top: var(--lx-space-xs);
}

.profile-name {
  font-size: var(--lx-font-3xl);
  font-weight: 600;
  color: var(--lx-text-body);
  line-height: var(--lx-leading-snug);
}

.profile-id {
  margin-top: var(--lx-space-xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.profile-loading {
  margin-left: var(--lx-space-sm);
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
}

.edit-profile-btn {
  flex-shrink: 0;
  margin-top: var(--lx-space-sm);
}

.profile-details {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space);
  padding: var(--lx-space-xs) var(--lx-space-2xl) var(--lx-space-xl);
  border-bottom: 1px solid var(--lx-border-light);
}

.detail-row {
  display: flex;
  align-items: baseline;
  gap: var(--lx-space-lg);
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading);
}

.detail-label {
  flex-shrink: 0;
  width: 36px;
  color: var(--lx-text-muted);
}

.detail-value {
  min-width: 0;
  flex: 1;
  color: var(--lx-text-body);
  word-break: break-word;
}

.detail-value.muted {
  color: var(--lx-text-muted);
}

.moments-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  width: 100%;
  padding: var(--lx-space-lg) var(--lx-space-2xl);
  border: none;
  border-top: 1px solid var(--lx-border-light);
  border-bottom: 1px solid var(--lx-border-light);
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: inherit;
  font: inherit;
}

.moments-row:hover {
  background: var(--lx-bg-panel);
}

.moments-label {
  font-size: var(--lx-font);
  color: var(--lx-text-body);
  flex-shrink: 0;
}

.moments-thumbs {
  flex: 1;
  display: flex;
  gap: var(--lx-space-xs);
  justify-content: flex-end;
  align-items: center;
  min-width: 0;
}

.moments-empty {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.thumb {
  width: 36px;
  height: 36px;
  border-radius: var(--lx-radius-2xs);
  object-fit: cover;
  background: var(--lx-bg-panel-deep);
  flex-shrink: 0;
}

.moments-arrow {
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.friend-edit {
  padding: var(--lx-space-lg) var(--lx-space-2xl) var(--lx-space-xs);
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-sm);
  border-top: 1px solid var(--lx-border-light);
}
.edit-label {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}
.edit-row {
  display: flex;
  gap: var(--lx-space);
  margin-bottom: var(--lx-space);
}
.edit-input {
  flex: 1;
  min-width: 0;
  height: 32px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-xs);
  padding: 0 var(--lx-space-md);
  background: var(--lx-bg-panel);
  color: var(--lx-text-body);
  font-size: var(--lx-font-md);
}
.edit-save {
  flex-shrink: 0;
  height: 32px;
}
.edit-hint {
  margin: -var(--lx-space-xs) 0 var(--lx-space);
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading);
}

.avatar-clickable {
  position: relative;
  cursor: pointer;
  border-radius: var(--lx-avatar-radius);
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
  transition: opacity var(--lx-duration-md);
  border-radius: var(--lx-avatar-radius);
}

.self-status {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  margin-top: var(--lx-space-sm);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-secondary);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--lx-border-strong, var(--lx-border-strong));
  flex-shrink: 0;
}

.status-dot.on {
  background: var(--lx-success, var(--lx-success));
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.18);
}

.online-section {
  border-top: 1px solid var(--lx-border-light);
  padding: var(--lx-space-lg) 0 var(--lx-space);
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.online-section-head {
  padding: 0 var(--lx-space-2xl) var(--lx-space-sm);
}

.online-section-title {
  display: block;
  font-size: var(--lx-font-md);
  font-weight: 600;
  color: var(--lx-text-body);
}

.online-section-hint {
  display: block;
  margin-top: var(--lx-space-2xs);
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
}

.notify-hint {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  margin: 0 var(--lx-space-2xl) var(--lx-space);
  border: none;
  background: transparent;
  padding: 0;
  font-size: var(--lx-font-sm);
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
  gap: var(--lx-space-md);
  padding: var(--lx-space) var(--lx-space-2xl);
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
  font-size: var(--lx-font-md);
  font-weight: 500;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.online-row-status {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  margin-top: var(--lx-space-2xs);
  font-size: var(--lx-font-xs);
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
  padding: var(--lx-space-2xl) var(--lx-space-2xl) var(--lx-space-lg);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  text-align: center;
}
</style>
