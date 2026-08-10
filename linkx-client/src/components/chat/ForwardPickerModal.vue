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
          <button type="button" class="fwd-filter-btn" :title="t('common.search')">
            <n-icon :component="OptionsOutline" :size="16" />
          </button>
        </div>

        <button type="button" class="fwd-create" :disabled="loading" @click="emit('createGroup')">
          <n-icon :component="AddOutline" :size="17" />
          {{ t('viewer.forwardCreateGroup') }}
        </button>

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
            <button type="button" class="fwd-chip-x" @click="removeSelected(s.id)">×</button>
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
          <button
            type="button"
            class="fwd-btn primary"
            :disabled="loading || !selectedIds.length"
            @click="onConfirm"
          >
            {{ t('common.confirm') }}
          </button>
          <button type="button" class="fwd-btn" :disabled="loading" @click="onClose">
            {{ t('common.cancel') }}
          </button>
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
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  color: #1f2329;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.18);
}

.fwd-left {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-right: 1px solid #ebebeb;
  background: #fff;
}

.fwd-search {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 14px 14px 10px;
  padding: 7px 10px;
  border-radius: 6px;
  background: #f5f5f5;
  border: 1px solid #ebebeb;
}

.fwd-search-ico {
  color: #999;
  flex-shrink: 0;
}

.fwd-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: inherit;
}

.fwd-filter-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  padding: 2px;
}

.fwd-create {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 0 14px 8px;
  height: 34px;
  border: 1px solid #e5e5e5;
  border-radius: 6px;
  background: #fff;
  color: #666;
  font-size: 13px;
  cursor: pointer;
}

.fwd-create:hover:not(:disabled) {
  background: #fafafa;
}

.fwd-create:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fwd-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 14px 6px;
  border: none;
  background: transparent;
  color: #888;
  font-size: 12px;
  cursor: pointer;
}

.fwd-list {
  flex: 1;
  overflow: auto;
  padding: 0 6px 10px;
}

.fwd-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 7px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: inherit;
}

.fwd-row:hover {
  background: #f5f5f5;
}

.chk {
  color: #c8c8c8;
  flex-shrink: 0;
}

.chk.on {
  color: #12b7f5;
}

.fwd-row-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.fwd-empty {
  margin: 32px 8px;
  text-align: center;
  font-size: 13px;
  color: #999;
}

.fwd-right {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px 14px;
  background: #fff;
}

.fwd-send-label {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 10px;
}

.fwd-selected {
  min-height: 56px;
  max-height: 88px;
  overflow: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
  margin-bottom: 12px;
}

.fwd-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px 3px 3px;
  border-radius: 999px;
  background: #f5f5f5;
  max-width: 100%;
}

.fwd-chip-name {
  font-size: 12px;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fwd-chip-x {
  border: none;
  background: transparent;
  cursor: pointer;
  color: #999;
  font-size: 15px;
  line-height: 1;
  padding: 0 2px;
}

.fwd-preview {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f3f3f3;
  max-height: 120px;
  overflow: auto;
}

.fwd-preview-img {
  display: block;
  max-width: 100%;
  max-height: 72px;
  border-radius: 4px;
  margin-bottom: 6px;
  object-fit: contain;
}

.fwd-preview-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: #4e5969;
  word-break: break-word;
}

.fwd-leave {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid #ebebeb;
  border-radius: 6px;
  background: #fff;
  margin-bottom: 12px;
}

.fwd-leave-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  font-size: 13px;
  color: #1f2329;
  background: transparent;
}

.fwd-leave-input::placeholder {
  color: #bbb;
}

.fwd-emoji {
  color: #999;
  flex-shrink: 0;
}

.fwd-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: auto;
  padding-top: 4px;
}

.fwd-btn {
  min-width: 72px;
  height: 32px;
  padding: 0 16px;
  border-radius: 6px;
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #1f2329;
  cursor: pointer;
  font-size: 13px;
}

.fwd-btn.primary {
  border-color: transparent;
  background: #12b7f5;
  color: #fff;
}

.fwd-btn.primary:hover:not(:disabled) {
  background: #0fa8e0;
}

.fwd-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
