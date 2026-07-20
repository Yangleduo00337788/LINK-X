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
import { NIcon, NModal, NButton, NRadio, NRadioGroup, useMessage } from 'naive-ui'
import {
  LocationOutline,
  AtCircleOutline,
  PersonCircleOutline,
  ChevronForwardOutline,
  PlayCircleOutline,
  CheckmarkCircleOutline,
  TrashOutline,
  AddOutline,
  CloseOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useAppStore } from '../stores/app'
import { useMomentsStore } from '../stores/moments'
import { useContactsStore } from '../stores/contacts'
import {
  readFileAsDataUrl,
  compressImage,
  MAX_IMAGE_BYTES,
  MAX_PUBLISH_IMAGE_BYTES,
  MAX_PUBLISH_VIDEO_BYTES
} from '../utils/file'
import AtMentionPicker from './common/AtMentionPicker.vue'
import LocationPickerPage from './LocationPickerPage.vue'
import { normalizeMediaUrl } from '../utils/mediaUrl'
import { useI18n } from '../i18n'

const route = useRoute()
const appStore = useAppStore()
const momentsStore = useMomentsStore()
const contactsStore = useContactsStore()
const { userProfile } = storeToRefs(appStore)

const message = useMessage()
const { t } = useI18n()

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
// images 改为持有原始 File,仅在选择时为缩略图缓存一次 dataURL 预览
interface PublishImage {
  file: File
  preview: string
}
const images = ref<PublishImage[]>([])
const videos = ref<{ url: string; file: File }[]>([])

// 上传进度(0~100),发布按钮显示 + 顶部进度条
const uploadProgress = ref(0)

// refs
const textArea = ref<HTMLTextAreaElement | null>(null)
const mediaInputRef = ref<HTMLInputElement | null>(null)

// @ 弹层 (输入 @ 触发)
const showMentionPicker = ref(false)
const mentionQuery = ref('')
const mentionStartIndex = ref(0)
const mentionPickerRef = ref<InstanceType<typeof AtMentionPicker> | null>(null)

// ========== 新增功能 ==========
// 所在位置
const location = ref('')
const showLocationPage = ref(false)

// 提醒谁看
const atUsers = ref<Array<{ id: number; name: string; avatar?: string }>>([])
const showAtUsersModal = ref(false)

// 谁可以看
const visibility = ref(0) // 0=公开，1=仅好友，2=私密
const showVisibilityModal = ref(false)
const visibilityOptions = computed(() => [
  { value: 0, label: t('moments.public'), desc: t('moments.publicDesc') },
  { value: 1, label: t('moments.friendsOnly'), desc: t('moments.friendsOnlyDesc') },
  { value: 2, label: t('moments.private'), desc: t('moments.privateDesc') }
])

