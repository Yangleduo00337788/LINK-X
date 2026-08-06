<script setup lang="ts">
/**
 * 消息页「LinkX官方」：微信团队式服务通知卡片流。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { NIcon, NDropdown, useMessage, useDialog, type DropdownOption } from 'naive-ui'
import {
  CheckmarkDoneOutline,
  TrashOutline,
  EllipsisHorizontalOutline,
  ChevronForwardOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../stores/notifications'
import EmptyState from './common/EmptyState.vue'
import BrandMarkIcon from './BrandMarkIcon.vue'
import type { MessageNotification } from '../stores/notifications'
import { resolveNoteMediaUrl } from '../api/note'
import { normalizeMediaUrl } from '../utils/mediaUrl'
import { useI18n } from '../i18n'
import {
  buildOfficialNotifyViewModel,
  formatOfficialDividerTime,
  type OfficialBodyPart,
  type OfficialNotifyViewModel
} from '../utils/officialNotifyContent'
import { openOfficialNotifyDetail } from '../utils/openOfficialNotifyDetail'

const message = useMessage()
const dialog = useDialog()
const { t } = useI18n()
const notificationsStore = useNotificationsStore()

const { officialNotifs } = storeToRefs(notificationsStore)
const {
  fetchMessageNotifications,
  markMessageAsRead,
  markOfficialNotifsAsRead,
  deleteMessageNotification,
  clearOfficialNotifs
} = notificationsStore

onMounted(() => {
  void fetchMessageNotifications()
})

const resolvedEvidenceUrls = ref<Record<string, string>>({})

function notificationToFeedItem(notif: MessageNotification): OfficialNotifyViewModel {
  return buildOfficialNotifyViewModel(notif, t)
}

const feedItems = computed<OfficialNotifyViewModel[]>(() => {
  const sorted = [...officialNotifs.value].sort(
    (a, b) => Date.parse(a.createTime) - Date.parse(b.createTime)
  )
  return sorted.map(notificationToFeedItem)
})

watch(
  feedItems,
  list => {
    const parts = list.flatMap(m => m.images)
    void resolveEvidenceKeys(parts)
  },
  { immediate: true, deep: true }
)

async function resolveEvidenceKeys(parts: OfficialBodyPart[]) {
  const keys = parts
    .filter(p => p.kind === 'image' && p.key && !resolvedEvidenceUrls.value[p.key])
    .map(p => p.key!)
  if (!keys.length) return
  await Promise.all(
    keys.map(async key => {
      try {
        const res = await resolveNoteMediaUrl(key)
        const url = normalizeMediaUrl(res.data) || res.data || ''
        if (res.code === 200 && url) {
          resolvedEvidenceUrls.value = { ...resolvedEvidenceUrls.value, [key]: url }
        }
      } catch {
        /* ignore */
      }
    })
  )
}

async function onClickItem(item: OfficialNotifyViewModel) {
  if (item.unread) {
    void markMessageAsRead(item.notifId)
  }
}

function openDetail(item: OfficialNotifyViewModel) {
  void onClickItem(item)
  openOfficialNotifyDetail(item.notifId)
}

async function markAllRead() {
  await markOfficialNotifsAsRead()
  message.success(t('chat.markedAllRead'))
}

async function clearOne(item: OfficialNotifyViewModel, e: Event) {
  e.stopPropagation()
  await deleteMessageNotification(item.notifId)
}

const headerMoreOptions = computed<DropdownOption[]>(() => [
  { label: t('chat.markRead'), key: 'markRead' },
  { label: t('chat.officialClearAll'), key: 'clearAll' }
])

function onHeaderMoreSelect(key: string) {
  if (key === 'markRead') {
    void markAllRead()
    return
  }
  if (key === 'clearAll') {
    const count = officialNotifs.value.length
    if (count === 0) {
      message.info(t('chat.officialNothingToClear'))
      return
    }
    dialog.warning({
      title: t('chat.officialClearAll'),
      content: t('chat.officialClearConfirm', { n: count }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        const cleared = await clearOfficialNotifs()
        if (cleared > 0) {
          message.success(t('chat.officialClearedCount', { n: cleared }))
        } else {
          message.warning(t('chat.officialNothingToClear'))
        }
      }
    })
  }
}
</script>

