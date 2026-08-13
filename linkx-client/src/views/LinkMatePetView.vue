<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 灵伴桌面宠物（Codex 风格）：透明浮动角色 + 点击弹出对话气泡。
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { NInput, NIcon, useMessage } from 'naive-ui'
import { CloseOutline, ExpandOutline, ChatbubbleOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useLinkMateStore } from '../stores/linkmate'
import { useAppStore } from '../stores/app'
import { hasRefreshToken } from '../utils/tokenStorage'
import { applyDocumentTheme } from '../utils/themeSync'
import { useI18n } from '../i18n'

const { t } = useI18n()
const message = useMessage()
const linkMate = useLinkMateStore()
const appStore = useAppStore()
const {
  activeMessages,
  loadingMessages,
  streaming,
  inputDraft,
  enabled,
  status
} = storeToRefs(linkMate)

const chatOpen = ref(false)
const hoverHint = ref(false)
const inputRef = ref<InstanceType<typeof NInput> | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const authReady = ref(false)
const authFailed = ref(false)
const characterSrc = ref('/linkmate-pet.png')
const petMood = ref<'idle' | 'happy' | 'think'>('idle')

const canChat = computed(() => authReady.value && enabled.value && !authFailed.value)

/** 去除立绘白底，避免桌面出现方块 */
async function processPetImage(src: string) {
  try {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.src = src
    await img.decode()
    const canvas = document.createElement('canvas')
    canvas.width = img.width
    canvas.height = img.height
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.drawImage(img, 0, 0)
    const data = ctx.getImageData(0, 0, canvas.width, canvas.height)
    for (let i = 0; i < data.data.length; i += 4) {
      const r = data.data[i]
      const g = data.data[i + 1]
      const b = data.data[i + 2]
      if (r > 235 && g > 235 && b > 235) {
        data.data[i + 3] = 0
      }
    }
    ctx.putImageData(data, 0, 0)
    characterSrc.value = canvas.toDataURL('image/png')
  } catch {
    characterSrc.value = src
  }
}

async function ensureAuth() {
  authFailed.value = false
  if (!(await hasRefreshToken())) {
    authFailed.value = true
    return
  }
  authReady.value = true
  await linkMate.loadStatus()
  if (!enabled.value) return
  await linkMate.loadSessions()
  if (!linkMate.activeSessionId && linkMate.sessions.length > 0) {
    await linkMate.selectSession(linkMate.sessions[0].id)
  }
}

