<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NImage,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTabPane,
  NTabs,
  NTag,
  NTooltip,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  deleteShortVideoComment,
  deleteShortVideoPost,
  listShortVideoComments,
  listShortVideoPosts,
  type ShortVideoCommentItem,
  type ShortVideoPostItem,
} from '@/api/shortVideo'
import { formatTime } from '@/utils/format'
import { resolveShortVideoAdminMediaSrc } from '@/utils/mediaUrl'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const activeTab = ref<'posts' | 'comments'>('posts')

const postLoading = ref(false)
const postItems = ref<ShortVideoPostItem[]>([])
const postTotal = ref(0)
const postQuery = reactive({
  page: 1,
  size: 20,
  keyword: '',
  userId: '',
  visibility: '' as '' | number,
  transcodeStatus: '',
})

const commentLoading = ref(false)
const commentItems = ref<ShortVideoCommentItem[]>([])
const commentTotal = ref(0)
const commentQuery = reactive({
  page: 1,
  size: 20,
  keyword: '',
  postId: '',
  userId: '',
})

const previewPost = ref<ShortVideoPostItem | null>(null)
const showPreview = ref(false)

const visibilityOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('shortVideo.visibilityPublic'), value: 0 },
    { label: t('shortVideo.visibilityFriends'), value: 1 },
    { label: t('shortVideo.visibilityPrivate'), value: 2 },
  ]
})

const transcodeOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('shortVideo.transcodeSkipped'), value: 'skipped' },
    { label: t('shortVideo.transcodePending'), value: 'pending' },
    { label: t('shortVideo.transcodeProcessing'), value: 'processing' },
    { label: t('shortVideo.transcodeCompleted'), value: 'completed' },
    { label: t('shortVideo.transcodeFailed'), value: 'failed' },
  ]
})

const visibilityLabel = (v?: number) => {
  if (v === 1) return t('shortVideo.visibilityFriends')
  if (v === 2) return t('shortVideo.visibilityPrivate')
  return t('shortVideo.visibilityPublic')
}

const transcodeTagType = (status?: string) => {
  switch (status) {
    case 'completed':
      return 'success'
    case 'failed':
      return 'error'
    case 'processing':
      return 'warning'
    case 'pending':
      return 'info'
    default:
      return 'default'
  }
}

const transcodeLabel = (status?: string) => {
  switch (status) {
    case 'pending':
      return t('shortVideo.transcodePending')
    case 'processing':
      return t('shortVideo.transcodeProcessing')
    case 'completed':
      return t('shortVideo.transcodeCompleted')
    case 'failed':
      return t('shortVideo.transcodeFailed')
    default:
      return t('shortVideo.transcodeSkipped')
  }
}

function renderCoverCell(coverUrl: string | null | undefined, postId?: string | null) {
  const id = postId || '-'
  const src = resolveShortVideoAdminMediaSrc(coverUrl)
  const thumb = src
    ? h(NImage, {
        src,
        width: 48,
        height: 64,
        objectFit: 'cover',
        previewDisabled: true,
        style: 'border-radius: 4px; display: block;',
      })
    : h('div', { class: 'sv-admin-cover-ph' }, '—')
  return h(
    NTooltip,
    { placement: 'right' },
    {
      trigger: () => thumb,
      default: () => `${t('shortVideo.postId')}: ${id}`,
    }
  )
}

const previewVideoSrc = computed(() =>
  previewPost.value?.videoUrl ? resolveShortVideoAdminMediaSrc(previewPost.value.videoUrl) : ''
)

