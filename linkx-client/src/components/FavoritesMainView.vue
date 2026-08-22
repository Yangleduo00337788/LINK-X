<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 收藏主视图 — 按设计稿全宽重做（分类侧栏 + 卡片网格）
 */
import { ref, computed, onMounted } from 'vue'
import {
  NIcon,
  NInput,
  NDropdown,
  useMessage,
  useDialog
} from 'naive-ui'
import { LxButton, LxIconButton, LxModal } from './ui'
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
  TrashOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useFavoritesStore } from '../stores/favorites'
import { useAppSettingsStore } from '../stores/appSettings'
import { useAppStore } from '../stores/app'
import { useNoteStore } from '../stores/note'
import type { FavoriteItem } from '../types'
import { formatFileSize } from '../utils/file'
import { useOverlayStore } from '../stores/overlay'
import { useI18n } from '../i18n'
import { lxColorHex } from '../theme/vars'
import FavoriteCoverImage from './favorites/FavoriteCoverImage.vue'

const message = useMessage()
const dialog = useDialog()
const { t } = useI18n()
const favStore = useFavoritesStore()
const noteStore = useNoteStore()
const appStore = useAppStore()
const appSettings = useAppSettingsStore()
const overlayStore = useOverlayStore()
const { items, loading, tags, typeCounts } = storeToRefs(favStore)
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
const newTagColor = ref(lxColorHex.slateMuted)

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
    color: tag.color || lxColorHex.slateMuted,
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
  void noteStore.ensurePanelReady()
  noteStore.openPanel()
}

function openNoteFavorite(item: FavoriteItem) {
  const noteId = (item.sourceId || item.id || '').trim()
  if (noteId) {
    void noteStore.ensurePanelReady({ noteId })
    noteStore.openPanel({ noteId })
    return
  }
  openNewNote()
}

function fileExtIcon(item: FavoriteItem) {
  const name = item.title.toLowerCase()
  if (name.endsWith('.pdf')) return { label: 'PDF', color: lxColorHex.danger, bg: 'var(--lx-danger-bg)' }
  if (name.endsWith('.zip') || name.endsWith('.rar') || name.endsWith('.7z')) {
    return { label: 'ZIP', color: lxColorHex.warning, bg: 'var(--lx-warning-bg)' }
  }
  if (name.endsWith('.mp3') || name.endsWith('.wav') || name.endsWith('.flac')) {
    return { label: 'MP3', color: lxColorHex.brandPurple, bg: lxColorHex.fileMediaBg, music: true }
  }
  return { label: 'FILE', color: lxColorHex.slate, bg: lxColorHex.slateSoft }
}

