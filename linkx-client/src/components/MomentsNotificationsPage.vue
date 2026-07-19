<script setup lang="ts">
/**
 * 友链模块内的消息通知抽屉
 *
 * 设计目标:
 *  - 不再是全屏覆盖,而是右侧滑入的抽屉(占友链独立窗口约 70% 宽度)
 *  - 默认仅展示友链相关通知(点赞/评论/@),点击右上角更多菜单可切换"显示全部消息"
 *  - 菜单中"清空所有消息"会清空当前用户所有通知
 *
 * 触发方式:由父组件 MomentsModal 通过 v-show 控制显示
 */
import { computed, onMounted, watch } from 'vue'
import { NIcon, NDropdown, useMessage, type DropdownOption } from 'naive-ui'
import {
  EllipsisHorizontal,
  HeartOutline,
  ChatbubbleOutline,
  AtCircleOutline,
  RefreshOutline,
  CloseOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../stores/notifications'
import EmptyState from './common/EmptyState.vue'

const message = useMessage()
const notificationsStore = useNotificationsStore()
const { messageNotifs, mentionOnly } = storeToRefs(notificationsStore)
const { markMessageAsRead, fetchMessageNotifications, setMentionOnly, clearAllMessageNotifsRemote } =
  notificationsStore

const props = defineProps<{
  /** 是否可见(抽屉打开状态) */
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select', n: typeof messageNotifs.value[0]): void
}>()

// 仅友链相关通知(点赞/评论/@)
const MOMENTS_NOTIF_TYPES = new Set(['moments_like', 'moments_comment', 'moments_mention'])
const showAll = defineModel<boolean>('showAll', { default: false })

/** 当前实际显示的列表 */
const displayList = computed(() => {
  if (showAll.value) return messageNotifs.value
  return messageNotifs.value.filter(n => MOMENTS_NOTIF_TYPES.has(n.type))
})

const isMentionOnly = computed(() => mentionOnly.value)

// 抽屉打开时拉取最新数据
onMounted(() => {
  void fetchMessageNotifications()
})
watch(
  () => props.visible,
  v => {
    if (v) void fetchMessageNotifications()
  }
)

/** 菜单选项 */
const moreOptions = computed<DropdownOption[]>(() => [
  {
    label: showAll.value ? '只看友链消息' : '显示全部消息',
    key: 'toggle-all'
  },
  {
    label: isMentionOnly.value ? '显示全部类型' : '只看@我的消息',
    key: 'toggle-mention'
  },
  { type: 'divider', key: 'div-1' },
  {
    label: '清空所有消息',
    key: 'clear-all',
    props: { class: 'lx-menu-danger' }
  }
])

async function handleMoreSelect(key: string | number) {
  if (key === 'toggle-all') {
    showAll.value = !showAll.value
  } else if (key === 'toggle-mention') {
    await setMentionOnly(!isMentionOnly.value)
  } else if (key === 'clear-all') {
    if (messageNotifs.value.length === 0) {
      message.info('当前没有可清空的消息')
      return
    }
    const ok = window.confirm(`确认清空全部 ${messageNotifs.value.length} 条消息通知?`)
    if (!ok) return
    const cleared = await clearAllMessageNotifsRemote()
    if (cleared > 0) message.success(`已清空 ${cleared} 条消息`)
    else message.warning('没有消息可清空')
  }
}

function getNotificationIcon(type: string) {
  if (type === 'moments_like') return HeartOutline
  if (type === 'moments_comment') return ChatbubbleOutline
  if (type === 'moments_mention') return AtCircleOutline
  return ChatbubbleOutline
}

function getNotificationTypeText(type: string) {
  switch (type) {
    case 'moments_like':
      return '赞了你的动态'
    case 'moments_comment':
      return '评论了你的动态'
    case 'moments_mention':
      return '在评论中@了你'
    default:
      return '有新通知'
  }
}

async function handleNotificationClick(notif: typeof messageNotifs.value[0]) {
  if (notif.readStatus === 0) {
    void markMessageAsRead(notif.id)
  }
  emit('select', notif)
}

async function refresh() {
  await fetchMessageNotifications()
  message.success('已刷新')
}

function close() {
  emit('close')
}
</script>

<template>
  <Transition name="notif-drawer">
    <aside v-if="visible" class="notif-drawer" @click.stop>
      <header class="notif-drawer-header">
        <div class="title-block">
          <h3 class="title">
            消息通知
            <span class="filter-tag" :class="{ mention: isMentionOnly }">
              {{ showAll ? '全部消息' : '仅友链消息' }}
            </span>
          </h3>
          <p class="subtitle">点赞 · 评论 · @ 我的</p>
        </div>
        <div class="actions">
          <button type="button" class="icon-btn" title="刷新" @click.stop="refresh">
            <n-icon :component="RefreshOutline" :size="18" />
          </button>
          <n-dropdown
            trigger="click"
            placement="bottom-end"
            :show-arrow="false"
            :options="moreOptions"
            @select="handleMoreSelect"
          >
            <button type="button" class="icon-btn" title="更多">
              <n-icon :component="EllipsisHorizontal" :size="18" />
            </button>
          </n-dropdown>
          <button type="button" class="icon-btn close" title="关闭" @click.stop="close">
            <n-icon :component="CloseOutline" :size="18" />
          </button>
        </div>
      </header>

      <div class="notif-content">
        <EmptyState
          v-if="displayList.length === 0"
          :title="showAll ? '暂无消息' : '暂无友链消息'"
          :description="showAll ? '稍后再来看看吧' : '还没有人给你点赞/评论/@~'"
        />
        <ul v-else class="notif-list">
          <li
            v-for="notif in displayList"
            :key="notif.id"
            class="notif-row"
            :class="{ unread: notif.readStatus === 0 }"
            @click="handleNotificationClick(notif)"
          >
            <img
              :src="notif.senderAvatar || '/default-avatar.svg'"
              class="notif-avatar"
              alt=""
              @error="(e: any) => (e.target as HTMLImageElement).src = '/default-avatar.svg'"
            />
            <div class="notif-info">
              <div class="notif-title">
                <span class="notif-name">{{ notif.senderName }}</span>
                <span class="notif-text">{{ getNotificationTypeText(notif.type) }}</span>
              </div>
              <div v-if="notif.content" class="notif-preview">{{ notif.content }}</div>
              <div class="notif-time">{{ notif.createTime }}</div>
            </div>
            <div class="notif-icon">
              <n-icon :component="getNotificationIcon(notif.type)" :size="20" />
            </div>
          </li>
        </ul>
      </div>
    </aside>
  </Transition>
</template>

<style scoped>
.notif-drawer {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 380px;
  max-width: 90%;
  max-height: 70%;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: 10px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.28);
  display: flex;
  flex-direction: column;
  z-index: 110;
  overflow: hidden;
}

