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
  CalendarOutline,
  RefreshOutline,
  CloseOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../stores/notifications'
import EmptyState from './common/EmptyState.vue'
import { generateDefaultAvatar } from '../utils/defaultAvatar'
import { normalizeMediaUrl } from '../utils/mediaUrl'
import { useI18n } from '../i18n'
import { aggregateNotifications } from '../utils/notifyAggregate'

const message = useMessage()
const { t } = useI18n()
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
  (e: 'select', n: (typeof messageNotifs.value)[0]): void
}>()

// 默认展示：友链相关 + 日历日程提醒
const DEFAULT_NOTIF_TYPES = new Set([
  'moments_like',
  'moments_comment',
  'moments_mention',
  'calendar_remind'
])
const showAll = defineModel<boolean>('showAll', { default: false })

const isMentionOnly = computed(() => mentionOnly.value)

/** 当前实际显示的列表（同类型同 relatedId 聚合） */
const displayList = computed(() => {
  let list = messageNotifs.value
  if (isMentionOnly.value) {
    list = list.filter(n => n.type === 'moments_mention')
  } else if (!showAll.value) {
    list = list.filter(n => DEFAULT_NOTIF_TYPES.has(n.type))
  }
  return aggregateNotifications(list)
})

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
    label: showAll.value ? t('moments.onlyMomentsCalendar') : t('moments.showAllMsg'),
    key: 'toggle-all'
  },
  {
    label: isMentionOnly.value ? t('moments.showAllTypes') : t('moments.onlyAtMeMsg'),
    key: 'toggle-mention'
  },
  { type: 'divider', key: 'div-1' },
  {
    label: t('moments.clearAll'),
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
      message.info(t('moments.nothingToClear'))
      return
    }
    const ok = window.confirm(t('moments.clearConfirm', { n: messageNotifs.value.length }))
    if (!ok) return
    const cleared = await clearAllMessageNotifsRemote()
    if (cleared > 0) message.success(t('moments.clearedCount', { n: cleared }))
    else message.warning(t('moments.noMsgToClear'))
  }
}

function getNotificationIcon(type: string) {
  if (type === 'moments_like') return HeartOutline
  if (type === 'moments_comment') return ChatbubbleOutline
  if (type === 'moments_mention') return AtCircleOutline
  if (type === 'calendar_remind') return CalendarOutline
  return ChatbubbleOutline
}

function getNotificationTypeText(type: string, aggregateCount = 1, aggregateNames: string[] = []) {
  const base = (() => {
    switch (type) {
      case 'moments_like':
        return t('moments.likedYour')
      case 'moments_comment':
        return t('moments.commentedYour')
      case 'moments_mention':
        return t('moments.mentionedYou')
      case 'calendar_remind':
        return t('moments.calendarRemind')
      default:
        return t('moments.newNotif')
    }
  })()
  if (aggregateCount <= 1) return base
  const others = aggregateNames.slice(0, 2).join('、')
  return others
    ? t('moments.aggregatedAction', { others, n: aggregateCount, action: base })
    : t('moments.aggregatedCount', { n: aggregateCount, action: base })
}

function resolveAvatar(notif: typeof messageNotifs.value[0]): string {
  const url = normalizeMediaUrl(notif.senderAvatar)
  if (url) return url
  return generateDefaultAvatar(notif.senderName || t('moments.user'), 76)
}

function formatNotifTime(raw: string): string {
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw
  const now = Date.now()
  const diff = Math.max(0, now - date.getTime())
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return t('chat.justNow')
  if (minutes < 60) return t('chat.minutesAgo', { n: minutes })
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return t('chat.hoursAgo', { n: hours })
  const y = date.getFullYear()
  const m = `${date.getMonth() + 1}`.padStart(2, '0')
  const d = `${date.getDate()}`.padStart(2, '0')
  const hh = `${date.getHours()}`.padStart(2, '0')
  const mm = `${date.getMinutes()}`.padStart(2, '0')
  const thisYear = new Date().getFullYear()
  return y === thisYear ? `${m}-${d} ${hh}:${mm}` : `${y}-${m}-${d} ${hh}:${mm}`
}

