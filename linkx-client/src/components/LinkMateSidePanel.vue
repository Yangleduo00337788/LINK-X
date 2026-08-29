<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 灵伴对话面板（主导航全屏 / 旧版侧栏嵌入）。
 */
import { computed, h, nextTick, onMounted, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import { NInput, NIcon, NDropdown, useMessage, useDialog, type DropdownOption } from 'naive-ui'
import {
  BulbOutline,
  CallOutline,
  SparklesOutline,
  ChevronDownOutline,
  TrashOutline,
  RefreshOutline,
  CopyOutline,
  CreateOutline,
  CloseOutline,
  EllipsisHorizontalOutline,
  OpenOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useLinkMateStore } from '../stores/linkmate'
import { useLinkMateAgentStore } from '../stores/linkmateAgent'
import { useCallStore } from '../stores/call'
import { useExtensionDockStore } from '../stores/extensionDock'
import type { LinkMateMessage } from '../api/linkmate'
import { useI18n } from '../i18n'
import LinkMateLogoMark from './LinkMateLogoMark.vue'
import { loadLinkMateMarkdown, copyCodeFromButtonLazy } from '../utils/linkmateMarkdownLazy'
import { copyText } from '../utils/clipboard'

const props = withDefaults(
  defineProps<{
    layout?: 'page' | 'side'
    /** 独立窗口模式：隐藏收起与弹出按钮 */
    standalone?: boolean
    /** 嵌入统一扩展坞：隐藏标签栏与拖拽条 */
    dockEmbed?: boolean
  }>(),
  {
    layout: 'page',
    standalone: false,
    dockEmbed: false
  }
)

const mdRenderer = shallowRef<((content: string) => string) | null>(null)

const isPageLayout = computed(() => props.layout === 'page')
const isStandalone = computed(() => props.standalone)
const isDockEmbed = computed(() => props.dockEmbed)

const { t } = useI18n()
const message = useMessage()
const dialog = useDialog()
const linkMate = useLinkMateStore()
const linkMateAgent = useLinkMateAgentStore()
const callStore = useCallStore()
const extensionDock = useExtensionDockStore()
const { panelWidth } = storeToRefs(extensionDock)
const {
  activeMessages,
  activeSessionId,
  loadingMessages,
  loadingSessions,
  streaming,
  inputDraft,
  enabled,
  sessions,
  openTabs,
  deepThinking,
  deepThinkingSupported,
  voiceCallSupported,
  agentEnabled,
  showHistory,
  attachedImContext,
  dailyQuotaExhausted,
  hasMoreBySession,
  loadingMoreBySession
} = storeToRefs(linkMate)
const { agentMode } = storeToRefs(linkMateAgent)

const inputRef = ref<InstanceType<typeof NInput> | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const booted = ref(false)
const isResizing = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)
const collapsedReasoning = reactive<Record<string, boolean>>({})
const statusNow = ref(Date.now())
const tabMoreShow = ref(false)
let statusTickTimer: number | null = null

const tabMoreOptions = computed<DropdownOption[]>(() => [
  { label: t('linkmate.newChat'), key: 'newChat' },
  { label: t('linkmate.openHistory'), key: 'history' },
  { type: 'divider', key: 'd1' },
  { label: t('linkmate.closeAllTabs'), key: 'closeAll' }
])

function handleCloseTab(sessionId: string) {
  if (streaming.value) return
  void linkMate.closeTab(sessionId)
}

function handleOpenStandalone(sessionId: string) {
  if (window.electronAPI?.openLinkMate) {
    window.electronAPI.openLinkMate(sessionId)
    return
  }
  const base = window.location.href.split('#')[0]
  const hash = `#/linkmate/${encodeURIComponent(sessionId)}`
  window.open(`${base}${hash}`, '_blank', 'noopener')
}

function handleCloseAllTabs() {
  if (streaming.value) return
  dialog.warning({
    title: t('linkmate.closeAllTabs'),
    content: t('linkmate.closeAllTabsConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => {
      void linkMate.closeAllTabs()
    }
  })
}

function onTabMoreSelect(key: string) {
  tabMoreShow.value = false
  if (key === 'newChat') {
    void handleNewChat()
    return
  }
  if (key === 'history') {
    showHistory.value = true
    return
  }
  if (key === 'closeAll') {
    handleCloseAllTabs()
  }
}

function startStatusTick() {
  if (statusTickTimer != null) return
  statusTickTimer = window.setInterval(() => {
    statusNow.value = Date.now()
  }, 200)
}

function stopStatusTick() {
  if (statusTickTimer == null) return
  window.clearInterval(statusTickTimer)
  statusTickTimer = null
}

function formatResponseDuration(msg: LinkMateMessage): string {
  let ms = msg.responseDurationMs
  if (
    ms == null &&
    msg.responseStartedAt &&
    streaming.value &&
    streamingAssistant.value?.id === msg.id
  ) {
    ms = statusNow.value - msg.responseStartedAt
  }
  if (ms == null || ms <= 0) return ''
  const sec = ms / 1000
  return sec < 10 ? sec.toFixed(1) : String(Math.round(sec))
}

function formatReasoningDuration(msg: LinkMateMessage): string {
  let ms = msg.reasoningDurationMs
  if (
    ms == null &&
    msg.reasoningContent?.trim() &&
    msg.responseStartedAt &&
    streaming.value &&
    streamingAssistant.value?.id === msg.id &&
    !msg.content.trim()
  ) {
    ms = statusNow.value - msg.responseStartedAt
  }
  if (ms == null || ms <= 0) return ''
  const sec = ms / 1000
  return sec < 10 ? sec.toFixed(1) : String(Math.round(sec))
}

