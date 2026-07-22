<script setup lang="ts">
/**
 * 文件主视图 — 按设计稿：顶栏搜索 / 面包屑操作 / 文件表 / 右侧详情
 */
import { ref, computed, onMounted, watch } from 'vue'
import { NIcon, NInput, useMessage } from 'naive-ui'
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
  ArchiveOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useFilesStore, type LocalFileItem } from '../stores/files'
import { useOverlayStore } from '../stores/overlay'
import { downloadFileWithSettings } from '../utils/downloadFile'
import { formatFileSize } from '../utils/file'
import { useI18n } from '../i18n'

const STORAGE_QUOTA_BYTES = 20 * 1024 * 1024 * 1024

interface FolderRow {
  kind: 'folder'
  id: string
  name: string
  count: number
  time: string
  sender: string
}

interface FileRow {
  kind: 'file'
  item: LocalFileItem
}

type ListRow = FolderRow | FileRow

const message = useMessage()
const { t } = useI18n()
const filesStore = useFilesStore()
const overlayStore = useOverlayStore()
const { items, loading, totalBytes } = storeToRefs(filesStore)
const { fetchCloudFiles } = filesStore
const { open: openOverlay } = overlayStore

const search = ref('')
const viewMode = ref<'list' | 'grid'>('list')
const currentFolderId = ref<string | null>(null)
const selectedId = ref<string | null>(null)
const detailTab = ref<'info' | 'activity'>('info')
const sortKey = ref<'time' | 'name'>('time')
const sortDesc = ref(true)

onMounted(() => {
  if (!filesStore.initialized) void fetchCloudFiles()
})

watch(currentFolderId, () => {
  selectedId.value = null
})

/** 按会话聚合为文件夹（有会话名的才成夹） */
const folders = computed<FolderRow[]>(() => {
  const map = new Map<string, FolderRow>()
  for (const f of items.value) {
    if (!f.conversationId && !f.conversationName) continue
    const key = f.conversationId || f.conversationName || ''
    const name = f.conversationName || t('files.ungrouped')
    const exist = map.get(key)
    if (!exist) {
      map.set(key, {
        kind: 'folder',
        id: key,
        name,
        count: 1,
        time: f.time,
        sender: f.sender
      })
    } else {
      exist.count += 1
      if (f.time && f.time > exist.time) exist.time = f.time
    }
  }
  return [...map.values()].sort((a, b) => b.time.localeCompare(a.time))
})

const currentFolder = computed(() =>
  currentFolderId.value ? folders.value.find(f => f.id === currentFolderId.value) ?? null : null
)

const filesInScope = computed(() => {
  if (!currentFolderId.value) return items.value
  return items.value.filter(f => {
    const key = f.conversationId || f.conversationName || '__other__'
    return key === currentFolderId.value
  })
})

const listRows = computed<ListRow[]>(() => {
  const q = search.value.trim().toLowerCase()
  const rows: ListRow[] = []

  const sortFiles = (list: LocalFileItem[]) => {
    list.sort((a, b) => {
      if (sortKey.value === 'name') {
        const cmp = a.title.localeCompare(b.title, 'zh-CN')
        return sortDesc.value ? -cmp : cmp
      }
      const cmp = a.time.localeCompare(b.time)
      return sortDesc.value ? -cmp : cmp
    })
    return list
  }

  if (!currentFolderId.value) {
    let folderList = folders.value
    if (q) {
      folderList = folderList.filter(
        f => f.name.toLowerCase().includes(q) || f.sender.toLowerCase().includes(q)
      )
    }
    rows.push(...folderList)

    // 根目录：无会话文件；有搜索词时同时匹配全部文件
    let fileList = q
      ? items.value.filter(
          f =>
            f.title.toLowerCase().includes(q) ||
            f.sender.toLowerCase().includes(q) ||
            (f.conversationName || '').toLowerCase().includes(q)
        )
      : items.value.filter(f => !f.conversationId && !f.conversationName)

    for (const item of sortFiles(fileList)) rows.push({ kind: 'file', item })
    return rows
  }

  let fileList = [...filesInScope.value]
  if (q) {
    fileList = fileList.filter(
      f =>
        f.title.toLowerCase().includes(q) ||
        f.sender.toLowerCase().includes(q) ||
        (f.conversationName || '').toLowerCase().includes(q)
    )
  }

  for (const item of sortFiles(fileList)) {
    rows.push({ kind: 'file', item })
  }
  return rows
})

