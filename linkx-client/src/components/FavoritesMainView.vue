<script setup lang="ts">
/**
 * 收藏主视图 — 按设计稿全宽重做（分类侧栏 + 卡片网格）
 */
import { ref, computed, onMounted } from 'vue'
import {
  NIcon,
  NInput,
  NDropdown,
  NModal,
  NButton,
  useMessage,
  useDialog
} from 'naive-ui'
import {
  SearchOutline,
  AddOutline,
  StarOutline,
  LinkOutline,
  ImageOutline,
  DocumentTextOutline,
  FolderOutline,
  ChatbubblesOutline,
  EllipsisHorizontalOutline,
  GridOutline,
  ListOutline,
  MusicalNotesOutline,
  TrashOutline,
  CloudOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useFavoritesStore } from '../stores/favorites'
import { useAppSettingsStore } from '../stores/appSettings'
import { useAppStore } from '../stores/app'
import type { FavoriteItem } from '../types'
import { formatFileSize } from '../utils/file'
import { useOverlayStore } from '../stores/overlay'
import { useI18n } from '../i18n'

const message = useMessage()
const dialog = useDialog()
const router = useRouter()
const { t } = useI18n()
const favStore = useFavoritesStore()
const appStore = useAppStore()
const appSettings = useAppSettingsStore()
const overlayStore = useOverlayStore()
const { items, loading, tags, typeCounts, usedBytes, quotaBytes, usedPercent } = storeToRefs(favStore)
const { favoritesViewMode, favoritesSort } = storeToRefs(appSettings)
const { sessions } = storeToRefs(appStore)

type CategoryKey = 'all' | 'link' | 'image' | 'file' | 'note' | 'message' | 'other'

const search = ref('')
const category = ref<CategoryKey>('all')
const activeTag = ref<string | null>(null)

const viewMode = computed({
  get: () => favoritesViewMode.value,
  set: (v: 'grid' | 'list') => {
    appSettings.favoritesViewMode = v
    appSettings.scheduleSave('favoritesViewMode')
  }
})

const sortKey = computed({
  get: () => favoritesSort.value,
  set: (v: 'newest' | 'oldest' | 'title') => {
    appSettings.favoritesSort = v
    appSettings.scheduleSave('favoritesSort')
  }
})

const tagModalShow = ref(false)
const tagModalItem = ref<FavoriteItem | null>(null)
const tagDraft = ref('')
const newTagModalShow = ref(false)
const newTagName = ref('')
const newTagColor = ref('#94a3b8')

onMounted(() => {
  void favStore.refreshAll()
})

const categoryCounts = computed(() => {
  const c = typeCounts.value || {}
  return {
    all: c.all ?? items.value.length,
    link: c.link ?? 0,
    image: c.image ?? 0,
    file: c.file ?? 0,
    note: c.note ?? 0,
    message: c.message ?? 0,
    other: c.other ?? 0
  } as Record<CategoryKey, number>
})

const displayTags = computed(() =>
  (tags.value || []).map(tag => ({
    id: tag.id,
    key: tag.name,
    color: tag.color || '#94a3b8',
    count: tag.count ?? 0,
    preset: !!tag.preset
  }))
)

const filteredItems = computed(() => {
  let list = [...items.value]
  if (category.value !== 'all') {
    list = list.filter(i => i.type === category.value)
  }
  if (activeTag.value) {
    list = list.filter(i => (i.tags || []).includes(activeTag.value!))
  }
  const q = search.value.trim().toLowerCase()
  if (q) {
    list = list.filter(i => {
      const hay = `${i.title} ${i.preview} ${(i.tags || []).join(' ')}`.toLowerCase()
      return hay.includes(q)
    })
  }
  list.sort((a, b) => {
    if (sortKey.value === 'title') return a.title.localeCompare(b.title, 'zh-CN')
    const ta = a.createTimeMs || 0
    const tb = b.createTimeMs || 0
    return sortKey.value === 'newest' ? tb - ta : ta - tb
  })
  return list
})

const usedLabel = computed(() => formatFileSize(usedBytes.value))
const quotaLabel = computed(() => formatFileSize(quotaBytes.value))
const storagePercent = computed(() => usedPercent.value)

