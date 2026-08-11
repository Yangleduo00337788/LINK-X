<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 转发选人弹窗（QQ 桌面版风格）：左栏最近聊天多选，右栏发送给 + 预览 + 留言。
 */
import { computed, ref, watch } from 'vue'
import { NIcon, NModal } from 'naive-ui'
import {
  AddOutline,
  ChevronDownOutline,
  ChevronForwardOutline,
  EllipseOutline,
  CheckmarkCircle,
  SearchOutline,
  HappyOutline,
  OptionsOutline
} from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { LxButton, LxIconButton } from '../ui'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../stores/app'
import { useI18n } from '../../i18n'

const props = defineProps<{
  show: boolean
  /** 排除的会话（通常是消息来源会话） */
  excludeSessionId?: string
  loading?: boolean
  /** 转发内容预览文案 */
  previewText?: string
  /** 图片类消息缩略图 */
  previewImageUrl?: string
}>()

const emit = defineEmits<{
  (e: 'update:show', v: boolean): void
  (e: 'confirm', payload: { targetIds: string[]; leaveMessage: string }): void
  (e: 'createGroup'): void
}>()

const { t } = useI18n()
const appStore = useAppStore()
const { sessions } = storeToRefs(appStore)

const search = ref('')
const recentExpanded = ref(true)
const selectedIds = ref<string[]>([])
const leaveMessage = ref('')

watch(
  () => props.show,
  open => {
    if (open) {
      search.value = ''
      selectedIds.value = []
      leaveMessage.value = ''
      recentExpanded.value = true
    }
  }
)

const recentSessions = computed(() => {
  const q = search.value.trim().toLowerCase()
  return (sessions.value || [])
    .filter(s => !s.isSystemNotify && !s.isOfficialNotify)
    .filter(s => !props.excludeSessionId || s.id !== props.excludeSessionId)
    .filter(s => !q || s.name.toLowerCase().includes(q))
})

const selectedSessions = computed(() =>
  recentSessions.value.filter(s => selectedIds.value.includes(s.id))
)

const hasPreview = computed(
  () => Boolean(props.previewText?.trim()) || Boolean(props.previewImageUrl?.trim())
)

function toggle(id: string) {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value = selectedIds.value.filter(x => x !== id)
  else selectedIds.value = [...selectedIds.value, id]
}

function removeSelected(id: string) {
  selectedIds.value = selectedIds.value.filter(x => x !== id)
}

function onClose() {
  if (props.loading) return
  emit('update:show', false)
}

function onConfirm() {
  if (!selectedIds.value.length || props.loading) return
  emit('confirm', {
    targetIds: [...selectedIds.value],
    leaveMessage: leaveMessage.value.trim()
  })
}
</script>

<template>
  <n-modal
    :show="show"
    :mask-closable="!loading"
    :auto-focus="false"
    transform-origin="center"
    class="fwd-modal-wrap"
    @update:show="v => emit('update:show', v)"
  >
    <div class="fwd-shell">
      <div class="fwd-left">
        <div class="fwd-search">
          <n-icon :component="SearchOutline" :size="16" class="fwd-search-ico" />
          <input
            v-model="search"
            class="fwd-search-input"
            type="search"
            :placeholder="t('common.search')"
          />
          <LxIconButton class="fwd-filter-btn" :title="t('common.search')">
            <n-icon :component="OptionsOutline" :size="16" />
          </LxIconButton>
        </div>

        <LxButton variant="outline" class="fwd-create" :disabled="loading" @click="emit('createGroup')">
          <n-icon :component="AddOutline" :size="17" />
          {{ t('viewer.forwardCreateGroup') }}
        </LxButton>

        <button type="button" class="fwd-section-head" @click="recentExpanded = !recentExpanded">
          <span>{{ t('modals.recentChats') }}</span>
          <n-icon
            :component="recentExpanded ? ChevronDownOutline : ChevronForwardOutline"
            :size="14"
          />
        </button>

        <div v-show="recentExpanded" class="fwd-list">
          <button
            v-for="s in recentSessions"
            :key="s.id"
            type="button"
            class="fwd-row"
            @click="toggle(s.id)"
          >
            <n-icon
              :component="selectedIds.includes(s.id) ? CheckmarkCircle : EllipseOutline"
              :size="18"
              :class="selectedIds.includes(s.id) ? 'chk on' : 'chk'"
            />
            <Avatar
              :text="s.avatarText || '?'"
              :color="s.avatarColor || 'var(--lx-accent)'"
              :image-url="s.avatarUrl"
              :size="36"
            />
            <span class="fwd-row-name" :title="s.name">{{ s.name }}</span>
          </button>
          <p v-if="!recentSessions.length" class="fwd-empty">{{ t('chat.noForwardTarget') }}</p>
        </div>
      </div>

      <div class="fwd-right">
        <div class="fwd-send-label">{{ t('viewer.forwardSendTo') }}</div>

        <div class="fwd-selected">
          <div v-for="s in selectedSessions" :key="s.id" class="fwd-chip">
            <Avatar
              :text="s.avatarText || '?'"
              :color="s.avatarColor || 'var(--lx-accent)'"
              :image-url="s.avatarUrl"
              :size="32"
            />
            <span class="fwd-chip-name">{{ s.name }}</span>
            <LxIconButton
              variant="close"
              class="fwd-chip-x lx-close-btn--sm"
              :title="t('common.close')"
              @click="removeSelected(s.id)"
            >
              ×
            </LxIconButton>
          </div>
        </div>

        <div v-if="hasPreview" class="fwd-preview">
          <img
            v-if="previewImageUrl"
            class="fwd-preview-img"
            :src="previewImageUrl"
            alt=""
          />
          <p v-if="previewText" class="fwd-preview-text">{{ previewText }}</p>
        </div>

        <div class="fwd-leave">
          <input
            v-model="leaveMessage"
            class="fwd-leave-input"
            type="text"
            :placeholder="t('viewer.forwardLeaveMsg')"
            :disabled="loading"
          />
          <n-icon :component="HappyOutline" :size="18" class="fwd-emoji" />
        </div>

        <div class="fwd-actions">
          <LxButton
            variant="modal-primary"
            :disabled="loading || !selectedIds.length"
            @click="onConfirm"
          >
            {{ t('common.confirm') }}
          </LxButton>
          <LxButton variant="modal" :disabled="loading" @click="onClose">
            {{ t('common.cancel') }}
          </LxButton>
        </div>
      </div>
    </div>
  </n-modal>