function reasoningDurationLabel(msg: LinkMateMessage | null | undefined): string {
  if (!msg) return ''
  const duration = formatReasoningDuration(msg)
  if (!duration) return ''
  return t('linkmate.reasoningDuration', { n: duration })
}

function shouldShowReasoningDuration(msg: LinkMateMessage): boolean {
  if (formatReasoningDuration(msg)) return true
  return !!(msg.reasoningDurationMs && msg.reasoningDurationMs > 0)
}

function responseDurationLabel(msg: LinkMateMessage | null | undefined): string {
  if (!msg) return ''
  const duration = formatResponseDuration(msg)
  if (!duration) return ''
  return t('linkmate.responseDuration', { n: duration })
}

function shouldShowResponseDuration(msg: LinkMateMessage): boolean {
  return !!formatResponseDuration(msg)
}

const panelStyle = computed(() =>
  isPageLayout.value || isDockEmbed.value ? undefined : { width: `${panelWidth.value}px` }
)
const canChat = computed(() => enabled.value && !dailyQuotaExhausted.value)
const canUseDeepThinking = computed(() => deepThinkingSupported.value && !streaming.value)

const streamingAssistant = computed(() => {
  if (!streaming.value) return null
  const last = activeMessages.value.at(-1)
  if (!last || last.role !== 'assistant' || !last.id.startsWith('temp-assistant')) return null
  return last
})

const statusHintText = computed(() => {
  if (!streamingAssistant.value) return ''
  const hasReasoning = !!streamingAssistant.value.reasoningContent?.trim()
  const hasContent = !!streamingAssistant.value.content.trim()
  if (deepThinking.value) {
    if (!hasReasoning && !hasContent) return t('linkmate.deepThinkingThinking')
    if (hasReasoning && !hasContent) return t('linkmate.deepThinkingReasoning')
    return t('linkmate.deepThinkingGenerating')
  }
  if (!hasContent) return t('linkmate.thinking')
  return t('linkmate.generating')
})

const starterPrompts = computed(() => [
  t('linkmate.promptSummary'),
  t('linkmate.promptEmail'),
  t('linkmate.promptTranslate')
])

const showWelcome = computed(
  () => booted.value && !loadingMessages.value && activeMessages.value.length === 0
)

const hasMoreMessages = computed(() => {
  const sid = activeSessionId.value
  return sid ? !!hasMoreBySession.value[sid] : false
})

const loadingMoreMessages = computed(() => {
  const sid = activeSessionId.value
  return sid ? !!loadingMoreBySession.value[sid] : false
})

const imContextPreview = computed(() => attachedImContext.value)

const imContextHint = computed(() => {
  if (!imContextPreview.value) return ''
  const { title, group } = imContextPreview.value
  return group
    ? t('linkmate.imContextGroup', { title })
    : t('linkmate.imContextActive', { title })
})

function isLastAssistantMessage(msg: LinkMateMessage, index: number) {
  if (msg.role !== 'assistant' || msg.id.startsWith('temp-assistant')) return false
  for (let i = activeMessages.value.length - 1; i >= 0; i--) {
    const m = activeMessages.value[i]
    if (m.role === 'assistant' && !m.id.startsWith('temp-assistant')) {
      return i === index
    }
  }
  return false
}

function canRegenerate(msg: LinkMateMessage, index: number) {
  return !streaming.value && isLastAssistantMessage(msg, index) && !!msg.content.trim()
}

async function handleCopyAssistant(msg: LinkMateMessage) {
  const text = msg.content?.trim()
  if (!text) return
  const ok = await copyText(text)
  if (ok) {
    message.success(t('linkmate.messageCopied'))
  } else {
    message.error(t('linkmate.copyCodeFailed'))
  }
}

async function handleRegenerate(msg: LinkMateMessage) {
  if (!canRegenerate(msg, activeMessages.value.indexOf(msg))) return
  try {
    await linkMate.regenerateMessage(msg.id)
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('linkmate.regenerateFailed'))
  }
}

async function handleMarkdownClick(e: MouseEvent) {
  const btn = (e.target as HTMLElement | null)?.closest('.lm-code-copy') as HTMLElement | null
  if (!btn) return
  e.preventDefault()
  e.stopPropagation()
  const ok = await copyCodeFromButtonLazy(btn)
  if (ok) {
    message.success(t('linkmate.codeCopied'))
  } else {
    message.error(t('linkmate.copyCodeFailed'))
  }
}

function isStreamingMessage(msg: LinkMateMessage) {
  return streaming.value && msg.role === 'assistant' && msg.id.startsWith('temp-assistant')
}

function renderAssistantContent(content: string) {
  const render = mdRenderer.value
  if (!render) return ''
  return render(content)
}

function isWaitingAssistant(msg: LinkMateMessage) {
  return (
    streaming.value &&
    msg.role === 'assistant' &&
    msg.id.startsWith('temp-assistant') &&
    !msg.content.trim() &&
    !msg.reasoningContent?.trim()
  )
}

function isReasoningStream(msg: LinkMateMessage) {
  return (
    streaming.value &&
    msg.role === 'assistant' &&
    msg.id.startsWith('temp-assistant') &&
    !!msg.reasoningContent?.trim() &&
    !msg.content.trim()
  )
}

function isReasoningCollapsed(msgId: string) {
  return collapsedReasoning[msgId] === true
}

function toggleReasoning(msgId: string) {
  collapsedReasoning[msgId] = !isReasoningCollapsed(msgId)
}

