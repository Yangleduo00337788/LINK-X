<script setup lang="ts">
/**
 * 友链发布统一独立窗口
 * 根据路由 hash 自动判断模式:
 *   - /moments/text  → 文字模式:标题"发表文字",不显示图片/视频上传
 *   - /moments/media → 媒体模式:标题"发表图片/视频",显示图片/视频上传
 *
 * 同一套页面、同一套交互,差异仅在标题与是否渲染媒体区。
 */
import { computed, nextTick, ref, onMounted } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import {
  LocationOutline,
  AtCircleOutline,
  PersonCircleOutline,
  ChevronForwardOutline,
  PlayCircleOutline,
  CheckmarkCircleOutline,
  TrashOutline,
  AddOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useAppStore } from '../stores/app'
import { useMomentsStore } from '../stores/moments'
import { useContactsStore } from '../stores/contacts'
import { readFileAsDataUrl, dataUrlToFile, MAX_IMAGE_BYTES } from '../utils/file'
import AtMentionPicker from './common/AtMentionPicker.vue'

const route = useRoute()
const appStore = useAppStore()
const momentsStore = useMomentsStore()
const contactsStore = useContactsStore()
const { userProfile } = storeToRefs(appStore)

const message = useMessage()

/** 当前模式:text=纯文字,media=图片/视频 */
const mode = computed<'text' | 'media'>(() => {
  const hash = (route.hash || window.location.hash || '').toLowerCase()
  if (hash.includes('media')) return 'media'
  return 'text'
})

// 文字内容
const text = ref('')
const mentions = ref<Record<number, string>>({})

// 媒体列表(仅在媒体模式下使用)
const images = ref<string[]>([])
const videos = ref<{ url: string; file?: File }[]>([])

// refs
const textArea = ref<HTMLTextAreaElement | null>(null)
const mediaInputRef = ref<HTMLInputElement | null>(null)

// @ 弹层 (输入 @ 触发)
const showMentionPicker = ref(false)
const mentionQuery = ref('')
const mentionStartIndex = ref(0)
const mentionPickerRef = ref<InstanceType<typeof AtMentionPicker> | null>(null)

// 选项列表状态
const visibility = ref('公开')

// 发布成功
const showSuccess = ref(false)

// 字数统计
const charCount = computed(() => text.value.length)
const remainingChars = computed(() => 2000 - charCount.value)
const isOverLimit = computed(() => charCount.value > 2000)

const mentionFriends = computed(() => {
  if (!mentionQuery.value) return contactsStore.friends.slice(0, 8)
  return contactsStore.friends
    .filter(f => f.name.toLowerCase().includes(mentionQuery.value.toLowerCase()))
    .slice(0, 8)
})

const canPublish = computed(() => {
  if (publishing.value || isOverLimit.value) return false
  if (mode.value === 'text') {
    return text.value.trim().length > 0
  }
  return text.value.trim().length > 0 || images.value.length > 0 || videos.value.length > 0
})

/** 媒体模式时,文字是否必填 */
const textRequired = computed(() => mode.value === 'media')

onMounted(() => {
  if (!contactsStore.items.length) {
    void contactsStore.fetchFriends()
  }
  nextTick(() => {
    if (mode.value === 'text') {
      textArea.value?.focus()
    }
  })
})

function closeWindow() {
  if (window.electronAPI) {
    window.electronAPI.close()
  }
}

/** @ 检测 */
function detectMentionTrigger() {
  const ta = textArea.value
  if (!ta) return
  const value = text.value
  const cursor = ta.selectionStart
  if (cursor == null) {
    showMentionPicker.value = false
    return
  }
  let i = cursor - 1
  while (i >= 0) {
    const ch = value[i]
    if (ch === '@') {
      const segment = value.slice(i + 1, cursor)
      if (/^\S{0,32}$/.test(segment) && !segment.includes(' ')) {
        mentionStartIndex.value = i
        mentionQuery.value = segment
        showMentionPicker.value = true
      } else {
        showMentionPicker.value = false
      }
      return
    }
    if (ch === ' ' || ch === '\n' || ch === '\t') break
    i--
  }
  showMentionPicker.value = false
}