.notif-drawer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  flex-shrink: 0;
}

.title-block {
  flex: 1;
  min-width: 0;
}

.title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.filter-tag {
  font-size: 10px;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}

.filter-tag.mention {
  background: rgba(255, 107, 107, 0.12);
  color: var(--lx-danger);
}

.subtitle {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--lx-text-muted);
}

.actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.icon-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-secondary);
  cursor: pointer;
  transition: background 0.15s;
}
.icon-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}
.icon-btn.close:hover {
  background: rgba(255, 107, 107, 0.12);
  color: var(--lx-danger);
}

.notif-content {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0 24px;
}

.notif-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.notif-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid var(--lx-border-light);
  transition: background 0.15s;
}

.notif-row:hover {
  background: var(--lx-bg-hover);
}

.notif-row.unread {
  background: rgba(18, 183, 245, 0.06);
}

.notif-row.unread .notif-name::after {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lx-danger);
  margin-left: 6px;
  vertical-align: middle;
}

.notif-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--lx-bg-panel);
}

.notif-info {
  flex: 1;
  min-width: 0;
}

.notif-title {
  font-size: 13px;
  color: var(--lx-text-body);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.notif-name {
  font-weight: 600;
  color: var(--lx-text-body);
}

.notif-text {
  font-weight: 400;
  color: var(--lx-text-secondary);
  font-size: 12px;
}

.notif-preview {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
  background: var(--lx-bg-panel);
  padding: 5px 8px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 60px;
  overflow: hidden;
}

.notif-time {
  margin-top: 4px;
  font-size: 11px;
  color: var(--lx-text-muted);
}

.notif-icon {
  flex-shrink: 0;
  color: var(--lx-accent);
}

/* 弹窗缩放淡入动画 */
.notif-drawer-enter-active,
.notif-drawer-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.notif-drawer-enter-from,
.notif-drawer-leave-to {
  transform: translate(-50%, -50%) scale(0.92);
  opacity: 0;
}
.notif-drawer-enter-to,
.notif-drawer-leave-from {
  transform: translate(-50%, -50%) scale(1);
  opacity: 1;
}
</style>