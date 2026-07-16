<script setup lang="ts">
// Vue 响应式 API、计算属性、生命周期与侦听器
import { ref, computed, onMounted, watch } from 'vue'
// Naive UI 图标、输入框、按钮与消息提示
import { NIcon, NInput, NButton, useMessage } from 'naive-ui'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../stores/chatModals'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 友链动态 Store
import { useMomentsStore } from '../stores/moments'
// 通知 Store
import { useNotificationsStore } from '../stores/notifications'
// 主题同步工具
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
// 文件读取工具与图片大小限制
import { readFileAsDataUrl, MAX_IMAGE_BYTES } from '../utils/file'
// 本地生成默认头像/封面（替代远程第三方）
import { generateDefaultAvatar, generateDefaultBanner } from '../utils/defaultAvatar'
// 空状态组件
import EmptyState from './common/EmptyState.vue'
// Ionicons5 友链相关图标
import {
  NotificationsOutline,
  RefreshOutline,
  RemoveOutline,
  CloseOutline,
  HeartOutline,
  Heart,
  ChatbubbleOutline,
  EllipsisHorizontal,
  SearchOutline,
  ImageOutline
} from '@vicons/ionicons5'

// 聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 应用 Store 实例
const appStore = useAppStore()
// 友链 Store 实例
const momentsStore = useMomentsStore()
// 通知 Store 实例
const notificationsStore = useNotificationsStore()
// 关闭友链独立窗口的方法
const { closeMomentsModal } = chatModalsStore
// 用户资料与主题
const { userProfile, theme } = storeToRefs(appStore)
// 动态列表
const { posts } = storeToRefs(momentsStore)
// 通知列表
const { messageNotifs } = storeToRefs(notificationsStore)
// 发布、点赞、展开操作面板的方法
const { addPost, toggleLike, toggleActions, isActionsOpen, fetchMoments } = momentsStore
// 消息通知相关
const { fetchMessageNotifications, markMessageAsRead, unreadMessageCount } = notificationsStore
// 消息提示实例
const message = useMessage()

// 滚动容器纵向偏移量
const scrollTop = ref(0)
// 评论输入草稿
const commentDraft = ref('')
// 当前正在评论的动态 ID
const commentPostId = ref<string | null>(null)
// 搜索关键词
const searchQuery = ref('')
// 是否显示顶部搜索框
const showSearch = ref(false)
// 发布动态文本内容
const composeText = ref('')
// 待发布图片 Data URL 列表
const composeImages = ref<string[]>([])
// 隐藏的图片上传 input 引用
const imageInputRef = ref<HTMLInputElement | null>(null)
// 用户封面与头像（本地生成，不再请求远程）
const defaultAvatar = computed(() =>
  generateDefaultAvatar(userProfile.value.nickname || '我')
)
const defaultBanner = computed(() =>
  generateDefaultBanner(userProfile.value.nickname || 'banner')
)

// 按搜索词过滤后的动态列表
const filteredPosts = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return posts.value
  return posts.value.filter(
    p => p.user.toLowerCase().includes(q) || p.content.toLowerCase().includes(q)
  )
})

// 记录滚动位置以驱动顶部栏渐变
function handleScroll(e: Event) {
  scrollTop.value = (e.target as HTMLElement).scrollTop
}

// 是否显示顶部标题（滚动超过阈值或搜索模式）
const showTitle = computed(() => scrollTop.value > 250 || showSearch.value)
// 顶部栏背景透明度（随滚动渐变）
const headerBgOpacity = computed(() => {
  if (showSearch.value) {
    const rgb = theme.value === 'dark' ? '34, 34, 34' : '245, 245, 245'
    return `rgba(${rgb}, 1)`
  }
  const opacity = Math.min(scrollTop.value / 200, 1)
  const rgb = theme.value === 'dark' ? '34, 34, 34' : '245, 245, 245'
  return `rgba(${rgb}, ${opacity})`
})
// 顶部栏图标颜色（随滚动切换深浅）
const headerIconColor = computed(() =>
  scrollTop.value > 200 || showSearch.value ? 'var(--lx-text)' : 'var(--lx-text-on-accent)'
)

// 挂载时同步主题到文档与 Electron
onMounted(() => {
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
  // 加载消息通知
  void fetchMessageNotifications()
})

// 主题变化时重新同步
watch(theme, t => {
  applyDocumentTheme(t)
  notifyElectronTheme(t)
})