async function ensureReady() {
  await linkMate.ensurePanelReady()
  booted.value = true
}

function scrollToBottom() {
  const el = messageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function startResize(e: MouseEvent) {
  isResizing.value = true
  resizeStartX.value = e.clientX
  resizeStartWidth.value = panelWidth.value
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function onResize(e: MouseEvent) {
  if (!isResizing.value) return
  const delta = resizeStartX.value - e.clientX
  linkMate.setPanelWidth(resizeStartWidth.value + delta)
}

function stopResize() {
  isResizing.value = false
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

async function handleNewChat() {
  if (streaming.value) return
  try {
    await linkMate.startNewChat()
    await nextTick()
    scrollToBottom()
    inputRef.value?.focus()
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('linkmate.createFailed'))
  }
}

async function handleSelectSession(sessionId: string) {
  if (sessionId === activeSessionId.value) {
    showHistory.value = false
    return
  }
  try {
    await linkMate.selectSession(sessionId)
    await nextTick()
    scrollToBottom()
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('linkmate.loadFailed'))
  }
}

function handleDeleteSession(sessionId: string, title: string) {
  if (streaming.value) return
  dialog.warning({
    title: t('linkmate.deleteChat'),
    content: title,
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await linkMate.deleteSession(sessionId)
        message.success(t('linkmate.deletedOk'))
      } catch (err) {
        message.error(err instanceof Error ? err.message : t('linkmate.deleteFailed'))
      }
    }
  })
}

function handleRenameSession(sessionId: string, currentTitle: string) {
  if (streaming.value) return
  const titleRef = ref(currentTitle || '')
  dialog.create({
    title: t('linkmate.renameChat'),
    content: () =>
      h(NInput, {
        value: titleRef.value,
        maxlength: 80,
        placeholder: t('linkmate.renameChatPrompt'),
        onUpdateValue: (value: string) => {
          titleRef.value = value
        }
      }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      const next = titleRef.value.trim()
      if (!next) {
        message.warning(t('linkmate.renameChatEmpty'))
        return false
      }
      try {
        await linkMate.renameSession(sessionId, next)
        message.success(t('linkmate.renameChatOk'))
      } catch (err) {
        message.error(err instanceof Error ? err.message : t('linkmate.renameChatFailed'))
        return false
      }
    }
  })
}

async function handleMessageListScroll() {
  const el = messageListRef.value
  const sessionId = activeSessionId.value
  if (!el || !sessionId || loadingMoreMessages.value || !hasMoreMessages.value) return
  if (el.scrollTop > 80) return
  await handleLoadMoreClick()
}

async function handleLoadMoreClick() {
  const sessionId = activeSessionId.value
  if (!sessionId || loadingMoreMessages.value || !hasMoreMessages.value) return
  const el = messageListRef.value
  const prevHeight = el?.scrollHeight ?? 0
  await linkMate.loadMoreMessages(sessionId)
  await nextTick()
  if (el && prevHeight) {
    el.scrollTop = el.scrollHeight - prevHeight
  }
}

function onInputDraftUpdate(value: string) {
  linkMate.setInputDraft(value)
}

function toggleDeepThinking() {
  if (!canUseDeepThinking.value) return
  linkMate.setDeepThinking(!deepThinking.value)
}

function handleDeepThinkingClick() {
  if (!deepThinkingSupported.value) {
    message.info(t('linkmate.deepThinkingUnsupported'))
    return
  }
  if (streaming.value) return
  toggleDeepThinking()
}

function handleAgentModeClick() {
  if (streaming.value) return
  if (!agentEnabled.value) {
    message.info(t('linkmateAgent.globallyDisabled'))
    return
  }
  linkMateAgent.toggleAgentMode()
  message.info(agentMode.value ? t('linkmateAgent.enabled') : t('linkmateAgent.disabled'))
}

async function handleVoiceCallClick() {
  if (!enabled.value) {
    message.warning(t('linkmate.serviceDisabled'))
    return
  }
  if (dailyQuotaExhausted.value) {
    message.warning(t('linkmate.dailyQuotaExhausted'))
    return
  }
  if (!voiceCallSupported.value) {
    message.warning(t('linkmate.voiceCallUnsupported'))
    return
  }
  if (callStore.isActive) {
    message.warning(t('errors.callInProgress'))
    return
  }
  try {
    await callStore.startLinkMateVoiceCall()
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('linkmate.voiceCallFail'))
  }
}

async function handleSend() {
  const text = inputDraft.value.trim()
  if (!text || streaming.value) return
  try {
    await linkMate.sendMessage(text)
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('linkmate.sendFailed'))
  }
}

async function applyStarterPrompt(text: string) {
  if (!text || streaming.value || !canChat.value) return
  try {
    await linkMate.sendMessage(text)
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('linkmate.sendFailed'))
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void handleSend()
  }
}