const visibilityLabels = computed(() => [
  t('moments.public'),
  t('moments.friendsOnly'),
  t('moments.private')
])
// ========== 新增功能结束 ==========

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
      message.warning(t('moments.unsupportedType', { name: file.name }))
      continue
    }
    if (isVideo) {
      if (videos.value.length >= 1) {
        message.warning(t('moments.oneVideoOnly'))
        continue
      }
      if (file.size > MAX_PUBLISH_VIDEO_BYTES) {
        message.warning(t('moments.videoTooLarge', { name: file.name }))
        continue
      }
      try {
        videos.value.push({ url: URL.createObjectURL(file), file })
      } catch {
        message.error(t('moments.readFail', { name: file.name }))
      }
    } else {
      if (images.value.length >= 9) {
        message.warning(t('moments.maxImages'))
        break
      }
      if (file.size > MAX_PUBLISH_IMAGE_BYTES) {
        message.warning(t('moments.imageFileTooLarge', { name: file.name }))
        continue
      }
      try {
        // 生成预览缩略图(仅用于展示,上传时仍用原始 File)
        const preview = await readFileAsDataUrl(file)
        images.value.push({ file, preview })
      } catch {
        message.error(t('moments.readFail', { name: file.name }))
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

// ========== 新增功能方法 ==========
function openLocationPage() {
  showLocationPage.value = true
}

function onLocationSelect(loc: string) {
  location.value = loc
  showLocationPage.value = false
}

function onLocationBack() {
  showLocationPage.value = false
}

function openAtUsersModal() {
  // 确保好友列表已加载
  if (!contactsStore.items.length) {
    void contactsStore.fetchFriends()
  }
  showAtUsersModal.value = true
}

function toggleAtUser(friend: { id: number; name: string; avatar?: string }) {
  const idx = atUsers.value.findIndex(u => u.id === friend.id)
  if (idx >= 0) {
    atUsers.value.splice(idx, 1)
  } else {
    atUsers.value.push(friend)
  }
}

function isAtUserSelected(friendId: number) {
  return atUsers.value.some(u => u.id === friendId)
}

function confirmAtUsers() {
  showAtUsersModal.value = false
}

function removeAtUser(id: number) {
  atUsers.value = atUsers.value.filter(u => u.id !== id)
}

function openVisibilityModal() {
  showVisibilityModal.value = true
}

function confirmVisibility() {
  showVisibilityModal.value = false
}

function toggleAtUserFromContact(friend: { id: string; name: string; avatarUrl?: string }) {
  const userId = Number(friend.id)
  const idx = atUsers.value.findIndex(u => u.id === userId)
  if (idx >= 0) {
    atUsers.value.splice(idx, 1)
  } else {
    atUsers.value.push({ id: userId, name: friend.name, avatar: friend.avatarUrl })
  }
}
// ========== 新增功能方法结束 ==========

const publishing = ref(false)

async function publish() {
  const trimmed = text.value.trim()
  if (mode.value === 'text') {
    if (!trimmed) {
      message.warning(t('moments.publishNeedContent'))
      return
    }
  } else {
    if (!trimmed && !images.value.length && !videos.value.length) {
      message.warning(t('moments.publishNeedMedia'))
      return
    }
  }

  publishing.value = true
  uploadProgress.value = 0
  try {
    let uploaded: string[] = []
    if (images.value.length) {
      message.info(t('moments.uploadingImages'))
      const { uploadMomentsImage } = await import('../api/moments')
      for (let i = 0; i < images.value.length; i++) {
        const item = images.value[i]
        // > 2MB 时压缩;≤ 2MB 直接上传原始 File,避免画质损失
        const shouldCompress = item.file.size > MAX_IMAGE_BYTES && /image\/(jpeg|webp)/i.test(item.file.type)
        let fileToUpload: File | Blob = item.file
        let displayName = item.file.name
        if (shouldCompress) {
          const compressed = await compressImage(item.file, MAX_IMAGE_BYTES)
          fileToUpload = compressed
          displayName = item.file.name.replace(/\.[^.]+$/, '') + '.jpg'
        }
        const finalFile = fileToUpload instanceof File
          ? fileToUpload
          : new File([fileToUpload], displayName, { type: 'image/jpeg' })
        const res = await uploadMomentsImage(finalFile)
        if (res.code !== 200 || !res.data) {
          throw new Error(res.message || t('moments.imageUploadFail'))
        }
        uploaded.push(res.data)
        uploadProgress.value = Math.round(((i + 1) / images.value.length) * 100)
      }
    }
    uploadProgress.value = 100
    const finalContent = trimmed || (mode.value === 'media' ? t('moments.shareImage') : '')
    const atUserIds = atUsers.value.map(u => u.id)
    const ok = await momentsStore.addPost(finalContent, uploaded, {
      location: location.value || undefined,
      atUsers: atUserIds.length > 0 ? atUserIds : undefined,
      visibility: visibility.value
    })
    if (ok) {
      // 通知友链列表窗口立即刷新（发布页与列表页是独立 Electron 窗口）
      window.electronAPI?.notifyMomentsPublished?.()
      showSuccess.value = true
      message.success(t('moments.publishOk'))
      setTimeout(() => {
        closeWindow()
      }, 1200)
    } else {
      message.error(t('moments.publishFail'))
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : t('moments.publishFailShort'))
  } finally {
    publishing.value = false
    setTimeout(() => { uploadProgress.value = 0 }, 800)
  }
}
</script>

<template>
  <!-- 发布成功动画 -->
  <transition name="success-fade">
    <div v-if="showSuccess" class="success-overlay">
      <div class="success-content">
        <n-icon :component="CheckmarkCircleOutline" :size="72" class="success-icon" />
        <span class="success-text">{{ t('moments.publishOk') }}</span>
      </div>
    </div>
  </transition>

  <div class="text-page">
    <!-- ============= 顶部栏(微信风) ============= -->
    <header class="page-header">
      <div class="header-left-spacer" aria-hidden="true" />
      <h1 class="page-title">
        {{ mode === 'text' ? t('moments.publishTextTitle') : t('moments.publishMediaTitle') }}
      </h1>
      <button
        type="button"
        class="header-btn publish-btn"
        :disabled="!canPublish"
        @click="publish"
      >
        {{ publishing ? t('moments.publishing') : t('moments.publish') }}
      </button>
    </header>

    <!-- 上传进度条(发布中显示) -->
    <div v-if="publishing && uploadProgress > 0 && uploadProgress < 100" class="upload-progress">
      <div class="upload-progress-bar" :style="{ width: uploadProgress + '%' }"></div>
      <span class="upload-progress-text">{{ t('moments.uploadingProgress', { n: uploadProgress }) }}</span>
    </div>

    <!-- ============= 主内容 ============= -->
    <main class="page-content">
      <!-- 文字输入 -->
      <div class="editor-section">
        <textarea
          ref="textArea"
          v-model="text"
          class="text-editor"
          :class="{ 'over-limit': isOverLimit }"
          :placeholder="mode === 'text' ? t('moments.textPh') : t('moments.mediaPh')"
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
            :title="t('moments.addMedia')"
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
            <span class="media-tag">{{ t('moments.video') }}</span>
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
            <img :src="img.preview" alt="" />
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

      <!-- 选项列表(微信风) -->
      <ul class="options-list">
        <li class="option-row" @click="openLocationPage">
          <span class="option-icon">
            <n-icon :component="LocationOutline" :size="20" />
          </span>
          <span class="option-label">{{ t('moments.location') }}</span>
          <span v-if="location" class="option-value">{{ location }}</span>
          <n-icon :component="ChevronForwardOutline" :size="16" class="option-arrow" />
        </li>
        <li class="option-row" @click="openAtUsersModal">
          <span class="option-icon">
            <n-icon :component="AtCircleOutline" :size="20" />
          </span>
          <span class="option-label">{{ t('moments.remindWho') }}</span>
          <span v-if="atUsers.length" class="option-value at-users-count">{{ t('moments.peopleCount', { n: atUsers.length }) }}</span>
          <n-icon :component="ChevronForwardOutline" :size="16" class="option-arrow" />
        </li>
        <li class="option-row" @click="openVisibilityModal">
          <span class="option-icon">
            <n-icon :component="PersonCircleOutline" :size="20" />
          </span>
          <span class="option-label">{{ t('moments.whoCanSee') }}</span>
          <span class="option-value">{{ visibilityLabels[visibility] }}</span>
          <n-icon :component="ChevronForwardOutline" :size="16" class="option-arrow" />
        </li>
      </ul>
    </main>

    <!-- 底部固定区域 -->
    <footer class="page-footer">
      <button type="button" class="publish-btn-footer" :disabled="!canPublish" @click="publish">
        {{ publishing ? t('moments.publishing') : t('moments.publish') }}
      </button>
    </footer>
  </div>

  <!-- ========== 位置选择页面 ========== -->
  <Teleport to="body">
    <Transition name="page-slide">
      <LocationPickerPage
        v-if="showLocationPage"
        @select="onLocationSelect"
        @back="onLocationBack"
      />
    </Transition>
  </Teleport>

  <!-- ========== 提醒谁看弹窗 ========== -->
  <n-modal v-model:show="showAtUsersModal" preset="card" :title="t('moments.remindWho')" style="max-width: 360px;">
    <div class="at-users-modal">
      <!-- 已选择的好友标签 -->
      <div v-if="atUsers.length" class="selected-tags">
        <span v-for="user in atUsers" :key="user.id" class="selected-tag">
          {{ user.name }}
          <n-icon :component="CloseOutline" :size="14" class="tag-close" @click.stop="removeAtUser(user.id)" />
        </span>
      </div>
      <!-- 好友列表 -->
      <div class="friends-list">
        <div
          v-for="friend in contactsStore.friends"
          :key="friend.id"
          class="friend-item"
          :class="{ selected: isAtUserSelected(Number(friend.id)) }"
          @click="toggleAtUserFromContact(friend)"
        >
          <img
            v-if="friend.avatarUrl"
            :src="friend.avatarUrl"
            class="friend-avatar"
            @error="$event.target.style.display='none'"
          />
          <div v-else class="friend-avatar-placeholder" :style="{ background: friend.avatarColor }">
            {{ friend.avatarText }}
          </div>
          <span class="friend-name">{{ friend.name }}</span>
          <n-icon v-if="isAtUserSelected(Number(friend.id))" :component="CheckmarkCircleOutline" :size="20" class="check-icon" />
        </div>
        <div v-if="!contactsStore.friends.length" class="no-friends">{{ t('moments.noFriends') }}</div>
      </div>
      <div class="modal-actions">
        <n-button @click="showAtUsersModal = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" @click="confirmAtUsers">{{ t('common.confirm') }}</n-button>
      </div>
    </div>
  </n-modal>

  <!-- ========== 谁可以看弹窗 ========== -->
  <n-modal v-model:show="showVisibilityModal" preset="card" :title="t('moments.whoCanSee')" style="max-width: 320px;">
    <div class="visibility-modal">
      <n-radio-group v-model:value="visibility" class="visibility-options">
        <div v-for="opt in visibilityOptions" :key="opt.value" class="visibility-option">
          <n-radio :value="opt.value">{{ opt.label }}</n-radio>
          <span class="visibility-desc">{{ opt.desc }}</span>
        </div>
      </n-radio-group>
      <div class="modal-actions">
        <n-button @click="showVisibilityModal = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" @click="confirmVisibility">{{ t('common.confirm') }}</n-button>
      </div>
    </div>
  </n-modal>
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
  width: env(titlebar-area-width, 100%);
  margin-left: env(titlebar-area-x, 0px);
  min-height: env(titlebar-area-height, 48px);
  box-sizing: border-box;
}

.header-left-spacer {
  display: flex;
  gap: 4px;
  -webkit-app-region: no-drag;
  min-width: 60px;
  pointer-events: none;
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

.upload-progress {
  position: relative;
  height: 4px;
  background: var(--lx-bg-input);
  overflow: hidden;
}
.upload-progress-bar {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, var(--lx-accent), var(--lx-accent));
  transition: width 0.3s ease;
}
.upload-progress-text {
  position: absolute;
  top: 8px;
  right: 12px;
  font-size: 11px;
  color: var(--lx-text-muted);
  background: var(--lx-bg-card);
  padding: 2px 6px;
  border-radius: 3px;
}
.options-list {
  list-style: none;
  margin: 0;
  padding: 0;
  flex-shrink: 0;
  margin-top: auto;
  border-top: 1px solid var(--lx-border-light);
}

.option-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  cursor: pointer;
  transition: background 0.15s ease;
}
.option-row:last-child {
  border-bottom: none;
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
  font-size: 13px;
  color: var(--lx-text-body);
}

