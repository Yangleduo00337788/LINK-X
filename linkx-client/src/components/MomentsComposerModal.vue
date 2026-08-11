<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 友链发布浮层
 * 支持两种模式:
 *   - text: 纯文字发布(简洁居中布局)
 *   - media: 图片或视频发布(网格布局)
 */
import { computed, nextTick, ref, watch } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import {
  CloseOutline,
  ImageOutline,
  VideocamOutline,
  SendOutline,
  AtCircleOutline,
  PlayCircleOutline,
  CheckmarkCircleOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useMomentsStore } from '../stores/moments'
import { useContactsStore } from '../stores/contacts'
import { readFileAsDataUrl, dataUrlToFile, MAX_IMAGE_BYTES } from '../utils/file'
import AtMentionPicker from './common/AtMentionPicker.vue'
import { useI18n } from '../i18n'
import { LxButton, LxIconButton } from './ui'

type Mode = 'text' | 'media'

const props = defineProps<{
  visible: boolean
  initialMode?: Mode
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'published'): void
}>()

const { t } = useI18n()
const message = useMessage()
const momentsStore = useMomentsStore()
const contactsStore = useContactsStore()
const appStore = useAppStore()
const { userProfile } = storeToRefs(appStore)

// 当前模式
const mode = ref<Mode>(props.initialMode ?? 'text')

watch(
  () => props.initialMode,
  m => { if (m) mode.value = m }
)

// 文案与媒体列表
const text = ref('')
const images = ref<string[]>([])
const videos = ref<{ url: string; file?: File }[]>([])

// @ 提及
const mentions = ref<Record<string, string>>({})

// refs
const textArea = ref<HTMLTextAreaElement | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)
const videoInputRef = ref<HTMLInputElement | null>(null)

// @ 面板
const showMentionPicker = ref(false)
const mentionQuery = ref('')
const mentionStartIndex = ref(0)
const mentionPickerRef = ref<InstanceType<typeof AtMentionPicker> | null>(null)

// 拖拽排序
const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

// 发布成功
const showSuccess = ref(false)

// 字数统计
const charCount = computed(() => text.value.length)
const remainingChars = computed(() => 2000 - charCount.value)
const isOverLimit = computed(() => charCount.value > 2000)

const friends = computed(() =>
  contactsStore.friends.filter(f =>
    !mentionQuery.value || f.name.toLowerCase().includes(mentionQuery.value.toLowerCase())
  )
)

const myAvatar = computed(() => userProfile.value.avatar || undefined)

/** 关闭 */
function close() {
  emit('update:visible', false)
  setTimeout(() => {
    text.value = ''
    images.value = []
    videos.value = []
    mentions.value = {}
    mentionQuery.value = ''
    showMentionPicker.value = false
    showSuccess.value = false
  }, 200)
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

function applyMention(id: string | number, name: string) {
  const ta = textArea.value
  if (!ta) return
  const before = text.value.slice(0, mentionStartIndex.value)
  const cursor = ta.selectionStart ?? mentionStartIndex.value
  const after = text.value.slice(cursor)
  const inserted = `@${name} `
  text.value = before + inserted + after
  mentions.value[String(id)] = name
  showMentionPicker.value = false
  nextTick(() => {
    const newPos = before.length + inserted.length
    ta.focus()
    ta.setSelectionRange(newPos, newPos)
  })
}

/** 选择图片 */
async function onPickImages(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    if (images.value.length >= 9) {
      message.warning(t('moments.maxImages'))
      break
    }
    if (file.size > MAX_IMAGE_BYTES) {
      message.warning(t('extra.fileTooLargeSkipped', { name: file.name, size: '2MB' }))
      continue
    }
    try {
      images.value.push(await readFileAsDataUrl(file))
    } catch {
      message.error(t('moments.readFail', { name: file.name }))
    }
  }
}

function removeImage(idx: number) {
  images.value.splice(idx, 1)
}

// 拖拽排序
function onImageDragStart(idx: number) {
  dragIndex.value = idx
}
function onImageDragOver(e: DragEvent, idx: number) {
  e.preventDefault()
  dragOverIndex.value = idx
}
function onImageDrop(idx: number) {
  if (dragIndex.value === null || dragIndex.value === idx) return
  const items = [...images.value]
  const [removed] = items.splice(dragIndex.value, 1)
  items.splice(idx, 0, removed)
  images.value = items
  dragIndex.value = null
  dragOverIndex.value = null
}
function onDragEnd() {
  dragIndex.value = null
  dragOverIndex.value = null
}

