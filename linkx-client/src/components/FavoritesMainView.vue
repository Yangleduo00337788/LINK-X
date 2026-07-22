<script setup lang="ts">
/**
 * 收藏主视图 — 按设计稿全宽重做（分类侧栏 + 卡片网格）
 */
import { ref, computed, onMounted, watch } from 'vue'
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
  CreateOutline,
  TrashOutline,
  CloudOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useFavoritesStore } from '../stores/favorites'
import type { FavoriteItem } from '../types'
import { formatFileSize } from '../utils/file'
import { useOverlayStore } from '../stores/overlay'
import { useI18n } from '../i18n'

const message = useMessage()
const dialog = useDialog()
const router = useRouter()
const { t } = useI18n()
const favStore = useFavoritesStore()
const overlayStore = useOverlayStore()
const { items, loading, usedBytes } = storeToRefs(favStore)

/** 收藏空间配额（与设计稿一致，后续可对接会员） */
const QUOTA_BYTES = 30 * 1024 * 1024 * 1024

type CategoryKey = 'all' | 'link' | 'image' | 'file' | 'note' | 'message' | 'other'

const PRESET_TAGS = [
  { key: '工作', color: '#ff4d79' },
  { key: '学习', color: '#3b82f6' },
  { key: '生活', color: '#22c55e' },
  { key: '灵感', color: '#f59e0b' },
  { key: '重要', color: '#a855f7' }
] as const

const search = ref('')
const category = ref<CategoryKey>('all')
const activeTag = ref<string | null>(null)
const viewMode = ref<'grid' | 'list'>('grid')
const sortKey = ref<'newest' | 'oldest' | 'title'>('newest')

const tagModalShow = ref(false)
const tagModalItem = ref<FavoriteItem | null>(null)
const tagDraft = ref('')
const newTagModalShow = ref(false)
const newTagName = ref('')

onMounted(() => {
  if (!favStore.initialized) void favStore.fetchFavorites()
})

const categoryCounts = computed(() => {
  const c: Record<CategoryKey, number> = {
    all: items.value.length,
    link: 0,
    image: 0,
    file: 0,
    note: 0,
    message: 0,
    other: 0
  }
  for (const i of items.value) {
    const k = (i.type || 'note') as CategoryKey
    if (k in c && k !== 'all') c[k] += 1
    else c.other += 1
  }
  return c
})

const tagCounts = computed(() => {
  const map: Record<string, number> = {}
  for (const tag of PRESET_TAGS) map[tag.key] = 0
  for (const i of items.value) {
    for (const tag of i.tags || []) {
      map[tag] = (map[tag] || 0) + 1
    }
  }
  return map
})

const displayTags = computed(() => {
  const extras = Object.keys(tagCounts.value).filter(
    k => !PRESET_TAGS.some(p => p.key === k) && tagCounts.value[k] > 0
  )
  return [
    ...PRESET_TAGS.map(p => ({ ...p, count: tagCounts.value[p.key] || 0 })),
    ...extras.map(k => ({ key: k, color: '#94a3b8', count: tagCounts.value[k] || 0 }))
  ]
})

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
const quotaLabel = computed(() => formatFileSize(QUOTA_BYTES))
const usedPercent = computed(() =>
  Math.min(100, Math.round((usedBytes.value / QUOTA_BYTES) * 1000) / 10)
)

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

watch(category, () => {
  // keep tag filter when switching category
})

function setCategory(key: CategoryKey) {
  category.value = key
}

function toggleTag(key: string) {
  activeTag.value = activeTag.value === key ? null : key
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
  if (item.type === 'note') {
    openNewNote()
    return
  }
  message.info(t('favorites.openHint'))
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
  newTagModalShow.value = true
}

function confirmNewTag() {
  const name = newTagName.value.trim()
  if (!name) return
  activeTag.value = name
  newTagModalShow.value = false
  message.info(t('favorites.tagFilterHint', { name }))
}

function tagColor(name: string) {
  const hit = PRESET_TAGS.find(p => p.key === name)
  return hit?.color || '#94a3b8'
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
          :key="tag.key"
          type="button"
          class="tag-item"
          :class="{ active: activeTag === tag.key }"
          @click="toggleTag(tag.key)"
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
          <div class="storage-fill" :style="{ width: `${usedPercent}%` }" />
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
            @click="viewMode = 'grid'"
          >
            <n-icon :component="GridOutline" :size="18" />
          </button>
          <button
            type="button"
            class="view-btn"
            :class="{ active: viewMode === 'list' }"
            :title="t('favorites.listView')"
            @click="viewMode = 'list'"
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
        <n-dropdown :options="sortOptions" @select="(k: string) => (sortKey = k as typeof sortKey)">
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
          <div class="card-media" :class="item.type">
            <img v-if="item.coverUrl" :src="item.coverUrl" alt="" class="card-cover" />
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
            <template v-else-if="item.type === 'note' || item.type === 'message'">
              <div class="note-preview">
                <n-icon :component="item.type === 'message' ? ChatbubblesOutline : CreateOutline" :size="20" />
                <p>{{ item.preview || item.title }}</p>
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
              <n-icon
                :component="
                  item.type === 'link'
                    ? LinkOutline
                    : item.type === 'image'
                      ? ImageOutline
                      : item.type === 'file'
                        ? FolderOutline
                        : item.type === 'message'
                          ? ChatbubblesOutline
                          : DocumentTextOutline
                "
                :size="12"
              />
            </span>
          </div>

          <div class="card-body">
            <h4 class="card-title">{{ item.title }}</h4>
            <p v-if="item.type === 'link'" class="card-sub url">{{ item.content || item.preview }}</p>
            <p v-else-if="item.fileSize != null" class="card-sub">{{ formatFileSize(item.fileSize) }}</p>
            <p v-else-if="item.type === 'note' || item.type === 'message'" class="card-sub clamp">
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
            <img v-if="item.coverUrl" :src="item.coverUrl" alt="" />
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
  --fav-accent: #ff4d79;
  --fav-accent-soft: rgba(255, 77, 121, 0.12);
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
.side-star { color: var(--fav-accent); }
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
  background: var(--fav-accent-soft);
  color: var(--fav-accent);
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
.tag-add:hover { background: var(--lx-bg-hover); color: var(--fav-accent); }
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
  background: var(--fav-accent);
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
  background: var(--fav-accent);
  color: #fff;
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
  background: var(--fav-accent-soft);
  color: var(--fav-accent);
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
.sort-btn:hover { color: var(--fav-accent); }

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
.note-preview {
  width: 100%;
  height: 100%;
  padding: 16px;
  box-sizing: border-box;
  color: var(--lx-text-secondary);
}
.note-preview p {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fallback-icon { color: var(--lx-text-muted); }
.type-chip {
  position: absolute;
  left: 10px;
  top: 10px;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--fav-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
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
  color: var(--fav-accent);
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