function onTextBlur() {
  setTimeout(() => { showMentionPicker.value = false }, 120)
}

function onTextInput() {
  detectMentionTrigger()
}

function onTextKeyDown(e: KeyboardEvent) {
  if (!showMentionPicker.value) return
  if (e.key === 'ArrowDown') {
    mentionPickerRef.value?.move(1)
    e.preventDefault()
  } else if (e.key === 'ArrowUp') {
    mentionPickerRef.value?.move(-1)
    e.preventDefault()
  } else if (e.key === 'Enter' || e.key === 'Tab') {
    const pick = mentionPickerRef.value?.confirm()
    if (pick) {
      e.preventDefault()
      applyMention(pick.id, pick.name)
    }
  } else if (e.key === 'Escape') {
    showMentionPicker.value = false
    e.preventDefault()
  }
}

function applyMention(id: number, name: string) {
  const ta = textArea.value
  if (!ta) return
  const before = text.value.slice(0, mentionStartIndex.value)
  const cursor = ta.selectionStart ?? mentionStartIndex.value
  const after = text.value.slice(cursor)
  const inserted = `@${name} `
  text.value = before + inserted + after
  mentions.value[id] = name
  showMentionPicker.value = false
  nextTick(() => {
    const newPos = before.length + inserted.length
    ta.focus()
    ta.setSelectionRange(newPos, newPos)
  })
}

/** 选择媒体(图片 + 视频) */
async function onPickMedia(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    const isVideo = file.type.startsWith('video/')
    const isImage = file.type.startsWith('image/')
    if (!isVideo && !isImage) {
      message.warning(`「${file.name}」类型不支持,已跳过`)
      continue
    }
    if (isVideo) {
      if (videos.value.length >= 1) {
        message.warning('目前仅支持添加 1 个视频')
        continue
      }
      if (file.size > 50 * 1024 * 1024) {
        message.warning(`「${file.name}」超过 50MB,已跳过`)
        continue
      }
      try {
        videos.value.push({ url: await readFileAsDataUrl(file), file })
      } catch {
        message.error(`「${file.name}」读取失败`)
      }
    } else {
      if (images.value.length >= 9) {
        message.warning('最多添加 9 张图片')
        break
      }
      if (file.size > MAX_IMAGE_BYTES) {
        message.warning(`「${file.name}」超过 2MB,已跳过`)
        continue
      }
      try {
        images.value.push(await readFileAsDataUrl(file))
      } catch {
        message.error(`「${file.name}」读取失败`)
      }
    }
  }
}

function removeImage(idx: number) {
  images.value.splice(idx, 1)
}

function removeVideo(idx: number) {
  videos.value.splice(idx, 1)
}

const publishing = ref(false)

