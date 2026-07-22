<script setup lang="ts">
/**
 * 个人网盘主视图 — 真实对接 /cloud
 * Electron 下 window.prompt/confirm 不可用，统一用 Naive UI 弹窗。
 */
import { ref, computed, onMounted, watch } from 'vue'
import {
  NIcon,
  NInput,
  NDropdown,
  NModal,
  NButton,
  NSwitch,
  useMessage,
  useDialog
} from 'naive-ui'
import {
  SearchOutline,
  AddOutline,
  CloudUploadOutline,
  FolderOpenOutline,
  GridOutline,
  ListOutline,
  EllipsisHorizontal,
  CloseOutline,
  CloudDownloadOutline,
  ShareOutline,
  ChevronDownOutline,
  DocumentTextOutline,
  ImageOutline,
  FilmOutline,
  FolderOutline,
  ArchiveOutline,
  CreateOutline,
  MoveOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useDriveStore } from '../stores/drive'
import type { DriveItemVO } from '../api/drive'
import { useOverlayStore } from '../stores/overlay'
import { useAppStore } from '../stores/app'
import { downloadDriveFileContent } from '../utils/authDownload'
import { formatFileSize } from '../utils/file'
import { generateDefaultAvatar } from '../utils/defaultAvatar'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../utils/mediaUrl'
import { useI18n } from '../i18n'
import Avatar from './Avatar.vue'

const message = useMessage()
const dialog = useDialog()
const { t } = useI18n()
const drive = useDriveStore()
const overlayStore = useOverlayStore()
const appStore = useAppStore()
const { userProfile } = storeToRefs(appStore)
const {
  items,
  breadcrumb,
  folderId,
  detailItem,
  activities,
  loading,
  uploading,
  selectedIds
} = storeToRefs(drive)

const search = ref('')
const viewMode = ref<'list' | 'grid'>('list')
const detailTab = ref<'info' | 'activity'>('info')
const sortKey = ref<'time' | 'name'>('time')
const sortDesc = ref(true)
const fileInputRef = ref<HTMLInputElement | null>(null)
const tagInput = ref('')
const showTagInput = ref(false)

/** 文本输入弹窗：新建文件夹 / 重命名 / 描述 */
const textModalShow = ref(false)
const textModalTitle = ref('')
const textModalValue = ref('')
const textModalMultiline = ref(false)
let textModalResolver: ((v: string | null) => void) | null = null

/** 分享设置弹窗 */
const shareModalShow = ref(false)
const shareNeedPassword = ref(true)
const sharePassword = ref('')
const shareTarget = ref<DriveItemVO | null>(null)
const shareSubmitting = ref(false)

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

onMounted(() => {
  void drive.refreshAll()
})

watch(search, q => {
  void drive.fetchItems(q.trim() || undefined)
})

const sortedItems = computed(() => {
  const list = [...items.value]
  list.sort((a, b) => {
    if (a.kind !== b.kind) return a.kind === 'folder' ? -1 : 1
    if (sortKey.value === 'name') {
      const cmp = a.name.localeCompare(b.name, 'zh-CN')
      return sortDesc.value ? -cmp : cmp
    }
    const ta = toMs(a.updateTime || a.createTime) || 0
    const tb = toMs(b.updateTime || b.createTime) || 0
    return sortDesc.value ? tb - ta : ta - tb
  })
  return list
})

const locationLabel = computed(() => {
  const parts = [t('files.myDrive'), ...breadcrumb.value.map(b => b.name)]
  if (detailItem.value?.kind === 'file' && detailItem.value.folderId) {
    // keep breadcrumb based
  }
  return parts.join(' / ')
})

const typeLabel = computed(() => {
  const f = detailItem.value
  if (!f || f.kind !== 'file') return ''
  const ext = (f.ext || '').toUpperCase()
  if (ext === 'PDF') return t('files.typePdf')
  if (['DOC', 'DOCX'].includes(ext)) return t('files.typeWord')
  if (['XLS', 'XLSX'].includes(ext)) return t('files.typeExcel')
  if (['PPT', 'PPTX'].includes(ext)) return t('files.typePpt')
  if (f.category === 'image') return t('files.typeImage')
  if (f.category === 'media') return t('files.typeMedia')
  if (ext) return `${ext} ${t('files.typeFile')}`
  return t('files.typeFile')
})

/** 兼容 number / 数字字符串时间戳 */
function toMs(ts?: number | string | null): number | null {
  if (ts == null || ts === '') return null
  if (typeof ts === 'number') return Number.isFinite(ts) ? ts : null
  const n = Number(ts)
  if (Number.isFinite(n)) return n
  const parsed = Date.parse(String(ts))
  return Number.isFinite(parsed) ? parsed : null
}