watch(
  () => activeMessages.value.length,
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

watch(
  () => [
    activeMessages.value.at(-1)?.content,
    activeMessages.value.at(-1)?.reasoningContent
  ],
  async () => {
    if (streaming.value) {
      await nextTick()
      scrollToBottom()
    }
  }
)

watch(streaming, (val, oldVal) => {
  if (val) {
    startStatusTick()
  } else {
    stopStatusTick()
  }
  if (oldVal && !val) {
    const last = activeMessages.value.at(-1)
    if (last?.role === 'assistant' && last.reasoningContent?.trim()) {
      collapsedReasoning[last.id] = true
    }
  }
})

watch(deepThinkingSupported, supported => {
  if (!supported) linkMate.setDeepThinking(false)
})

watch(loadingMessages, (loading, wasLoading) => {
  if (wasLoading && !loading) {
    for (const msg of activeMessages.value) {
      if (msg.reasoningContent?.trim()) {
        collapsedReasoning[msg.id] = true
      }
    }
  }
})

onMounted(async () => {
  void loadLinkMateMarkdown().then(m => {
    mdRenderer.value = m.renderLinkMateMarkdown
  })
  linkMate.restoreInputDraft()
  await ensureReady()
  if (!isDockEmbed.value) {
    await nextTick()
    scrollToBottom()
    inputRef.value?.focus()
  }
})

onUnmounted(() => {
  stopResize()
  stopStatusTick()
})
</script>

<template>
  <aside
    class="linkmate-side"
    :class="{
      'linkmate-side--page': isPageLayout,
      'linkmate-side--standalone': isStandalone
    }"
    :style="panelStyle"
  >
    <div
      v-if="!isPageLayout && !isDockEmbed"
      class="linkmate-resizer"
      :class="{ dragging: isResizing }"
      :title="t('linkmate.resizePanel')"
      @mousedown="startResize"
    />

    <div class="linkmate-side-body">
      <header v-if="!isDockEmbed && !isStandalone" class="linkmate-tabbar">
        <button
          v-if="!isStandalone"
          type="button"
          class="linkmate-tabbar-btn"
          :title="t('linkmate.collapsePanel')"
          :disabled="streaming"
          @click="linkMate.collapsePanel()"
        >
          <svg class="linkmate-collapse-ico" viewBox="0 0 20 20" aria-hidden="true">
            <rect x="3.5" y="3.5" width="13" height="13" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.4" />
            <path
              d="M12.5 7.5 L9.5 10.5 M9.5 10.5 H12.5 M9.5 10.5 V7.5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>

        <div v-if="!isStandalone" class="linkmate-tabbar-divider" aria-hidden="true" />

        <div class="linkmate-tabbar-tabs">
          <button
            v-for="tab in openTabs"
            :key="tab.id"
            type="button"
            class="linkmate-tab"
            :class="{ active: tab.id === activeSessionId }"
            @click="handleSelectSession(tab.id)"
          >
            <LinkMateLogoMark size="sm" />
            <span class="linkmate-tab-title">{{ tab.title || t('linkmate.newChat') }}</span>
            <span
              class="linkmate-tab-close"
              role="button"
              tabindex="0"
              :title="t('linkmate.closeTab')"
              @click.stop="handleCloseTab(tab.id)"
              @keydown.enter.stop.prevent="handleCloseTab(tab.id)"
            >
              <NIcon :component="CloseOutline" :size="14" />
            </span>
            <span
              v-if="!isStandalone"
              class="linkmate-tab-popout"
              role="button"
              tabindex="0"
              :title="t('linkmate.openStandalone')"
              @click.stop="handleOpenStandalone(tab.id)"
              @keydown.enter.stop.prevent="handleOpenStandalone(tab.id)"
            >
              <NIcon :component="OpenOutline" :size="13" />
            </span>
          </button>
        </div>

        <div class="linkmate-tabbar-divider" aria-hidden="true" />

        <n-dropdown
          v-model:show="tabMoreShow"
          trigger="click"
          placement="bottom-end"
          :options="tabMoreOptions"
          @select="onTabMoreSelect"
        >
          <button type="button" class="linkmate-tabbar-btn" :title="t('common.more')">
            <NIcon :component="EllipsisHorizontalOutline" :size="18" />
          </button>
        </n-dropdown>
      </header>

      <div v-if="showHistory" class="linkmate-history-layer">
        <div class="linkmate-history-pop linkmate-history-pop--layer">
          <div class="linkmate-history-head">
            <span class="linkmate-history-title">{{ t('linkmate.historyChat') }}</span>
            <button type="button" class="linkmate-history-close" @click="showHistory = false">
              <NIcon :component="CloseOutline" :size="16" />
            </button>
          </div>
          <div v-if="loadingSessions" class="linkmate-history-empty">{{ t('common.loading') }}</div>
          <div v-else-if="sessions.length === 0" class="linkmate-history-empty">
            {{ t('linkmate.noSessions') }}
          </div>
          <div v-else class="linkmate-history-list">
            <div
              v-for="session in sessions"
              :key="session.id"
              class="linkmate-history-item"
              :class="{ active: session.id === activeSessionId }"
            >
              <button
                type="button"
                class="linkmate-history-main"
                @click="handleSelectSession(session.id)"
              >
                <span class="linkmate-history-name">{{ session.title || t('linkmate.newChat') }}</span>
              </button>
              <button
                type="button"
                class="linkmate-history-rename"
                :title="t('linkmate.renameChat')"
                :disabled="streaming"
                @click.stop="handleRenameSession(session.id, session.title)"
              >
                <NIcon :component="CreateOutline" :size="14" />
              </button>
              <button
                type="button"
                class="linkmate-history-del"
                :title="t('linkmate.deleteChat')"
                :disabled="streaming"
                @click.stop="handleDeleteSession(session.id, session.title)"
              >
                <NIcon :component="TrashOutline" :size="14" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!enabled" class="linkmate-side-disabled" :class="{ 'linkmate-standalone-slot': isStandalone }">
        <p>{{ t('linkmate.disabledHint') }}</p>
      </div>

      <div
        v-else
        class="linkmate-side-main"
        :class="{ 'linkmate-standalone-column': isStandalone }"
      >
        <div ref="messageListRef" class="linkmate-side-messages" @scroll="handleMessageListScroll">
          <div v-if="loadingMessages || !booted" class="linkmate-side-empty">
            {{ t('common.loading') }}
          </div>
          <div v-else-if="showWelcome" class="linkmate-welcome">
            <LinkMateLogoMark size="lg" />
            <h3 class="linkmate-welcome-title">{{ t('linkmate.welcomeTitle') }}</h3>
            <p class="linkmate-welcome-sub">{{ t('linkmate.welcomeSub') }}</p>
            <div class="linkmate-starter-prompts">
              <button
                v-for="prompt in starterPrompts"
                :key="prompt"
                type="button"
                class="linkmate-starter-btn"
                :disabled="streaming || !canChat"
                @click="applyStarterPrompt(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </div>
          <template v-else>
            <div v-if="hasMoreMessages" class="linkmate-load-more">
              <button
                type="button"
                class="linkmate-load-more-btn"
                :disabled="loadingMoreMessages"
                @click="handleLoadMoreClick"
              >
                {{ loadingMoreMessages ? t('common.loading') : t('linkmate.loadOlderMessages') }}
              </button>
            </div>
            <template v-for="(msg, msgIndex) in activeMessages" :key="msg.id">
              <div v-if="msg.role === 'user'" class="linkmate-side-msg is-user">
                <div class="linkmate-side-msg-content">{{ msg.content }}</div>
              </div>
              <div v-else class="linkmate-assistant-turn">
                <div
                  v-if="isWaitingAssistant(msg) || isReasoningStream(msg)"
                  class="linkmate-status-hint"
                >
                  <span class="linkmate-status-text">{{ statusHintText }}</span>
                </div>
                <div
                  v-if="msg.reasoningContent?.trim()"
                  class="linkmate-reasoning-block"
                >
                  <button
                    type="button"
                    class="linkmate-reasoning-toggle"
                    @click="toggleReasoning(msg.id)"
                  >
                    <span class="linkmate-reasoning-chev-wrap">
                      <NIcon
                        :component="ChevronDownOutline"
                        :size="12"
                        class="linkmate-reasoning-chev"
                        :class="{ collapsed: isReasoningCollapsed(msg.id) }"
                      />
                    </span>
                    <span class="linkmate-reasoning-label">{{ t('linkmate.deepThinking') }}</span>
                  </button>
                  <div
                    v-show="!isReasoningCollapsed(msg.id)"
                    class="linkmate-reasoning-content"
                  >
                    {{ msg.reasoningContent }}
                  </div>
                </div>
                <div v-if="msg.content" class="linkmate-side-msg is-assistant">
                  <div class="linkmate-side-msg-content">
                    <template v-if="isStreamingMessage(msg)">
                      {{ msg.content }}
                      <span class="linkmate-cursor">▍</span>
                    </template>
                    <div
                      v-else
                      class="linkmate-md"
                      v-html="renderAssistantContent(msg.content)"
                      @click.capture="handleMarkdownClick"
                    />
                  </div>
                </div>
                <p
                  v-if="msg.content && !isStreamingMessage(msg)"
                  class="linkmate-ai-disclaimer"
                >
                  {{ t('linkmate.aiDisclaimer') }}
                </p>
                <div
                  v-if="canRegenerate(msg, msgIndex) || (!isStreamingMessage(msg) && msg.content.trim())"
                  class="linkmate-msg-actions"
                >
                  <button
                    v-if="!isStreamingMessage(msg) && msg.content.trim()"
                    type="button"
                    class="linkmate-action-btn"
                    :title="t('linkmate.copyMessage')"
                    @click="handleCopyAssistant(msg)"
                  >
                    <NIcon :component="CopyOutline" :size="14" />
                    <span>{{ t('linkmate.copyMessage') }}</span>
                  </button>
                  <button
                    v-if="canRegenerate(msg, msgIndex)"
                    type="button"
                    class="linkmate-action-btn"
                    :title="t('linkmate.regenerate')"
                    @click="handleRegenerate(msg)"
                  >
                    <NIcon :component="RefreshOutline" :size="14" />
                    <span>{{ t('linkmate.regenerate') }}</span>
                  </button>
                </div>
                <div
                  v-if="isStreamingMessage(msg) && msg.content.trim()"
                  class="linkmate-status-hint linkmate-status-hint--tail"
                >
                  <span class="linkmate-status-text">{{ statusHintText }}</span>
                </div>
                <div
                  v-if="shouldShowReasoningDuration(msg) || shouldShowResponseDuration(msg)"
                  class="linkmate-response-duration linkmate-response-duration--end"
                >
                  <span v-if="shouldShowReasoningDuration(msg)">{{ reasoningDurationLabel(msg) }}</span>
                  <span v-if="shouldShowResponseDuration(msg)">{{ responseDurationLabel(msg) }}</span>
                </div>
              </div>
            </template>
          </template>
        </div>

        <footer class="linkmate-side-footer">
          <div v-if="imContextPreview" class="linkmate-im-context">
            {{ imContextHint }}
          </div>
          <div class="linkmate-footer-tools">
            <button
              type="button"
              class="linkmate-deep-btn"
              :class="{ active: deepThinking, disabled: !deepThinkingSupported }"
              :title="
                deepThinkingSupported
                  ? deepThinking
                    ? t('linkmate.deepThinkingOn')
                    : t('linkmate.deepThinkingOff')
                  : t('linkmate.deepThinkingUnsupported')
              "
              @click="handleDeepThinkingClick"
            >
              <NIcon :component="BulbOutline" :size="14" />
              <span>{{ t('linkmate.deepThinking') }}</span>
            </button>
            <button
              type="button"
              class="linkmate-deep-btn"
              :class="{ active: agentMode, disabled: streaming || !agentEnabled }"
              :title="
                agentEnabled
                  ? agentMode
                    ? t('linkmateAgent.modeOn')
                    : t('linkmateAgent.modeOff')
                  : t('linkmateAgent.globallyDisabled')
              "
              @click="handleAgentModeClick"
            >
              <NIcon :component="SparklesOutline" :size="14" />
              <span>{{ t('linkmateAgent.modeLabel') }}</span>
            </button>
            <button
              type="button"
              class="linkmate-deep-btn"
              :class="{ disabled: !canChat || !voiceCallSupported || callStore.isActive }"
              :title="
                voiceCallSupported
                  ? t('linkmate.voiceCall')
                  : t('linkmate.voiceCallUnsupported')
              "
              :disabled="!canChat || callStore.isActive"
              @click="handleVoiceCallClick"
            >
              <NIcon :component="CallOutline" :size="14" />
              <span>{{ t('linkmate.voiceCall') }}</span>
            </button>
          </div>
          <div class="linkmate-side-input-row">
            <div class="linkmate-side-input-wrap">
              <NInput
                ref="inputRef"
                :value="inputDraft"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                :placeholder="t('linkmate.inputPlaceholder')"
                :disabled="streaming || !canChat"
                @update:value="onInputDraftUpdate"
                @keydown="handleKeydown"
              />
            </div>
            <button
              v-if="streaming"
              type="button"
              class="linkmate-side-btn linkmate-side-btn--stop"
              @click="linkMate.abortStream()"
            >
              {{ t('linkmate.stop') }}
            </button>
            <button
              v-else
              type="button"
              class="linkmate-side-btn linkmate-side-btn--send"
              :disabled="!inputDraft.trim() || !canChat"
              @click="handleSend"
            >
              {{ t('linkmate.send') }}
            </button>
          </div>
        </footer>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.linkmate-side {
  position: relative;
  flex-shrink: 0;
  height: 100%;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.linkmate-side--page {
  flex: 1;
  width: 100%;
  min-width: 0;
  border-left: none;
}

.linkmate-side--standalone .linkmate-tabbar,
.linkmate-side--standalone .linkmate-history-layer {
  align-self: stretch;
  width: 100%;
}

.linkmate-side-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.linkmate-standalone-column,
.linkmate-standalone-slot {
  width: min(100%, 720px);
  margin-left: auto;
  margin-right: auto;
  box-sizing: border-box;
}

.linkmate-standalone-column {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.linkmate-side--standalone .linkmate-side-messages {
  padding-left: 24px;
  padding-right: 24px;
}

.linkmate-side--standalone .linkmate-side-footer {
  padding-left: 24px;
  padding-right: 24px;
}

.linkmate-side-body {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--lx-bg-panel);
  position: relative;
}

.linkmate-tabbar {
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  min-height: 40px;
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
}

.linkmate-tabbar-btn {
  flex-shrink: 0;
  width: 40px;
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background var(--lx-duration), color var(--lx-duration);
}

.linkmate-tabbar-btn:hover:not(:disabled) {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.linkmate-tabbar-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.linkmate-collapse-ico {
  width: 18px;
  height: 18px;
  display: block;
}

.linkmate-tabbar-divider {
  width: 1px;
  align-self: stretch;
  background: var(--lx-border-light);
  flex-shrink: 0;
}

.linkmate-tabbar-tabs {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: stretch;
  gap: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.linkmate-tabbar-tabs::-webkit-scrollbar {
  display: none;
}

.linkmate-tab {
  flex-shrink: 0;
  max-width: 200px;
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px 0 12px;
  font-size: var(--lx-font-md);
  transition: background var(--lx-duration), color var(--lx-duration);
}

.linkmate-tab:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.linkmate-tab.active {
  color: var(--lx-text-body);
  background: var(--lx-bg-hover);
}

.linkmate-tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.linkmate-tab-close {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-muted);
  transition: background var(--lx-duration), color var(--lx-duration);
}

.linkmate-tab-close:hover {
  background: var(--lx-bg-panel-deep, var(--lx-bg-hover));
  color: var(--lx-text-body);
}

.linkmate-tab-popout {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-muted);
  transition: background var(--lx-duration), color var(--lx-duration);
}

.linkmate-tab-popout:hover {
  background: var(--lx-bg-panel-deep, var(--lx-bg-hover));
  color: var(--lx-text-body);
}

.linkmate-history-layer {
  position: absolute;
  top: 40px;
  right: 8px;
  z-index: var(--lx-z-dropdown);
}

.linkmate-history-pop--layer {
  box-shadow: var(--lx-shadow-dropdown);
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-md);
  background: var(--lx-bg-card);
  max-height: 320px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.linkmate-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--lx-border-light);
}

