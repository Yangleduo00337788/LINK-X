<!-- 作者：yangleduo -->
<script setup lang="ts">
import AdminFormShell from '@/components/AdminFormShell.vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NBadge, NButton, NEmpty, NIcon, NModal, NPopover, NSpin, NTooltip } from 'naive-ui'
import { NotificationsOutline } from '@vicons/ionicons5'
import { listNoticeInbox, type NoticeItem } from '@/api/notices'
import { onAdminRealtimeEvent } from '@/api/realtime'
import { useAuthStore } from '@/stores/auth'
import { formatTime } from '@/utils/format'

const SEEN_KEY = 'linkx_admin_notice_seen_ids'

const { t } = useI18n()
const auth = useAuthStore()

const show = ref(false)
const loading = ref(false)
const items = ref<NoticeItem[]>([])
const detailItem = ref<NoticeItem | null>(null)
const showDetail = ref(false)
const seenIds = ref<Set<string>>(loadSeen())

let offRealtime: (() => void) | null = null

const canView = computed(() => auth.hasPermission('admin:notice:inbox'))

const unreadCount = computed(
  () => items.value.filter((n) => n.id && !seenIds.value.has(String(n.id))).length
)

function loadSeen(): Set<string> {
  try {
    const raw = localStorage.getItem(SEEN_KEY)
    if (!raw) return new Set()
    const arr = JSON.parse(raw) as unknown
    if (!Array.isArray(arr)) return new Set()
    return new Set(arr.map((x) => String(x)))
  } catch {
    return new Set()
  }
}

function persistSeen() {
  localStorage.setItem(SEEN_KEY, JSON.stringify([...seenIds.value]))
}

function markAllSeen() {
  const next = new Set(seenIds.value)
  for (const n of items.value) {
    if (n.id) next.add(String(n.id))
  }
  seenIds.value = next
  persistSeen()
}

function pruneSeen(activeIds: string[]) {
  const active = new Set(activeIds)
  const next = new Set<string>()
  for (const id of seenIds.value) {
    if (active.has(id)) next.add(id)
  }
  seenIds.value = next
  persistSeen()
}

function abbreviate(text?: string, max = 72) {
  const s = (text || '').trim().replace(/\s+/g, ' ')
  if (!s) return ''
  return s.length <= max ? s : `${s.slice(0, max)}…`
}

async function load() {
  if (!canView.value) return
  loading.value = true
  try {
    const data = await listNoticeInbox({ page: 1, size: 50 })
    items.value = data?.items || []
    pruneSeen(items.value.map((n) => String(n.id)))
  } catch {
    /* interceptor toasts */
  } finally {
    loading.value = false
  }
}

function onShowUpdate(v: boolean) {
  show.value = v
  if (v) {
    void load().then(() => markAllSeen())
  }
}

function openDetail(row: NoticeItem) {
  detailItem.value = row
  showDetail.value = true
  show.value = false
}

onMounted(() => {
  void load()
  offRealtime = onAdminRealtimeEvent((evt) => {
    if (!evt?.type) return
    const type = String(evt.type)
    if (
      type === 'admin_notice_published' ||
      type === 'admin_notice_unpublished' ||
      type === 'notice_published' ||
      type === 'notice_unpublished' ||
      type === 'notice_deleted'
    ) {
      void load()
    }
  })
})

onUnmounted(() => {
  offRealtime?.()
  offRealtime = null
})
</script>

<template>
  <NPopover
    v-if="canView"
    :show="show"
    trigger="click"
    placement="bottom-end"
    :width="380"
    display-directive="show"
    @update:show="onShowUpdate"
  >
    <template #trigger>
      <NTooltip>
        <template #trigger>
          <NBadge :value="unreadCount" :max="99" :show-zero="false">
            <NButton quaternary circle class="lx-float-btn header-action-btn" aria-label="notices">
              <template #icon>
                <NIcon :component="NotificationsOutline" :size="18" />
              </template>
            </NButton>
          </NBadge>
        </template>
        {{ t('notice.bellTooltip') }}
      </NTooltip>
    </template>

    <div class="notice-panel">
      <div class="notice-panel__head">
        <span class="notice-panel__title">{{ t('notice.bellTitle') }}</span>
        <span v-if="items.length" class="notice-panel__count">{{ items.length }}</span>
      </div>
      <NSpin :show="loading" size="small">
        <div class="notice-panel__list">
          <template v-if="items.length">
            <button
              v-for="row in items"
              :key="row.id"
              type="button"
              class="notice-item"
              :class="{ 'notice-item--unread': row.id && !seenIds.has(String(row.id)) }"
              @click="openDetail(row)"
            >
              <div class="notice-item__title">{{ row.title }}</div>
              <div class="notice-item__preview">{{ abbreviate(row.content) }}</div>
              <div class="notice-item__time">{{ formatTime(row.publishedAt) }}</div>
            </button>
          </template>
          <NEmpty
            v-else
            :description="t('notice.inboxEmpty')"
            size="small"
            class="notice-panel__empty"
          />
        </div>
      </NSpin>
    </div>
  </NPopover>

  <AdminFormShell
    v-model:show="showDetail"
    :title="t('notice.inboxDetailTitle')"
    :width="520"
  >
    <template v-if="detailItem">
      <div class="notice-detail">
        <div class="notice-detail__row">
          <span class="notice-detail__label">{{ t('notice.title') }}</span>
          <span>{{ detailItem.title }}</span>
        </div>
        <div class="notice-detail__row">
          <span class="notice-detail__label">{{ t('notice.publishedAt') }}</span>
          <span>{{ formatTime(detailItem.publishedAt) }}</span>
        </div>
        <div class="notice-detail__content">
          <div class="notice-detail__label">{{ t('notice.content') }}</div>
          <pre class="notice-detail__body">{{ detailItem.content }}</pre>
        </div>
      </div>
    </template>
  </AdminFormShell>
</template>

<style scoped>
.notice-panel {
  margin: -4px -8px;
}
.notice-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px 10px;
  border-bottom: 1px solid var(--n-border-color);
}
.notice-panel__title {
  font-weight: 600;
  font-size: 14px;
}
.notice-panel__count {
  font-size: 12px;
  color: var(--n-text-color-3);
}
.notice-panel__list {
  max-height: 360px;
  overflow: auto;
  min-height: 80px;
}
.notice-panel__empty {
  padding: 24px 0;
}
.notice-item {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 12px 14px;
  cursor: pointer;
  border-bottom: 1px solid var(--n-divider-color);
  color: inherit;
  transition: background 0.15s ease;
}
.notice-item:last-child {
  border-bottom: 0;
}
.notice-item:hover {
  background: var(--n-color-hover);
}
.notice-item--unread .notice-item__title::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  margin-bottom: 1px;
  border-radius: 50%;
  background: var(--n-primary-color);
  vertical-align: middle;
}
.notice-item__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 4px;
}
.notice-item__preview {
  font-size: 12px;
  color: var(--n-text-color-3);
  line-height: 1.45;
  margin-bottom: 6px;
}
.notice-item__time {
  font-size: 11px;
  color: var(--n-text-color-disabled);
}
.notice-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.notice-detail__row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  line-height: 1.5;
}
.notice-detail__label {
  flex: 0 0 72px;
  color: var(--n-text-color-3);
  font-size: 13px;
}
.notice-detail__content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.notice-detail__body {
  margin: 0;
  padding: 12px;
  background: var(--n-color-embedded);
  border-radius: var(--lx-radius);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  max-height: 360px;
  overflow: auto;
}
</style>
