<script setup lang="ts">
/**
 * 友链发布浮层(取代头像下方的发布区)
 * 支持两种模式:
 *   - text: 纯文字(可 @ 好友, 不允许图片/视频)
 *   - media: 图片或视频
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import {
  CloseOutline,
  ImageOutline,
  VideocamOutline,
  SendOutline,
  AtCircleOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useMomentsStore } from '../stores/moments'
import { useContactsStore } from '../stores/contacts'
import { readFileAsDataUrl, dataUrlToFile, MAX_IMAGE_BYTES } from '../utils/file'
import AtMentionPicker from './common/AtMentionPicker.vue'

type Mode = 'text' | 'media'

const props = defineProps<{
  visible: boolean
  initialMode?: Mode
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'published'): void
}>()

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
const images = ref<string[]>([]) // DataURL 预览
const videos = ref<string[]>([]) // DataURL 预览(仅展示本地 dataURL,用于预览)

// 选中的被 @ 好友 id->name 映射(后端冗余字段)
const mentions = ref<Record<number, string>>({})

// refs
const textArea = ref<HTMLTextAreaElement | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)
const videoInputRef = ref<HTMLInputElement | null>(null)

// @ 提示面板相关
const showMentionPicker = ref(false)
const mentionQuery = ref('')
const mentionStartIndex = ref(0)
const mentionPickerRef = ref<InstanceType<typeof AtMentionPicker> | null>(null)

const friends = computed(() =>
  contactsStore.friends.filter(f =>
    !mentionQuery.value || f.name.toLowerCase().includes(mentionQuery.value.toLowerCase())
  )
)

// 当前登录用户头像
const myAvatar = computed(() => userProfile.value.avatar || undefined)

/** 关闭 */
function close() {
  emit('update:visible', false)
  // 重置
  setTimeout(() => {
    text.value = ''
    images.value = []
    videos.value = []
    mentions.value = {}
    mentionQuery.value = ''
    showMentionPicker.value = false
  }, 200)
}

/** 解析 @ 触发表情 */
function detectMentionTrigger() {
  const ta = textArea.value
  if (!ta) return
  const value = text.value
  const cursor = ta.selectionStart
  if (cursor == null) {
    showMentionPicker.value = false
    return
  }
  // 从光标往前找最近的 @
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
    if (ch === ' ' || ch === '\n' || ch === '\t') {
      break
    }
    i--
  }
  showMentionPicker.value = false
}

/** text 区输入监听 */
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

/** 应用 @ 选择 */
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

/** 选择图片 */
async function onPickImages(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
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

function removeImage(idx: number) {
  images.value.splice(idx, 1)
}

async function onPickVideos(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    if (videos.value.length >= 1) {
      message.warning('目前仅支持添加 1 个视频')
      break
    }
    if (file.size > 50 * 1024 * 1024) {
      message.warning(`「${file.name}」超过 50MB,已跳过`)
      continue
    }
    try {
      videos.value.push(await readFileAsDataUrl(file))
    } catch {
      message.error(`「${file.name}」读取失败`)
    }
  }
}

function removeVideo(idx: number) {
  videos.value.splice(idx, 1)
}

/** 切换为文字模式时清空图片视频;切换为媒体模式时清空文案的 mentions 含义 */
function switchMode(m: Mode) {
  mode.value = m
}

/** 发布按钮 */
const publishing = ref(false)

