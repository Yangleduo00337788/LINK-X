<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  NButton,
  NDataTable,
  NInput,
  NModal,
  NSpace,
  NTabPane,
  NTabs,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import ApprovalInstanceDetailPanel from '@/components/ApprovalInstanceDetailPanel.vue'
import {
  approveApprovalRecord,
  getApprovalInstance,
  listApprovalCc,
  listApprovalInbox,
  rejectApprovalRecord,
  type ApprovalInboxItem,
  type ApprovalInstance,
} from '@/api/approvals'
import { useApprovalDisplay } from '@/composables/useApprovalDisplay'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const { t, locale } = useI18n()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()
const { nodeTypeLabel } = useApprovalDisplay()

type TabKey = 'pending' | 'cc'

const activeTab = ref<TabKey>('pending')
const loading = ref(false)
const items = ref<ApprovalInboxItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20 })

const showDetail = ref(false)
const detailLoading = ref(false)
const current = ref<ApprovalInboxItem | null>(null)
const instance = ref<ApprovalInstance | null>(null)
const comment = ref('')
const acting = ref(false)

const pageDesc = computed(() =>
  activeTab.value === 'cc' ? t('approvalInbox.ccSubtitle') : t('approvalInbox.subtitle')
)

const columns = computed<DataTableColumns<ApprovalInboxItem>>(() => {
  void locale.value
  const base: DataTableColumns<ApprovalInboxItem> = [
    { title: t('approvalInbox.titleCol'), key: 'title', minWidth: 180, ellipsis: { tooltip: true } },
    { title: t('approvalInbox.flow'), key: 'flowName', width: 120 },
    { title: t('approvalInbox.step'), key: 'stepName', width: 120 },
    {
      title: t('approvalInbox.nodeType'),
      key: 'nodeType',
      width: 100,
      render: (row) => nodeTypeLabel(row.nodeType),
    },
    { title: t('approvalInbox.applicant'), key: 'applicantName', width: 100 },
  ]
  if (activeTab.value === 'cc') {
    base.push({
      title: t('approvalInbox.ccTime'),
      key: 'createTime',
      width: 160,
      render: (row) => (row.createTime ? formatTime(row.createTime) : '-'),
    })
  } else {
    base.push({
      title: t('common.createTime'),
      key: 'createTime',
      width: 160,
      render: (row) => (row.createTime ? formatTime(row.createTime) : '-'),
    })
  }
  base.push({
    title: t('common.actions'),
    key: 'actions',
    width: activeTab.value === 'cc' ? 100 : 220,
    render: (row) =>
      h(NSpace, { size: 4 }, () => [
        h(NButton, { size: 'tiny', onClick: () => openDetail(row) }, () => t('common.detail')),
        activeTab.value === 'pending' && auth.hasPermission('admin:approval:action')
          ? h(
              NButton,
              { size: 'tiny', type: 'primary', onClick: () => doApprove(row) },
              () => t('approvalInbox.approve')
            )
          : null,
        activeTab.value === 'pending' && auth.hasPermission('admin:approval:action')
          ? h(
              NButton,
              { size: 'tiny', type: 'error', tertiary: true, onClick: () => doReject(row) },
              () => t('approvalInbox.reject')
            )
          : null,
      ]),
  })
  return base
})

async function load() {
  loading.value = true
  try {
    const res =
      activeTab.value === 'cc'
        ? await listApprovalCc({ page: query.page, size: query.size })
        : await listApprovalInbox({ page: query.page, size: query.size })
    items.value = res.items || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function switchTab(tab: TabKey) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  query.page = 1
  void load()
}

async function openDetail(row: ApprovalInboxItem) {
  current.value = row
  comment.value = ''
  showDetail.value = true
  detailLoading.value = true
  try {
    instance.value = await getApprovalInstance(row.instanceId)
  } finally {
    detailLoading.value = false
  }
}

async function doApprove(row: ApprovalInboxItem) {
  acting.value = true
  try {
    await approveApprovalRecord(row.recordId, comment.value || undefined)
    message.success(t('approvalInbox.approveSuccess'))
    showDetail.value = false
    items.value = items.value.filter((i) => i.recordId !== row.recordId)
    total.value = Math.max(0, total.value - 1)
  } catch {
    /* 错误 toast 由 request 拦截器处理 */
  } finally {
    acting.value = false
  }
}

async function doReject(row: ApprovalInboxItem) {
  acting.value = true
  try {
    await rejectApprovalRecord(row.recordId, comment.value || undefined)
    message.success(t('approvalInbox.rejectSuccess'))
    showDetail.value = false
    items.value = items.value.filter((i) => i.recordId !== row.recordId)
    total.value = Math.max(0, total.value - 1)
  } catch {
    /* 错误 toast 由 request 拦截器处理 */
  } finally {
    acting.value = false
  }
}

function openBiz() {
  if (current.value?.bizType === 'review' && current.value.bizId) {
    void router.push({ path: '/admin/reviews', query: { keyword: current.value.bizId } })
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <div class="page-head">
        <div>
          <h1 class="page-title">{{ t('route.approvalInbox') }}</h1>
          <p class="page-desc">{{ pageDesc }}</p>
        </div>
        <NButton size="small" @click="load">{{ t('common.refresh') }}</NButton>
      </div>

      <NTabs type="line" :value="activeTab" @update:value="(v) => switchTab(v as TabKey)">
        <NTabPane name="pending" :tab="t('approvalInbox.tabPending')" />
        <NTabPane name="cc" :tab="t('approvalInbox.tabCc')" />
      </NTabs>

      <NDataTable
        class="inbox-table"
        :loading="loading"
        :columns="columns"
        :data="items"
        :bordered="false"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          onUpdatePage: (p: number) => {
            query.page = p
            void load()
          },
        }"
      />
    </div>

    <NModal
      v-model:show="showDetail"
      preset="card"
      style="width: 720px; max-width: 96vw"
      :title="current?.title || t('approvalInbox.detailTitle')"
    >
      <div v-if="detailLoading" class="loading-hint">{{ t('common.loading') }}</div>
      <template v-else>
        <NInput
          v-if="activeTab === 'pending'"
          v-model:value="comment"
          type="textarea"
          :placeholder="t('approvalInbox.commentPlaceholder')"
          :rows="2"
          class="comment-input"
        />
        <ApprovalInstanceDetailPanel
          :instance="instance"
          :current="current"
          :acting="acting"
          :show-actions="activeTab === 'pending'"
          :can-action="auth.hasPermission('admin:approval:action')"
          @approve="current && doApprove(current)"
          @reject="current && doReject(current)"
          @open-biz="openBiz"
        />
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.page-desc {
  margin: 6px 0 0;
  color: var(--lx-text-2);
  font-size: 13px;
}

.inbox-table {
  margin-top: 8px;
}

.comment-input {
  margin-bottom: 12px;
}

.loading-hint {
  color: var(--lx-text-3);
  font-size: 13px;
}
</style>