async function onPickVideos(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    if (videos.value.length >= 1) {
      message.warning(t('moments.oneVideoOnly'))
      break
    }
    if (file.size > 50 * 1024 * 1024) {
      message.warning(t('moments.videoTooLarge', { name: file.name }))
      continue
    }
    try {
      videos.value.push({ url: await readFileAsDataUrl(file), file })
    } catch {
      message.error(t('moments.readFail', { name: file.name }))
    }
  }
}

function removeVideo(idx: number) {
  videos.value.splice(idx, 1)
}

function switchMode(m: Mode) {
  mode.value = m
}

const publishing = ref(false)

async function publish() {
  if (mode.value === 'text') {
    const trimmed = text.value.trim()
    if (!trimmed) {
      message.warning(t('moments.publishNeedContent'))
      return
    }
    publishing.value = true
    try {
      const result = await momentsStore.addPost(trimmed)
      if (result.ok) {
        showSuccessAnimation()
      } else {
        message.error(result.message || t('moments.publishFail'))
      }
    } finally {
      publishing.value = false
    }
    return
  }

  // media 模式
  if (videos.value.length === 0 && images.value.length === 0) {
    message.warning(t('extra.publishNeedPhotoOrVideo'))
    return
  }

  publishing.value = true
  try {
    let uploaded: string[] = []
    if (images.value.length) {
      message.info(t('moments.uploadingImages'))
      for (const dataUrl of images.value) {
        const ext = dataUrl.match(/data:image\/(\w+)/)?.[1] || 'jpeg'
        const safeExt = ext === 'jpg' ? 'jpeg' : ext
        const fileName = `moments_${Date.now()}.${safeExt === 'jpeg' ? 'jpg' : safeExt}`
        const file = dataUrlToFile(dataUrl, fileName)
        const normalized = new File([file], fileName, { type: `image/${safeExt}` })
        const { uploadMomentsImage } = await import('../api/moments')
        const res = await uploadMomentsImage(normalized)
        if (res.code !== 200 || !res.data) {
          throw new Error(res.message || t('moments.imageUploadFail'))
        }
        uploaded.push(res.data)
      }
    }
    const finalContent = (text.value.trim() || t('moments.shareImage')) + (videos.value.length ? t('extra.videoCountTag') : '')
    const result = await momentsStore.addPost(finalContent, uploaded)
    if (result.ok) {
      showSuccessAnimation()
    } else {
      message.error(result.message || t('moments.publishFail'))
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : t('moments.publishFailShort'))
  } finally {
    publishing.value = false
  }
}

function showSuccessAnimation() {
  showSuccess.value = true
  message.success(t('moments.publishOk'))
  emit('published')
  setTimeout(() => {
    close()
  }, 1200)
}

// 加载好友列表
watch(
  () => props.visible,
  v => {
    if (v && !contactsStore.items.length) {
      void contactsStore.fetchFriends()
    }
  },
  { immediate: true }
)

// 自动切换模式
watch(images, () => { if (images.value.length && mode.value === 'text') mode.value = 'media' })
watch(videos, () => { if (videos.value.length && mode.value === 'text') mode.value = 'media' })

// 图片网格布局
const imageGridClass = computed(() => {
  const count = images.value.length
  if (count === 1) return 'grid-1'
  if (count === 2) return 'grid-2'
  if (count === 4) return 'grid-4'
  return 'grid-more'
})

// 媒体区域显示状态
const showMediaEmpty = computed(() => mode.value === 'media' && images.value.length === 0 && videos.value.length === 0)
</script>

