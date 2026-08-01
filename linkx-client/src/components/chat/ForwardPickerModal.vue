<script setup lang="ts">
/**
 * 转发选人弹窗（微信风格）：左栏最近聊天多选，右栏发送给 + 留言。
 */
import { computed, ref, watch } from 'vue'
import { NIcon, NInput, NModal } from 'naive-ui'
import {
  AddOutline,
  ChevronDownOutline,
  ChevronForwardOutline,
  EllipseOutline,
  CheckmarkCircle,
  SearchOutline,
  HappyOutline
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
        </div>

        <button type="button" class="fwd-create" :disabled="loading" @click="emit('createGroup')">
          <n-icon :component="AddOutline" :size="18" />
          {{ t('viewer.forwardCreateGroup') }}
        </button>

        <button type="button" class="fwd-section-head" @click="recentExpanded = !recentExpanded">
          <span>{{ t('modals.recentChats') }}</span>
          <n-icon
            :component="recentExpanded ? ChevronDownOutline : ChevronForwardOutline"
            :size="16"
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
              :size="20"
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
              :size="28"
            />
            <span class="fwd-chip-name">{{ s.name }}</span>
            <button type="button" class="fwd-chip-x" @click="removeSelected(s.id)">×</button>
          </div>
        </div>

        <div class="fwd-leave">
          <n-input
            v-model:value="leaveMessage"
            :placeholder="t('viewer.forwardLeaveMsg')"
            :disabled="loading"
          >
            <template #suffix>
              <n-icon :component="HappyOutline" :size="18" class="fwd-emoji" />
            </template>
          </n-input>
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
.fwd-shell {
  width: min(720px, 94vw);
  height: min(520px, 80vh);
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  background: var(--lx-bg-card, #fff);
  border-radius: 12px;
  overflow: hidden;
  color: var(--lx-text-body, #1f2329);
}

.fwd-left {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--lx-border-light, rgba(0, 0, 0, 0.08));
  min-width: 0;
  background: var(--lx-bg-panel, #f7f8fa);
}

.fwd-search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 12px 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--lx-bg-card, #fff);
  border: 1px solid var(--lx-border-light, rgba(0, 0, 0, 0.06));
}

.fwd-search-ico {
  color: var(--lx-text-muted, #999);
  flex-shrink: 0;
}

.fwd-search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: inherit;
}

.fwd-create {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 0 12px 10px;
  height: 36px;
  border: 1px solid rgba(18, 183, 245, 0.35);
  border-radius: 8px;
  background: rgba(18, 183, 245, 0.08);
  color: var(--lx-accent, #12b7f5);
  font-size: 13px;
  cursor: pointer;
}

.fwd-create:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fwd-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 14px;
  border: none;
  background: transparent;
  color: var(--lx-text-muted, #888);
  font-size: 12px;
  cursor: pointer;
}

.fwd-list {
  flex: 1;
  overflow: auto;
  padding: 0 8px 12px;
}

.fwd-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: inherit;
}

.fwd-row:hover {
  background: var(--lx-bg-hover, rgba(0, 0, 0, 0.04));
}

.chk {
  color: #c0c4cc;
  flex-shrink: 0;
}

.chk.on {
  color: var(--lx-accent, #12b7f5);
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
  margin: 24px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--lx-text-muted, #999);
}

.fwd-right {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px;
  background: var(--lx-bg-card, #fff);
}

.fwd-send-label {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.fwd-selected {
  flex: 1;
  overflow: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
  min-height: 120px;
}

.fwd-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px 4px 4px;
  border-radius: 999px;
  background: var(--lx-bg-panel, #f5f6f8);
  max-width: 100%;
}

.fwd-chip-name {
  font-size: 12px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fwd-chip-x {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--lx-text-muted, #999);
  font-size: 16px;
  line-height: 1;
  padding: 0 2px;
}

.fwd-leave {
  margin-top: 12px;
}

.fwd-emoji {
  color: var(--lx-text-muted, #999);
}

.fwd-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}

.fwd-btn {
  min-width: 76px;
  height: 34px;
  padding: 0 14px;
  border-radius: 6px;
  border: 1px solid var(--lx-border-light, #dcdfe6);
  background: var(--lx-bg-card, #fff);
  color: var(--lx-text-body, #1f2329);
  cursor: pointer;
  font-size: 13px;
}

.fwd-btn.primary {
  border-color: transparent;
  background: var(--lx-accent, #12b7f5);
  color: #fff;
}

.fwd-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