.linkmate-history-close {
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: inline-flex;
  padding: 2px;
  border-radius: var(--lx-radius-xs);
}

.linkmate-history-close:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.linkmate-resizer {
  position: absolute;
  left: 0;
  top: 0;
  width: 1px;
  height: 100%;
  cursor: col-resize;
  z-index: var(--lx-z-dropdown);
  transition: background var(--lx-duration-md);
}

.linkmate-resizer::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: -3px;
  right: -3px;
  cursor: col-resize;
}

.linkmate-resizer:hover,
.linkmate-resizer.dragging {
  background: var(--lx-separator-fade, rgba(0, 0, 0, 0.06));
}

.collapse-hover-zone {
  position: absolute;
  left: -14px;
  top: 0;
  width: 20px;
  height: 100%;
  z-index: var(--lx-z-raised-4);
}

.linkmate-side:hover .lx-collapse-handle,
.lx-collapse-handle:focus-visible {
  opacity: 1;
  pointer-events: auto;
}

.linkmate-side-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-sm);
  padding: 12px 14px;
  border-bottom: 1px solid var(--lx-border-light);
  background: linear-gradient(180deg, rgba(18, 183, 245, 0.06), transparent);
  flex-shrink: 0;
}

.linkmate-side-hdr-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.linkmate-side-hdr-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.linkmate-hdr-btn {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: var(--lx-radius-md);
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background var(--lx-duration), color var(--lx-duration);
}