<template>
  <!-- 发布成功动画 -->
  <transition name="success-fade">
    <div v-if="showSuccess" class="success-overlay">
      <div class="success-content">
        <n-icon :component="CheckmarkCircleOutline" :size="64" class="success-icon" />
        <span class="success-text">{{ t('moments.publishOk') }}</span>
      </div>
    </div>
  </transition>

  <transition name="composer-fade">
    <div v-if="visible" class="moments-composer-mask" @click.self="close">
      
      <!-- ========== 文字发布模式 ========== -->
      <div v-if="mode === 'text'" class="composer-text-mode" role="dialog">
        <header class="composer-header">
          <div class="header-title">
            <n-icon :component="AtCircleOutline" :size="18" />
            <span>{{ t('moments.publishText') }}</span>
          </div>
          <LxIconButton variant="close" :title="t('common.close')" @click="close">
            <n-icon :component="CloseOutline" :size="18" />
          </LxIconButton>
        </header>

        <div class="text-body">
          <div class="user-card">
            <div class="avatar-wrap">
              <img v-if="myAvatar" :src="myAvatar" alt="" class="user-avatar" />
              <div v-else class="user-avatar user-avatar-placeholder">
                {{ (userProfile.nickname || t('common.me')).charAt(0) }}
              </div>
            </div>
            <div class="user-info">
              <span class="user-name">{{ userProfile.nickname || t('common.me') }}</span>
            </div>
          </div>

          <div class="text-editor-wrap">
            <textarea
              ref="textArea"
              v-model="text"
              class="text-editor"
              :class="{ 'over-limit': isOverLimit }"
              :placeholder="t('extra.shareFreshPh')"
              maxlength="2000"
              @input="onTextInput"
              @keydown="onTextKeyDown"
              @blur="showMentionPicker = false"
            />
            <div class="char-counter" :class="{ warning: remainingChars < 100, danger: isOverLimit }">
              <span>{{ charCount }}</span>
              <span class="sep">/</span>
              <span>2000</span>
            </div>
          </div>

          <AtMentionPicker
            v-if="showMentionPicker"
            ref="mentionPickerRef"
            :friends="friends"
            :text="text"
            :caret-index="(textArea?.selectionStart ?? 0)"
            @apply="(p) => applyMention(p.id, p.name)"
            @close="showMentionPicker = false"
          />
        </div>

        <footer class="composer-footer">
          <span class="footer-tip">{{ t('extra.atHint') }}</span>
          <LxButton
            variant="moments-submit"
            :disabled="publishing || isOverLimit || !text.trim()"
            @click="publish"
          >
            <n-icon v-if="!publishing" :component="SendOutline" :size="16" />
            <span>{{ publishing ? t('moments.publishing') : t('moments.publish') }}</span>
          </LxButton>
        </footer>
      </div>

      <!-- ========== 图片/视频发布模式 ========== -->
      <div v-else class="composer-media-mode" role="dialog">
        <header class="composer-header">
          <div class="header-title">
            <n-icon :component="ImageOutline" :size="18" />
            <span>{{ t('moments.publishMedia') }}</span>
          </div>
          <div class="header-actions">
            <LxButton variant="mode-switch" @click="switchMode('text')">
              {{ t('extra.switchToText') }}
            </LxButton>
            <LxIconButton variant="close" :title="t('common.close')" @click="close">
              <n-icon :component="CloseOutline" :size="18" />
            </LxIconButton>
          </div>
        </header>

        <div class="media-body">
          <!-- 用户信息 -->
          <div class="user-card">
            <div class="avatar-wrap">
              <img v-if="myAvatar" :src="myAvatar" alt="" class="user-avatar" />
              <div v-else class="user-avatar user-avatar-placeholder">
                {{ (userProfile.nickname || t('common.me')).charAt(0) }}
              </div>
            </div>
            <span class="user-name">{{ userProfile.nickname || t('common.me') }}</span>
          </div>

          <!-- 文字描述 -->
          <div class="text-editor-wrap">
            <textarea
              ref="textArea"
              v-model="text"
              class="text-editor small"
              :placeholder="t('moments.mediaPh')"
              maxlength="2000"
            />
          </div>

          <!-- 媒体预览区 -->
          <div class="media-preview-area">
            <!-- 视频预览 -->
            <div v-for="(vid, j) in videos" :key="'v' + j" class="video-preview">
              <video :src="vid.url" muted></video>
              <div class="video-overlay">
                <n-icon :component="PlayCircleOutline" :size="40" class="play-icon" />
              </div>
              <div class="video-badge">
                <n-icon :component="VideocamOutline" :size="12" />
                <span>{{ t('moments.video') }}</span>
              </div>
              <button type="button" class="lx-remove-badge--media" @click="removeVideo(j)">
                <n-icon :component="CloseOutline" :size="12" />
              </button>
            </div>

            <!-- 图片网格 -->
            <div v-if="images.length" class="image-grid" :class="imageGridClass">
              <div
                v-for="(img, i) in images"
                :key="'i' + i"
                class="image-item"
                :class="{ dragging: dragIndex === i, 'drag-over': dragOverIndex === i }"
                draggable="true"
                @dragstart="onImageDragStart(i)"
                @dragover="(e) => onImageDragOver(e, i)"
                @drop="onImageDrop(i)"
                @dragend="onDragEnd"
              >
                <img :src="img" alt="" />
                <button type="button" class="lx-remove-badge--media" @click.stop="removeImage(i)">
                  <n-icon :component="CloseOutline" :size="10" />
                </button>
                <div class="drag-hint">
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor">
                    <circle cx="5" cy="5" r="2"/><circle cx="12" cy="5" r="2"/><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="5" cy="19" r="2"/><circle cx="12" cy="19" r="2"/>
                  </svg>
                </div>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-if="showMediaEmpty" class="media-empty">
              <div class="empty-icon">
                <n-icon :component="ImageOutline" :size="36" />
              </div>
              <p>{{ t('extra.clickAddMedia') }}</p>
            </div>
          </div>
        </div>

        <footer class="composer-footer">
          <div class="tool-buttons">
            <LxButton variant="composer-tool" @click="imageInputRef?.click()">
              <n-icon :component="ImageOutline" :size="18" />
              <span>{{ t('chat.image') }}</span>
            </LxButton>
            <LxButton variant="composer-tool" @click="videoInputRef?.click()">
              <n-icon :component="VideocamOutline" :size="18" />
              <span>{{ t('moments.video') }}</span>
            </LxButton>
            <input ref="imageInputRef" type="file" accept="image/*" multiple hidden @change="onPickImages" />
            <input ref="videoInputRef" type="file" accept="video/*" hidden @change="onPickVideos" />
          </div>
          <LxButton
            variant="moments-submit"
            :disabled="publishing || !images.length && !videos.length"
            @click="publish"
          >
            <n-icon v-if="!publishing" :component="SendOutline" :size="16" />
            <span>{{ publishing ? t('moments.publishing') : t('moments.publish') }}</span>
          </LxButton>
        </footer>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/* ========== 基础动画 ========== */