async function publish() {
  if (mode.value === 'text') {
    const trimmed = text.value.trim()
    if (!trimmed) {
      message.warning('请输入要发布的内容')
      return
    }
    publishing.value = true
    try {
      // 将 @nickname 占位文本嵌入正文;后端会再次解析 mentions
      const ok = await momentsStore.addPost(trimmed)
      if (ok) {
        message.success('发布成功')
        emit('published')
        close()
      } else {
        message.error('发布失败,请重试')
      }
    } finally {
      publishing.value = false
    }
    return
  }

  // media 模式
  if (videos.value.length === 0 && images.value.length === 0) {
    message.warning('请添加至少一张图片或一个视频')
    return
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
    // 视频(暂未上传到 MinIO,前端仅展示本地 DataURL 作为占位;后端支持图片即可)
    const finalContent = (text.value.trim() || '分享图片') + (videos.value.length ? ` [视频 1 个]` : '')
    const ok = await momentsStore.addPost(finalContent, uploaded)
    if (ok) {
      message.success('发布成功')
      emit('published')
      close()
    } else {
      message.error('发布失败,请重试')
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发布失败')
  } finally {
    publishing.value = false
  }
}

/** 关闭弹窗时清理 mentions */
onBeforeUnmount(() => {
  // noop
})

// 弹窗打开时确保加载好友列表
watch(
  () => props.visible,
  v => {
    if (v && !contactsStore.items.length) {
      void contactsStore.fetchFriends()
    }
  },
  { immediate: true }
)

// 选择图片/视频后保持当前模式
watch(images, () => { if (images.value.length && mode.value === 'text') mode.value = 'media' })
watch(videos, () => { if (videos.value.length && mode.value === 'text') mode.value = 'media' })

/** 通过代码在光标处触发 @ 面板 */
function triggerMentionFromButton() {
  const ta = textArea.value
  if (!ta) return
  ta.focus()
  const cursor = ta.selectionStart ?? text.value.length
  const before = text.value.slice(0, cursor)
  const after = text.value.slice(cursor)
  const prefix = before.length && !before.endsWith(' ') && !before.endsWith('\n') ? ' ' : ''
  text.value = before + prefix + '@' + after
  nextTick(() => {
    const newPos = (before + prefix + '@').length
    ta.setSelectionRange(newPos, newPos)
    detectMentionTrigger()
  })
}
</script>

<template>
  <transition name="composer-fade">
    <div v-if="visible" class="moments-composer-mask" @click.self="close">
      <div class="moments-composer" role="dialog" aria-modal="true">
        <header class="composer-header">
          <div class="title">
            <n-icon :component="mode === 'text' ? AtCircleOutline : ImageOutline" :size="18" />
            <span>{{ mode === 'text' ? '发布文字' : '发布图片/视频' }}</span>
          </div>
          <button type="button" class="close-btn" title="关闭" @click="close">
            <n-icon :component="CloseOutline" :size="18" />
          </button>
        </header>

        <!-- 模式切换 -->
        <div class="mode-switch">
          <button
            type="button"
            class="mode-btn"
            :class="{ active: mode === 'text' }"
            @click="switchMode('text')"
          >
            <n-icon :component="AtCircleOutline" :size="16" />
            <span>文字</span>
          </button>
          <button
            type="button"
            class="mode-btn"
            :class="{ active: mode === 'media' }"
            @click="switchMode('media')"
          >
            <n-icon :component="ImageOutline" :size="16" />
            <span>图片/视频</span>
          </button>
        </div>

        <div class="composer-body">
          <!-- 用户头像 -->
          <div class="me">
            <img v-if="myAvatar" :src="myAvatar" alt="" class="me-avatar" />
            <div v-else class="me-avatar me-avatar-placeholder">
              {{ (userProfile.nickname || '我').charAt(0) }}
            </div>
            <div class="me-name">{{ userProfile.nickname || '我' }}</div>
          </div>

          <!-- 编辑器 -->
          <div class="editor-wrap">
            <textarea
              ref="textArea"
              v-model="text"
              class="editor-textarea"
              :placeholder="
                mode === 'text'
                  ? '分享新鲜事… 使用 @ 提及你的好友'
                  : '为图片/视频添加描述(可选)'
              "
              maxlength="2000"
              @input="onTextInput"
              @keydown="onTextKeyDown"
              @blur="showMentionPicker = false"
            />

            <!-- 媒体预览 -->
            <div v-if="mode === 'media'" class="media-previews">
              <div v-for="(img, i) in images" :key="'i' + i" class="thumb">
                <img :src="img" alt="" />
                <button type="button" class="thumb-remove" @click="removeImage(i)" title="移除">
                  <n-icon :component="CloseOutline" :size="12" />
                </button>
              </div>
              <div v-for="(vid, j) in videos" :key="'v' + j" class="thumb thumb-video">
                <video :src="vid" muted></video>
                <button type="button" class="thumb-remove" @click="removeVideo(j)" title="移除">
                  <n-icon :component="CloseOutline" :size="12" />
                </button>
              </div>
            </div>

            <!-- @ 面板 -->
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
        </div>

        <footer class="composer-footer">
          <!-- 文案模式下可触发 @ -->
          <div class="footer-actions">
            <template v-if="mode === 'text'">
              <button
                type="button"
                class="tool-btn"
                title="提及好友"
                @click="triggerMentionFromButton"
              >
                <n-icon :component="AtCircleOutline" :size="18" />
                <span>@好友</span>
              </button>
            </template>
            <template v-else>
              <button type="button" class="tool-btn" title="添加图片" @click="imageInputRef?.click()">
                <n-icon :component="ImageOutline" :size="18" />
                <span>图片</span>
              </button>
              <button type="button" class="tool-btn" title="添加视频" @click="videoInputRef?.click()">
                <n-icon :component="VideocamOutline" :size="18" />
                <span>视频</span>
              </button>
              <input ref="imageInputRef" type="file" accept="image/*" multiple hidden @change="onPickImages" />
              <input ref="videoInputRef" type="file" accept="video/*" hidden @change="onPickVideos" />
            </template>
          </div>
          <button
            type="button"
            class="submit-btn"
            :disabled="publishing"
            @click="publish"
          >
            <n-icon :component="SendOutline" :size="16" />
            <span>{{ publishing ? '发布中…' : '发布' }}</span>
          </button>
        </footer>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.composer-fade-enter-active,
.composer-fade-leave-active {
  transition: opacity 0.18s ease;
}
.composer-fade-enter-from,
.composer-fade-leave-to {
  opacity: 0;
}

.moments-composer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 400;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-app-region: no-drag;
}

