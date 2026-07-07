<script setup lang="ts">
import { ref, computed } from 'vue'
import { NModal, NIcon, useMessage } from 'naive-ui'
import { useChatModals } from '../composables/useChatModals'
import { useAppState } from '../composables/useAppState'
import {
  NotificationsOutline,
  RefreshOutline,
  RemoveOutline,
  CloseOutline,
  HeartOutline,
  Heart,
  ChatbubbleOutline,
  EllipsisHorizontal
} from '@vicons/ionicons5'

const { momentsModalOpen, closeMomentsModal } = useChatModals()
const { userProfile } = useAppState()
const message = useMessage()

const scrollTop = ref(0)
const handleScroll = (e: Event) => {
  const target = e.target as HTMLElement
  scrollTop.value = target.scrollTop
}

// 控制顶部导航栏的状态
const showTitle = computed(() => scrollTop.value > 250)
const headerBgOpacity = computed(() => {
  const opacity = Math.min(scrollTop.value / 200, 1)
  return `rgba(245, 245, 245, ${opacity})`
})
const headerIconColor = computed(() => (scrollTop.value > 200 ? '#1a1a1a' : '#ffffff'))

interface Comment {
  id: string
  user: string
  content: string
}

interface Post {
  id: string
  user: string
  avatar: string
  content: string
  images?: string[]
  time: string
  liked: boolean
  likes: string[]
  comments: Comment[]
  showActions: boolean
}

// 模拟朋友圈数据
const posts = ref<Post[]>([
  {
    id: '1',
    user: '张三',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=张三',
    content: '今天天气真不错，出去放松一下心情！大家周末都在干嘛呢？',
    images: ['https://images.unsplash.com/photo-1506744626753-1fa44df14c28?w=400&h=400&fit=crop'],
    time: '1小时前',
    liked: false,
    likes: ['李四', '王五'],
    comments: [
      { id: 'c1', user: '李四', content: '去哪里玩了呀？' },
      { id: 'c2', user: '张三', content: '回复 李四: 就在附近的公园转转~' }
    ],
    showActions: false
  },
  {
    id: '2',
    user: '李四',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=李四',
    content: '刚刚完成了一个大项目，好累但很有成就感！准备好好睡一觉。',
    time: '3小时前',
    liked: true,
    likes: ['养乐多', '王五'],
    comments: [],
    showActions: false
  },
  {
    id: '3',
    user: '王五',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=王五',
    content: '分享一首好听的歌 🎵',
    time: '昨天',
    liked: false,
    likes: [],
    comments: [
      { id: 'c3', user: '养乐多', content: '确实好听！' }
    ],
    showActions: false
  }
])

function toggleLike(post: Post) {
  post.liked = !post.liked
  if (post.liked) {
    post.likes.push(userProfile.value.nickname || '我')
  } else {
    post.likes = post.likes.filter(name => name !== (userProfile.value.nickname || '我'))
  }
  post.showActions = false
}

function refresh() {
  // 模拟刷新动画
  scrollTop.value = 0
  const container = document.querySelector('.moments-scroll-container')
  if (container) {
    container.scrollTo({ top: 0, behavior: 'smooth' })
  }
  message.success('刷新成功')
}

function showMessage() {
  message.info('暂无新消息')
}

function minimizeMoments() {
  if (window.electronAPI) {
    window.electronAPI.minimize()
  } else {
    closeMomentsModal()
  }
}

function closeMoments() {
  if (window.electronAPI) {
    window.electronAPI.close()
  } else {
    closeMomentsModal()
  }
}
</script>