.linkmate-hdr-btn:hover:not(:disabled) {
  background: var(--lx-bg-hover);
  color: var(--lx-accent);
}

.linkmate-hdr-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.linkmate-side-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-primary);
}

.linkmate-side-sub {
  font-size: 11px;
  color: var(--lx-text-secondary);
  margin-top: 2px;
}

.linkmate-side-token {
  font-size: 10px;
  color: var(--lx-text-muted);
  margin-top: 2px;
}

.linkmate-side-disabled {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  text-align: center;
  color: var(--lx-text-secondary);
  font-size: 13px;
}

.linkmate-side-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--lx-bg-panel);
}

.linkmate-side-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-secondary);
  font-size: 13px;
  text-align: center;
  padding: 24px;
}

.linkmate-welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 28px 20px;
  gap: 10px;
}

.linkmate-welcome-title {
  margin: 8px 0 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--lx-text-primary);
}

.linkmate-welcome-sub {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--lx-text-secondary);
  max-width: 280px;
}

.linkmate-starter-prompts {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-width: 300px;
  margin-top: 12px;
}

.linkmate-starter-btn {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-lg);
  background: var(--lx-bg-card);
  color: var(--lx-text-primary);
  font-size: 13px;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--lx-duration),
    background var(--lx-duration);
}