.moments-composer {
  width: 520px;
  max-width: 92vw;
  max-height: 86vh;
  background: var(--lx-bg-card);
  border-radius: 12px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.composer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--lx-border-light);
}

.title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.close-btn {
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  width: 30px;
  height: 30px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.close-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text);
}

.mode-switch {
  display: flex;
  gap: 4px;
  padding: 8px 16px 0;
}
.mode-btn {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  font-size: 13px;
  padding: 6px 8px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.mode-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}
.mode-btn.active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}

.composer-body {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  flex: 1;
  min-height: 0;
}

.me {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  width: 64px;
}

.me-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--lx-bg-panel);
}
.me-avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lx-accent);
  color: #fff;
  font-weight: 600;
  font-size: 18px;
}
.me-name {
  font-size: 11px;
  color: var(--lx-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 64px;
}

.editor-wrap {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.editor-textarea {
  width: 100%;
  min-height: 120px;
  max-height: 280px;
  border: 1px solid var(--lx-border-light);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  background: var(--lx-bg-input);
  color: var(--lx-text);
}
.editor-textarea:focus {
  outline: none;
  border-color: var(--lx-accent);
  background: var(--lx-bg-card);
}

.media-previews {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.thumb {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--lx-bg-input);
}
.thumb img,
.thumb video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb-video video {
  background: #000;
}
.thumb-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: var(--lx-danger);
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 14px;
  border-top: 1px solid var(--lx-border-light);
}

.footer-actions {
  display: flex;
  gap: 4px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 13px;
  color: var(--lx-text-muted);
  cursor: pointer;
}
.tool-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.submit-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 18px;
  border: none;
  background: var(--lx-accent);
  color: var(--lx-text-on-accent, #fff);
  border-radius: 18px;
  font-size: 13px;
  cursor: pointer;
}
.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.submit-btn:hover:not(:disabled) {
  filter: brightness(1.06);
}
</style>