async function handleNotificationClick(notif: typeof messageNotifs.value[0]) {
  if (notif.readStatus === 0) {
    void markMessageAsRead(notif.id)
  }
  emit('select', notif)
}

async function refresh() {
  await fetchMessageNotifications()
  message.success(t('moments.refreshed'))
}

function close() {
  emit('close')
}

function onAvatarError(e: Event, notif: typeof messageNotifs.value[0]) {
  const img = e.target as HTMLImageElement
  img.src = generateDefaultAvatar(notif.senderName || t('moments.user'), 76)
}
</script>

<template>
  <Transition name="notif-drawer">
    <div v-if="visible" class="notif-drawer-layer" @click.stop>
      <aside class="notif-drawer">
        <header class="notif-drawer-header">
          <div class="title-block">
            <h3 class="title">
              {{ t('moments.notifTitle') }}
              <span class="filter-tag" :class="{ mention: isMentionOnly }">
                {{ showAll ? t('moments.allMessages') : t('moments.momentsAndCalendar') }}
              </span>
            </h3>
            <p class="subtitle">{{ t('moments.notifSubtitle') }}</p>
          </div>
          <div class="actions">
            <button type="button" class="icon-btn" :title="t('moments.refresh')" @click.stop="refresh">
              <n-icon :component="RefreshOutline" :size="18" />
            </button>
            <n-dropdown
              trigger="click"
              placement="bottom-end"
              :show-arrow="false"
              :options="moreOptions"
              @select="handleMoreSelect"
            >
              <button type="button" class="icon-btn" :title="t('moments.more')">
                <n-icon :component="EllipsisHorizontal" :size="18" />
              </button>
            </n-dropdown>
            <button type="button" class="icon-btn close" :title="t('common.close')" @click.stop="close">
              <n-icon :component="CloseOutline" :size="18" />
            </button>
          </div>
        </header>

        <div class="notif-content">
          <EmptyState
            v-if="displayList.length === 0"
            :title="showAll ? t('moments.noMessages') : t('moments.noMomentsMessages')"
            :description="showAll ? t('moments.emptyNotifAll') : t('moments.emptyNotifMoments')"
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
                :src="resolveAvatar(notif)"
                class="notif-avatar"
                alt=""
                @error="onAvatarError($event, notif)"
              />
              <div class="notif-info">
                <div class="notif-title">
                  <span class="notif-name">{{ notif.senderName }}</span>
                  <span class="notif-text">{{
                    getNotificationTypeText(notif.type, notif.aggregateCount, notif.aggregateNames)
                  }}</span>
                </div>
                <div v-if="notif.content" class="notif-preview">{{ notif.content }}</div>
                <div class="notif-time">{{ formatNotifTime(notif.createTime) }}</div>
              </div>
              <div class="notif-icon">
                <n-icon :component="getNotificationIcon(notif.type)" :size="20" />
              </div>
            </li>
          </ul>
        </div>
      </aside>
    </div>
  </Transition>
</template>

<style scoped>
/* 定位层：不参与 transform 动画，避免与居中 transform 冲突导致抖动 */
.notif-drawer-layer {
  position: absolute;
  inset: 0;
  z-index: 110;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.notif-drawer {
  pointer-events: auto;
  width: 380px;
  max-width: 90%;
  max-height: 70%;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: 10px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.28);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  will-change: opacity, transform;
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

/* 仅淡入上移，定位层用 flex 居中，不再动画 translate(-50%,-50%) */
.notif-drawer-enter-active .notif-drawer,
.notif-drawer-leave-active .notif-drawer {
  transition: transform 0.18s ease, opacity 0.18s ease;
}
.notif-drawer-enter-from .notif-drawer,
.notif-drawer-leave-to .notif-drawer {
  transform: translateY(8px) scale(0.98);
  opacity: 0;
}
.notif-drawer-enter-to .notif-drawer,
.notif-drawer-leave-from .notif-drawer {
  transform: translateY(0) scale(1);
  opacity: 1;
}
</style>