const selectedFile = computed(() => {
  if (!selectedId.value) return null
  return items.value.find(f => f.id === selectedId.value) ?? null
})

const usedLabel = computed(() => formatFileSize(totalBytes.value))
const quotaLabel = computed(() => '20 GB')
const usedPercent = computed(() =>
  Math.min(100, Math.round((totalBytes.value / STORAGE_QUOTA_BYTES) * 1000) / 10)
)

const locationLabel = computed(() => {
  if (selectedFile.value?.conversationName) {
    return `${t('files.teamFiles')} / ${selectedFile.value.conversationName}`
  }
  return t('files.teamFiles')
})

const typeLabel = computed(() => {
  const f = selectedFile.value
  if (!f) return ''
  const ext = (f.ext || '').toUpperCase()
  if (ext === 'PDF') return t('files.typePdf')
  if (['DOC', 'DOCX'].includes(ext)) return t('files.typeWord')
  if (['XLS', 'XLSX'].includes(ext)) return t('files.typeExcel')
  if (['PPT', 'PPTX'].includes(ext)) return t('files.typePpt')
  if (f.type === 'image') return t('files.typeImage')
  if (f.type === 'media') return t('files.typeMedia')
  if (ext) return `${ext} ${t('files.typeFile')}`
  return t('files.typeFile')
})

function toggleSort(key: 'time' | 'name') {
  if (sortKey.value === key) sortDesc.value = !sortDesc.value
  else {
    sortKey.value = key
    sortDesc.value = key === 'time'
  }
}

function openFolder(folder: FolderRow) {
  currentFolderId.value = folder.id
}

function goRoot() {
  currentFolderId.value = null
  selectedId.value = null
}

function selectFile(item: LocalFileItem) {
  selectedId.value = item.id
  detailTab.value = 'info'
}

function closeDetail() {
  selectedId.value = null
}

function openPreview(item: LocalFileItem) {
  if (!item.fileUrl) {
    message.info(t('files.noPreview', { title: item.title }))
    return
  }
  openOverlay('file-preview', {
    filePreview: {
      fileName: item.title,
      fileSize: item.size,
      fileUrl: item.fileUrl,
      isImage: item.type === 'image'
    }
  })
}

function onRowClick(row: ListRow) {
  if (row.kind === 'folder') {
    openFolder(row)
    return
  }
  selectFile(row.item)
}

function onRowDblClick(row: ListRow) {
  if (row.kind === 'folder') {
    openFolder(row)
    return
  }
  openPreview(row.item)
}

async function downloadSelected() {
  const file = selectedFile.value
  if (!file?.fileUrl) {
    message.info(t('files.noPreview', { title: file?.title || '' }))
    return
  }
  const result = await downloadFileWithSettings(file.fileUrl, file.title)
  if (result.canceled) return
  if (result.ok) {
    message.success(
      result.path ? t('files.downloadSaved', { path: result.path }) : t('files.downloadOk')
    )
  } else {
    message.error(result.message || t('files.downloadFail'))
  }
}

function onUploadClick() {
  message.info(t('files.uploadHint'))
}

function onNewFolderClick() {
  message.info(t('files.newFolderHint'))
}

function onShareClick() {
  message.info(t('files.shareHint'))
}

function onExpandStorage() {
  message.info(t('files.expandHint'))
}