.option-value {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-right: 4px;
}

.option-arrow {
  color: var(--lx-text-muted);
  opacity: 0.6;
  font-size: 12px;
}

/* ========== 底部固定区域 ========== */
.page-footer {
  flex-shrink: 0;
  padding: 12px 16px;
  border-top: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
}

.publish-btn-footer {
  width: 100%;
  padding: 12px;
  border: none;
  background: var(--lx-accent, #07c160);
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.publish-btn-footer:hover:not(:disabled) {
  opacity: 0.9;
}
.publish-btn-footer:disabled {
  background: var(--lx-bg-input);
  color: var(--lx-text-muted);
  cursor: not-allowed;
}

/* ========== 弹窗样式 ========== */
.location-modal {
  padding: 8px 0;
}

.location-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--lx-border);
  border-radius: 8px;
  font-size: 15px;
  background: var(--lx-bg-input);
  color: var(--lx-text);
  outline: none;
  box-sizing: border-box;
}
.location-input:focus {
  border-color: var(--lx-accent);
}

.at-users-modal {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 400px;
  overflow: hidden;
}

.selected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px;
  background: var(--lx-bg-input);
  border-radius: 8px;
  min-height: 40px;
}

.selected-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  border-radius: 12px;
  font-size: 13px;
}

.tag-close {
  cursor: pointer;
  opacity: 0.7;
}
.tag-close:hover {
  opacity: 1;
}

.friends-list {
  flex: 1;
  overflow-y: auto;
  max-height: 280px;
}

.friend-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.friend-item:hover {
  background: var(--lx-bg-hover);
}
.friend-item.selected {
  background: var(--lx-accent-soft);
}

.friend-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.friend-avatar-placeholder {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.friend-name {
  flex: 1;
  font-size: 15px;
  color: var(--lx-text-body);
}

.check-icon {
  color: var(--lx-accent);
}

.no-friends {
  text-align: center;
  padding: 20px;
  color: var(--lx-text-muted);
}

.visibility-modal {
  padding: 8px 0;
}

.visibility-options {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.visibility-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.visibility-desc {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-left: 24px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--lx-border-light);
}

.at-users-count {
  color: var(--lx-accent);
}

/* ========== 页面滑动动画 ========== */
.page-slide-enter-active,
.page-slide-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}
.page-slide-enter-from {
  transform: translateX(100%);
  opacity: 0;
}
.page-slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

</style>