async function publish() {
  const trimmed = text.value.trim()
  if (mode.value === 'text') {
    if (!trimmed) {
      message.warning('请输入要发表的内容')
      return
    }
  } else {
    if (!trimmed && !images.value.length && !videos.value.length) {
      message.warning('请输入文字或添加图片/视频')
      return
    }
  }

  publishing.value = true
  try {
    let uploaded: string[] = []
    if (images.value.length) {
      message.info('正在上传图片...')
      for (const dataUrl of images.value) {
        const ext = dataUrl.match(/data:image\/(\w+)/)?.[1] || 'jpeg'
        const safeExt = ext === 'jpg' ? 'jpeg' : ext
        const fileName = `moments_${Date.now()}.${safeExt === 'jpeg' ? 'jpg' : safeExt}`
        const file = dataUrlToFile(dataUrl, fileName)
        const normalized = new File([file], fileName, { type: `image/${safeExt}` })
        const { uploadMomentsImage } = await import('../api/moments')
        const res = await uploadMomentsImage(normalized)
        if (res.code !== 200 || !res.data) {
          throw new Error(res.message || '图片上传失败')
        }
        uploaded.push(res.data)
      }
    }
    const finalContent = trimmed || (mode.value === 'media' ? '分享图片' : '')
    const ok = await momentsStore.addPost(finalContent, uploaded)
    if (ok) {
      showSuccess.value = true
      message.success('发布成功')
      setTimeout(() => {
        closeWindow()
      }, 1200)
    } else {
      message.error('发布失败,请重试')
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发布失败')
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <!-- 发布成功动画 -->
  <transition name="success-fade">
    <div v-if="showSuccess" class="success-overlay">
      <div class="success-content">
        <n-icon :component="CheckmarkCircleOutline" :size="72" class="success-icon" />
        <span class="success-text">发布成功</span>
      </div>
    </div>
  </transition>

  <div class="text-page">
    <!-- ============= 顶部栏(微信风) ============= -->
    <header class="page-header">
      <button type="button" class="header-btn cancel-btn" @click="closeWindow">
        取消
      </button>
      <h1 class="page-title">
        {{ mode === 'text' ? '发表文字' : '发表图片/视频' }}
      </h1>
      <button
        type="button"
        class="header-btn publish-btn"
        :disabled="!canPublish"
        @click="publish"
      >
        {{ publishing ? '发表中...' : '发表' }}
      </button>
    </header>

    <!-- ============= 主内容 ============= -->
    <main class="page-content">
      <!-- 文字输入 -->
      <div class="editor-section">
        <textarea
          ref="textArea"
          v-model="text"
          class="text-editor"
          :class="{ 'over-limit': isOverLimit }"
          :placeholder="mode === 'text' ? '这一刻的想法...' : '为图片添加描述...'"
          maxlength="2000"
          @input="onTextInput"
          @keydown="onTextKeyDown"
          @blur="onTextBlur"
        />

        <!-- @ 浮层(输入 @ 时弹出) -->
        <AtMentionPicker
          v-if="showMentionPicker"
          ref="mentionPickerRef"
          :friends="mentionFriends"
          :text="text"
          :caret-index="(textArea?.selectionStart ?? 0)"
          @apply="(p) => applyMention(p.id, p.name)"
          @close="showMentionPicker = false"
        />
      </div>

      <!-- 媒体上传(仅 media 模式显示) -->
      <div v-if="mode === 'media'" class="media-section">
        <div class="media-grid">
          <!-- 单个圆形加号槽(始终显示在第一位) -->
          <button
            type="button"
            class="add-slot"
            :disabled="images.length >= 9 || videos.length >= 1"
            title="点击添加图片或视频"
            @click="mediaInputRef?.click()"
          >
            <n-icon :component="AddOutline" :size="26" />
          </button>

          <!-- 已选视频 -->
          <div v-for="(vid, j) in videos" :key="'v' + j" class="media-cell video-cell">
            <video :src="vid.url" muted></video>
            <div class="cell-overlay">
              <n-icon :component="PlayCircleOutline" :size="36" />
            </div>
            <span class="media-tag">视频</span>
            <button type="button" class="cell-remove" @click="removeVideo(j)">
              <n-icon :component="TrashOutline" :size="10" />
            </button>
          </div>

          <!-- 已选图片(3 列网格,正方形) -->
          <div
            v-for="(img, i) in images"
            :key="'i' + i"
            class="media-cell"
          >
            <img :src="img" alt="" />
            <button type="button" class="cell-remove" @click="removeImage(i)">
              <n-icon :component="TrashOutline" :size="10" />
            </button>
          </div>
        </div>
        <input
          ref="mediaInputRef"
          type="file"
          accept="image/*,video/*"
          multiple
          hidden
          @change="onPickMedia"
        />
      </div>

      <!-- 选项列表(图二核心:微信风列表项) -->
      <ul class="options-list">
        <li class="option-row">
          <span class="option-icon">
            <n-icon :component="LocationOutline" :size="20" />
          </span>
          <span class="option-label">所在位置</span>
          <n-icon :component="ChevronForwardOutline" :size="16" class="option-arrow" />
        </li>
        <li class="option-row">
          <span class="option-icon">
            <n-icon :component="AtCircleOutline" :size="20" />
          </span>
          <span class="option-label">提醒谁看</span>
          <n-icon :component="ChevronForwardOutline" :size="16" class="option-arrow" />
        </li>
        <li class="option-row">
          <span class="option-icon">
            <n-icon :component="PersonCircleOutline" :size="20" />
          </span>
          <span class="option-label">谁可以看</span>
          <span class="option-value">{{ visibility }}</span>
          <n-icon :component="ChevronForwardOutline" :size="16" class="option-arrow" />
        </li>
      </ul>
    </main>
  </div>
</template>

<style scoped>
/* ========== 成功动画 ========== */
.success-fade-enter-active,
.success-fade-leave-active {
  transition: opacity 0.4s ease, transform 0.4s ease;
}
.success-fade-enter-from,
.success-fade-leave-to {
  opacity: 0;
  transform: scale(0.8);
}

.success-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.success-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.success-icon {
  color: var(--lx-accent, #07c160);
  animation: bounce 0.6s ease;
}

.success-text {
  font-size: 20px;
  font-weight: 600;
  color: var(--lx-text-body);
}

@keyframes bounce {
  0% { transform: scale(0); }
  50% { transform: scale(1.2); }
  70% { transform: scale(0.9); }
  100% { transform: scale(1); }
}

/* ========== 页面布局 ========== */
.text-page {
  width: 100vw;
  height: 100vh;
  background: var(--lx-bg-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ========== 顶部栏(微信风) ========== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--lx-border-light);
  -webkit-app-region: drag;
  flex-shrink: 0;
  background: var(--lx-bg-card);
  gap: 8px;
}

.header-btn {
  -webkit-app-region: no-drag;
  border: none;
  background: transparent;
  font-size: 15px;
  color: var(--lx-text-body);
  cursor: pointer;
  padding: 6px 4px;
  transition: opacity 0.15s ease;
  flex-shrink: 0;
}
.header-btn:active {
  opacity: 0.6;
}

.cancel-btn {
  font-size: 15px;
  color: var(--lx-text-body);
}

.publish-btn {
  background: var(--lx-accent, #07c160);
  color: #fff;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 14px;
  min-width: 56px;
  text-align: center;
  font-weight: 500;
}
.publish-btn:disabled {
  background: var(--lx-bg-input);
  color: var(--lx-text-muted);
  cursor: not-allowed;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-text-body);
  margin: 0;
  flex: 1;
  text-align: center;
  min-width: 0;
}

/* ========== 主内容 ========== */
.page-content {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* ========== 文字编辑器 ========== */
.editor-section {
  position: relative;
  padding: 16px 20px 0;
}

.text-editor {
  width: 100%;
  min-height: 100px;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--lx-text);
  font-size: 17px;
  line-height: 1.5;
  font-family: inherit;
  padding: 0;
}
.text-editor::placeholder {
  color: var(--lx-text-muted);
}
.text-editor.over-limit {
  color: var(--lx-danger, #e05454);
}

/* ========== 媒体上传(媒体模式) ========== */
.media-section {
  padding: 12px 20px 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.media-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-start;
}

.media-cell {
  position: relative;
  width: 68px;
  height: 68px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--lx-bg-input);
}
.media-cell img,
.media-cell video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.video-cell {
  background: #000;
}
.video-cell video {
  object-fit: contain;
}

.cell-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.32);
  color: #fff;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.5));
  pointer-events: none;
}

.media-tag {
  position: absolute;
  bottom: 6px;
  left: 6px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
}

.cell-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease;
}
.cell-remove:hover {
  background: rgba(224, 84, 84, 0.85);
}

.add-slot {
  position: relative;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  border: 1.5px dashed var(--lx-border);
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  font-size: 12px;
  align-self: flex-start;
}
.add-slot:hover:not(:disabled) {
  border-color: var(--lx-accent);
  color: var(--lx-accent);
  background: var(--lx-accent-soft);
}
.add-slot:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ========== 选项列表(微信风) ========== */
.options-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 20px;
  border-top: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  cursor: pointer;
  transition: background 0.15s ease;
}
.option-row:last-child {
  border-bottom: 1px solid var(--lx-border-light);
}
.option-row:active {
  background: var(--lx-bg-hover);
}

.option-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.option-label {
  flex: 1;
  font-size: 15px;
  color: var(--lx-text-body);
}

.option-value {
  font-size: 14px;
  color: var(--lx-text-muted);
  margin-right: 4px;
}

.option-arrow {
  color: var(--lx-text-muted);
  opacity: 0.6;
}
</style>
