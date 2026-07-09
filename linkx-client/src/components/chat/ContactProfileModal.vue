<script setup lang="ts">
/**
 * 联系人资料卡浮层。
 * <p>
 * 在聊天中点击头像时展示昵称、LinkX ID、友链缩略图，非本人时可发起会话。
 * </p>
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { ChatbubbleEllipsesOutline, ChevronForwardOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useMomentsStore } from '../../stores/moments'
import type { ContactItem } from '../../types'

const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const momentsStore = useMomentsStore()
// 资料卡开关、当前联系人、卡片位置、是否为自己
const { contactProfileOpen, currentContactProfile, profileCardPos, profileCardIsSelf } = storeToRefs(chatModalsStore)
const { closeContactProfile } = chatModalsStore
const { userProfile, savedLogin } = storeToRefs(appStore)
const { startChatWithContact } = appStore
const { posts } = storeToRefs(momentsStore)

// 当前展示的联系人（可能为空）
const contact = computed<ContactItem | null>(() => currentContactProfile.value)

/** 展示用 LinkX ID：本人取登录名，好友取 id 后缀 */
const displayId = computed(() => {
  if (!contact.value) return ''
  if (profileCardIsSelf.value) {
    return savedLogin.value.username || userProfile.value.nickname || 'linkx_888888'
  }
  const id = contact.value.id.replace(/^f-/, '')
  return `linkx_${id}`
})

/** 友链缩略图：优先取该用户动态图片，不足 4 张用占位图补齐 */
const momentPreviews = computed(() => {
  if (!contact.value) return [] as string[]
  const images: string[] = []
  for (const post of posts.value) {
    if (post.user !== contact.value.name) continue
    if (post.images?.length) images.push(...post.images)
    if (images.length >= 4) break
  }
  if (images.length) return images.slice(0, 4)
  return Array.from({ length: 4 }, (_, i) =>
    `https://picsum.photos/seed/${encodeURIComponent(contact.value!.name)}-${i}/120/120`
  )
})

/** 从资料卡发起与该联系人的聊天 */
function handleSendMessage() {
  if (!contact.value) return
  startChatWithContact(contact.value)
  closeContactProfile()
}
</script>

<template>
  <!-- 挂载到 body 的资料卡浮层 -->
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
        <!-- 头像与基本信息 -->
        <section class="card-head">
          <Avatar
            :text="contact.avatarText || contact.name.charAt(0)"
            :color="contact.avatarColor || 'var(--lx-accent)'"
            :size="64"
          />
          <div class="head-meta">
            <div class="profile-name">{{ contact.name }}</div>
            <div class="profile-id">LinkX ID: {{ displayId }}</div>
          </div>
        </section>

        <!-- 友链预览行 -->
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

        <!-- 非本人时显示发消息按钮 -->
        <button v-if="!profileCardIsSelf" type="button" class="send-btn" @click="handleSendMessage">
          <n-icon :component="ChatbubbleEllipsesOutline" :size="18" />
          <span>发消息</span>
        </button>
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
  align-items: center;
  gap: 14px;
  padding: 20px 18px 16px;
}

.head-meta {
  min-width: 0;
  flex: 1;
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
</style>