// 处理用户选择的图片
async function onPickImages(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    if (composeImages.value.length >= 9) {
      message.warning('最多添加 9 张图片')
      break
    }
    if (file.size > MAX_IMAGE_BYTES) {
      message.warning(`「${file.name}」超过 2MB，已跳过`)
      continue
    }
    try {
      composeImages.value.push(await readFileAsDataUrl(file))
    } catch {
      message.error(`「${file.name}」读取失败`)
    }
  }
}

// 移除待发布图片
function removeComposeImage(index: number) {
  composeImages.value.splice(index, 1)
}

// 发布新动态
function publishPost() {
  const text = composeText.value.trim()
  if (!text && !composeImages.value.length) {
    message.warning('请输入内容或添加图片')
    return
  }
  addPost(
    text || '分享图片',
    composeImages.value.length ? [...composeImages.value] : undefined
  )
  composeText.value = ''
  composeImages.value = []
  message.success('动态已发布')
}

// 切换顶部搜索框显示
function toggleSearch() {
  showSearch.value = !showSearch.value
  if (!showSearch.value) searchQuery.value = ''
}

// 点赞/取消点赞
function onToggleLike(postId: string) {
  toggleLike(postId)
}

// 展开评论输入并打开操作面板
function onComment(post: { id: string }) {
  commentPostId.value = post.id
  toggleActions(post.id)
}

// 提交评论
function submitComment(postId: string) {
  const text = commentDraft.value.trim()
  if (!text) return
  momentsStore.addComment(postId, text)
  commentDraft.value = ''
  commentPostId.value = null
  message.success('评论已发送')
}

// 刷新：滚动回顶部并刷新动态和通知
async function refresh() {
  scrollTop.value = 0
  document.querySelector('.moments-scroll-container')?.scrollTo({ top: 0, behavior: 'smooth' })
  await Promise.all([
    fetchMoments(),
    fetchMessageNotifications()
  ])
  message.success('刷新成功')
}

// 显示消息通知列表
const showNotifications = ref(false)

async function showMessage() {
  showNotifications.value = !showNotifications.value
  if (showNotifications.value) {
    await fetchMessageNotifications()
  }
}