const categoryItems = computed(() => [
  { key: 'all' as CategoryKey, label: t('favorites.allFavorites'), icon: StarOutline },
  { key: 'link' as CategoryKey, label: t('favorites.link'), icon: LinkOutline },
  { key: 'image' as CategoryKey, label: t('favorites.image'), icon: ImageOutline },
  { key: 'file' as CategoryKey, label: t('favorites.file'), icon: FolderOutline },
  { key: 'note' as CategoryKey, label: t('favorites.note'), icon: DocumentTextOutline },
  { key: 'message' as CategoryKey, label: t('favorites.chatHistory'), icon: ChatbubblesOutline },
  { key: 'other' as CategoryKey, label: t('favorites.other'), icon: EllipsisHorizontalOutline }
])

const sortOptions = computed(() => [
  { label: t('favorites.sortNewest'), key: 'newest' },
  { label: t('favorites.sortOldest'), key: 'oldest' },
  { label: t('favorites.sortTitle'), key: 'title' }
])

const sortLabel = computed(() => {
  const hit = sortOptions.value.find(o => o.key === sortKey.value)
  return hit?.label || t('favorites.sortNewest')
})

function setCategory(key: CategoryKey) {
  category.value = key
}

function toggleTag(key: string) {
  activeTag.value = activeTag.value === key ? null : key
}

function setSort(key: string) {
  if (key === 'newest' || key === 'oldest' || key === 'title') {
    sortKey.value = key
  }
}

function setViewMode(mode: 'grid' | 'list') {
  viewMode.value = mode
}

function openNewNote() {
  if (window.electronAPI) {
    window.electronAPI.openNoteEditor()
  } else {
    router.push('/note-editor')
  }
}

function fileExtIcon(item: FavoriteItem) {
  const name = item.title.toLowerCase()
  if (name.endsWith('.pdf')) return { label: 'PDF', color: '#ef4444', bg: '#fee2e2' }
  if (name.endsWith('.zip') || name.endsWith('.rar') || name.endsWith('.7z')) {
    return { label: 'ZIP', color: '#f59e0b', bg: '#fef3c7' }
  }
  if (name.endsWith('.mp3') || name.endsWith('.wav') || name.endsWith('.flac')) {
    return { label: 'MP3', color: '#a855f7', bg: '#f3e8ff', music: true }
  }
  return { label: 'FILE', color: '#64748b', bg: '#f1f5f9' }
}

/** 仅图片、文件展示封面图；笔记等不展示 */
function showCover(item: FavoriteItem): boolean {
  return (item.type === 'image' || item.type === 'file') && !!item.coverUrl
}

/** 仅媒体类展示顶部预览区；聊天记录/笔记不展示文本块 */
function showMedia(item: FavoriteItem): boolean {
  return item.type === 'image' || item.type === 'file' || item.type === 'link'
}

function onCoverError(item: FavoriteItem) {
  item.coverUrl = undefined
}

function typeIcon(item: FavoriteItem) {
  if (item.type === 'link') return LinkOutline
  if (item.type === 'image') return ImageOutline
  if (item.type === 'file') return FolderOutline
  if (item.type === 'message') return ChatbubblesOutline
  return DocumentTextOutline
}

function cardMenuOptions(item: FavoriteItem) {
  return [
    { label: t('favorites.open'), key: 'open' },
    { label: t('favorites.editTags'), key: 'tags' },
    { label: t('favorites.delete'), key: 'delete' }
  ]
}

function onCardMenu(key: string, item: FavoriteItem) {
  if (key === 'open') openItem(item)
  else if (key === 'tags') openTagEditor(item)
  else if (key === 'delete') confirmDelete(item)
}