function fileIconMeta(item: LocalFileItem): { icon: typeof DocumentTextOutline; color: string; bg: string; label: string } {
  const ext = item.ext
  if (ext === 'pdf') return { icon: DocumentTextOutline, color: '#e74c3c', bg: '#fdecea', label: 'PDF' }
  if (['doc', 'docx'].includes(ext)) return { icon: DocumentTextOutline, color: '#2b579a', bg: '#e8f0fe', label: 'W' }
  if (['xls', 'xlsx', 'csv'].includes(ext)) return { icon: DocumentTextOutline, color: '#1d6f42', bg: '#e8f5ee', label: 'X' }
  if (['ppt', 'pptx'].includes(ext)) return { icon: DocumentTextOutline, color: '#c43e1c', bg: '#fde8e1', label: 'P' }
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return { icon: ArchiveOutline, color: '#8e44ad', bg: '#f3e8fa', label: 'Z' }
  if (item.type === 'image') return { icon: ImageOutline, color: '#ff8800', bg: '#fff0e6', label: 'IMG' }
  if (item.type === 'media') return { icon: FilmOutline, color: '#722ed1', bg: '#f3e8ff', label: 'VID' }
  if (['sketch', 'fig', 'psd', 'ai'].includes(ext)) return { icon: DocumentTextOutline, color: '#f7b500', bg: '#fff8e1', label: 'SK' }
  return { icon: FolderOutline, color: 'var(--lx-accent)', bg: 'var(--lx-accent-soft)', label: (ext || 'FILE').slice(0, 3).toUpperCase() }
}
</script>