// 标记通知为已读并跳转
function handleNotificationClick(notif: typeof messageNotifs.value[0]) {
  if (notif.readStatus === 0) {
    void markMessageAsRead(notif.id)
  }
  // 根据通知类型跳转相关动态
  if (notif.type === 'moments_like' || notif.type === 'moments_comment') {
    // 滚动到对应动态位置
    const postIndex = posts.value.findIndex(p => String(p.id) === String(notif.relatedId))
    if (postIndex >= 0) {
      const container = document.querySelector('.moments-scroll-container')
      const targetPost = container?.querySelectorAll('.post-item')[postIndex]
      if (targetPost) {
        targetPost.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    }
  }
}

// 获取通知类型文本
function getNotificationTypeText(type: string) {
  switch (type) {
    case 'moments_like': return '赞了你的动态'
    case 'moments_comment': return '评论了你的动态'
    case 'moments_follow': return '关注了你'
    default: return '有新通知'
  }
}

// 最小化友链窗口
function minimizeMoments() {
  if (window.electronAPI) window.electronAPI.minimize()
  else closeMomentsModal()
}

// 关闭友链窗口
function closeMoments() {
  if (window.electronAPI) window.electronAPI.close()
  else closeMomentsModal()
}
</script>

<template>
  <!-- 友链独立窗口 -->
  <div class="moments-wrapper standalone-window">
    <!-- 可滚动内容区 -->
    <div class="moments-scroll-container" @scroll="handleScroll">
    <!-- 顶部封面与用户资料 -->
    <div class="moments-header">
      <div class="header-banner">
        <img :src="defaultBanner" alt="Banner" class="banner-img" />
      </div>
      <div class="user-info">
        <span class="username">{{ userProfile.nickname }}</span>
        <img :src="defaultAvatar" alt="Avatar" class="avatar-img" />
      </div>
    </div>

      <!-- 动态列表与发布区 -->
      <div class="moments-content">
        <!-- 发布动态卡片 -->
        <section class="compose-card">
          <n-input
            v-model:value="composeText"
            type="textarea"
            placeholder="分享新鲜事…"
            :rows="2"
            :autosize="{ minRows: 2, maxRows: 4 }"
          />
          <div v-if="composeImages.length" class="compose-images">
            <div v-for="(img, i) in composeImages" :key="i" class="compose-thumb-wrap">
              <img :src="img" alt="" class="compose-thumb" />
              <button type="button" class="compose-remove" @click="removeComposeImage(i)">
                <n-icon :component="CloseOutline" :size="12" />
              </button>
            </div>
          </div>
          <div class="compose-actions">
            <input ref="imageInputRef" type="file" accept="image/*" multiple hidden @change="onPickImages" />
            <button type="button" class="compose-tool" title="添加图片" @click="imageInputRef?.click()">
              <n-icon :component="ImageOutline" :size="18" />
            </button>
            <n-button type="primary" size="small" @click="publishPost">发布</n-button>
          </div>
        </section>

        <div v-for="post in filteredPosts" :key="post.id" class="post-item">
          <img :src="post.avatar" alt="Avatar" class="post-avatar" />
          <div class="post-main">
            <div class="post-user">{{ post.user }}</div>
            <div class="post-text">{{ post.content }}</div>
            <div v-if="post.images?.length" class="post-images">
              <img v-for="(img, index) in post.images" :key="index" :src="img" class="post-image" />
            </div>
            <div class="post-footer">
              <span class="post-time">{{ post.time }}</span>
              <div class="post-action-wrap">
                <div class="action-panel" :class="{ show: isActionsOpen(post.id) }">
                  <div class="action-btn-item" @click="onToggleLike(post.id)">
                    <n-icon :component="post.liked ? Heart : HeartOutline" size="16" />
                    <span>{{ post.liked ? '取消' : '赞' }}</span>
                  </div>
                  <div class="action-divider" />
                  <div class="action-btn-item" @click="onComment(post)">
                    <n-icon :component="ChatbubbleOutline" size="16" />
                    <span>评论</span>
                  </div>
                </div>
                <div class="action-trigger" @click.stop="toggleActions(post.id)">
                  <n-icon :component="EllipsisHorizontal" size="18" />
                </div>
              </div>
            </div>
            <div v-if="commentPostId === post.id" class="comment-input-row">
              <input v-model="commentDraft" class="comment-input" placeholder="写评论…" @keyup.enter="submitComment(post.id)" />
              <button type="button" class="comment-send" @click="submitComment(post.id)">发送</button>
            </div>
            <div v-if="post.likedBy.length || post.comments.length" class="post-interactions">
              <div class="interaction-arrow" />
              <div v-if="post.likedBy.length" class="likes-list">
                <n-icon :component="HeartOutline" size="14" class="like-icon" />
                <span class="like-users">{{ post.likedBy.join('，') }}</span>
              </div>
              <div v-if="post.likedBy.length && post.comments.length" class="interaction-divider" />
              <div v-if="post.comments.length" class="comments-list">
                <div v-for="comment in post.comments" :key="comment.id" class="comment-item">
                  <span class="comment-user">{{ comment.user }}：</span>
                  <span class="comment-text">{{ comment.content }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <EmptyState
          v-if="!filteredPosts.length"
          :title="searchQuery.trim() ? '未找到相关动态' : '暂无动态'"
          :description="searchQuery.trim() ? '换个关键词试试' : '发布第一条友链动态吧'"
        />
        <div v-else class="bottom-tip">没有更多了</div>
      </div>
    </div>

    <!-- 固定顶部操作栏（随滚动渐变） -->
    <div class="fixed-header" :style="{ backgroundColor: headerBgOpacity, color: headerIconColor }">
      <div class="header-left">
        <div class="action-btn" title="搜索" @click.stop="toggleSearch">
          <n-icon :component="SearchOutline" size="22" />
        </div>
        <div class="action-btn" :class="{ active: showNotifications }" title="消息" @click.stop="showMessage">
          <n-icon :component="NotificationsOutline" size="22" />
          <span v-if="unreadMessageCount > 0" class="notif-badge">{{ unreadMessageCount > 99 ? '99+' : unreadMessageCount }}</span>
        </div>
        <div class="action-btn" title="刷新" @click.stop="refresh">
          <n-icon :component="RefreshOutline" size="22" />
        </div>
      </div>
      <div class="header-center" :class="{ visible: showTitle }">
        <span v-if="!showSearch && !showNotifications">友链</span>
        <input
          v-else-if="showSearch"
          v-model="searchQuery"
          class="header-search"
          placeholder="搜索友链"
          @click.stop
        />
      </div>
      <div class="header-right">
        <div class="action-btn window-btn" title="最小化" @click.stop="minimizeMoments">
          <n-icon :component="RemoveOutline" size="18" />
        </div>
        <div class="action-btn window-btn close-btn" title="关闭" @click.stop="closeMoments">
          <n-icon :component="CloseOutline" size="18" />
        </div>
      </div>

      <!-- 消息通知面板 -->
      <div v-if="showNotifications" class="notifications-panel" @click.stop>
        <div class="notif-header">
          <span>消息通知</span>
          <span v-if="unreadMessageCount > 0" class="mark-all-read" @click="notificationsStore.markAllMessagesAsRead()">全部已读</span>
        </div>
        <div class="notif-list">
          <div v-if="messageNotifs.length === 0" class="notif-empty">暂无新通知</div>
          <div
            v-for="notif in messageNotifs"
            :key="notif.id"
            class="notif-item"
            :class="{ unread: notif.readStatus === 0 }"
            @click="handleNotificationClick(notif)"
          >
            <img :src="notif.senderAvatar || '/default-avatar.svg'" class="notif-avatar" />
            <div class="notif-content">
              <div class="notif-title">{{ notif.senderName }}</div>
              <div class="notif-text">{{ getNotificationTypeText(notif.type) }}</div>
              <div v-if="notif.content" class="notif-preview">{{ notif.content }}</div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="showNotifications" class="notif-backdrop" @click="showNotifications = false" />
    </div>
  </div>
</template>

<style scoped>
.standalone-window {
  width: 100vw !important;
  height: 100vh !important;
  border-radius: 0 !important;
  margin: 0 !important;
  background: var(--lx-bg-window);
}

.moments-wrapper {
  position: relative;
  width: 440px;
  height: 560px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  text-align: left;
  margin: auto;
}

.standalone-window.moments-wrapper {
  width: 100vw !important;
  height: 100vh !important;
  border-radius: 0 !important;
  margin: 0 !important;
}

.fixed-header {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  z-index: 100;
  transition: background-color 0.3s ease, color 0.3s ease;
  box-sizing: border-box;
  -webkit-app-region: drag;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  -webkit-app-region: no-drag;
  pointer-events: auto;
}

.header-right {
  margin-left: auto;
}

.window-btn {
  width: 28px;
  height: 28px;
  border-radius: var(--lx-radius);
}

.window-btn.close-btn:hover {
  background: var(--lx-danger) !important;
  color: #fff !important;
}

.header-center {
  flex: 1;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
  min-width: 0;
  padding: 0 8px;
  -webkit-app-region: no-drag;
}

.header-center.visible {
  opacity: 1;
  pointer-events: auto;
}

.header-search {
  width: 100%;
  max-width: 200px;
  border: none;
  outline: none;
  background: var(--lx-bg-input);
  color: var(--lx-text-body);
  border-radius: var(--lx-radius);
  padding: 6px 10px;
  font-size: 14px;
}

.close-btn:hover {
  background: var(--lx-danger);
  color: var(--lx-text-on-accent) !important;
}

.compose-card {
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  padding: 12px;
  margin-bottom: 12px;
}

.compose-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.compose-thumb-wrap {
  position: relative;
  width: 72px;
  height: 72px;
}

.compose-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: var(--lx-radius);
}

.compose-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: var(--lx-danger);
  color: var(--lx-text-on-accent);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.compose-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.compose-tool {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-input);
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.compose-tool:hover {
  color: var(--lx-accent);
  background: var(--lx-accent-soft);
}

.moments-content {
  padding: 0 20px 20px;
}

.action-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s;
  pointer-events: auto;
}