function openItem(item: FavoriteItem) {
  if (item.type === 'link' && item.content) {
    const url = item.content.trim().split(/\s|\n/)[0]
    if (/^https?:\/\//i.test(url)) {
      window.open(url, '_blank', 'noopener')
      return
    }
  }
  if (item.type === 'image' && item.coverUrl) {
    overlayStore.open('file-preview', {
      filePreview: {
        fileName: item.title,
        fileSize: item.fileSize != null ? formatFileSize(item.fileSize) : '',
        fileUrl: item.coverUrl,
        isImage: true
      }
    })
    return
  }
  if (item.type === 'message' || (item.sourceType === 'conversation' && item.sourceId)) {
    openConversationFavorite(item)
    return
  }
  if (item.type === 'note') {
    openNewNote()
    return
  }
  message.info(t('favorites.openHint'))
}

/** 跳转到收藏消息所属会话 */
function openConversationFavorite(item: FavoriteItem) {
  const raw = (item.sourceId || '').trim()
  const sessionId = raw.includes('#') ? raw.split('#')[0] : raw
  if (!sessionId) {
    message.warning(t('favorites.sessionMissing'))
    return
  }
  const session = sessions.value.find(s => s.id === sessionId)
  if (!session) {
    message.warning(t('favorites.sessionMissing'))
    return
  }
  appStore.setNav('chat')
  appStore.selectSession(session)
  message.success(t('overlay.jumpedToSession'))
}

function confirmDelete(item: FavoriteItem) {
  dialog.warning({
    title: t('favorites.delete'),
    content: t('favorites.deleteConfirm', { name: item.title }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      const ok = await favStore.remove(item.id)
      if (ok) message.success(t('favorites.deleted'))
      else message.error(t('favorites.deleteFail'))
    }
  })
}

function openTagEditor(item: FavoriteItem) {
  tagModalItem.value = item
  tagDraft.value = (item.tags || []).join('，')
  tagModalShow.value = true
}

async function saveTags() {
  const item = tagModalItem.value
  if (!item) return
  const tags = tagDraft.value
    .split(/[,，]/)
    .map(s => s.trim())
    .filter(Boolean)
  const ok = await favStore.update(item.id, { tags })
  if (ok) {
    message.success(t('favorites.tagsSaved'))
    tagModalShow.value = false
  } else {
    message.error(t('favorites.tagsFail'))
  }
}

function openNewTagModal() {
  newTagName.value = ''
  newTagColor.value = '#94a3b8'
  newTagModalShow.value = true
}

async function confirmNewTag() {
  const name = newTagName.value.trim()
  if (!name) return
  try {
    await favStore.createTag(name, newTagColor.value)
    activeTag.value = name
    newTagModalShow.value = false
    message.success(t('favorites.tagCreated'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('favorites.tagCreateFail'))
  }
}

function tagColor(name: string) {
  const hit = displayTags.value.find(p => p.key === name)
  return hit?.color || '#94a3b8'
}

function onTagContextMenu(e: MouseEvent, tag: { id: string; key: string; preset: boolean }) {
  e.preventDefault()
  if (tag.preset) {
    message.info(t('favorites.presetTagLocked'))
    return
  }
  dialog.warning({
    title: t('favorites.deleteTag'),
    content: t('favorites.deleteTagConfirm', { name: tag.key }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await favStore.deleteTag(tag.id)
        if (activeTag.value === tag.key) activeTag.value = null
        message.success(t('favorites.tagDeleted'))
      } catch (err) {
        message.error(err instanceof Error ? err.message : t('favorites.tagDeleteFail'))
      }
    }
  })
}
</script>