<template>
  <div class="files-main">
    <!-- 顶栏：搜索 -->
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
        <button type="button" class="add-btn" :title="t('files.newFolder')" @click="onNewFolderClick">
          <n-icon :component="AddOutline" :size="18" />
        </button>
      </div>
    </div>

    <!-- 标题 + 操作 -->
    <div class="files-header">
      <div class="header-left">
        <h1 class="page-title">{{ t('nav.files') }}</h1>
        <nav class="breadcrumb">
          <button type="button" class="crumb" :class="{ link: !!currentFolder }" @click="goRoot">
            {{ t('files.teamFiles') }}
          </button>
          <template v-if="currentFolder">
            <span class="crumb-sep">/</span>
            <span class="crumb current">{{ currentFolder.name }}</span>
          </template>
        </nav>
      </div>
      <div class="header-actions">
        <button type="button" class="btn-primary" @click="onUploadClick">
          <n-icon :component="CloudUploadOutline" :size="16" />
          {{ t('files.upload') }}
        </button>
        <button type="button" class="btn-ghost" @click="onNewFolderClick">
          <n-icon :component="FolderOpenOutline" :size="16" />
          {{ t('files.newFolder') }}
        </button>
        <div class="view-toggle">
          <button
            type="button"
            class="view-btn"
            :class="{ active: viewMode === 'grid' }"
            :title="t('files.gridView')"
            @click="viewMode = 'grid'"
          >
            <n-icon :component="GridOutline" :size="18" />
          </button>
          <button
            type="button"
            class="view-btn"
            :class="{ active: viewMode === 'list' }"
            :title="t('files.listView')"
            @click="viewMode = 'list'"
          >
            <n-icon :component="ListOutline" :size="18" />
          </button>
        </div>
        <button type="button" class="icon-more" :title="t('common.more')">
          <n-icon :component="EllipsisHorizontal" :size="18" />
        </button>
      </div>
    </div>

    <div class="files-body">
      <div class="list-pane">
        <div class="section-head">
          <h2>{{ t('files.allFiles') }}</h2>
        </div>

        <!-- 列表视图 -->
        <div v-if="viewMode === 'list'" class="table-wrap">
          <div class="table-head">
            <div class="col-check" />
            <button type="button" class="col-name th" @click="toggleSort('name')">
              {{ t('files.colName') }}
              <n-icon
                v-if="sortKey === 'name'"
                :component="ChevronDownOutline"
                :size="14"
                :class="{ asc: !sortDesc }"
              />
            </button>
            <button type="button" class="col-time th" @click="toggleSort('time')">
              {{ t('files.colTime') }}
              <n-icon
                v-if="sortKey === 'time'"
                :component="ChevronDownOutline"
                :size="14"
                :class="{ asc: !sortDesc }"
              />
            </button>
            <div class="col-size th">{{ t('files.colSize') }}</div>
            <div class="col-uploader th">{{ t('files.colUploader') }}</div>
            <div class="col-more" />
          </div>

          <div class="table-body">
            <div
              v-for="row in listRows"
              :key="row.kind === 'folder' ? `f-${row.id}` : row.item.id"
              class="table-row"
              :class="{
                active: row.kind === 'file' && selectedId === row.item.id
              }"
              @click="onRowClick(row)"
              @dblclick="onRowDblClick(row)"
            >
              <div class="col-check">
                <span class="checkbox" />
              </div>

              <template v-if="row.kind === 'folder'">
                <div class="col-name">
                  <div class="file-icon folder">
                    <n-icon :component="FolderOutline" :size="22" />
                  </div>
                  <div class="name-block">
                    <span class="name">{{ row.name }}</span>
                    <span class="sub">{{ t('files.itemCount', { n: row.count }) }}</span>
                  </div>
                </div>
                <div class="col-time">{{ row.time || '—' }}</div>
                <div class="col-size">—</div>
                <div class="col-uploader">
                  <span class="avatar">{{ row.sender.slice(0, 1) }}</span>
                  <span class="uploader-name">{{ row.sender }}</span>
                </div>
              </template>

              <template v-else>
                <div class="col-name">
                  <div
                    class="file-icon"
                    :style="{ color: fileIconMeta(row.item).color, background: fileIconMeta(row.item).bg }"
                  >
                    <span class="ext-badge">{{ fileIconMeta(row.item).label }}</span>
                  </div>
                  <div class="name-block">
                    <span class="name">{{ row.item.title }}</span>
                  </div>
                </div>
                <div class="col-time">{{ row.item.time || '—' }}</div>
                <div class="col-size">{{ row.item.size || '—' }}</div>
                <div class="col-uploader">
                  <span class="avatar">{{ row.item.sender.slice(0, 1) }}</span>
                  <span class="uploader-name">{{ row.item.sender }}</span>
                </div>
              </template>

              <div class="col-more">
                <button type="button" class="row-more" @click.stop>
                  <n-icon :component="EllipsisHorizontal" :size="16" />
                </button>
              </div>
            </div>

            <div v-if="!loading && listRows.length === 0" class="empty">
              {{ t('files.empty') }}
            </div>
            <div v-if="loading" class="empty">{{ t('common.loading') }}</div>
          </div>
        </div>

        <!-- 网格视图 -->
        <div v-else class="grid-wrap">
          <div
            v-for="row in listRows"
            :key="row.kind === 'folder' ? `g-${row.id}` : `g-${row.item.id}`"
            class="grid-card"
            :class="{ active: row.kind === 'file' && selectedId === row.item.id }"
            @click="onRowClick(row)"
            @dblclick="onRowDblClick(row)"
          >
            <div v-if="row.kind === 'folder'" class="grid-icon folder">
              <n-icon :component="FolderOutline" :size="36" />
            </div>
            <div
              v-else
              class="grid-icon"
              :style="{ color: fileIconMeta(row.item).color, background: fileIconMeta(row.item).bg }"
            >
              <span>{{ fileIconMeta(row.item).label }}</span>
            </div>
            <div class="grid-name">
              {{ row.kind === 'folder' ? row.name : row.item.title }}
            </div>
            <div class="grid-meta">
              {{
                row.kind === 'folder'
                  ? t('files.itemCount', { n: row.count })
                  : row.item.size
              }}
            </div>
          </div>
          <div v-if="!loading && listRows.length === 0" class="empty">{{ t('files.empty') }}</div>
        </div>

        <div class="list-footer">
          <div class="storage">
            <div class="storage-top">
              <span class="storage-label">{{ t('files.storage') }}</span>
              <span class="storage-usage">
                {{ t('files.storageUsed', { used: usedLabel, total: quotaLabel }) }}
              </span>
            </div>
            <div class="storage-bar">
              <div class="storage-fill" :style="{ width: `${usedPercent}%` }" />
            </div>
            <button type="button" class="expand-btn" @click="onExpandStorage">
              {{ t('files.expandStorage') }}
            </button>
          </div>
          <div class="total-count">{{ t('files.totalItems', { n: listRows.length }) }}</div>
        </div>
      </div>

      <!-- 右侧详情 -->
      <aside v-if="selectedFile" class="detail-pane">
        <div class="detail-head">
          <h3 class="detail-title" :title="selectedFile.title">{{ selectedFile.title }}</h3>
          <button type="button" class="close-btn" @click="closeDetail">
            <n-icon :component="CloseOutline" :size="18" />
          </button>
        </div>

        <div class="detail-preview">
          <div
            class="preview-icon"
            :style="{
              color: fileIconMeta(selectedFile).color,
              background: fileIconMeta(selectedFile).bg
            }"
          >
            <span>{{ fileIconMeta(selectedFile).label }}</span>
          </div>
        </div>

        <div class="detail-tabs">
          <button
            type="button"
            class="tab"
            :class="{ active: detailTab === 'info' }"
            @click="detailTab = 'info'"
          >
            {{ t('files.tabInfo') }}
          </button>
          <button
            type="button"
            class="tab"
            :class="{ active: detailTab === 'activity' }"
            @click="detailTab = 'activity'"
          >
            {{ t('files.tabActivity') }}
          </button>
        </div>

        <div v-if="detailTab === 'info'" class="detail-body">
          <div class="meta-row">
            <span class="meta-label">{{ t('files.metaType') }}</span>
            <span class="meta-value">{{ typeLabel }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ t('files.metaSize') }}</span>
            <span class="meta-value">{{ selectedFile.size || '—' }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ t('files.metaLocation') }}</span>
            <span class="meta-value">{{ locationLabel }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ t('files.metaUploader') }}</span>
            <span class="meta-value uploader">
              <span class="avatar sm">{{ selectedFile.sender.slice(0, 1) }}</span>
              {{ selectedFile.sender }}
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ t('files.metaUploadTime') }}</span>
            <span class="meta-value">{{ selectedFile.timeFull || selectedFile.time || '—' }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ t('files.metaModifyTime') }}</span>
            <span class="meta-value">{{ selectedFile.timeFull || selectedFile.time || '—' }}</span>
          </div>

          <div class="meta-block">
            <div class="meta-label">{{ t('files.metaTags') }}</div>
            <div class="tags">
              <span v-if="selectedFile.type !== 'other'" class="tag">{{
                t(`files.${selectedFile.type}`)
              }}</span>
              <span v-if="selectedFile.ext" class="tag">.{{ selectedFile.ext }}</span>
              <button type="button" class="tag-add" :title="t('files.addTag')">+</button>
            </div>
          </div>

          <div class="meta-block">
            <div class="meta-label">{{ t('files.metaDesc') }}</div>
            <p class="desc">
              {{
                selectedFile.conversationName
                  ? t('files.descFromChat', { name: selectedFile.conversationName })
                  : t('files.descEmpty')
              }}
            </p>
          </div>
        </div>

        <div v-else class="detail-body activity">
          <div class="activity-item">
            <span class="dot" />
            <div>
              <div class="act-title">{{ t('files.activityUploaded') }}</div>
              <div class="act-meta">
                {{ selectedFile.sender }} · {{ selectedFile.timeFull || selectedFile.time }}
              </div>
            </div>
          </div>
        </div>

        <div class="detail-actions">
          <button type="button" class="btn-primary grow" @click="downloadSelected">
            <n-icon :component="CloudDownloadOutline" :size="16" />
            {{ t('files.downloadFile') }}
          </button>
          <button type="button" class="btn-ghost" @click="onShareClick">
            <n-icon :component="ShareOutline" :size="16" />
            {{ t('files.share') }}
          </button>
          <button type="button" class="icon-more" @click="openPreview(selectedFile)">
            <n-icon :component="EllipsisHorizontal" :size="18" />
          </button>
        </div>
      </aside>
    </div>
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