const postColumns = computed<DataTableColumns<ShortVideoPostItem>>(() => {
  void locale.value
  return [
    {
      title: t('shortVideo.cover'),
      key: 'coverUrl',
      width: 72,
      render: row => renderCoverCell(row.coverUrl, row.id),
    },
    {
      title: t('shortVideo.author'),
      key: 'nickname',
      width: 120,
      render: row => row.nickname || row.username || row.userId || '-',
    },
    {
      title: t('shortVideo.description'),
      key: 'description',
      minWidth: 160,
      ellipsis: { tooltip: true },
      render: row => row.description || '-',
    },
    {
      title: t('shortVideo.stats'),
      key: 'stats',
      width: 180,
      render: row =>
        `${t('shortVideo.plays')}: ${row.playCount ?? 0} / ${t('shortVideo.likes')}: ${row.likeCount ?? 0}`,
    },
    {
      title: t('shortVideo.visibility'),
      key: 'visibility',
      width: 90,
      render: row => visibilityLabel(row.visibility),
    },
    {
      title: t('shortVideo.transcode'),
      key: 'transcodeStatus',
      width: 100,
      render: row =>
        h(NTag, { size: 'small', type: transcodeTagType(row.transcodeStatus) }, () =>
          transcodeLabel(row.transcodeStatus)
        ),
    },
    {
      title: t('common.createTime'),
      key: 'createTime',
      width: 150,
      render: row => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: row =>
        h(NSpace, { size: 8 }, () => [
          auth.hasPermission('admin:short-video:view')
            ? h(
                NButton,
                { size: 'small', onClick: () => openPreview(row) },
                () => t('shortVideo.preview')
              )
            : null,
          auth.hasPermission('admin:short-video:delete')
            ? h(
                NButton,
                { size: 'small', type: 'error', onClick: () => confirmDeletePost(row) },
                () => t('shortVideo.takeDown')
              )
            : null,
        ]),
    },
  ]
})

const commentColumns = computed<DataTableColumns<ShortVideoCommentItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 170, ellipsis: { tooltip: true } },
    {
      title: t('shortVideo.postId'),
      key: 'postCoverUrl',
      width: 72,
      render: row => renderCoverCell(row.postCoverUrl, row.postId),
    },
    {
      title: t('shortVideo.author'),
      key: 'nickname',
      width: 120,
      render: row => row.nickname || row.username || row.userId || '-',
    },
    {
      title: t('shortVideo.commentContent'),
      key: 'content',
      minWidth: 200,
      ellipsis: { tooltip: true },
    },
    {
      title: t('shortVideo.likes'),
      key: 'likeCount',
      width: 80,
      render: row => row.likeCount ?? 0,
    },
    {
      title: t('common.createTime'),
      key: 'createTime',
      width: 150,
      render: row => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: row =>
        auth.hasPermission('admin:short-video:delete')
          ? h(
              NButton,
              { size: 'small', type: 'error', onClick: () => confirmDeleteComment(row) },
              () => t('common.delete')
            )
          : null,
    },
  ]
})

async function loadPosts() {
  postLoading.value = true
  try {
    const res = await listShortVideoPosts({
      page: postQuery.page,
      size: postQuery.size,
      keyword: postQuery.keyword || undefined,
      userId: postQuery.userId || undefined,
      visibility: postQuery.visibility === '' ? undefined : postQuery.visibility,
      transcodeStatus: postQuery.transcodeStatus || undefined,
    })
    postItems.value = res.items || []
    postTotal.value = res.total || 0
  } catch {
    message.error(t('shortVideo.loadPostsFail'))
  } finally {
    postLoading.value = false
  }
}

async function loadComments() {
  commentLoading.value = true
  try {
    const res = await listShortVideoComments({
      page: commentQuery.page,
      size: commentQuery.size,
      keyword: commentQuery.keyword || undefined,
      postId: commentQuery.postId || undefined,
      userId: commentQuery.userId || undefined,
    })
    commentItems.value = res.items || []
    commentTotal.value = res.total || 0
  } catch {
    message.error(t('shortVideo.loadCommentsFail'))
  } finally {
    commentLoading.value = false
  }
}

function searchPosts() {
  postQuery.page = 1
  void loadPosts()
}

function searchComments() {
  commentQuery.page = 1
  void loadComments()
}

function openPreview(row: ShortVideoPostItem) {
  previewPost.value = row
  showPreview.value = true
}

function confirmDeletePost(row: ShortVideoPostItem) {
  dialog.warning({
    title: t('shortVideo.takeDown'),
    content: t('shortVideo.takeDownConfirm', { id: row.id }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await deleteShortVideoPost(row.id)
      message.success(t('shortVideo.takeDownOk'))
      await loadPosts()
    },
  })
}

function confirmDeleteComment(row: ShortVideoCommentItem) {
  dialog.warning({
    title: t('common.delete'),
    content: t('shortVideo.deleteCommentConfirm', { id: row.id }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await deleteShortVideoComment(row.id)
      message.success(t('common.deleted'))
      await loadComments()
    },
  })
}