<template>
  <div class="fav-main">
    <!-- 左侧分类 -->
    <aside class="fav-side">
      <div class="side-head">
        <n-icon :component="StarOutline" :size="20" class="side-star" />
        <h2>{{ t('nav.favorites') }}</h2>
      </div>

      <nav class="side-cats">
        <button
          v-for="c in categoryItems"
          :key="c.key"
          type="button"
          class="cat-item"
          :class="{ active: category === c.key }"
          @click="setCategory(c.key)"
        >
          <n-icon :component="c.icon" :size="18" />
          <span class="cat-label">{{ c.label }}</span>
          <span class="cat-count">{{ categoryCounts[c.key] }}</span>
        </button>
      </nav>

      <div class="side-tags-head">
        <span>{{ t('favorites.tags') }}</span>
        <button type="button" class="tag-add" :title="t('favorites.addTag')" @click="openNewTagModal">
          <n-icon :component="AddOutline" :size="16" />
        </button>
      </div>
      <div class="side-tags">
        <button
          v-for="tag in displayTags"
          :key="tag.id"
          type="button"
          class="tag-item"
          :class="{ active: activeTag === tag.key }"
          :title="tag.preset ? undefined : t('favorites.tagContextHint')"
          @click="toggleTag(tag.key)"
          @contextmenu="onTagContextMenu($event, tag)"
        >
          <span class="tag-dot" :style="{ background: tag.color }" />
          <span class="tag-name">{{ tag.key }}</span>
          <span class="tag-count">{{ tag.count }}</span>
        </button>
      </div>

      <div class="side-storage">
        <div class="storage-top">
          <n-icon :component="CloudOutline" :size="16" />
          <span>{{ t('favorites.storage') }}</span>
        </div>
        <div class="storage-usage">{{ usedLabel }} / {{ quotaLabel }}</div>
        <div class="storage-bar">
          <div class="storage-fill" :style="{ width: `${storagePercent}%` }" />
        </div>
      </div>
    </aside>

    <!-- 主区 -->
    <section class="fav-content">
      <div class="fav-toolbar">
        <n-input
          v-model:value="search"
          size="medium"
          class="fav-search lx-search-input"
          :placeholder="t('favorites.searchPh')"
          clearable
        >
          <template #prefix>
            <n-icon :component="SearchOutline" :size="16" />
          </template>
        </n-input>
        <button type="button" class="btn-new" @click="openNewNote">
          <n-icon :component="AddOutline" :size="16" />
          {{ t('favorites.newNote') }}
        </button>
        <div class="view-toggle">
          <button
            type="button"
            class="view-btn"
            :class="{ active: viewMode === 'grid' }"
            :title="t('favorites.gridView')"
            @click="setViewMode('grid')"
          >
            <n-icon :component="GridOutline" :size="18" />
          </button>
          <button
            type="button"
            class="view-btn"
            :class="{ active: viewMode === 'list' }"
            :title="t('favorites.listView')"
            @click="setViewMode('list')"
          >
            <n-icon :component="ListOutline" :size="18" />
          </button>
        </div>
      </div>

      <div class="fav-subhead">
        <h3>
          {{
            category === 'all'
              ? t('favorites.allFavorites')
              : categoryItems.find(c => c.key === category)?.label
          }}
          <span class="sub-count">({{ filteredItems.length }})</span>
        </h3>
        <n-dropdown :options="sortOptions" @select="setSort">
          <button type="button" class="sort-btn">{{ sortLabel }} ▾</button>
        </n-dropdown>
      </div>

      <div v-if="viewMode === 'grid'" class="fav-grid">
        <article
          v-for="item in filteredItems"
          :key="item.id"
          class="fav-card"
          @click="openItem(item)"
          @dblclick="openItem(item)"
        >
          <div v-if="showMedia(item)" class="card-media" :class="item.type">
            <img
              v-if="showCover(item)"
              :src="item.coverUrl"
              alt=""
              class="card-cover"
              loading="lazy"
              decoding="async"
              @error="onCoverError(item)"
            />
            <template v-else-if="item.type === 'file'">
              <div
                class="file-badge"
                :style="{ color: fileExtIcon(item).color, background: fileExtIcon(item).bg }"
              >
                <n-icon
                  v-if="fileExtIcon(item).music"
                  :component="MusicalNotesOutline"
                  :size="28"
                />
                <span v-else>{{ fileExtIcon(item).label }}</span>
              </div>
            </template>
            <template v-else>
              <div class="fallback-icon">
                <n-icon
                  :component="item.type === 'link' ? LinkOutline : ImageOutline"
                  :size="28"
                />
              </div>
            </template>
            <span class="type-chip">
              <n-icon :component="typeIcon(item)" :size="12" />
            </span>
          </div>

          <div class="card-body" :class="{ 'no-media': !showMedia(item) }">
            <div v-if="!showMedia(item)" class="text-type-row">
              <span class="type-chip inline">
                <n-icon :component="typeIcon(item)" :size="12" />
              </span>
            </div>
            <h4 class="card-title">{{ item.title }}</h4>
            <p v-if="item.type === 'link'" class="card-sub url">{{ item.content || item.preview }}</p>
            <p v-else-if="item.fileSize != null" class="card-sub">{{ formatFileSize(item.fileSize) }}</p>
            <p
              v-else-if="
                (item.type === 'note' || item.type === 'message') &&
                item.preview &&
                item.preview !== item.title
              "
              class="card-sub clamp"
            >
              {{ item.preview }}
            </p>

            <div v-if="item.tags?.length" class="card-tags">
              <span
                v-for="tag in item.tags"
                :key="tag"
                class="pill"
                :style="{ color: tagColor(tag), background: tagColor(tag) + '22' }"
              >
                {{ tag }}
              </span>
            </div>

            <div class="card-foot">
              <span class="card-time">{{ item.time }}</span>
              <n-dropdown
                :options="cardMenuOptions(item)"
                @select="(k: string) => onCardMenu(k, item)"
              >
                <button type="button" class="more-btn" @click.stop>
                  <n-icon :component="EllipsisHorizontalOutline" :size="16" />
                </button>
              </n-dropdown>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="fav-list">
        <div
          v-for="item in filteredItems"
          :key="'l-' + item.id"
          class="list-row"
          @click="openItem(item)"
        >
          <div class="list-thumb">
            <img
              v-if="showCover(item)"
              :src="item.coverUrl"
              alt=""
              loading="lazy"
              decoding="async"
              @error="onCoverError(item)"
            />
            <n-icon
              v-else
              :component="
                item.type === 'link'
                  ? LinkOutline
                  : item.type === 'image'
                    ? ImageOutline
                    : item.type === 'file'
                      ? FolderOutline
                      : DocumentTextOutline
              "
              :size="20"
            />
          </div>
          <div class="list-main">
            <div class="list-title">{{ item.title }}</div>
            <div class="list-sub">{{ item.preview }}</div>
          </div>
          <div class="list-meta">{{ item.time }}</div>
          <button type="button" class="list-del" @click.stop="confirmDelete(item)">
            <n-icon :component="TrashOutline" :size="16" />
          </button>
        </div>
      </div>

      <div v-if="!loading && !filteredItems.length" class="empty">{{ t('favorites.empty') }}</div>
      <div v-if="loading" class="empty">{{ t('common.loading') }}</div>
    </section>

    <n-modal
      v-model:show="tagModalShow"
      preset="card"
      :title="t('favorites.editTags')"
      style="width: 420px"
      :mask-closable="false"
    >
      <n-input
        v-model:value="tagDraft"
        type="textarea"
        :rows="3"
        :placeholder="t('favorites.tagsPh')"
      />
      <template #footer>
        <div class="modal-actions">
          <n-button @click="tagModalShow = false">{{ t('common.cancel') }}</n-button>
          <n-button type="primary" @click="saveTags">{{ t('common.save') }}</n-button>
        </div>
      </template>
    </n-modal>

    <n-modal
      v-model:show="newTagModalShow"
      preset="card"
      :title="t('favorites.addTag')"
      style="width: 360px"
    >
      <n-input v-model:value="newTagName" :placeholder="t('favorites.tagNamePh')" autofocus />
      <template #footer>
        <div class="modal-actions">
          <n-button @click="newTagModalShow = false">{{ t('common.cancel') }}</n-button>
          <n-button type="primary" @click="confirmNewTag">{{ t('common.confirm') }}</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.fav-main {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  background: var(--lx-bg-card);
  overflow: hidden;
}
.fav-side {
  width: 220px;
  min-width: 200px;
  border-right: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  padding: 16px 12px 12px;
}
.side-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px 14px;
}
.side-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--lx-text);
}
.side-star { color: var(--lx-accent); }
.side-cats { display: flex; flex-direction: column; gap: 2px; }
.cat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
  padding: 0 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  font-size: 13px;
}
.cat-item:hover { background: var(--lx-bg-hover); }
.cat-item.active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-weight: 600;
}
.cat-label { flex: 1; text-align: left; }
.cat-count { font-size: 12px; opacity: 0.8; }
.side-tags-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18px;
  padding: 0 8px 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--lx-text-muted);
}
.tag-add {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tag-add:hover { background: var(--lx-bg-hover); color: var(--lx-accent); }
.side-tags {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  font-size: 13px;
}
.tag-item:hover,
.tag-item.active { background: var(--lx-bg-hover); }
.tag-item.active { color: var(--lx-text); font-weight: 600; }
.tag-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.tag-name { flex: 1; text-align: left; }
.tag-count { font-size: 12px; color: var(--lx-text-muted); }
.side-storage {
  margin-top: 12px;
  padding: 12px;
  border-radius: 12px;
  background: var(--lx-bg-input);
}
.storage-top {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--lx-text-secondary);
}
.storage-usage {
  margin-top: 6px;
  font-size: 12px;
  color: var(--lx-text-muted);
}
.storage-bar {
  margin-top: 8px;
  height: 6px;
  border-radius: 99px;
  background: var(--lx-border-light);
  overflow: hidden;
}
.storage-fill {
  height: 100%;
  border-radius: 99px;
  background: var(--lx-accent);
  transition: width 0.2s;
}