.linkmate-starter-btn:hover:not(:disabled) {
  border-color: var(--lx-border);
  background: var(--lx-bg-hover);
}

.linkmate-starter-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.linkmate-assistant-turn {
  align-self: stretch;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.linkmate-side-msg.is-user {
  align-self: flex-end;
  max-width: 88%;
}

.linkmate-side-msg.is-assistant {
  align-self: stretch;
  max-width: 100%;
}

.linkmate-side-msg-content {
  word-break: break-word;
  white-space: pre-wrap;
}

.linkmate-side-msg.is-user .linkmate-side-msg-content {
  padding: 8px 12px;
  border-radius: var(--lx-radius-lg);
  font-size: 13px;
  line-height: 1.5;
  background: var(--lx-accent);
  color: #fff;
}

.linkmate-side-msg.is-assistant .linkmate-side-msg-content {
  padding: 2px 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--lx-text-primary);
}

.linkmate-md :deep(p) {
  margin: 0 0 0.6em;
}

.linkmate-md :deep(p:last-child) {
  margin-bottom: 0;
}

.linkmate-md :deep(ul),
.linkmate-md :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.4em;
}

.linkmate-md :deep(pre) {
  margin: 0.5em 0;
  padding: 10px 12px;
  border-radius: var(--lx-radius-md);
  background: var(--lx-bg-hover);
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.5;
}

.linkmate-md :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.9em;
}

.linkmate-md :deep(:not(pre) > code) {
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: var(--lx-bg-hover);
}

.linkmate-md :deep(a) {
  color: var(--lx-accent);
  text-decoration: none;
}

.linkmate-md :deep(a:hover) {
  text-decoration: underline;
}

.linkmate-md :deep(.lm-code-wrap) {
  position: relative;
  margin: 0.5em 0;
}

.linkmate-md :deep(.lm-code-wrap pre) {
  margin: 0;
}

.linkmate-md :deep(.lm-code-copy) {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
  padding: 2px 8px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-sm);
  background: var(--lx-bg-panel);
  color: var(--lx-text-secondary);
  font-size: 11px;
  line-height: 1.4;
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--lx-duration);
}

.linkmate-md :deep(.lm-code-wrap:hover .lm-code-copy),
.linkmate-md :deep(.lm-code-copy:focus-visible) {
  opacity: 1;
}

.linkmate-ai-disclaimer {
  margin: 4px 0 0;
  padding: 0 2px;
  font-size: var(--lx-font-xs);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-muted);
  user-select: none;
}

.linkmate-msg-actions {
  display: flex;
  gap: 8px;
  margin-top: 2px;
}

.linkmate-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border: none;
  border-radius: var(--lx-radius-sm);
  background: transparent;
  color: var(--lx-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: color var(--lx-duration), background var(--lx-duration);
}

.linkmate-action-btn:hover {
  color: var(--lx-text-primary);
  background: var(--lx-bg-hover);
}