onMounted(() => {
  void loadPosts()
  void loadComments()
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NTabs v-model:value="activeTab" type="line" animated>
        <NTabPane name="posts" :tab="t('shortVideo.postsTab')">
          <NSpace vertical :size="12">
            <NSpace wrap>
              <SearchAutoComplete
                v-model="postQuery.keyword"
                :placeholder="t('shortVideo.searchPosts')"
                style="width: 220px"
                @search="searchPosts"
              />
              <NInput
                v-model:value="postQuery.userId"
                :placeholder="t('shortVideo.authorId')"
                clearable
                style="width: 160px"
              />
              <NSelect
                v-model:value="postQuery.visibility"
                :options="visibilityOptions"
                style="width: 120px"
              />
              <NSelect
                v-model:value="postQuery.transcodeStatus"
                :options="transcodeOptions"
                style="width: 130px"
              />
              <NButton type="primary" @click="searchPosts">{{ t('common.search') }}</NButton>
            </NSpace>
            <NDataTable
              :columns="postColumns"
              :data="postItems"
              :loading="postLoading"
              :scroll-x="1000"
              :pagination="{
                page: postQuery.page,
                pageSize: postQuery.size,
                itemCount: postTotal,
                onUpdatePage: (p: number) => { postQuery.page = p; loadPosts() },
                onUpdatePageSize: (s: number) => { postQuery.size = s; postQuery.page = 1; loadPosts() },
              }"
            />
          </NSpace>
        </NTabPane>

        <NTabPane name="comments" :tab="t('shortVideo.commentsTab')">
          <NSpace vertical :size="12">
            <NSpace wrap>
              <SearchAutoComplete
                v-model="commentQuery.keyword"
                :placeholder="t('shortVideo.searchComments')"
                style="width: 220px"
                @search="searchComments"
              />
              <NInput
                v-model:value="commentQuery.postId"
                :placeholder="t('shortVideo.postId')"
                clearable
                style="width: 170px"
              />
              <NInput
                v-model:value="commentQuery.userId"
                :placeholder="t('shortVideo.authorId')"
                clearable
                style="width: 160px"
              />
              <NButton type="primary" @click="searchComments">{{ t('common.search') }}</NButton>
            </NSpace>
            <NDataTable
              :columns="commentColumns"
              :data="commentItems"
              :loading="commentLoading"
              :scroll-x="900"
              :pagination="{
                page: commentQuery.page,
                pageSize: commentQuery.size,
                itemCount: commentTotal,
                onUpdatePage: (p: number) => { commentQuery.page = p; loadComments() },
                onUpdatePageSize: (s: number) => { commentQuery.size = s; commentQuery.page = 1; loadComments() },
              }"
            />
          </NSpace>
        </NTabPane>
      </NTabs>

      <NModal
        v-model:show="showPreview"
        preset="card"
        :title="t('shortVideo.preview')"
        style="width: min(520px, 92vw)"
      >
        <template v-if="previewPost">
          <p class="sv-admin-preview__meta">
            {{ previewPost.nickname || previewPost.username }} · {{ formatTime(previewPost.createTime) }}
          </p>
          <p class="sv-admin-preview__desc">{{ previewPost.description || t('shortVideo.noDescription') }}</p>
          <div v-if="previewPost.coverUrl" class="sv-admin-preview__cover">
            <NImage
              :src="resolveShortVideoAdminMediaSrc(previewPost.coverUrl)"
              width="120"
              object-fit="cover"
            />
          </div>
          <video
            v-if="previewVideoSrc"
            :key="previewPost.id"
            class="sv-admin-preview__video"
            :src="previewVideoSrc"
            controls
            playsinline
            preload="metadata"
          />
        </template>
      </NModal>
    </div>
  </div>
</template>

<style scoped>
.sv-admin-cover-ph {
  width: 48px;
  height: 64px;
  border-radius: 4px;
  background: var(--n-action-color);
  color: var(--n-text-color-3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.sv-admin-preview__meta {
  margin: 0 0 8px;
  color: var(--n-text-color-3);
  font-size: 13px;
}
.sv-admin-preview__desc {
  margin: 0 0 12px;
  line-height: 1.5;
  word-break: break-word;
}
.sv-admin-preview__cover {
  margin-bottom: 12px;
}
.sv-admin-preview__video {
  width: 100%;
  max-height: 360px;
  border-radius: 8px;
  background: #000;
}
</style>