.fav-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.fav-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px 8px;
}
.fav-search { flex: 1; max-width: 480px; }
.btn-new {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 99px;
  background: var(--lx-accent);
  color: var(--lx-text-on-accent, #fff);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.btn-new:hover { filter: brightness(1.05); }
.view-toggle {
  display: flex;
  border: 1px solid var(--lx-border-light);
  border-radius: 10px;
  overflow: hidden;
}
.view-btn {
  width: 34px;
  height: 34px;
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.view-btn.active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}
.fav-subhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px 12px;
}
.fav-subhead h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text);
}
.sub-count { color: var(--lx-text-muted); font-weight: 500; margin-left: 4px; }
.sort-btn {
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.sort-btn:hover { color: var(--lx-accent); }

.fav-grid {
  flex: 1;
  overflow-y: auto;
  padding: 4px 20px 24px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  align-content: start;
}
.fav-card {
  border: 1px solid var(--lx-border-light);
  border-radius: 14px;
  background: var(--lx-bg-card);
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.15s, transform 0.15s;
  display: flex;
  flex-direction: column;
}
.fav-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.card-media {
  position: relative;
  height: 140px;
  background: var(--lx-bg-input);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.card-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.file-badge {
  width: 64px;
  height: 72px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 14px;
}
.fallback-icon { color: var(--lx-text-muted); }
.card-body.no-media {
  padding-top: 14px;
}
.text-type-row {
  margin-bottom: 8px;
}
.type-chip {
  position: absolute;
  left: 10px;
  top: 10px;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--lx-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.type-chip.inline {
  position: static;
  background: var(--lx-accent-soft);
}
.card-body { padding: 12px 14px 12px; flex: 1; display: flex; flex-direction: column; }
.card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 650;
  color: var(--lx-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--lx-text-muted);
}
.card-sub.url,
.card-sub.clamp {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 99px;
  font-size: 11px;
  font-weight: 600;
}
.card-foot {
  margin-top: auto;
  padding-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-time { font-size: 12px; color: var(--lx-text-muted); }
.more-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.more-btn:hover { background: var(--lx-bg-hover); color: var(--lx-text); }

.fav-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px 24px;
}
.list-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 10px;
  border-radius: 10px;
  cursor: pointer;
}
.list-row:hover { background: var(--lx-bg-hover); }
.list-thumb {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--lx-bg-input);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-accent);
  flex-shrink: 0;
}
.list-thumb img { width: 100%; height: 100%; object-fit: cover; }
.list-main { flex: 1; min-width: 0; }
.list-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.list-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--lx-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.list-meta { font-size: 12px; color: var(--lx-text-muted); flex-shrink: 0; }
.list-del {
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  opacity: 0;
}
.list-row:hover .list-del { opacity: 1; }
.list-del:hover { color: #ef4444; }

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: 64px 16px;
  font-size: 13px;
}
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>