.composer-fade-enter-active,
.composer-fade-leave-active {
  transition: opacity var(--lx-duration-md) ease, transform var(--lx-duration-md) ease;
}
.composer-fade-enter-from,
.composer-fade-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

.success-fade-enter-active,
.success-fade-leave-active {
  transition: opacity var(--lx-duration-slower) ease, transform var(--lx-duration-slower) ease;
}
.success-fade-enter-from,
.success-fade-leave-to {
  opacity: 0;
  transform: scale(0.8);
}

/* ========== 成功遮罩 ========== */
.success-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--lx-z-modal);
  border-radius: var(--lx-radius-3xl);
}

.success-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lx-space-lg);
}

.success-icon {
  color: var(--lx-accent, var(--lx-success));
  animation: bounce var(--lx-duration-slowest) ease;
}

.success-text {
  font-size: var(--lx-font-3xl);
  font-weight: 600;
  color: var(--lx-text-body);
}

@keyframes bounce {
  0% { transform: scale(0); }
  50% { transform: scale(1.2); }
  70% { transform: scale(0.9); }
  100% { transform: scale(1); }
}

/* ========== 遮罩层 ========== */
.moments-composer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  z-index: var(--lx-z-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-app-region: no-drag;
}

/* ========== 通用头部 ========== */
.composer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lx-space-xl) var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border-light);
}

.header-title {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  font-size: var(--lx-font-lg);
  font-weight: 600;
  color: var(--lx-text-body);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
}

/* ========== 通用底部 ========== */
.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lx-space-lg) var(--lx-space-2xl);
  border-top: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
}

/* ========== 用户信息 ========== */
.user-card {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  margin-bottom: var(--lx-space-lg);
}

.avatar-wrap {
  flex-shrink: 0;
}

.user-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--lx-bg-panel);
  border: 2px solid var(--lx-bg-card);
}

.user-avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--lx-accent) 0%, var(--lx-success) 100%);
  color: var(--lx-text-on-accent);
  font-weight: 600;
  font-size: var(--lx-font-xl);
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: var(--lx-font);
  font-weight: 500;
  color: var(--lx-text-body);
}

/* ========== 文字编辑器 ========== */
.text-editor-wrap {
  position: relative;
}

.text-editor {
  width: 100%;
  min-height: 120px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-lg);
  padding: var(--lx-space-lg) var(--lx-space-xl);
  padding-bottom: var(--lx-space-5xl-minus);
  font-size: var(--lx-font);
  font-family: inherit;
  line-height: var(--lx-leading-relaxed);
  resize: none;
  background: var(--lx-bg-input);
  color: var(--lx-text);
  transition: all var(--lx-duration-md);
  box-sizing: border-box;
}
.text-editor.small {
  min-height: 60px;
  margin-bottom: var(--lx-space-lg);
}
.text-editor:focus {
  outline: none;
  border-color: var(--lx-accent);
  background: var(--lx-bg-card);
  box-shadow: var(--lx-shadow-ring-accent);
}
.text-editor.over-limit {
  border-color: var(--lx-danger, var(--lx-danger));
  background: rgba(224, 84, 84, 0.05);
}