.action-btn:hover {
  background: var(--lx-bg-active);
}

.moments-scroll-container {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  background: var(--lx-bg-card);
}

.moments-scroll-container::-webkit-scrollbar {
  width: 0;
  background: transparent;
}

.moments-header {
  position: relative;
  height: 320px;
  background: var(--lx-bg-card);
}

.header-banner {
  width: 100%;
  height: 280px;
  overflow: hidden;
}

.banner-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-info {
  position: absolute;
  bottom: 16px;
  right: 16px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.username {
  color: var(--lx-bg-card);
  font-size: 20px;
  font-weight: 600;
  text-shadow: 0 1px 4px rgba(0,0,0,0.6);
  margin-top: 8px;
}

.avatar-img {
  width: 68px;
  height: 68px;
  border-radius: var(--lx-avatar-radius);
  border: 2px solid var(--lx-bg-card);
  background: var(--lx-bg-card);
  object-fit: cover;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.post-item {
  display: flex;
  padding: 16px 0;
  border-bottom: 1px solid var(--lx-border-light);
}

.post-avatar {
  width: 42px;
  height: 42px;
  border-radius: var(--lx-avatar-radius);
  object-fit: cover;
  flex-shrink: 0;
  margin-right: 12px;
  background: var(--lx-bg-panel);
}

.post-user {
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-accent);
  margin-bottom: 4px;
}

.post-text {
  font-size: 15px;
  color: var(--lx-text);
  line-height: 1.5;
  margin-bottom: 8px;
  word-break: break-all;
}

.post-images {
  margin-bottom: 10px;
}

.post-image {
  max-width: 180px;
  max-height: 180px;
  object-fit: cover;
}

.post-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  margin-bottom: 8px;
}