.files-toolbar {
  flex-shrink: 0;
  padding: 12px 20px 0;
}

.search-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 320px;
}

.search-input {
  flex: 1;
}

.add-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-input);
  color: var(--lx-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
}

.add-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text);
}

.files-header {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px 12px;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--lx-text);
  line-height: 1.2;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--lx-text-muted);
}

.crumb {
  border: none;
  background: none;
  padding: 0;
  color: inherit;
  font-size: inherit;
  cursor: default;
}

.crumb.link {
  cursor: pointer;
  color: var(--lx-text-secondary);
}

.crumb.link:hover {
  color: var(--lx-accent);
}

.crumb.current {
  color: var(--lx-text-body);
}

.crumb-sep {
  color: var(--lx-text-muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.btn-primary,
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--lx-radius);
  font-size: 13px;
  cursor: pointer;
  border: 1px solid transparent;
  white-space: nowrap;
}

.btn-primary {
  background: var(--lx-accent);
  color: var(--lx-text-on-accent);
  border-color: var(--lx-accent);
}

.btn-primary:hover {
  background: var(--lx-accent-hover);
}

.btn-ghost {
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
  border-color: var(--lx-border-strong);
}

.btn-ghost:hover {
  background: var(--lx-bg-hover);
}

.btn-primary.grow {
  flex: 1;
  justify-content: center;
}

