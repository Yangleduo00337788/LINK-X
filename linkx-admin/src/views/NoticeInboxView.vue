<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NDataTable, NEmpty, NModal, NSpace, NTag, type DataTableColumns } from 'naive-ui'
import { listNoticeInbox, type NoticeItem } from '@/api/notices'
import { formatTime } from '@/utils/format'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'
import { onAdminRealtimeEvent } from '@/api/realtime'

const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<NoticeItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
})

const showDetail = ref(false)
const detailItem = ref<NoticeItem | null>(null)

let offRealtime: (() => void) | null = null

const columns = computed<DataTableColumns<NoticeItem>>(() => {
  void locale.value
  return [
    {
      title: t('notice.title'),
      key: 'title',
      ellipsis: { tooltip: true },
      render: (row) =>
        h(
          'a',
          {
            class: 'inbox-title-link',
            href: 'javascript:;',
            onClick: (e: Event) => {
              e.preventDefault()
              openDetail(row)
            },
          },
          row.title
        ),
    },
    {
      title: t('notice.content'),
      key: 'content',
      ellipsis: { tooltip: true },
      render: (row) => abbreviate(row.content, 80),
    },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: () => h(NTag, { type: 'success', size: 'small' }, () => t('notice.published')),
    },
    {
      title: t('notice.publishedAt'),
      key: 'publishedAt',
      width: 170,
      render: (row) => formatTime(row.publishedAt),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 100,
      render: (row) =>
        h(
          NButton,
          { text: true, type: 'primary', size: 'small', onClick: () => openDetail(row) },
          () => t('common.detail')
        ),
    },
  ]
})

function abbreviate(text?: string, max = 80) {
  const t0 = (text || '').trim()
  if (t0.length <= max) return t0 || '-'
  return `${t0.slice(0, max)}…`
}

function openDetail(row: NoticeItem) {
  detailItem.value = row
  showDetail.value = true
}

async function load() {
  loading.value = true
  try {
    const data = await listNoticeInbox({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    items.value = data?.items || []
    total.value = data?.total || 0
  } catch {
    /* interceptor already toasts */
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
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
  <div class="page">
    <div class="page-shell">
      <p class="inbox-hint">{{ t('notice.inboxHint') }}</p>
      <NSpace class="page-toolbar">
        <SearchAutoComplete
          v-model="query.keyword"
          :placeholder="t('notice.searchPlaceholder')"
          width="260px"
          @search="search"
        />
        <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
        :scroll-x="900"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [10, 20, 50],
          onUpdatePage: (p: number) => {
            query.page = p
            load()
          },
          onUpdatePageSize: (s: number) => {
            query.size = s
            query.page = 1
            load()
          },
        }"
        remote
      >
        <template #empty>
          <NEmpty :description="t('notice.inboxEmpty')" />
        </template>
      </NDataTable>
    </div>

    <NModal
      v-model:show="showDetail"
      preset="card"
      :title="t('notice.inboxDetailTitle')"
      style="width: 560px; max-width: 92vw"
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
    </NModal>
  </div>
</template>

<style scoped>
.page {
  padding: 0;
}
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.inbox-hint {
  margin: 0;
  font-size: 13px;
  color: var(--n-text-color-3);
  line-height: 1.5;
}
.page-toolbar {
  margin-bottom: 4px;
}
.inbox-title-link {
  color: var(--n-primary-color);
  text-decoration: none;
}
.inbox-title-link:hover {
  text-decoration: underline;
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
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  max-height: 360px;
  overflow: auto;
}
</style>
