<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NDataTable,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  closeFeedback,
  listFeedback,
  reopenFeedback,
  replyFeedback,
  type FeedbackItem,
} from '@/api/feedback'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()

const loading = ref(false)
const items = ref<FeedbackItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', status: '' as string })

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待处理', value: 'pending' },
  { label: '已回复', value: 'replied' },
  { label: '已关闭', value: 'closed' },
]

const showReply = ref(false)
const replyTarget = ref<FeedbackItem | null>(null)
const replyContent = ref('')
const replySaving = ref(false)

function statusTag(status?: string) {
  const map: Record<string, 'warning' | 'success' | 'default'> = {
    pending: 'warning',
    replied: 'success',
    closed: 'default',
  }
  const label: Record<string, string> = {
    pending: '待处理',
    replied: '已回复',
    closed: '已关闭',
  }
  return h(NTag, { type: map[status || ''] || 'default', size: 'small' }, () => label[status || ''] || status || '-')
}

const columns: DataTableColumns<FeedbackItem> = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '用户', key: 'username', width: 120 },
  { title: '类型', key: 'type', width: 100 },
  { title: '内容', key: 'content', ellipsis: { tooltip: true } },
  { title: '联系方式', key: 'contact', width: 140, ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', width: 100, render: (row) => statusTag(row.status) },
  { title: '时间', key: 'createTime', width: 170, render: (row) => formatTime(row.createTime) },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) =>
      h(NSpace, { size: 8 }, () => [
        auth.hasPermission('admin:feedback:reply') && row.status !== 'closed'
          ? h(NButton, { size: 'tiny', onClick: () => openReply(row) }, () => '回复')
          : null,
        auth.hasPermission('admin:feedback:close') && row.status !== 'closed'
          ? h(
              NButton,
              {
                size: 'tiny',
                onClick: () =>
                  dialog.warning({
                    title: '关闭反馈',
                    content: '确定关闭该反馈吗？',
                    positiveText: '关闭',
                    negativeText: '取消',
                    onPositiveClick: async () => {
                      await closeFeedback(row.id)
                      message.success('已关闭')
                      await load()
                    },
                  }),
              },
              () => '关闭',
            )
          : null,
        auth.hasPermission('admin:feedback:reopen') && row.status === 'closed'
          ? h(
              NButton,
              {
                size: 'tiny',
                onClick: async () => {
                  await reopenFeedback(row.id)
                  message.success('已重新打开')
                  await load()
                },
              },
              () => '重开',
            )
          : null,
      ]),
  },
]

function openReply(row: FeedbackItem) {
  replyTarget.value = row
  replyContent.value = ''
  showReply.value = true
}

async function submitReply() {
  if (!replyTarget.value || !replyContent.value.trim()) {
    message.warning('请输入回复内容')
    return
  }
  replySaving.value = true
  try {
    await replyFeedback(replyTarget.value.id, replyContent.value.trim())
    message.success('回复成功')
    showReply.value = false
    await load()
  } finally {
    replySaving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await listFeedback({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
    })
    items.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">反馈管理</h1>
    </div>
    <div class="page-card">
      <NSpace style="margin-bottom: 16px">
        <NInput
          v-model:value="query.keyword"
          clearable
          placeholder="搜索内容/用户"
          style="width: 220px"
          @keyup.enter="() => { query.page = 1; load() }"
        />
        <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
        <NButton type="primary" @click="() => { query.page = 1; load() }">查询</NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
        :scroll-x="1100"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          onUpdatePage: (p: number) => { query.page = p; load() },
          onUpdatePageSize: (s: number) => { query.size = s; query.page = 1; load() },
        }"
        remote
      />
    </div>

    <NModal v-model:show="showReply" preset="card" title="回复反馈" style="width: 520px">
      <p style="color: #a8b0bd; margin-top: 0">{{ replyTarget?.content }}</p>
      <NInput v-model:value="replyContent" type="textarea" :rows="4" placeholder="请输入回复内容" />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showReply = false">取消</NButton>
          <NButton type="primary" :loading="replySaving" @click="submitReply">提交</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
