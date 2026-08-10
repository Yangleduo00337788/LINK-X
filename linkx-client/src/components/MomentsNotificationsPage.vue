<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 友链模块内的消息通知下拉弹层
 *
 * 自铃铛图标下方弹出，带顶部三角指示；默认仅展示友链互动（moments_*）。
 */
import { computed, onMounted, onBeforeUnmount, watch, ref, nextTick } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import {
  EllipsisHorizontal,
  HeartOutline,
  ChatbubbleOutline,
  AtCircleOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../stores/notifications'
import { useContactsStore } from '../stores/contacts'
import { generateDefaultAvatar } from '../utils/defaultAvatar'
import { normalizeMediaUrl } from '../utils/mediaUrl'
import { useI18n } from '../i18n'
import { aggregateNotifications } from '../utils/notifyAggregate'

const message = useMessage()
const { t } = useI18n()
const notificationsStore = useNotificationsStore()
const contactsStore = useContactsStore()
const { messageNotifs } = storeToRefs(notificationsStore)
const { markMessageAsRead, fetchMessageNotifications, clearAllMessageNotifsRemote } =
  notificationsStore

const props = defineProps<{
  /** 弹层是否展开 */
  visible: boolean
  /** 铃铛按钮锚点，用于 fixed 定位（避免父级 overflow 裁切） */
  anchorEl?: HTMLElement | null
}>()

const POPOVER_WIDTH = 360
const HEADER_HEIGHT = 52
const GAP = 10
const EDGE = 12

const layout = ref({
  top: 0,
  left: 0,
  arrowLeft: POPOVER_WIDTH / 2,
  bodyMaxHeight: 320
})

function updateLayout() {
  const el = props.anchorEl
  if (!el) return
  const rect = el.getBoundingClientRect()
  let left = rect.left + rect.width / 2 - POPOVER_WIDTH / 2
  left = Math.max(EDGE, Math.min(left, window.innerWidth - POPOVER_WIDTH - EDGE))
  const top = rect.bottom + GAP
  const available = window.innerHeight - top - EDGE
  const bodyMaxHeight = Math.max(160, Math.min(400, available - HEADER_HEIGHT))
  layout.value = {
    top,
    left,
    arrowLeft: rect.left + rect.width / 2 - left,
    bodyMaxHeight
  }
}

const wrapStyle = computed(() => ({
  top: `${layout.value.top}px`,
  left: `${layout.value.left}px`,
  width: `${POPOVER_WIDTH}px`
}))

const popoverStyle = computed(() => ({
  '--arrow-left': `${layout.value.arrowLeft}px`
}))

const bodyStyle = computed(() => ({
  maxHeight: `${layout.value.bodyMaxHeight}px`,
  minHeight: `${Math.min(240, layout.value.bodyMaxHeight)}px`
}))

let layoutListenersBound = false

function bindLayoutListeners() {
  if (layoutListenersBound) return
  layoutListenersBound = true
  window.addEventListener('resize', updateLayout)
  window.addEventListener('scroll', updateLayout, true)
}

function unbindLayoutListeners() {
  if (!layoutListenersBound) return
  layoutListenersBound = false
  window.removeEventListener('resize', updateLayout)
  window.removeEventListener('scroll', updateLayout, true)
}

async function refreshLayout() {
  await nextTick()
  updateLayout()
}

const showMoreMenu = ref(false)
/** 仅展示好友发来的互动（点赞/评论/@） */
const friendsInteractOnly = ref(false)

const friendIdSet = computed(() => {
  const set = new Set<string>()
  for (const f of contactsStore.friends) {
    const id = String(f.userId ?? f.id)
    if (id) set.add(id)
  }
  return set
})

function toggleMoreMenu() {
  showMoreMenu.value = !showMoreMenu.value
}

function closeMoreMenu() {
  showMoreMenu.value = false
}

/** 仅友链互动通知 */
const momentsNotifs = computed(() =>
  messageNotifs.value.filter(n => typeof n.type === 'string' && n.type.startsWith('moments_'))
)

/** 当前实际显示的列表（同类型同 relatedId 聚合） */
const displayList = computed(() => {
  let list = momentsNotifs.value
  if (friendsInteractOnly.value && friendIdSet.value.size > 0) {
    list = list.filter(n => friendIdSet.value.has(String(n.senderId)))
  }
  return aggregateNotifications(list)
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select', n: (typeof messageNotifs.value)[0]): void
}>()

onMounted(() => {
  void fetchMessageNotifications()
  void contactsStore.fetchFriends()
})
watch(
  () => props.visible,
  v => {
    if (v) {
      void refreshLayout().then(() => {
        bindLayoutListeners()
        void fetchMessageNotifications()
        void contactsStore.fetchFriends()
      })
    } else {
      unbindLayoutListeners()
      closeMoreMenu()
    }
  }
)

watch(
  () => props.anchorEl,
  () => {
    if (props.visible) void refreshLayout()
  }
)

onBeforeUnmount(() => {
  unbindLayoutListeners()
})

async function handleClearAll() {
  closeMoreMenu()
  const count = momentsNotifs.value.length
  if (count === 0) {
    message.info(t('moments.nothingToClear'))
    return
  }
  const ok = window.confirm(t('moments.clearConfirm', { n: count }))
  if (!ok) return
  const cleared = await clearAllMessageNotifsRemote()
  if (cleared > 0) message.success(t('moments.clearedCount', { n: cleared }))
  else message.warning(t('moments.noMsgToClear'))
}

function toggleFriendsInteractOnly() {
  friendsInteractOnly.value = !friendsInteractOnly.value
  closeMoreMenu()
}

function getNotificationIcon(type: string) {
  if (type === 'moments_like') return HeartOutline
  if (type === 'moments_comment') return ChatbubbleOutline
  if (type === 'moments_mention' || type === 'moments_at') return AtCircleOutline
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
      case 'moments_at':
        return t('moments.mentionedYou')
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

function onAvatarError(e: Event, notif: typeof messageNotifs.value[0]) {
  const img = e.target as HTMLImageElement
  img.src = generateDefaultAvatar(notif.senderName || t('moments.user'), 76)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="notif-popover">
      <div v-if="visible" class="notif-popover-wrap" :style="wrapStyle" @click.stop>
        <div class="notif-popover" :style="popoverStyle">
          <header class="notif-popover-header">
            <h3 class="notif-popover-title">{{ t('moments.allInteractiveMessages') }}</h3>
            <div class="more-menu-wrap">
              <button
                type="button"
                class="more-btn"
                :class="{ active: showMoreMenu }"
                :title="t('moments.more')"
                @click.stop="toggleMoreMenu"
              >
                <n-icon :component="EllipsisHorizontal" :size="18" />
              </button>
              <Transition name="more-menu">
                <div v-if="showMoreMenu" class="more-menu" @click.stop>
                  <button type="button" class="more-menu-item" @click="handleClearAll">
                    {{ t('moments.clearAll') }}
                  </button>
                  <div class="more-menu-divider" />
                  <button
                    type="button"
                    class="more-menu-item"
                    :class="{ active: friendsInteractOnly }"
                    @click="toggleFriendsInteractOnly"
                  >
                    {{ t('moments.onlyFriendsInteract') }}
                  </button>
                </div>
              </Transition>
            </div>
          </header>

          <div class="notif-popover-body" :style="bodyStyle">
            <p v-if="displayList.length === 0" class="notif-empty">{{ t('moments.noMessages') }}</p>
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
                  referrerpolicy="no-referrer"
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
                  <n-icon :component="getNotificationIcon(notif.type)" :size="18" />
                </div>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.notif-popover-wrap {
  position: fixed;
  z-index: 10050;
  transform-origin: top center;
}

.notif-popover {
  position: relative;
  width: 100%;
  background: var(--lx-bg-card);
  border-radius: 12px;
  box-shadow:
    0 8px 28px rgba(0, 0, 0, 0.14),
    0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.notif-popover::before {
  content: '';
  position: absolute;
  top: -6px;
  left: var(--arrow-left, 50%);
  width: 12px;
  height: 12px;
  margin-left: -6px;
  background: var(--lx-bg-card);
  transform: rotate(45deg);
  box-shadow: -2px -2px 4px rgba(0, 0, 0, 0.04);
}

.notif-popover-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px 40px 12px;
  border-bottom: 1px solid var(--lx-border-light);
}

.more-menu-wrap {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
}

.more-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 220px;
  background: var(--lx-bg-card);
  border-radius: 10px;
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.12),
    0 2px 8px rgba(0, 0, 0, 0.06);
  padding: 6px 0;
  z-index: 20;
}

.more-menu::before {
  content: '';
  position: absolute;
  top: -5px;
  right: 10px;
  width: 10px;
  height: 10px;
  background: var(--lx-bg-card);
  transform: rotate(45deg);
  box-shadow: -2px -2px 4px rgba(0, 0, 0, 0.04);
}

.more-menu-divider {
  height: 1px;
  margin: 4px 0;
  background: var(--lx-border-light);
}

.more-menu-item {
  display: block;
  width: 100%;
  padding: 10px 16px;
  border: none;
  background: transparent;
  color: var(--lx-text-body);
  font-size: 14px;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
}

.more-menu-item:hover {
  background: var(--lx-bg-hover);
}

.more-menu-item.active {
  color: var(--lx-accent);
  font-weight: 500;
}

.more-menu-enter-active,
.more-menu-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
}

.more-menu-enter-from,
.more-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.more-btn.active {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.notif-popover-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
  text-align: center;
  line-height: 1.3;
}

.more-btn {
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

.more-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.notif-popover-body {
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.notif-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 48px 20px;
  font-size: 14px;
  color: var(--lx-text-muted);
  text-align: center;
}

.notif-list {
  list-style: none;
  margin: 0;
  padding: 4px 0 8px;
}

.notif-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.notif-row:hover {
  background: var(--lx-bg-hover);
}

.notif-row.unread {
  background: rgba(18, 183, 245, 0.05);
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
  width: 36px;
  height: 36px;
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
  padding: 4px 8px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 48px;
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
  opacity: 0.85;
}

.notif-popover-enter-active,
.notif-popover-leave-active {
  transition:
    opacity 0.16s ease,
    transform 0.16s ease;
}

.notif-popover-enter-from,
.notif-popover-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.97);
}

.notif-popover-enter-to,
.notif-popover-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
}
</style>