.view-toggle {
  display: flex;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  overflow: hidden;
}

.view-btn,
.icon-more,
.row-more,
.close-btn {
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.view-btn {
  width: 34px;
  height: 34px;
}

.view-btn.active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}

.icon-more {
  width: 34px;
  height: 34px;
  border-radius: var(--lx-radius);
}

.icon-more:hover,
.row-more:hover,
.close-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text);
}

.files-body {
  flex: 1;
  min-height: 0;
  display: flex;
  border-top: 1px solid var(--lx-border-light);
}

.list-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.section-head {
  padding: 14px 20px 8px;
}

.section-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text);
}

.table-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0 12px;
}

.table-head,
.table-row {
  display: grid;
  grid-template-columns: 36px minmax(180px, 1.6fr) 120px 90px 140px 40px;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
}

.table-head {
  height: 36px;
  color: var(--lx-text-muted);
  font-size: 12px;
  border-bottom: 1px solid var(--lx-border-light);
}

.th {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: none;
  padding: 0;
  color: inherit;
  font-size: inherit;
  cursor: default;
  text-align: left;
}

button.th {
  cursor: pointer;
}

button.th:hover {
  color: var(--lx-text-body);
}

.th .asc {
  transform: rotate(180deg);
}

.table-body {
  flex: 1;
  overflow-y: auto;
}

.table-row {
  min-height: 56px;
  border-radius: var(--lx-radius);
  cursor: pointer;
  transition: background 0.15s;
}

.table-row:hover {
  background: var(--lx-bg-hover);
}

.table-row.active {
  background: var(--lx-bg-active);
}

.checkbox {
  width: 16px;
  height: 16px;
  border: 1.5px solid var(--lx-border-strong);
  border-radius: 4px;
  display: inline-block;
}

.col-name {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.file-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
}

.file-icon.folder {
  background: #fff4d6;
  color: #f5a623;
}

.ext-badge {
  letter-spacing: 0.02em;
}