function scrollToBottom() {
  const el = messageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function syncPetExpanded() {
  await window.electronAPI?.setLinkMatePetExpanded?.(chatOpen.value)
}

function pulseMood(next: 'idle' | 'happy' | 'think', ms = 600) {
  petMood.value = next
  window.setTimeout(() => {
    if (!chatOpen.value && !streaming.value) petMood.value = 'idle'
  }, ms)
}

async function openChat() {
  if (chatOpen.value) return
  chatOpen.value = true
  hoverHint.value = false
  petMood.value = 'happy'
  await syncPetExpanded()
  await nextTick()
  scrollToBottom()
  inputRef.value?.focus()
}

async function closeChat() {
  if (!chatOpen.value) return
  chatOpen.value = false
  petMood.value = 'idle'
  await syncPetExpanded()
}

async function toggleChat() {
  pulseMood('happy')
  if (chatOpen.value) {
    await closeChat()
    return
  }
  if (!canChat.value) {
    message.warning(
      authFailed.value ? t('linkmatePet.loginRequired') : t('linkmate.disabledHint')
    )
    return
  }
  await openChat()
}

async function handleSend() {
  const text = inputDraft.value.trim()
  if (!text || streaming.value) return
  inputDraft.value = ''
  petMood.value = 'think'
  try {
    await linkMate.sendMessage(text)
    petMood.value = 'happy'
  } catch (err) {
    petMood.value = 'idle'
    message.error(err instanceof Error ? err.message : t('linkmate.sendFailed'))
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void handleSend()
  }
}

function openMainLinkMate() {
  window.electronAPI?.openLinkMate?.()
}

onMounted(async () => {
  document.documentElement.classList.add('lx-pet-window')
  applyDocumentTheme(appStore.theme)
  await processPetImage('/linkmate-pet.png')
  await ensureAuth()
})

onUnmounted(() => {
  document.documentElement.classList.remove('lx-pet-window')
})

watch(
  () => activeMessages.value.length,
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

watch(
  () => activeMessages.value.at(-1)?.content,
  async () => {
    if (streaming.value) {
      petMood.value = 'think'
      await nextTick()
      scrollToBottom()
    }
  }
)

watch(streaming, val => {
  if (!val && chatOpen.value) petMood.value = 'happy'
})
</script>

<template>
  <div class="pet-stage">
    <Transition name="pet-bubble">
      <div v-if="chatOpen" class="pet-chat-bubble">
        <header class="pet-chat-hdr">
          <div class="pet-chat-title">
            <span>{{ t('linkmate.name') }}</span>
            <small v-if="status">{{ status.model }}</small>
          </div>
          <div class="pet-chat-actions">
            <button
              type="button"
              class="pet-icon-btn"
              :title="t('linkmatePet.openMain')"
              @click="openMainLinkMate"
            >
              <NIcon :component="ExpandOutline" />
            </button>
            <button type="button" class="pet-icon-btn" :title="t('common.close')" @click="closeChat">
              <NIcon :component="CloseOutline" />
            </button>
          </div>
        </header>

        <div ref="messageListRef" class="pet-chat-body">
          <div v-if="loadingMessages" class="pet-chat-empty">{{ t('common.loading') }}</div>
          <div v-else-if="activeMessages.length === 0" class="pet-chat-empty">
            <NIcon :component="ChatbubbleOutline" class="pet-chat-empty-ico" />
            <p>{{ t('linkmatePet.chatHint') }}</p>
          </div>
          <template v-else>
            <div
              v-for="msg in activeMessages"
              :key="msg.id"
              class="pet-msg"
              :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
            >
              <div class="pet-msg-content">
                {{ msg.content }}
                <span
                  v-if="streaming && msg.id.startsWith('temp-assistant')"
                  class="pet-cursor"
                >▍</span>
              </div>
            </div>
          </template>
        </div>

        <footer class="pet-chat-footer">
          <NInput
            ref="inputRef"
            v-model:value="inputDraft"
            type="textarea"
            size="small"
            :autosize="{ minRows: 1, maxRows: 3 }"
            :placeholder="t('linkmatePet.inputShort')"
            :disabled="streaming || !canChat"
            @keydown="handleKeydown"
          />
          <button
            type="button"
            class="pet-send-btn"
            :disabled="!inputDraft.trim() || streaming || !canChat"
            @click="handleSend"
          >
            {{ t('linkmate.send') }}
          </button>
        </footer>
        <div class="pet-bubble-tail" aria-hidden="true" />
      </div>
    </Transition>

    <Transition name="pet-hint">
      <div
        v-if="!chatOpen && hoverHint"
        class="pet-hint-bubble"
        role="status"
      >
        {{ t('linkmatePet.tapToChat') }}
        <div class="pet-hint-tail" aria-hidden="true" />
      </div>
    </Transition>

    <div
      class="pet-sprite-wrap"
      :class="[`mood-${petMood}`, { 'is-chat-open': chatOpen }]"
      @mouseenter="hoverHint = true"
      @mouseleave="hoverHint = false"
    >
      <button
        type="button"
        class="pet-sprite-btn"
        :aria-label="t('linkmatePet.tapToChat')"
        @click="toggleChat"
      >
        <img class="pet-sprite-img" :src="characterSrc" alt="LinkMate" />
        <span v-if="petMood === 'think'" class="pet-think-dots" aria-hidden="true">
          <i /><i /><i />
        </span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.pet-stage {
  width: 100%;
  height: 100%;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  box-sizing: border-box;
  background: transparent;
  overflow: visible;
  user-select: none;
}

.pet-chat-bubble {
  position: absolute;
  left: 50%;
  bottom: 78px;
  transform: translateX(-50%);
  width: 288px;
  max-width: calc(100% - 8px);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid rgba(18, 183, 245, 0.35);
  box-shadow:
    0 12px 32px rgba(15, 23, 42, 0.18),
    0 0 0 1px rgba(255, 255, 255, 0.6) inset;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  -webkit-app-region: no-drag;
  z-index: 2;
}

.pet-bubble-tail {
  position: absolute;
  left: 50%;
  bottom: -7px;
  width: 14px;
  height: 14px;
  margin-left: -7px;
  background: rgba(255, 255, 255, 0.97);
  border-right: 1px solid rgba(18, 183, 245, 0.25);
  border-bottom: 1px solid rgba(18, 183, 245, 0.25);
  transform: rotate(45deg);
  box-shadow: 4px 4px 8px rgba(15, 23, 42, 0.06);
}

.pet-chat-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.pet-chat-title {
  display: flex;
  flex-direction: column;
  gap: 1px;
  font-weight: 600;
  font-size: 13px;
  color: var(--lx-text-primary);
}

.pet-chat-title small {
  font-size: 10px;
  color: var(--lx-text-secondary);
  font-weight: 400;
}

.pet-chat-actions {
  display: flex;
  gap: 2px;
}

.pet-icon-btn {
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pet-icon-btn:hover {
  background: rgba(18, 183, 245, 0.12);
  color: var(--lx-accent);
}

.pet-chat-body {
  min-height: 120px;
  max-height: 220px;
  overflow-y: auto;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pet-chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--lx-text-secondary);
  font-size: 12px;
  text-align: center;
  padding: 12px;
}

.pet-chat-empty-ico {
  font-size: 22px;
  color: var(--lx-accent);
  opacity: 0.75;
}

.pet-msg {
  max-width: 90%;
}

.pet-msg.is-user {
  align-self: flex-end;
}

.pet-msg.is-assistant {
  align-self: flex-start;
}

.pet-msg-content {
  padding: 6px 10px;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.45;
  word-break: break-word;
}

.pet-msg.is-user .pet-msg-content {
  background: var(--lx-accent);
  color: #fff;
}

.pet-msg.is-assistant .pet-msg-content {
  background: #f0f4f8;
  color: var(--lx-text-primary);
}

.pet-cursor {
  animation: pet-cursor 1s step-end infinite;
}

@keyframes pet-cursor {
  50% {
    opacity: 0;
  }
}

.pet-chat-footer {
  display: flex;
  gap: 6px;
  align-items: flex-end;
  padding: 8px 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.pet-chat-footer :deep(.n-input) {
  flex: 1;
}

.pet-send-btn {
  flex-shrink: 0;
  min-height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: 10px;
  background: var(--lx-accent);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.pet-send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pet-hint-bubble {
  position: absolute;
  left: 50%;
  bottom: 76px;
  transform: translateX(-50%);
  padding: 6px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(18, 183, 245, 0.3);
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.12);
  font-size: 11px;
  color: var(--lx-text-secondary);
  white-space: nowrap;
  z-index: 1;
  -webkit-app-region: no-drag;
}

.pet-hint-tail {
  position: absolute;
  left: 50%;
  bottom: -5px;
  width: 10px;
  height: 10px;
  margin-left: -5px;
  background: rgba(255, 255, 255, 0.95);
  border-right: 1px solid rgba(18, 183, 245, 0.2);
  border-bottom: 1px solid rgba(18, 183, 245, 0.2);
  transform: rotate(45deg);
}

.pet-sprite-wrap {
  position: relative;
  flex-shrink: 0;
  -webkit-app-region: drag;
  animation: pet-float 3.2s ease-in-out infinite;
}

.pet-sprite-wrap.mood-happy {
  animation: pet-hop 0.55s ease;
}

.pet-sprite-wrap.mood-think {
  animation: pet-float 2s ease-in-out infinite;
}

.pet-sprite-btn {
  display: block;
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
  -webkit-app-region: no-drag;
  position: relative;
  line-height: 0;
}

.pet-sprite-img {
  height: 72px;
  width: auto;
  max-width: 80px;
  display: block;
  pointer-events: none;
  filter: drop-shadow(0 6px 14px rgba(15, 23, 42, 0.22));
  transition: transform 0.18s ease;
}

.pet-sprite-btn:hover .pet-sprite-img {
  transform: scale(1.06) translateY(-2px);
}

.pet-sprite-btn:active .pet-sprite-img {
  transform: scale(0.96);
}

.pet-think-dots {
  position: absolute;
  top: 4px;
  right: -2px;
  display: flex;
  gap: 3px;
  padding: 4px 6px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.pet-think-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--lx-accent);
  animation: pet-dot 1.2s ease-in-out infinite;
}

.pet-think-dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.pet-think-dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes pet-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-5px);
  }
}

@keyframes pet-hop {
  0% {
    transform: translateY(0) scale(1);
  }
  35% {
    transform: translateY(-10px) scale(1.04);
  }
  100% {
    transform: translateY(0) scale(1);
  }
}

@keyframes pet-dot {
  0%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.pet-bubble-enter-active,
.pet-bubble-leave-active,
.pet-hint-enter-active,
.pet-hint-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.pet-bubble-enter-from,
.pet-bubble-leave-to,
.pet-hint-enter-from,
.pet-hint-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(8px) scale(0.95);
}
</style>
