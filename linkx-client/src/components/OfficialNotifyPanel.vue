<!-- 作者：yangleduo -->
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
import '../styles/notifyFeed.css'

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
  <div class="notify-feed-panel">
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
      <div v-else class="notify-feed-scroll">
        <div v-for="item in feedItems" :key="item.id" class="notify-feed-block">
          <div class="notify-feed-divider-time">{{ formatOfficialDividerTime(item.time) }}</div>
          <div class="notify-feed-card" :class="{ unread: item.unread }">
            <div class="notify-feed-card-main" @click="onClickItem(item)">
              <h3 class="notify-feed-card-title">{{ item.title }}</h3>
              <p v-if="item.dateLabel" class="notify-feed-card-date">{{ item.dateLabel }}</p>
              <p v-if="item.body" class="notify-feed-card-body">{{ item.body }}</p>
              <dl v-if="item.fields.length" class="notify-feed-fields">
                <div v-for="(field, idx) in item.fields" :key="idx" class="notify-feed-field-row">
                  <dt>{{ field.label }}：</dt>
                  <dd>{{ field.value }}</dd>
                </div>
              </dl>
              <p v-if="item.footerHint" class="notify-feed-footer-hint">{{ item.footerHint }}</p>
              <div v-if="item.images.length" class="notify-feed-images">
                <a
                  v-for="(img, idx) in item.images"
                  :key="idx"
                  class="notify-feed-evidence-thumb"
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
            <button type="button" class="notify-feed-detail-row" @click.stop="openDetail(item)">
              <span>{{ t('chat.officialDetail') }}</span>
              <n-icon :component="ChevronForwardOutline" :size="16" />
            </button>
            <button
              type="button"
              class="notify-feed-delete-btn"
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