/** 仅图片、文件展示封面图；有 messageId 时走鉴权加载 */
function showCover(item: FavoriteItem): boolean {
  return (item.type === 'image' || item.type === 'file') && !!(item.coverUrl || item.messageId)
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

function cardMenuOptions(_item: FavoriteItem) {
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
  const sessionId = item.sessionId || parseFavoriteSourceId(item).sessionId
  const messageId = item.messageId || parseFavoriteSourceId(item).messageId

  if (sessionId && messageId) {
    openConversationFavorite(item, sessionId, messageId)
    return
  }

  if (item.type === 'link' && item.content) {
    const url = item.content.trim().split(/\s|\n/)[0]
    if (/^https?:\/\//i.test(url)) {
      window.open(url, '_blank', 'noopener')
      return
    }
  }
  if (item.type === 'image' && (item.coverUrl || item.messageId)) {
    overlayStore.open('file-preview', {
      filePreview: {
        fileName: item.title,
        fileSize: item.fileSize != null ? formatFileSize(item.fileSize) : '',
        fileUrl: item.coverUrl || item.content || '',
        isImage: true,
        messageId: item.messageId
      }
    })
    return
  }
  if (item.type === 'message' || (item.sourceType === 'conversation' && item.sourceId)) {
    openConversationFavorite(item, sessionId, messageId)
    return
  }
  if (item.type === 'note') {
    openNoteFavorite(item)
    return
  }
  message.info(t('favorites.openHint'))
}

function parseFavoriteSourceId(item: FavoriteItem) {
  const raw = (item.sourceId || '').trim()
  if (!raw) return { sessionId: undefined, messageId: undefined }
  if (raw.includes('#')) {
    const [sessionId, messageId] = raw.split('#', 2)
    return { sessionId: sessionId?.trim(), messageId: messageId?.trim() }
  }
  return { sessionId: raw, messageId: undefined }
}

/** 跳转到收藏消息所属会话，并定位高亮原消息 */
function openConversationFavorite(
  _item: FavoriteItem,
  sessionId?: string,
  messageId?: string
) {
  const sid = (sessionId || '').trim()
  if (!sid) {
    message.warning(t('favorites.sessionMissing'))
    return
  }
  const session = sessions.value.find(s => s.id === sid)
  if (!session) {
    message.warning(t('favorites.sessionMissing'))
    return
  }
  const mid = (messageId || '').trim()
  if (mid) {
    const ok = appStore.openSessionAtMessage(sid, mid)
    if (!ok) {
      message.warning(t('favorites.sessionMissing'))
    }
    return
  }
  appStore.setNav('chat')
  appStore.selectSession(session)
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
  newTagColor.value = lxColorHex.slateMuted
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
  return hit?.color || lxColorHex.slateMuted
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
          :class="{ 'is-active': category === c.key }"
          @click="setCategory(c.key)"
        >
          <n-icon :component="c.icon" :size="18" />
          <span class="cat-label">{{ c.label }}</span>
          <span class="cat-count">{{ categoryCounts[c.key] }}</span>
        </button>
      </nav>

      <div class="side-tags-head">
        <span>{{ t('favorites.tags') }}</span>
        <LxIconButton class="tag-add" :title="t('favorites.addTag')" @click="openNewTagModal">
          <n-icon :component="AddOutline" :size="16" />
        </LxIconButton>
      </div>
      <div class="side-tags">
        <button
          v-for="tag in displayTags"
          :key="tag.id"
          type="button"
          class="tag-item"
          :class="{ 'is-active': activeTag === tag.key }"
          :title="tag.preset ? undefined : t('favorites.tagContextHint')"
          @click="toggleTag(tag.key)"
          @contextmenu="onTagContextMenu($event, tag)"
        >
          <span class="tag-dot" :style="{ background: tag.color }" />
          <span class="tag-name">{{ tag.key }}</span>
          <span class="tag-count">{{ tag.count }}</span>
        </button>
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
        <LxButton variant="pill-primary" @click="openNewNote">
          <n-icon :component="AddOutline" :size="16" />
          {{ t('favorites.newNote') }}
        </LxButton>
        <div class="view-toggle">
          <LxIconButton
            class="view-btn"
            :active="viewMode === 'grid'"
            :title="t('favorites.gridView')"
            @click="setViewMode('grid')"
          >
            <n-icon :component="GridOutline" :size="18" />
          </LxIconButton>
          <LxIconButton
            class="view-btn"
            :active="viewMode === 'list'"
            :title="t('favorites.listView')"
            @click="setViewMode('list')"
          >
            <n-icon :component="ListOutline" :size="18" />
          </LxIconButton>
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
          <LxButton variant="ghost" class="sort-btn">{{ sortLabel }} ▾</LxButton>
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
            <FavoriteCoverImage
              v-if="showCover(item) && item.messageId"
              :message-id="item.messageId"
              :fallback-url="item.coverUrl"
              img-class="card-cover"
              @error="onCoverError(item)"
            />
            <img
              v-else-if="showCover(item)"
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
                <LxIconButton class="more-btn" @click.stop>
                  <n-icon :component="EllipsisHorizontalOutline" :size="16" />
                </LxIconButton>
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
            <FavoriteCoverImage
              v-if="showCover(item) && item.messageId"
              :message-id="item.messageId"
              :fallback-url="item.coverUrl"
              @error="onCoverError(item)"
            />
            <img
              v-else-if="showCover(item)"
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
          <LxIconButton class="list-del" :title="t('common.delete')" @click.stop="confirmDelete(item)">
            <n-icon :component="TrashOutline" :size="16" />
          </LxIconButton>
        </div>
      </div>

      <div v-if="!loading && !filteredItems.length" class="empty">{{ t('favorites.empty') }}</div>
      <div v-if="loading" class="empty">{{ t('common.loading') }}</div>
    </section>

    <LxModal
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
          <LxButton variant="modal" @click="tagModalShow = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" @click="saveTags">{{ t('common.save') }}</LxButton>
        </div>
      </template>
    </LxModal>

    <LxModal
      v-model:show="newTagModalShow"
      preset="card"
      :title="t('favorites.addTag')"
      style="width: 360px"
    >
      <n-input v-model:value="newTagName" :placeholder="t('favorites.tagNamePh')" autofocus />
      <template #footer>
        <div class="modal-actions">
          <LxButton variant="modal" @click="newTagModalShow = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" @click="confirmNewTag">{{ t('common.confirm') }}</LxButton>
        </div>
      </template>
    </LxModal>
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
  padding: var(--lx-space-2xl) var(--lx-space-lg) var(--lx-space-lg);
}
.side-head {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  padding: 0 var(--lx-space) var(--lx-space-xl);
}
.side-head h2 {
  margin: 0;
  font-size: var(--lx-font-3xl);
  font-weight: 700;
  color: var(--lx-text);
}
.side-star { color: var(--lx-accent); }
.side-cats { display: flex; flex-direction: column; gap: var(--lx-space-2xs); }
.cat-item {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  height: 36px;
  padding: 0 var(--lx-space-md);
  border: none;
  border-radius: var(--lx-radius-xl);
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  font-size: var(--lx-font-md);
}
.cat-item:hover { background: var(--lx-bg-hover); }
.cat-item.is-active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-weight: 600;
}
.cat-label { flex: 1; text-align: left; }
.cat-count { font-size: var(--lx-font-sm); opacity: 0.8; }
.side-tags-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--lx-space-2xl);
  padding: 0 var(--lx-space) var(--lx-space);
  font-size: var(--lx-font-sm);
  font-weight: 600;
  color: var(--lx-text-muted);
}
.tag-add {
  width: 24px;
  height: 24px;
  border-radius: var(--lx-radius-xs);
}
.side-tags {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-2xs);
}
.tag-item {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  height: 32px;
  padding: 0 var(--lx-space-md);
  border: none;
  border-radius: var(--lx-radius-sm);
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  font-size: var(--lx-font-md);
}
.tag-item:hover,
.tag-item.is-active { background: var(--lx-bg-hover); }
.tag-item.is-active { color: var(--lx-text); font-weight: 600; }
.tag-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.tag-name { flex: 1; text-align: left; }
.tag-count { font-size: var(--lx-font-sm); color: var(--lx-text-muted); }

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
  gap: var(--lx-space-md);
  padding: var(--lx-space-2xl) var(--lx-space-3xl) var(--lx-space);
}
.fav-search { flex: 1; max-width: 480px; }
.view-toggle {
  display: flex;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-xl);
  overflow: hidden;
}
.view-toggle :deep(.view-btn) {
  width: 34px;
  height: 34px;
  border-radius: 0;
}
.fav-subhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lx-space) var(--lx-space-3xl) var(--lx-space-lg);
}
.fav-subhead h3 {
  margin: 0;
  font-size: var(--lx-font-lg);
  font-weight: 600;
  color: var(--lx-text);
}
.sub-count { color: var(--lx-text-muted); font-weight: 500; margin-left: var(--lx-space-xs); }
.sort-btn {
  border: none !important;
  background: transparent !important;
  color: var(--lx-text-secondary);
  font-size: var(--lx-font-md);
  padding: var(--lx-space-xs) var(--lx-space);
  height: auto;
}
.sort-btn:hover { color: var(--lx-accent); }