<template>
  <div class="official-notify-panel">
    <header class="header">
      <div class="title-wrap">
        <BrandMarkIcon :size="28" />
        <h2 class="title">{{ t('chat.officialSession') }}</h2>
      </div>
      <div class="actions">
        <button type="button" class="action-btn" :title="t('chat.markRead')" @click="markAllRead">
          <n-icon :component="CheckmarkDoneOutline" :size="18" />
        </button>
        <n-dropdown trigger="click" :options="headerMoreOptions" @select="onHeaderMoreSelect">
          <button type="button" class="action-btn" :title="t('chat.officialMore')">
            <n-icon :component="EllipsisHorizontalOutline" :size="18" />
          </button>
        </n-dropdown>
      </div>
    </header>

    <div class="content">
      <EmptyState
        v-if="feedItems.length === 0"
        :title="t('chat.noOfficial')"
        :description="t('chat.officialEmptyDesc')"
      />
      <div v-else class="feed-scroll">
        <div v-for="item in feedItems" :key="item.id" class="feed-block">
          <div class="feed-divider-time">{{ formatOfficialDividerTime(item.time) }}</div>
          <div class="feed-card" :class="{ unread: item.unread }">
            <div class="feed-card-main" @click="onClickItem(item)">
              <h3 class="feed-card-title">{{ item.title }}</h3>
              <p v-if="item.dateLabel" class="feed-card-date">{{ item.dateLabel }}</p>
              <p v-if="item.body" class="feed-card-body">{{ item.body }}</p>
              <dl v-if="item.fields.length" class="feed-fields">
                <div v-for="(field, idx) in item.fields" :key="idx" class="feed-field-row">
                  <dt>{{ field.label }}：</dt>
                  <dd>{{ field.value }}</dd>
                </div>
              </dl>
              <p v-if="item.footerHint" class="feed-footer-hint">{{ item.footerHint }}</p>
              <div v-if="item.images.length" class="feed-images">
                <a
                  v-for="(img, idx) in item.images"
                  :key="idx"
                  class="evidence-thumb"
                  :href="img.key ? resolvedEvidenceUrls[img.key] : undefined"
                  target="_blank"
                  rel="noopener noreferrer"
                  @click.stop
                >
                  <img
                    v-if="img.key && resolvedEvidenceUrls[img.key]"
                    :src="resolvedEvidenceUrls[img.key]"
                    alt=""
                  />
                </a>
              </div>
            </div>
            <button type="button" class="feed-detail-row" @click.stop="openDetail(item)">
              <span>{{ t('chat.officialDetail') }}</span>
              <n-icon :component="ChevronForwardOutline" :size="16" />
            </button>
            <button
              type="button"
              class="delete-btn"
              :title="t('common.delete')"
              @click="clearOne(item, $event)"
            >
              <n-icon :component="TrashOutline" :size="15" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.official-notify-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ededed;
}

:global([data-theme='dark']) .official-notify-panel {
  background: var(--lx-bg-window, #1a1a1a);
}

.header {
  min-height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #e0e0e0;
  background: #f7f7f7;
}

:global([data-theme='dark']) .header {
  background: var(--lx-bg-panel, #222);
  border-bottom-color: var(--lx-divider);
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--lx-text-primary);
}

.actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
}

.action-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: var(--lx-text-primary);
}

.content {
  flex: 1;
  overflow: auto;
  padding: 16px 12px 24px;
  background: #ededed;
}

:global([data-theme='dark']) .content {
  background: var(--lx-bg-window, #1a1a1a);
}

.feed-scroll {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.feed-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.feed-divider-time {
  font-size: 12px;
  color: #b2b2b2;
  text-align: center;
  line-height: 1.4;
}

.feed-card {
  position: relative;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

:global([data-theme='dark']) .feed-card {
  background: var(--lx-bg-card, #2a2a2a);
  box-shadow: none;
  border: 1px solid var(--lx-divider);
}

.feed-card.unread .feed-card-title::after {
  content: '';
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-left: 6px;
  border-radius: 50%;
  background: #fa5151;
  vertical-align: middle;
}

.feed-card-main {
  padding: 16px 16px 12px;
  cursor: default;
}

.feed-card-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.35;
  color: #111;
}

:global([data-theme='dark']) .feed-card-title {
  color: var(--lx-text-primary);
}

.feed-card-date {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.4;
  color: #b2b2b2;
}

.feed-card-body {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.55;
  color: #333;
  word-break: break-word;
}

:global([data-theme='dark']) .feed-card-body {
  color: var(--lx-text-body, #ddd);
}

.feed-fields {
  margin: 12px 0 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.feed-field-row {
  display: flex;
  gap: 0;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
}

:global([data-theme='dark']) .feed-field-row {
  color: var(--lx-text-body, #ddd);
}

.feed-field-row dt {
  flex-shrink: 0;
  margin: 0;
  font-weight: 400;
}

.feed-field-row dd {
  margin: 0;
  flex: 1;
  word-break: break-word;
}

.feed-footer-hint {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: #b2b2b2;
}

.feed-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.evidence-thumb {
  display: inline-block;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e5e5e5;
  line-height: 0;
}

.evidence-thumb img {
  width: 100px;
  height: 100px;
  object-fit: cover;
  display: block;
}

.feed-detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 12px 16px;
  border: none;
  border-top: 1px solid #ededed;
  background: #fff;
  font-size: 14px;
  color: #333;
  cursor: pointer;
}

:global([data-theme='dark']) .feed-detail-row {
  background: var(--lx-bg-card, #2a2a2a);
  border-top-color: var(--lx-divider);
  color: var(--lx-text-body);
}

.feed-detail-row:hover {
  background: #f7f7f7;
}

:global([data-theme='dark']) .feed-detail-row:hover {
  background: rgba(255, 255, 255, 0.04);
}

.delete-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.9);
  color: #b2b2b2;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.feed-card:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: var(--lx-danger, #e34d59);
}
</style>