function formatTime(ts?: number | string) {
  const ms = toMs(ts)
  if (ms == null) return '—'
  try {
    const d = new Date(ms)
    if (Number.isNaN(d.getTime())) return '—'
    const date = d
      .toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
      .replace(/\//g, '-')
    const time = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
    return `${date} ${time}`
  } catch {
    return '—'
  }
}

function formatTimeFull(ts?: number | string) {
  return formatTime(ts)
}

function openTextModal(opts: {
  title: string
  value?: string
  multiline?: boolean
}): Promise<string | null> {
  textModalTitle.value = opts.title
  textModalValue.value = opts.value || ''
  textModalMultiline.value = !!opts.multiline
  textModalShow.value = true
  return new Promise(resolve => {
    textModalResolver = resolve
  })
}

function resolveTextModal(value: string | null) {
  textModalShow.value = false
  textModalResolver?.(value)
  textModalResolver = null
}

function onTextModalConfirm() {
  resolveTextModal(textModalValue.value)
}

function onTextModalCancel() {
  resolveTextModal(null)
}

function toggleSort(key: 'time' | 'name') {
  if (sortKey.value === key) sortDesc.value = !sortDesc.value
  else {
    sortKey.value = key
    sortDesc.value = key === 'time'
  }
}

function isSelected(id: string) {
  return selectedIds.value.includes(id)
}

function onCheckClick(e: Event, item: DriveItemVO) {
  e.stopPropagation()
  drive.toggleSelect(item)
}

function onRowClick(item: DriveItemVO) {
  if (item.kind === 'folder') {
    void drive.enterFolder(item.id)
    return
  }
  detailTab.value = 'info'
  void drive.openDetail(item)
}

function onRowDblClick(item: DriveItemVO) {
  if (item.kind === 'folder') {
    void drive.enterFolder(item.id)
    return
  }
  openPreview(item)
}

function goRoot() {
  void drive.enterFolder(null)
}

function goCrumb(id: string) {
  void drive.enterFolder(id)
}

function triggerUpload() {
  fileInputRef.value?.click()
}

async function onFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files?.length) return
  try {
    await drive.uploadFiles(files)
    message.success(t('files.uploadOk'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('files.uploadFail'))
  } finally {
    input.value = ''
  }
}

function onNewFolder() {
  void (async () => {
    const name = ((await openTextModal({ title: t('files.folderNamePrompt') })) || '').trim()
    if (!name) return
    try {
      await drive.createFolder(name)
      message.success(t('files.folderCreated'))
    } catch (err) {
      message.error(err instanceof Error ? err.message : t('files.folderCreateFail'))
    }
  })()
}

async function downloadItem(item: DriveItemVO) {
  if (item.kind !== 'file') {
    message.info(t('files.noPreview', { title: item.name }))
    return
  }
  // 走鉴权中转下载，避免依赖会过期的预签名 URL
  const result = await downloadDriveFileContent(item.id, item.name)
  if (result.canceled) return
  if (result.ok) {
    message.success(
      result.path ? t('files.downloadSaved', { path: result.path }) : t('files.downloadOk')
    )
  } else {
    message.error(result.message || t('files.downloadFail'))
  }
}

function openPreview(item: DriveItemVO) {
  if (!item.fileUrl) {
    message.info(t('files.noPreview', { title: item.name }))
    return
  }
  overlayStore.open('file-preview', {
    filePreview: {
      fileName: item.name,
      fileSize: item.fileSize != null ? formatFileSize(item.fileSize) : '',
      fileUrl: item.fileUrl,
      isImage: item.category === 'image'
    }
  })
}

function onShare(item?: DriveItemVO | null) {
  const target = item || detailItem.value
  if (!target) return
  shareTarget.value = target
  shareNeedPassword.value = true
  sharePassword.value = genExtractCode()
  shareModalShow.value = true
}

function genExtractCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let s = ''
  for (let i = 0; i < 4; i++) s += chars[Math.floor(Math.random() * chars.length)]
  return s
}

async function confirmShare() {
  const target = shareTarget.value
  if (!target) return
  if (shareNeedPassword.value && !sharePassword.value.trim()) {
    message.warning(t('files.sharePasswordRequired'))
    return
  }
  shareSubmitting.value = true
  try {
    const password = shareNeedPassword.value ? sharePassword.value.trim() : undefined
    const share = await drive.createShare(target, { password, expireHours: 72 })
    const fullUrl = `${apiBase}${share.shareUrl}`
    await navigator.clipboard.writeText(
      password ? `${fullUrl}\n${t('files.sharePasswordLabel')}: ${password}` : fullUrl
    )
    shareModalShow.value = false
    message.success(t('files.shareCopied'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('files.shareFail'))
  } finally {
    shareSubmitting.value = false
  }
}

async function onBatchDelete() {
  if (!selectedIds.value.length) return
  dialog.warning({
    title: t('files.batchDelete'),
    content: t('files.batchDeleteConfirm', { n: selectedIds.value.length }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await drive.deleteSelected()
        message.success(t('files.deleted'))
      } catch (err) {
        message.error(err instanceof Error ? err.message : t('files.deleteFail'))
      }
    }
  })
}

async function onDeleteOne(item: DriveItemVO) {
  dialog.warning({
    title: t('files.delete'),
    content: t('files.deleteConfirm', { name: item.name }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await drive.deleteOne(item)
        message.success(t('files.deleted'))
      } catch (err) {
        message.error(err instanceof Error ? err.message : t('files.deleteFail'))
      }
    }
  })
}