.fav-grid {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-xs) var(--lx-space-3xl) var(--lx-space-4xl);
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--lx-space-2xl);
  align-content: start;
}
.fav-card {
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-card);
  background: var(--lx-bg-card);
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: box-shadow var(--lx-duration), transform var(--lx-duration);
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
  border-radius: var(--lx-radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: var(--lx-font);
}
.fallback-icon { color: var(--lx-text-muted); }
.card-body.no-media {
  padding-top: var(--lx-space-xl);
}
.text-type-row {
  margin-bottom: var(--lx-space);
}
.type-chip {
  position: absolute;
  left: 10px;
  top: 10px;
  width: 24px;
  height: 24px;
  border-radius: var(--lx-radius-sm);
  background: rgba(255, 255, 255, 0.92);
  color: var(--lx-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--lx-shadow-xs);
}
.type-chip.inline {
  position: static;
  background: var(--lx-accent-soft);
}
.card-body { padding: var(--lx-space-lg) var(--lx-space-xl) var(--lx-space-lg); flex: 1; display: flex; flex-direction: column; }
.card-title {
  margin: 0;
  font-size: var(--lx-font);
  font-weight: 650;
  color: var(--lx-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-sub {
  margin: var(--lx-space-xs) 0 0;
  font-size: var(--lx-font-sm);
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
  gap: var(--lx-space-sm);
  margin-top: var(--lx-space-md);
}
.pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 var(--lx-space);
  border-radius: var(--lx-radius-pill);
  font-size: var(--lx-font-xs);
  font-weight: 600;
}
.card-foot {
  margin-top: auto;
  padding-top: var(--lx-space-md);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-time { font-size: var(--lx-font-sm); color: var(--lx-text-muted); }
.more-btn {
  width: 28px;
  height: 28px;
  border-radius: var(--lx-radius-sm);
}

.fav-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--lx-space-3xl) var(--lx-space-4xl);
}
.list-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-lg) var(--lx-space-md);
  border-radius: var(--lx-radius-xl);
  cursor: pointer;
}
.list-row:hover { background: var(--lx-bg-hover); }
.list-thumb {
  width: 44px;
  height: 44px;
  border-radius: var(--lx-radius-xl);
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
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.list-sub {
  margin-top: var(--lx-space-2xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.list-meta { font-size: var(--lx-font-sm); color: var(--lx-text-muted); flex-shrink: 0; }
.list-del {
  opacity: 0;
  color: var(--lx-text-muted);
}
.list-row:hover :deep(.list-del) { opacity: 1; }
.list-del:hover { color: var(--lx-danger); }

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: var(--lx-space-block-xl) var(--lx-space-2xl);
  font-size: var(--lx-font-md);
}
.modal-actions { display: flex; justify-content: flex-end; gap: var(--lx-space); }
</style>