<template>
  <div class="moments-wrapper standalone-window">
    <!-- 可滚动的内容区 -->
    <div class="moments-scroll-container" @scroll="handleScroll">
        <div class="moments-header">
          <div class="header-banner">
            <!-- 占满上半部分页面 (280px) -->
            <img src="https://images.unsplash.com/photo-1579546929518-9e396f3cc809?auto=format&fit=crop&q=80&w=1000" alt="Banner" class="banner-img" />
          </div>
          <div class="user-info">
            <span class="username">{{ userProfile.nickname }}</span>
            <img src="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user" alt="Avatar" class="avatar-img" />
          </div>
        </div>

        <div class="moments-content">
          <!-- 动态列表 -->
          <div v-for="post in posts" :key="post.id" class="post-item">
            <img :src="post.avatar" alt="Avatar" class="post-avatar" />
            <div class="post-main">
              <div class="post-user">{{ post.user }}</div>
              <div class="post-text">{{ post.content }}</div>
              
              <div class="post-images" v-if="post.images && post.images.length > 0">
                <img v-for="(img, index) in post.images" :key="index" :src="img" class="post-image" />
              </div>

              <div class="post-footer">
                <span class="post-time">{{ post.time }}</span>
                <div class="post-action-wrap">
                  <!-- 展开的操作面板 -->
                  <div class="action-panel" :class="{ 'show': post.showActions }">
                    <div class="action-btn-item" @click="toggleLike(post)">
                      <n-icon :component="post.liked ? Heart : HeartOutline" size="16" />
                      <span>{{ post.liked ? '取消' : '赞' }}</span>
                    </div>
                    <div class="action-divider"></div>
                    <div class="action-btn-item" @click="post.showActions = false">
                      <n-icon :component="ChatbubbleOutline" size="16" />
                      <span>评论</span>
                    </div>
                  </div>
                  <!-- 触发按钮 -->
                  <div class="action-trigger" @click.stop="post.showActions = !post.showActions">
                    <n-icon :component="EllipsisHorizontal" size="18" />
                  </div>
                </div>
              </div>

              <!-- 点赞和评论区域 -->
              <div class="post-interactions" v-if="post.likes.length > 0 || post.comments.length > 0">
                <!-- 小三角 -->
                <div class="interaction-arrow"></div>
                
                <div class="likes-list" v-if="post.likes.length > 0">
                  <n-icon :component="HeartOutline" size="14" class="like-icon" />
                  <span class="like-users">{{ post.likes.join('，') }}</span>
                </div>
                
                <div class="interaction-divider" v-if="post.likes.length > 0 && post.comments.length > 0"></div>
                
                <div class="comments-list" v-if="post.comments.length > 0">
                  <div v-for="comment in post.comments" :key="comment.id" class="comment-item">
                    <span class="comment-user">{{ comment.user }}：</span>
                    <span class="comment-text">{{ comment.content }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
          <div class="bottom-tip">
            没有更多了
          </div>
        </div>
      </div>

      <!-- 固定的顶部导航栏 -->
      <div class="fixed-header" :style="{ backgroundColor: headerBgOpacity, color: headerIconColor }">
        <div class="header-left">
          <div class="action-btn" title="消息" @click.stop="showMessage">
            <n-icon :component="NotificationsOutline" size="22" />
          </div>
          <div class="action-btn" title="刷新" @click.stop="refresh">
            <n-icon :component="RefreshOutline" size="22" />
          </div>
        </div>
        <div class="header-center" :class="{ 'visible': showTitle }">
          X友圈
        </div>
        <div class="header-right">
          <div class="action-btn minimize-btn" title="最小化" @click.stop="minimizeMoments">
            <n-icon :component="RemoveOutline" size="24" />
          </div>
          <div class="action-btn close-btn" title="关闭" @click.stop="closeMoments">
            <n-icon :component="CloseOutline" size="24" />
          </div>
        </div>
      </div>
    </div>
</template>

<style scoped>
.standalone-window {
  width: 100vw !important;
  height: 100vh !important;
  border-radius: 0 !important;
  margin: 0 !important;
}

.moments-wrapper {
  position: relative;
  width: 440px;
  height: 560px;
  background: #ffffff;
  border-radius: var(--lx-radius);
  overflow: hidden;
  text-align: left;
  margin: auto;
}

/* 固定头部 */
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
  pointer-events: none;
  -webkit-app-region: drag; /* 允许拖动窗口 */
}

.header-left, .header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  -webkit-app-region: no-drag; /* 按钮区域不可拖动 */
}

.header-center {
  font-size: 16px;
  font-weight: 600;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.header-center.visible {
  opacity: 1;
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
  background: rgba(0, 0, 0, 0.1);
}

.close-btn:hover {
  background: #fa5151;
  color: #ffffff !important;
}

/* 滚动区域 */
.moments-scroll-container {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
}

/* 隐藏滚动条 */
.moments-scroll-container::-webkit-scrollbar {
  width: 0;
  background: transparent;
}

/* 头部背景和个人信息 */
.moments-header {
  position: relative;
  height: 320px; /* 给下方留出一点空间显示头像 */
  background: #ffffff;
}

.header-banner {
  width: 100%;
  height: 280px; /* 占满一半尺寸 (560/2) */
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
  color: #ffffff;
  font-size: 20px;
  font-weight: 600;
  text-shadow: 0 1px 4px rgba(0,0,0,0.6);
  margin-top: 8px;
}

.avatar-img {
  width: 68px;
  height: 68px;
  border-radius: 50%; /* 圆形头像 */
  border: 2px solid #ffffff;
  background: #ffffff;
  object-fit: cover;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

/* 动态列表 */
.moments-content {
  padding: 0 20px 20px;
}

.post-item {
  display: flex;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.post-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%; /* 圆形头像 */
  object-fit: cover;
  flex-shrink: 0;
  margin-right: 12px;
  background: #f5f5f5;
  cursor: pointer;
}

.post-main {
  flex: 1;
  min-width: 0;
}

.post-user {
  font-size: 15px;
  font-weight: 600;
  color: #576b95; /* 微信朋友圈用户名的经典蓝色 */
  margin-bottom: 4px;
  cursor: pointer;
}

.post-text {
  font-size: 15px;
  color: #1a1a1a;
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
  color: #999;
}

.post-action-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.action-trigger {
  width: 32px;
  height: 22px;
  background: #f5f5f5;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #576b95;
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
  color: #ffffff;
  font-size: 13px;
  padding: 10px 12px;
  cursor: pointer;
  white-space: nowrap;
}

.action-btn-item:hover {
  opacity: 0.8;
}

.action-divider {
  width: 1px;
  height: 16px;
  background: #393d40;
}

/* 点赞和评论区 */
.post-interactions {
  background: #f7f7f7;
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
  border-bottom: 8px solid #f7f7f7;
}

.likes-list {
  display: flex;
  align-items: flex-start;
  color: #576b95;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.like-icon {
  margin-top: 3px;
  margin-right: 6px;
  flex-shrink: 0;
}

.like-users {
  font-weight: 500;
}

.interaction-divider {
  height: 1px;
  background: rgba(0, 0, 0, 0.05);
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
  color: #576b95;
  font-weight: 500;
  cursor: pointer;
}

.comment-text {
  color: #1a1a1a;
}

.bottom-tip {
  text-align: center;
  color: #999;
  font-size: 13px;
  padding: 30px 0;
}
</style>