.char-counter {
  position: absolute;
  bottom: 8px;
  right: 12px;
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
  display: flex;
  gap: var(--lx-space-2xs);
  pointer-events: none;
}
.char-counter.warning { color: var(--lx-char-warning); }
.char-counter.danger { color: var(--lx-danger, var(--lx-danger)); font-weight: 600; }
.sep { opacity: 0.5; }

/* ========== 文字发布模式 ========== */
.composer-text-mode {
  width: 480px;
  max-width: 94vw;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-3xl);
  box-shadow: var(--lx-shadow-heavy);
  overflow: hidden;
}

.text-body {
  padding: var(--lx-space-2xl);
}

/* ========== 图片/视频发布模式 ========== */
.composer-media-mode {
  width: 540px;
  max-width: 94vw;
  max-height: 88vh;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-3xl);
  box-shadow: var(--lx-shadow-heavy);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.media-body {
  flex: 1;
  padding: var(--lx-space-2xl);
  overflow-y: auto;
}

.media-preview-area {
  margin-top: var(--lx-space-lg);
}

/* 视频预览 */
.video-preview {
  position: relative;
  width: 100%;
  max-height: 300px;
  border-radius: var(--lx-radius-lg);
  overflow: hidden;
  background: var(--lx-black);
  margin-bottom: var(--lx-space-lg);
}
.video-preview video {
  width: 100%;
  max-height: 300px;
  object-fit: contain;
  display: block;
}
.video-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.25);
  opacity: 0;
  transition: opacity var(--lx-duration-md);
}
.video-preview:hover .video-overlay {
  opacity: 1;
}
.play-icon {
  color: var(--lx-text-on-accent);
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.4));
}
.video-badge {
  position: absolute;
  bottom: 8px;
  left: 8px;
  background: rgba(0, 0, 0, 0.6);
  color: var(--lx-text-on-accent);
  font-size: var(--lx-font-xs);
  padding: var(--lx-space-2xs) var(--lx-space);
  border-radius: var(--lx-radius-2xs);
  display: flex;
  align-items: center;
  gap: var(--lx-space-xs);
}

/* 删除按钮 hover 显示 */
.video-preview:hover .lx-remove-badge--media,
.image-item:hover .lx-remove-badge--media {
  opacity: 1;
  transform: scale(1);
}

/* 图片网格 */
.image-grid {
  display: grid;
  gap: var(--lx-space-sm);
}
.image-grid.grid-1 { grid-template-columns: 1fr; }
.image-grid.grid-2 { grid-template-columns: repeat(2, 1fr); }
.image-grid.grid-4 { grid-template-columns: repeat(2, 1fr); }
.image-grid.grid-more { grid-template-columns: repeat(3, 1fr); }

.grid-1 .image-item {
  max-height: 320px;
}
.grid-1 .image-item img {
  width: 100%;
  max-height: 320px;
  object-fit: contain;
}

.image-item {
  position: relative;
  border-radius: var(--lx-radius-xl);
  overflow: hidden;
  background: var(--lx-bg-input);
  cursor: grab;
  aspect-ratio: 1;
}
.image-item:active {
  cursor: grabbing;
}
.image-item.dragging {
  opacity: 0.5;
  transform: scale(0.95);
}
.image-item.drag-over {
  box-shadow: 0 0 0 2px var(--lx-accent);
  transform: scale(1.02);
}
.image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--lx-duration-slow) ease;
}
.image-item:hover img {
  transform: scale(1.05);
}

.drag-hint {
  position: absolute;
  bottom: 4px;
  right: 4px;
  width: 18px;
  height: 18px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: var(--lx-radius-2xs);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-on-accent);
  opacity: 0;
  transition: opacity var(--lx-duration-md);
}
.image-item:hover .drag-hint {
  opacity: 0.6;
}

/* 空状态 */
.media-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-5xl);
  background: var(--lx-bg-input);
  border-radius: var(--lx-radius-lg);
  border: 2px dashed var(--lx-border-light);
}
.empty-icon {
  color: var(--lx-text-muted);
  opacity: 0.5;
  margin-bottom: var(--lx-space);
}
.media-empty p {
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
  margin: 0;
}

.tool-buttons {
  display: flex;
  gap: var(--lx-space-sm);
}

.footer-tip {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}
.footer-tip b {
  color: var(--lx-accent);
  font-weight: 600;
  padding: var(--lx-space-hair) var(--lx-space-sm);
  border: 1px solid var(--lx-accent-soft);
  border-radius: var(--lx-radius-2xs);
  background: var(--lx-accent-soft);
}
</style>