.linkmate-im-context {
  margin-bottom: 6px;
  padding: 4px 8px;
  border-radius: var(--lx-radius-sm);
  background: var(--lx-bg-hover);
  color: var(--lx-text-secondary);
  font-size: 11px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.linkmate-status-hint,
.linkmate-generating-hint {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.linkmate-status-hint {
  align-self: flex-start;
  padding: 4px 0;
}

.linkmate-status-hint--tail {
  padding: 2px 0 0;
}

.linkmate-status-text {
  color: var(--lx-text-secondary);
}

.linkmate-response-duration--end {
  margin-top: 2px;
  padding-bottom: 2px;
}

.linkmate-reasoning-block {
  align-self: stretch;
  border-left: 2px solid var(--lx-accent-soft);
  padding-left: 10px;
}

.linkmate-reasoning-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  padding: 0;
  margin-bottom: 6px;
  min-height: 18px;
}

.linkmate-reasoning-toggle:hover {
  color: var(--lx-accent);
}

.linkmate-reasoning-chev-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.linkmate-reasoning-label {
  line-height: 1;
}

.linkmate-response-duration {
  line-height: 1;
  color: var(--lx-text-muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.linkmate-reasoning-chev {
  display: block;
  transition: transform var(--lx-duration);
}

.linkmate-reasoning-chev.collapsed {
  transform: rotate(-90deg);
}

.linkmate-reasoning-content {
  font-size: 12px;
  line-height: 1.6;
  color: var(--lx-text-muted);
  white-space: pre-wrap;
  word-break: break-word;
  padding-bottom: 4px;
}

.linkmate-cursor {
  animation: linkmate-cursor 1s step-end infinite;
}

@keyframes linkmate-cursor {
  50% {
    opacity: 0;
  }
}

.linkmate-side-footer {
  padding: 10px 14px 14px;
  border-top: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  flex-shrink: 0;
}

.linkmate-footer-tools {
  margin-bottom: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.linkmate-deep-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--lx-radius-lg);
  border: 1px solid var(--lx-border-light);
  background: var(--lx-bg-panel);
  color: var(--lx-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition:
    border-color var(--lx-duration),
    color var(--lx-duration),
    background var(--lx-duration);
}

.linkmate-deep-btn:hover:not(:disabled) {
  border-color: var(--lx-accent);
  color: var(--lx-accent);
}

.linkmate-deep-btn.active {
  border-color: var(--lx-accent);
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}

.linkmate-deep-btn.disabled,
.linkmate-deep-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.linkmate-side-input-row {
  display: flex;
  align-items: stretch;
  gap: 8px;
  min-height: 36px;
}

.linkmate-side-input-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  min-height: 36px;
  padding: 6px 10px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-lg);
  background: var(--lx-bg-panel);
  box-sizing: border-box;
  transition: border-color var(--lx-duration);
}

.linkmate-side-input-wrap:focus-within {
  border-color: var(--lx-accent);
}

.linkmate-side-input-wrap :deep(.n-input) {
  background: transparent;
}

.linkmate-side-input-wrap :deep(.n-input__border),
.linkmate-side-input-wrap :deep(.n-input__state-border) {
  display: none;
}

.linkmate-side-input-wrap :deep(.n-input__textarea-el),
.linkmate-side-input-wrap :deep(.n-input__placeholder),
.linkmate-side-input-wrap :deep(.n-input__textarea-mirror) {
  min-height: 22px !important;
  padding: 0 !important;
  line-height: 22px !important;
  font-size: 13px;
}

.linkmate-side-btn {
  flex-shrink: 0;
  min-width: 64px;
  min-height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: var(--lx-radius-lg);
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  box-sizing: border-box;
  transition: background var(--lx-duration), filter var(--lx-duration);
}

.linkmate-side-btn--send {
  background: var(--lx-accent);
  color: #fff;
}

.linkmate-side-btn--send:hover:not(:disabled) {
  background: var(--lx-accent-hover);
}

.linkmate-side-btn--send:disabled {
  background: #e9e9e9;
  color: rgba(255, 255, 255, 0.9);
  cursor: not-allowed;
}

.linkmate-side-btn--stop {
  background: var(--lx-danger);
  color: #fff;
}

.linkmate-side-btn--stop:hover {
  filter: brightness(1.06);
}

.linkmate-history-pop {
  padding: 10px;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-lg);
  box-shadow: var(--lx-shadow-elevated);
}

.linkmate-history-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text-primary);
  margin-bottom: 8px;
  padding: 0 4px;
}

.linkmate-history-empty {
  padding: 16px 8px;
  text-align: center;
  color: var(--lx-text-muted);
  font-size: 12px;
}

.linkmate-history-list {
  max-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.linkmate-history-item {
  display: flex;
  align-items: center;
  gap: 4px;
  border-radius: var(--lx-radius-md);
}

.linkmate-history-item.active {
  background: var(--lx-accent-soft);
}

.linkmate-history-main {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  text-align: left;
  padding: 8px 10px;
  cursor: pointer;
  color: var(--lx-text-primary);
  font-size: 13px;
}

.linkmate-history-name {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.linkmate-history-rename,
.linkmate-history-del {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: var(--lx-radius-sm);
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.linkmate-history-rename:hover:not(:disabled) {
  background: var(--lx-bg-hover);
  color: var(--lx-text-primary);
}

.linkmate-history-del:hover:not(:disabled) {
  color: var(--lx-danger);
  background: rgba(255, 77, 79, 0.08);
}

.linkmate-history-rename:disabled,
.linkmate-history-del:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.linkmate-load-more {
  display: flex;
  justify-content: center;
  padding: 8px 0 12px;
}

.linkmate-load-more-btn {
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-md);
  background: var(--lx-bg-panel);
  color: var(--lx-text-secondary);
  font-size: var(--lx-font-xs);
  padding: 4px 12px;
  cursor: pointer;
}

.linkmate-load-more-btn:hover:not(:disabled) {
  color: var(--lx-accent);
  border-color: var(--lx-accent-soft);
}

.linkmate-load-more-btn:disabled {
  opacity: 0.6;
  cursor: wait;
}
</style>