.post-time {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.post-action-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.action-trigger {
  width: 32px;
  height: 22px;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-accent);
  cursor: pointer;
}

.action-panel {
  position: absolute;
  right: 40px;
  top: 50%;
  transform: translateY(-50%) scale(0.9);
  transform-origin: right center;
  background: #4c5154;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  padding: 0 12px;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.action-panel.show {
  opacity: 1;
  visibility: visible;
  transform: translateY(-50%) scale(1);
}

.action-btn-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--lx-bg-card);
  font-size: 13px;
  padding: 10px 12px;
  cursor: pointer;
  white-space: nowrap;
}

.action-divider {
  width: 1px;
  height: 16px;
  background: #393d40;
}

.comment-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.comment-input {
  flex: 1;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 6px 10px;
  font-size: 13px;
  background: var(--lx-bg-card);
  color: var(--lx-text);
}

.comment-send {
  border: none;
  background: var(--lx-accent);
  color: var(--lx-text-on-accent);
  border-radius: var(--lx-radius);
  padding: 0 12px;
  font-size: 13px;
  cursor: pointer;
}

.post-interactions {
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  padding: 8px 10px;
  position: relative;
  margin-top: 10px;
}

.interaction-arrow {
  position: absolute;
  top: -8px;
  left: 12px;
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-bottom: 8px solid var(--lx-bg-panel);
}

.likes-list {
  display: flex;
  align-items: flex-start;
  color: var(--lx-accent);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.like-icon {
  margin-top: 3px;
  margin-right: 6px;
  flex-shrink: 0;
}

.interaction-divider {
  height: 1px;
  background: var(--lx-bg-hover);
  margin: 6px 0;
}

.comments-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.comment-item {
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.comment-user {
  color: var(--lx-accent);
  font-weight: 500;
}

.bottom-tip {
  text-align: center;
  color: var(--lx-text-muted);
  font-size: 13px;
  padding: 30px 0;
}

/* 通知面板样式 */
.notif-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  background: #ff4d4f;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.notifications-panel {
  position: absolute;
  top: 48px;
  left: 0;
  width: 300px;
  max-height: 400px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  z-index: 200;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.notif-backdrop {
  position: fixed;
  inset: 0;
  z-index: 199;
}

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--lx-border-light);
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.mark-all-read {
  font-size: 12px;
  color: var(--lx-accent);
  cursor: pointer;
}

.mark-all-read:hover {
  text-decoration: underline;
}

.notif-list {
  flex: 1;
  overflow-y: auto;
}

.notif-empty {
  padding: 32px 16px;
  text-align: center;
  color: var(--lx-text-muted);
  font-size: 13px;
}

.notif-item {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
}

.notif-item:hover {
  background: var(--lx-bg-hover);
}

.notif-item.unread {
  background: rgba(18, 183, 245, 0.06);
}

.notif-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--lx-bg-panel);
}

.notif-content {
  flex: 1;
  min-width: 0;
}

.notif-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.notif-text {
  font-size: 12px;
  color: var(--lx-text-secondary);
  margin-top: 2px;
}

.notif-preview {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-btn.active {
  background: var(--lx-bg-active);
}
</style>