.name-block {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name {
  font-size: 14px;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.col-time,
.col-size {
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.col-uploader {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-size: 11px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar.sm {
  width: 20px;
  height: 20px;
  font-size: 10px;
}

.uploader-name {
  font-size: 13px;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-more {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  opacity: 0;
}

.table-row:hover .row-more {
  opacity: 1;
}

.grid-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 8px 20px 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 12px;
  align-content: start;
}

.grid-card {
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 16px 12px 12px;
  text-align: center;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.grid-card:hover {
  background: var(--lx-bg-hover);
}

.grid-card.active {
  border-color: var(--lx-accent);
  background: var(--lx-accent-soft);
}

.grid-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 10px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}

.grid-icon.folder {
  background: #fff4d6;
  color: #f5a623;
}

.grid-name {
  font-size: 13px;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: 48px 16px;
  font-size: 13px;
}

.list-footer {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--lx-border-light);
}

.storage {
  min-width: 220px;
  max-width: 280px;
}

.storage-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 6px;
}

.storage-label {
  color: var(--lx-text-secondary);
  font-weight: 500;
}

.storage-usage {
  color: var(--lx-text-muted);
}

.storage-bar {
  height: 6px;
  border-radius: 99px;
  background: var(--lx-bg-input);
  overflow: hidden;
}

.storage-fill {
  height: 100%;
  border-radius: 99px;
  background: var(--lx-accent);
  min-width: 0;
  transition: width 0.2s;
}

.expand-btn {
  margin-top: 8px;
  border: none;
  background: none;
  padding: 0;
  color: var(--lx-accent);
  font-size: 12px;
  cursor: pointer;
}

.expand-btn:hover {
  text-decoration: underline;
}

.total-count {
  font-size: 12px;
  color: var(--lx-text-muted);
  padding-bottom: 2px;
}

.detail-pane {
  width: 300px;
  min-width: 280px;
  max-width: 340px;
  border-left: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
}

.detail-head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 16px 16px 8px;
}

.detail-title {
  flex: 1;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text);
  line-height: 1.4;
  word-break: break-all;
}

.close-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  flex-shrink: 0;
}

.detail-preview {
  display: flex;
  justify-content: center;
  padding: 12px 16px 20px;
}

.preview-icon {
  width: 88px;
  height: 104px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 800;
  box-shadow: var(--lx-shadow-card);
}

.detail-tabs {
  display: flex;
  gap: 20px;
  padding: 0 16px;
  border-bottom: 1px solid var(--lx-border-light);
}

.tab {
  border: none;
  background: none;
  padding: 10px 0;
  font-size: 13px;
  color: var(--lx-text-muted);
  cursor: pointer;
  position: relative;
}

.tab.active {
  color: var(--lx-accent);
  font-weight: 600;
}

.tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--lx-accent);
  border-radius: 1px;
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  font-size: 13px;
}

.meta-label {
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.meta-value {
  color: var(--lx-text-body);
  text-align: right;
  word-break: break-all;
}

.meta-value.uploader {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.meta-block {
  margin-top: 14px;
}

.meta-block .meta-label {
  margin-bottom: 8px;
  font-size: 13px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 99px;
  background: var(--lx-bg-input);
  color: var(--lx-text-secondary);
  font-size: 12px;
}

.tag-add {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px dashed var(--lx-border-strong);
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}

.tag-add:hover {
  border-color: var(--lx-accent);
  color: var(--lx-accent);
}

.desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--lx-text-secondary);
}

.activity-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.activity-item .dot {
  width: 8px;
  height: 8px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--lx-accent);
  flex-shrink: 0;
}

.act-title {
  font-size: 13px;
  color: var(--lx-text-body);
}

.act-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.detail-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--lx-border-light);
}

@media (max-width: 960px) {
  .table-head,
  .table-row {
    grid-template-columns: 28px minmax(140px, 1fr) 100px 70px 36px;
  }

  .col-uploader {
    display: none;
  }

  .detail-pane {
    width: 260px;
    min-width: 240px;
  }
}
</style>