</template>

<style scoped>
:global(.fwd-modal-wrap .n-modal) {
  width: auto;
  max-width: none;
  background: transparent;
  box-shadow: none;
}

:global(.fwd-modal-wrap .n-modal-body-wrapper) {
  padding: 0;
}

.fwd-shell {
  width: min(680px, 94vw);
  height: min(480px, 82vh);
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-xl);
  overflow: hidden;
  color: var(--lx-conf-surface);
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.18);
}

.fwd-left {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-right: 1px solid var(--lx-divider);
  background: var(--lx-bg-card);
}

.fwd-search {
  display: flex;
  align-items: center;
  gap: var(--lx-space-sm);
  margin: var(--lx-space-xl) var(--lx-space-xl) var(--lx-space-md);
  padding: var(--lx-space-sm-plus) var(--lx-space-md);
  border-radius: var(--lx-radius-xs);
  background: var(--lx-bg-panel);
  border: 1px solid var(--lx-divider);
}

.fwd-search-ico {
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.fwd-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--lx-font-md);
  color: inherit;
}

.fwd-filter-btn {
  width: 28px;
  height: 28px;
  color: var(--lx-text-muted);
}

.fwd-create {
  display: flex;
  width: calc(100% - 28px);
  margin: 0 var(--lx-space-xl) var(--lx-space);
  justify-content: center;
}

.fwd-create:hover:not(:disabled) {
  background: var(--lx-picker-bg);
}

.fwd-create:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fwd-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lx-space-xs) var(--lx-space-xl) var(--lx-space-sm);
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  font-size: var(--lx-font-sm);
  cursor: pointer;
}

.fwd-list {
  flex: 1;
  overflow: auto;
  padding: 0 var(--lx-space-sm) var(--lx-space-md);
}

.fwd-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  width: 100%;
  padding: var(--lx-space-sm-plus) var(--lx-space);
  border: none;
  border-radius: var(--lx-radius-xs);
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: inherit;
}

.fwd-row:hover {
  background: var(--lx-bg-panel);
}

.chk {
  color: var(--lx-picker-muted);
  flex-shrink: 0;
}

.chk.on {
  color: var(--lx-accent);
}

.fwd-row-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--lx-font);
}

.fwd-empty {
  margin: var(--lx-space-5xl) var(--lx-space);
  text-align: center;
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}

.fwd-right {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--lx-space-2xl) var(--lx-space-2xl) var(--lx-space-xl);
  background: var(--lx-bg-card);
}

.fwd-send-label {
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-conf-surface);
  margin-bottom: var(--lx-space-md);
}

.fwd-selected {
  min-height: 56px;
  max-height: 88px;
  overflow: auto;
  display: flex;
  flex-wrap: wrap;
  gap: var(--lx-space);
  align-content: flex-start;
  margin-bottom: var(--lx-space-lg);
}

.fwd-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-sm);
  padding: var(--lx-space-2xs) var(--lx-space) var(--lx-space-2xs) var(--lx-space-2xs);
  border-radius: var(--lx-radius-pill);
  background: var(--lx-bg-panel);
  max-width: 100%;
}

.fwd-chip-name {
  font-size: var(--lx-font-sm);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fwd-chip-x {
  width: 22px;
  height: 22px;
  font-size: var(--lx-font);
  color: var(--lx-text-muted);
}

.fwd-preview {
  margin-bottom: var(--lx-space-md);
  padding: var(--lx-space-md) var(--lx-space-lg);
  border-radius: var(--lx-radius-sm);
  background: var(--lx-picker-row-bg);
  max-height: 120px;
  overflow: auto;
}

.fwd-preview-img {
  display: block;
  max-width: 100%;
  max-height: 72px;
  border-radius: var(--lx-radius-2xs);
  margin-bottom: var(--lx-space-sm);
  object-fit: contain;
}

.fwd-preview-text {
  margin: 0;
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
  color: var(--lx-picker-text);
  word-break: break-word;
}

.fwd-leave {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  padding: var(--lx-space) var(--lx-space-md);
  border: 1px solid var(--lx-divider);
  border-radius: var(--lx-radius-xs);
  background: var(--lx-bg-card);
  margin-bottom: var(--lx-space-lg);
}

.fwd-leave-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  font-size: var(--lx-font-md);
  color: var(--lx-conf-surface);
  background: transparent;
}

.fwd-leave-input::placeholder {
  color: var(--lx-picker-faint);
}

.fwd-emoji {
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.fwd-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lx-space-md);
  margin-top: auto;
  padding-top: var(--lx-space-xs);
}
</style>