async function onRename(item: DriveItemVO) {
  const name = (
    (await openTextModal({ title: t('files.renamePrompt'), value: item.name })) || ''
  ).trim()
  if (!name || name === item.name) return
  try {
    await drive.renameItem(item, name)
    message.success(t('files.renameOk'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('files.renameFail'))
  }
}

async function onMoveToRoot(item?: DriveItemVO) {
  try {
    if (item) {
      drive.clearSelection()
      drive.toggleSelect(item)
    }
    await drive.moveSelected(null)
    message.success(t('files.moveOk'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('files.moveFail'))
  }
}

async function submitTag() {
  const name = tagInput.value.trim()
  if (!name || !detailItem.value || detailItem.value.kind !== 'file') return
  try {
    await drive.addTag(detailItem.value.id, name)
    tagInput.value = ''
    showTagInput.value = false
    message.success(t('files.tagAdded'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('files.tagFail'))
  }
}

async function removeTag(tag: string) {
  if (!detailItem.value || detailItem.value.kind !== 'file') return
  try {
    await drive.removeTag(detailItem.value.id, tag)
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('files.tagFail'))
  }
}

async function saveDescription() {
  if (!detailItem.value || detailItem.value.kind !== 'file') return
  const raw = await openTextModal({
    title: t('files.descPrompt'),
    value: detailItem.value.description || '',
    multiline: true
  })
  if (raw === null) return
  const desc = raw.trim()
  try {
    await drive.updateDescription(detailItem.value.id, desc)
    message.success(t('files.descSaved'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('files.descFail'))
  }
}

function activityText(action: string, detail?: string) {
  return detail || action
}

function fileIconMeta(item: DriveItemVO) {
  const ext = (item.ext || '').toLowerCase()
  if (item.kind === 'folder') {
    return { color: '#f5a623', bg: '#fff4d6', label: 'DIR' }
  }
  if (ext === 'pdf') return { color: '#e74c3c', bg: '#fdecea', label: 'PDF' }
  if (['doc', 'docx'].includes(ext)) return { color: '#2b579a', bg: '#e8f0fe', label: 'W' }
  if (['xls', 'xlsx', 'csv'].includes(ext)) return { color: '#1d6f42', bg: '#e8f5ee', label: 'X' }
  if (['ppt', 'pptx'].includes(ext)) return { color: '#c43e1c', bg: '#fde8e1', label: 'P' }
  if (['zip', 'rar', '7z'].includes(ext)) return { color: '#8e44ad', bg: '#f3e8fa', label: 'Z' }
  if (item.category === 'image') return { color: '#ff8800', bg: '#fff0e6', label: 'IMG' }
  if (item.category === 'media') return { color: '#722ed1', bg: '#f3e8ff', label: 'VID' }
  return { color: 'var(--lx-accent)', bg: 'var(--lx-accent-soft)', label: (ext || 'FILE').slice(0, 3).toUpperCase() }
}

function isImageItem(item: DriveItemVO) {
  return item.kind === 'file' && item.category === 'image' && !!normalizeMediaUrl(item.fileUrl)
}

function thumbUrl(item: DriveItemVO) {
  return normalizeMediaUrl(item.fileUrl)
}

function uploaderAvatarUrl(item: DriveItemVO) {
  const fromApi = normalizeMediaUrl(item.uploaderAvatar)
  if (fromApi && isDisplayableMediaUrl(fromApi)) return fromApi
  const mine = normalizeMediaUrl(userProfile.value.avatar)
  if (mine && isDisplayableMediaUrl(mine)) return mine
  return generateDefaultAvatar(item.uploaderName || userProfile.value.nickname || t('common.me'))
}

function rowMenuOptions(item: DriveItemVO) {
  const opts: { label: string; key: string }[] = [
    { label: t('files.rename'), key: 'rename' },
    { label: t('files.share'), key: 'share' },
    { label: t('files.moveToRoot'), key: 'moveRoot' },
    { label: t('files.delete'), key: 'delete' }
  ]
  if (item.kind === 'file') {
    opts.unshift({ label: t('files.downloadFile'), key: 'download' })
  }
  return opts
}

function onRowMenu(key: string, item: DriveItemVO) {
  if (key === 'download') void downloadItem(item)
  else if (key === 'share') onShare(item)
  else if (key === 'rename') void onRename(item)
  else if (key === 'moveRoot') void onMoveToRoot(item)
  else if (key === 'delete') void onDeleteOne(item)
}

const headerMoreOptions = computed(() => [
  { label: t('files.selectAll'), key: 'selectAll' },
  { label: t('files.batchDelete'), key: 'batchDelete', disabled: !selectedIds.value.length },
  { label: t('files.moveToRoot'), key: 'moveRoot', disabled: !selectedIds.value.length }
])

function onHeaderMore(key: string) {
  if (key === 'selectAll') drive.selectAll()
  else if (key === 'batchDelete') void onBatchDelete()
  else if (key === 'moveRoot') void onMoveToRoot()
}

// silence unused icon imports
void DocumentTextOutline
void ImageOutline
void FilmOutline
void ArchiveOutline
void CreateOutline
void MoveOutline
</script>

<template>
  <div class="files-main">
    <input
      ref="fileInputRef"
      type="file"
      multiple
      class="hidden-input"
      @change="onFilePicked"
    />

    <div class="files-toolbar">
      <div class="search-wrap">
        <n-input
          v-model:value="search"
          size="small"
          class="search-input lx-search-input"
          :placeholder="t('common.search')"
          clearable
        >
          <template #prefix>
            <n-icon :component="SearchOutline" :size="16" />
          </template>
        </n-input>
        <button type="button" class="add-btn" :title="t('files.newFolder')" @click="onNewFolder">
          <n-icon :component="AddOutline" :size="18" />
        </button>
      </div>
    </div>

    <div class="files-header">
      <div class="header-left">
        <h1 class="page-title">{{ t('nav.files') }}</h1>
        <nav class="breadcrumb">
          <button type="button" class="crumb" :class="{ link: !!folderId }" @click="goRoot">
            {{ t('files.myDrive') }}
          </button>
          <template v-for="c in breadcrumb" :key="c.id">
            <span class="crumb-sep">/</span>
            <button type="button" class="crumb link" @click="goCrumb(c.id)">{{ c.name }}</button>
          </template>
        </nav>
      </div>
      <div class="header-actions">
        <button type="button" class="btn-primary" :disabled="uploading" @click="triggerUpload">
          <n-icon :component="CloudUploadOutline" :size="16" />
          {{ uploading ? t('common.loading') : t('files.upload') }}
        </button>
        <button type="button" class="btn-ghost" @click="onNewFolder">
          <n-icon :component="FolderOpenOutline" :size="16" />
          {{ t('files.newFolder') }}
        </button>
        <div class="view-toggle">
          <button
            type="button"
            class="view-btn"
            :class="{ active: viewMode === 'grid' }"
            @click="viewMode = 'grid'"
          >
            <n-icon :component="GridOutline" :size="18" />
          </button>
          <button
            type="button"
            class="view-btn"
            :class="{ active: viewMode === 'list' }"
            @click="viewMode = 'list'"
          >
            <n-icon :component="ListOutline" :size="18" />
          </button>
        </div>
        <n-dropdown :options="headerMoreOptions" @select="onHeaderMore">
          <button type="button" class="icon-more">
            <n-icon :component="EllipsisHorizontal" :size="18" />
          </button>
        </n-dropdown>
      </div>
    </div>

    <div v-if="selectedIds.length" class="batch-bar">
      <span>{{ t('files.selectedCount', { n: selectedIds.length }) }}</span>
      <button type="button" class="btn-ghost sm" @click="onBatchDelete">{{ t('files.batchDelete') }}</button>
      <button type="button" class="btn-ghost sm" @click="onMoveToRoot()">{{ t('files.moveToRoot') }}</button>
      <button type="button" class="btn-ghost sm" @click="drive.clearSelection()">{{ t('common.cancel') }}</button>
    </div>

    <div class="files-body">
      <div class="list-pane">
        <div class="section-head">
          <h2>{{ t('files.allFiles') }}</h2>
        </div>

        <div v-if="viewMode === 'list'" class="table-wrap">
          <div class="table-head">
            <div class="col-check" />
            <button type="button" class="col-name th" @click="toggleSort('name')">
              {{ t('files.colName') }}
              <n-icon v-if="sortKey === 'name'" :component="ChevronDownOutline" :size="14" :class="{ asc: !sortDesc }" />
            </button>
            <button type="button" class="col-time th" @click="toggleSort('time')">
              {{ t('files.colTime') }}
              <n-icon v-if="sortKey === 'time'" :component="ChevronDownOutline" :size="14" :class="{ asc: !sortDesc }" />
            </button>
            <div class="col-size th">{{ t('files.colSize') }}</div>
            <div class="col-uploader th">{{ t('files.colUploader') }}</div>
            <div class="col-more" />
          </div>

          <div class="table-body">
            <div
              v-for="item in sortedItems"
              :key="item.id"
              class="table-row"
              :class="{ active: detailItem?.id === item.id }"
              @click="onRowClick(item)"
              @dblclick="onRowDblClick(item)"
            >
              <div class="col-check" @click="onCheckClick($event, item)">
                <span class="checkbox" :class="{ checked: isSelected(item.id) }" />
              </div>
              <div class="col-name">
                <div
                  class="file-icon"
                  :class="{ folder: item.kind === 'folder', thumb: isImageItem(item) }"
                  :style="
                    item.kind === 'file' && !isImageItem(item)
                      ? { color: fileIconMeta(item).color, background: fileIconMeta(item).bg }
                      : undefined
                  "
                >
                  <n-icon v-if="item.kind === 'folder'" :component="FolderOutline" :size="22" />
                  <img v-else-if="isImageItem(item)" :src="thumbUrl(item)" alt="" class="thumb-img" />
                  <span v-else class="ext-badge">{{ fileIconMeta(item).label }}</span>
                </div>
                <div class="name-block">
                  <span class="name">{{ item.name }}</span>
                  <span v-if="item.kind === 'folder'" class="sub">{{ t('files.itemCount', { n: item.childCount || 0 }) }}</span>
                </div>
              </div>
              <div class="col-time">{{ formatTime(item.updateTime || item.createTime) }}</div>
              <div class="col-size">{{ item.fileSize != null ? formatFileSize(item.fileSize) : '—' }}</div>
              <div class="col-uploader">
                <Avatar
                  :text="(item.uploaderName || t('common.me')).slice(0, 1)"
                  color="transparent"
                  :size="24"
                  :image-url="uploaderAvatarUrl(item)"
                />
                <span class="uploader-name">{{ item.uploaderName || t('common.me') }}</span>
              </div>
              <div class="col-more" @click.stop>
                <n-dropdown :options="rowMenuOptions(item)" @select="(k: string) => onRowMenu(k, item)">
                  <button type="button" class="row-more">
                    <n-icon :component="EllipsisHorizontal" :size="16" />
                  </button>
                </n-dropdown>
              </div>
            </div>

            <div v-if="!loading && sortedItems.length === 0" class="empty">{{ t('files.empty') }}</div>
            <div v-if="loading" class="empty">{{ t('common.loading') }}</div>
          </div>
        </div>

        <div v-else class="grid-wrap">
          <div
            v-for="item in sortedItems"
            :key="'g-' + item.id"
            class="grid-card"
            :class="{ active: detailItem?.id === item.id }"
            @click="onRowClick(item)"
            @dblclick="onRowDblClick(item)"
          >
            <div
              class="grid-icon"
              :class="{ folder: item.kind === 'folder', thumb: isImageItem(item) }"
              :style="
                item.kind === 'file' && !isImageItem(item)
                  ? { color: fileIconMeta(item).color, background: fileIconMeta(item).bg }
                  : undefined
              "
            >
              <n-icon v-if="item.kind === 'folder'" :component="FolderOutline" :size="36" />
              <img v-else-if="isImageItem(item)" :src="thumbUrl(item)" alt="" class="thumb-img" />
              <span v-else>{{ fileIconMeta(item).label }}</span>
            </div>
            <div class="grid-name">{{ item.name }}</div>
            <div class="grid-meta">
              {{
                item.kind === 'folder'
                  ? (item.fileSize != null
                      ? `${t('files.itemCount', { n: item.childCount || 0 })} · ${formatFileSize(item.fileSize)}`
                      : t('files.itemCount', { n: item.childCount || 0 }))
                  : formatFileSize(item.fileSize || 0)
              }}
            </div>
          </div>
          <div v-if="!loading && sortedItems.length === 0" class="empty">{{ t('files.empty') }}</div>
        </div>

        <div class="list-footer">
          <div class="total-count">{{ t('files.totalItems', { n: sortedItems.length }) }}</div>
        </div>
      </div>

      <aside v-if="detailItem" class="detail-pane">
        <div class="detail-head">
          <h3 class="detail-title">{{ detailItem.name }}</h3>
          <button type="button" class="close-btn" @click="drive.detailItem = null">
            <n-icon :component="CloseOutline" :size="18" />
          </button>
        </div>

        <div class="detail-preview">
          <div
            v-if="isImageItem(detailItem)"
            class="preview-thumb"
          >
            <img :src="thumbUrl(detailItem)" alt="" />
          </div>
          <div
            v-else
            class="preview-icon"
            :class="{ folder: detailItem.kind === 'folder' }"
            :style="detailItem.kind === 'file' ? { color: fileIconMeta(detailItem).color, background: fileIconMeta(detailItem).bg } : undefined"
          >
            <n-icon v-if="detailItem.kind === 'folder'" :component="FolderOutline" :size="40" />
            <span v-else>{{ fileIconMeta(detailItem).label }}</span>
          </div>
        </div>

        <div class="detail-tabs">
          <button type="button" class="tab" :class="{ active: detailTab === 'info' }" @click="detailTab = 'info'">
            {{ t('files.tabInfo') }}
          </button>
          <button
            type="button"
            class="tab"
            :class="{ active: detailTab === 'activity' }"
            @click="detailTab = 'activity'; drive.fetchActivities(detailItem.kind === 'file' ? detailItem.id : undefined)"
          >
            {{ t('files.tabActivity') }}
          </button>
        </div>

        <div v-if="detailTab === 'info'" class="detail-body">
          <template v-if="detailItem.kind === 'file'">
            <div class="meta-row">
              <span class="meta-label">{{ t('files.metaType') }}</span>
              <span class="meta-value">{{ typeLabel }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">{{ t('files.metaSize') }}</span>
              <span class="meta-value">{{ detailItem.fileSize != null ? formatFileSize(detailItem.fileSize) : '—' }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">{{ t('files.metaLocation') }}</span>
              <span class="meta-value">{{ locationLabel }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">{{ t('files.metaUploader') }}</span>
              <span class="meta-value">{{ detailItem.uploaderName || t('common.me') }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">{{ t('files.metaUploadTime') }}</span>
              <span class="meta-value">{{ formatTimeFull(detailItem.createTime) }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">{{ t('files.metaModifyTime') }}</span>
              <span class="meta-value">{{ formatTimeFull(detailItem.updateTime) }}</span>
            </div>

            <div class="meta-block">
              <div class="meta-label">{{ t('files.metaTags') }}</div>
              <div class="tags">
                <span v-for="tag in detailItem.tags || []" :key="tag" class="tag" @click="removeTag(tag)" :title="t('files.removeTag')">
                  {{ tag }} ×
                </span>
                <button v-if="!showTagInput" type="button" class="tag-add" @click="showTagInput = true">+</button>
                <input
                  v-else
                  v-model="tagInput"
                  class="tag-input"
                  :placeholder="t('files.addTag')"
                  @keyup.enter="submitTag"
                  @blur="submitTag"
                />
              </div>
            </div>

            <div class="meta-block">
              <div class="meta-label">
                {{ t('files.metaDesc') }}
                <button type="button" class="link-edit" @click="saveDescription">{{ t('common.edit') }}</button>
              </div>
              <p class="desc">{{ detailItem.description || t('files.descEmpty') }}</p>
            </div>
          </template>
          <template v-else>
            <div class="meta-row">
              <span class="meta-label">{{ t('files.metaType') }}</span>
              <span class="meta-value">{{ t('files.typeFolder') }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">{{ t('files.metaSize') }}</span>
              <span class="meta-value">{{ detailItem.fileSize != null ? formatFileSize(detailItem.fileSize) : '—' }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">{{ t('files.itemCount', { n: detailItem.childCount || 0 }) }}</span>
            </div>
          </template>
        </div>

        <div v-else class="detail-body activity">
          <div v-for="a in activities" :key="a.id" class="activity-item">
            <span class="dot" />
            <div>
              <div class="act-title">{{ activityText(a.action, a.detail) }}</div>
              <div class="act-meta">{{ formatTimeFull(a.createTime) }}</div>
            </div>
          </div>
          <div v-if="!activities.length" class="empty">{{ t('files.noActivity') }}</div>
        </div>

        <div class="detail-actions">
          <button
            v-if="detailItem.kind === 'file'"
            type="button"
            class="btn-primary grow"
            @click="downloadItem(detailItem)"
          >
            <n-icon :component="CloudDownloadOutline" :size="16" />
            {{ t('files.downloadFile') }}
          </button>
          <button type="button" class="btn-ghost" @click="onShare(detailItem)">
            <n-icon :component="ShareOutline" :size="16" />
            {{ t('files.share') }}
          </button>
          <n-dropdown :options="rowMenuOptions(detailItem)" @select="(k: string) => onRowMenu(k, detailItem!)">
            <button type="button" class="icon-more">
              <n-icon :component="EllipsisHorizontal" :size="18" />
            </button>
          </n-dropdown>
        </div>
      </aside>
    </div>

    <!-- 文本输入弹窗（新建/重命名/描述） -->
    <n-modal
      v-model:show="textModalShow"
      preset="card"
      :title="textModalTitle"
      style="width: 420px"
      :mask-closable="false"
      @close="onTextModalCancel"
    >
      <n-input
        v-model:value="textModalValue"
        :type="textModalMultiline ? 'textarea' : 'text'"
        :rows="textModalMultiline ? 4 : undefined"
        autofocus
        @keyup.enter="!textModalMultiline && onTextModalConfirm()"
      />
      <template #footer>
        <div class="modal-actions">
          <n-button @click="onTextModalCancel">{{ t('common.cancel') }}</n-button>
          <n-button type="primary" @click="onTextModalConfirm">{{ t('common.confirm') }}</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 分享设置 -->
    <n-modal
      v-model:show="shareModalShow"
      preset="card"
      :title="t('files.shareSettings')"
      style="width: 420px"
      :mask-closable="false"
    >
      <div class="share-form">
        <div class="share-row">
          <span>{{ t('files.shareEnablePassword') }}</span>
          <n-switch v-model:value="shareNeedPassword" />
        </div>
        <div v-if="shareNeedPassword" class="share-pwd">
          <div class="share-pwd-label">{{ t('files.sharePasswordLabel') }}</div>
          <div class="share-pwd-row">
            <n-input v-model:value="sharePassword" maxlength="16" :placeholder="t('files.sharePasswordPrompt')" />
            <n-button @click="sharePassword = genExtractCode()">{{ t('files.shareRegenCode') }}</n-button>
          </div>
        </div>
        <p class="share-hint">{{ t('files.shareExpireHint') }}</p>
      </div>
      <template #footer>
        <div class="modal-actions">
          <n-button @click="shareModalShow = false">{{ t('common.cancel') }}</n-button>
          <n-button type="primary" :loading="shareSubmitting" @click="confirmShare">
            {{ t('files.shareCreate') }}
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.files-main {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  overflow: hidden;
}
.hidden-input { display: none; }
.files-toolbar { flex-shrink: 0; padding: 12px 20px 0; }
.search-wrap { display: flex; align-items: center; gap: 8px; max-width: 320px; }
.search-input { flex: 1; }
.add-btn {
  width: 32px; height: 32px; border: none; border-radius: var(--lx-radius);
  background: var(--lx-bg-input); color: var(--lx-text-secondary);
  display: flex; align-items: center; justify-content: center; cursor: pointer;
}
.add-btn:hover { background: var(--lx-bg-hover); color: var(--lx-text); }
.files-header {
  flex-shrink: 0; display: flex; align-items: flex-start; justify-content: space-between;
  gap: 16px; padding: 16px 20px 12px;
}
.page-title { margin: 0; font-size: 22px; font-weight: 700; color: var(--lx-text); }
.breadcrumb { display: flex; align-items: center; gap: 6px; margin-top: 6px; font-size: 13px; color: var(--lx-text-muted); flex-wrap: wrap; }
.crumb { border: none; background: none; padding: 0; color: inherit; font-size: inherit; cursor: default; }
.crumb.link { cursor: pointer; color: var(--lx-text-secondary); }
.crumb.link:hover { color: var(--lx-accent); }
.crumb-sep { color: var(--lx-text-muted); }
.header-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.btn-primary, .btn-ghost {
  display: inline-flex; align-items: center; gap: 6px; height: 34px; padding: 0 14px;
  border-radius: var(--lx-radius); font-size: 13px; cursor: pointer; border: 1px solid transparent; white-space: nowrap;
}
.btn-primary { background: var(--lx-accent); color: var(--lx-text-on-accent); border-color: var(--lx-accent); }
.btn-primary:hover:not(:disabled) { background: var(--lx-accent-hover); }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-ghost { background: var(--lx-bg-card); color: var(--lx-text-body); border-color: var(--lx-border-strong); }
.btn-ghost:hover { background: var(--lx-bg-hover); }
.btn-ghost.sm { height: 28px; padding: 0 10px; font-size: 12px; }
.btn-primary.grow { flex: 1; justify-content: center; }
.view-toggle { display: flex; border: 1px solid var(--lx-border-light); border-radius: var(--lx-radius); overflow: hidden; }
.view-btn, .icon-more, .row-more, .close-btn {
  border: none; background: transparent; color: var(--lx-text-muted); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.view-btn { width: 34px; height: 34px; }
.view-btn.active { background: var(--lx-accent-soft); color: var(--lx-accent); }
.icon-more { width: 34px; height: 34px; border-radius: var(--lx-radius); }
.icon-more:hover, .row-more:hover, .close-btn:hover { background: var(--lx-bg-hover); color: var(--lx-text); }
.batch-bar {
  display: flex; align-items: center; gap: 10px; padding: 8px 20px;
  background: var(--lx-accent-soft); font-size: 13px; color: var(--lx-text-body);
}
.files-body { flex: 1; min-height: 0; display: flex; border-top: 1px solid var(--lx-border-light); }
.list-pane { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.section-head { padding: 14px 20px 8px; }
.section-head h2 { margin: 0; font-size: 15px; font-weight: 600; color: var(--lx-text); }
.table-wrap { flex: 1; min-height: 0; display: flex; flex-direction: column; padding: 0 12px; }
.table-head, .table-row {
  display: grid; grid-template-columns: 36px minmax(180px, 1.6fr) 150px 90px 140px 40px;
  align-items: center; gap: 8px; padding: 0 8px;
}
.table-head { height: 36px; color: var(--lx-text-muted); font-size: 12px; border-bottom: 1px solid var(--lx-border-light); }
.th { display: inline-flex; align-items: center; gap: 2px; border: none; background: none; padding: 0; color: inherit; font-size: inherit; cursor: pointer; text-align: left; }
.th .asc { transform: rotate(180deg); }
.table-body { flex: 1; overflow-y: auto; }
.table-row { min-height: 56px; border-radius: var(--lx-radius); cursor: pointer; }
.table-row:hover { background: var(--lx-bg-hover); }
.table-row.active { background: var(--lx-bg-active); }
.checkbox {
  width: 16px; height: 16px; border: 1.5px solid var(--lx-border-strong); border-radius: 4px; display: inline-block;
}
.checkbox.checked { background: var(--lx-accent); border-color: var(--lx-accent); box-shadow: inset 0 0 0 2px #fff; }
.col-name { display: flex; align-items: center; gap: 12px; min-width: 0; }
.file-icon {
  width: 40px; height: 40px; border-radius: 8px; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; font-size: 11px; font-weight: 700; overflow: hidden;
}
.file-icon.folder, .grid-icon.folder, .preview-icon.folder { background: #fff4d6; color: #f5a623; }
.file-icon.thumb, .grid-icon.thumb { padding: 0; background: var(--lx-bg-input); }
.thumb-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.name-block { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.name { font-size: 14px; color: var(--lx-text-body); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sub { font-size: 12px; color: var(--lx-text-muted); }
.col-time, .col-size { font-size: 13px; color: var(--lx-text-secondary); }
.col-uploader { display: flex; align-items: center; gap: 8px; min-width: 0; }
.avatar {
  width: 24px; height: 24px; border-radius: 50%; background: var(--lx-accent-soft); color: var(--lx-accent);
  font-size: 11px; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.uploader-name { font-size: 13px; color: var(--lx-text-body); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-more { width: 28px; height: 28px; border-radius: 6px; opacity: 0; }
.table-row:hover .row-more { opacity: 1; }
.grid-wrap {
  flex: 1; overflow-y: auto; padding: 8px 20px 16px;
  display: grid; grid-template-columns: repeat(auto-fill, minmax(132px, 1fr)); gap: 12px; align-content: start;
}
.grid-card {
  border: 1px solid var(--lx-border-light); border-radius: var(--lx-radius); padding: 16px 12px 12px;
  text-align: center; cursor: pointer;
}
.grid-card:hover { background: var(--lx-bg-hover); }
.grid-card.active { border-color: var(--lx-accent); background: var(--lx-accent-soft); }
.grid-icon {
  width: 56px; height: 56px; margin: 0 auto 10px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700;
  overflow: hidden;
}
.grid-name { font-size: 13px; color: var(--lx-text-body); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.grid-meta { margin-top: 4px; font-size: 12px; color: var(--lx-text-muted); }
.empty { text-align: center; color: var(--lx-text-muted); padding: 48px 16px; font-size: 13px; }
.list-footer {
  flex-shrink: 0; display: flex; align-items: center; justify-content: flex-end;
  gap: 16px; padding: 12px 20px 16px; border-top: 1px solid var(--lx-border-light);
}
.total-count { font-size: 12px; color: var(--lx-text-muted); }
.detail-pane {
  width: 300px; min-width: 280px; max-width: 340px; border-left: 1px solid var(--lx-border-light);
  display: flex; flex-direction: column; background: var(--lx-bg-card);
}
.detail-head { display: flex; align-items: flex-start; gap: 8px; padding: 16px 16px 8px; }
.detail-title { flex: 1; margin: 0; font-size: 15px; font-weight: 600; color: var(--lx-text); word-break: break-all; }
.close-btn { width: 28px; height: 28px; border-radius: 6px; flex-shrink: 0; }
.detail-preview { display: flex; justify-content: center; padding: 12px 16px 20px; }
.preview-icon {
  width: 88px; height: 104px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 800; box-shadow: var(--lx-shadow-card);
}
.preview-thumb {
  width: 120px; height: 120px; border-radius: 12px; overflow: hidden;
  box-shadow: var(--lx-shadow-card); background: var(--lx-bg-input);
}
.preview-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.detail-tabs { display: flex; gap: 20px; padding: 0 16px; border-bottom: 1px solid var(--lx-border-light); }
.tab { border: none; background: none; padding: 10px 0; font-size: 13px; color: var(--lx-text-muted); cursor: pointer; position: relative; }
.tab.active { color: var(--lx-accent); font-weight: 600; }
.tab.active::after { content: ''; position: absolute; left: 0; right: 0; bottom: 0; height: 2px; background: var(--lx-accent); }
.detail-body { flex: 1; overflow-y: auto; padding: 16px; }
.meta-row { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; font-size: 13px; }
.meta-label { color: var(--lx-text-muted); flex-shrink: 0; }
.meta-value { color: var(--lx-text-body); text-align: right; word-break: break-all; }
.meta-block { margin-top: 14px; }
.meta-block .meta-label { margin-bottom: 8px; font-size: 13px; display: flex; justify-content: space-between; }
.link-edit { border: none; background: none; color: var(--lx-accent); cursor: pointer; font-size: 12px; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.tag {
  display: inline-flex; align-items: center; height: 24px; padding: 0 10px; border-radius: 99px;
  background: var(--lx-bg-input); color: var(--lx-text-secondary); font-size: 12px; cursor: pointer;
}
.tag-add {
  width: 24px; height: 24px; border-radius: 50%; border: 1px dashed var(--lx-border-strong);
  background: transparent; color: var(--lx-text-muted); cursor: pointer;
}
.tag-input {
  height: 24px; border: 1px solid var(--lx-border-strong); border-radius: 6px; padding: 0 8px; width: 100px; font-size: 12px;
}
.desc { margin: 0; font-size: 13px; line-height: 1.6; color: var(--lx-text-secondary); }
.activity-item { display: flex; gap: 10px; align-items: flex-start; margin-bottom: 12px; }
.activity-item .dot { width: 8px; height: 8px; margin-top: 5px; border-radius: 50%; background: var(--lx-accent); flex-shrink: 0; }
.act-title { font-size: 13px; color: var(--lx-text-body); }
.act-meta { margin-top: 4px; font-size: 12px; color: var(--lx-text-muted); }
.detail-actions {
  flex-shrink: 0; display: flex; align-items: center; gap: 8px;
  padding: 12px 16px 16px; border-top: 1px solid var(--lx-border-light);
}
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; }
.share-form { display: flex; flex-direction: column; gap: 14px; }
.share-row { display: flex; align-items: center; justify-content: space-between; font-size: 14px; color: var(--lx-text-body); }
.share-pwd-label { margin-bottom: 6px; font-size: 13px; color: var(--lx-text-muted); }
.share-pwd-row { display: flex; gap: 8px; }
.share-hint { margin: 0; font-size: 12px; color: var(--lx-text-muted); }
</style>
