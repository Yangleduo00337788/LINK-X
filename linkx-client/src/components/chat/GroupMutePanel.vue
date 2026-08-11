<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群聊禁言管理面板。
 * <p>
 * 统一入口内包含：全体禁言开关、定时禁言 CRUD、指定成员禁言。
 * </p>
 */
import { ref, computed, watch } from 'vue'
import { NSwitch, useMessage, useDialog } from 'naive-ui'
import Avatar from '../Avatar.vue'
import { LxButton, LxIconButton } from '../ui'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'

const props = defineProps<{
  sessionId: string
  isOwner: boolean
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

const { t } = useI18n()
const message = useMessage()
const dialog = useDialog()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()

const muteAllSaving = ref(false)
const muteSaving = ref(false)
const scheduleEditing = ref(false)
const scheduleStart = ref('')
const scheduleEnd = ref('')
const memberPageOpen = ref(false)

const muteState = computed(() => groupMetaStore.muteStateFor(props.sessionId))

const members = computed(() => groupMetaStore.membersFor(props.sessionId))

const activeSchedule = computed(() => {
  const end = muteState.value.muteAllEnd
  const start = muteState.value.muteAllStart
  if (!end || end <= Date.now()) return null
  return { start: start || undefined, end }
})

const scheduleStatusText = computed(() => {
  const s = activeSchedule.value
  if (!s) return ''
  const endText = new Date(s.end).toLocaleString()
  if (s.start && s.start > Date.now()) {
    return t('modals.scheduleMuteUpcoming', {
      start: new Date(s.start).toLocaleString(),
      end: endText
    })
  }
  return t('modals.scheduleMuteUntil', { end: endText })
})

/** 成员禁言页：展示全部群成员 */
const allMembers = computed(() => members.value)

const meId = computed(() => appStore.userProfile.userId)

function canMuteMember(m: { id: string; role?: string }): boolean {
  if (!meId.value || m.id === meId.value) return false
  if (m.role === 'owner') return false
  if (!props.isOwner && m.role === 'admin') return false
  return true
}

function memberMuteDisabledReason(m: { id: string; role?: string }): string {
  if (m.id === meId.value) return t('modals.muteMemberSelf')
  if (m.role === 'owner') return t('modals.muteMemberOwner')
  if (!props.isOwner && m.role === 'admin') return t('modals.muteMemberAdmin')
  return ''
}

watch(
  () => props.sessionId,
  id => {
    if (id) void groupMetaStore.fetchMembers(id, true)
    scheduleEditing.value = false
    memberPageOpen.value = false
  },
  { immediate: true }
)

function apiErrorMessage(e: unknown, fallback: string): string {
  const ax = e as { response?: { data?: { message?: string } }; message?: string }
  return ax.response?.data?.message || ax.message || fallback
}

function toLocalInputValue(ms?: number): string {
  if (!ms) return ''
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fromLocalInputValue(v: string): number {
  return new Date(v).getTime()
}

async function setMuteAll(enabled: boolean) {
  if (!props.sessionId || muteAllSaving.value) return
  muteAllSaving.value = true
  try {
    await groupMetaStore.setMuteAll(props.sessionId, { enabled })
    message.success(enabled ? t('modals.muteAllOn') : t('modals.muteAllOff'))
  } catch (e) {
    message.error(apiErrorMessage(e, t('modals.muteAllFail')))
  } finally {
    muteAllSaving.value = false
  }
}

function startCreateSchedule() {
  scheduleStart.value = toLocalInputValue(Date.now() + 5 * 60 * 1000)
  scheduleEnd.value = toLocalInputValue(Date.now() + 65 * 60 * 1000)
  scheduleEditing.value = true
}

function startEditSchedule() {
  const s = activeSchedule.value
  scheduleStart.value = toLocalInputValue(s?.start) || toLocalInputValue(Date.now() + 5 * 60 * 1000)
  scheduleEnd.value = toLocalInputValue(s?.end) || toLocalInputValue(Date.now() + 65 * 60 * 1000)
  scheduleEditing.value = true
}

function cancelScheduleEdit() {
  scheduleEditing.value = false
}

async function saveSchedule() {
  if (!props.sessionId || muteSaving.value) return
  if (!scheduleStart.value || !scheduleEnd.value) {
    message.warning(t('modals.scheduleMuteNeedTime'))
    return
  }
  const startTime = fromLocalInputValue(scheduleStart.value)
  const endTime = fromLocalInputValue(scheduleEnd.value)
  if (!Number.isFinite(startTime) || !Number.isFinite(endTime)) {
    message.warning(t('modals.scheduleMuteNeedTime'))
    return
  }
  if (endTime <= startTime) {
    message.warning(t('modals.scheduleMuteEndAfterStart'))
    return
  }
  if (endTime <= Date.now()) {
    message.warning(t('modals.scheduleMuteEndInFuture'))
    return
  }
  muteSaving.value = true
  try {
    await groupMetaStore.setMuteAll(props.sessionId, { startTime, endTime })
    message.success(t('modals.scheduleMuteOk'))
    scheduleEditing.value = false
  } catch (e) {
    message.error(apiErrorMessage(e, t('modals.scheduleMuteFail')))
  } finally {
    muteSaving.value = false
  }
}

function clearSchedule() {
  if (!props.sessionId || muteSaving.value) return
  dialog.warning({
    title: t('modals.scheduleMuteClear'),
    content: t('modals.scheduleMuteClearConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      muteSaving.value = true
      try {
        await groupMetaStore.setMuteAll(props.sessionId, { clearSchedule: true })
        message.success(t('modals.scheduleMuteClearOk'))
        scheduleEditing.value = false
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.scheduleMuteFail')))
      } finally {
        muteSaving.value = false
      }
    }
  })
}

async function openMemberPage() {
  memberPageOpen.value = true
  await groupMetaStore.fetchMembers(props.sessionId, true)
}

function closeMemberPage() {
  memberPageOpen.value = false
}

function toggleMemberMute(memberId: string, memberName: string, muted: boolean) {
  if (!props.sessionId || muteSaving.value) return
  dialog.warning({
    title: muted ? t('modals.unmuteMember') : t('modals.muteMember'),
    content: muted
      ? t('modals.unmuteMemberConfirm', { name: memberName })
      : t('modals.muteMemberConfirm', { name: memberName }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      muteSaving.value = true
      try {
        await groupMetaStore.setMemberMute(props.sessionId, memberId, !muted)
        message.success(muted ? t('modals.unmuteMemberOk') : t('modals.muteMemberOk'))
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.muteMemberFail')))
      } finally {
        muteSaving.value = false
      }
    }
  })
}
</script>

<template>
  <div class="mute-panel">
    <div class="mute-head">
      <LxIconButton class="mute-back" :title="t('common.back')" @click="emit('back')">‹</LxIconButton>
      <h3>{{ t('modals.groupMute') }}</h3>
    </div>

    <div class="mute-scroll">
      <!-- 全体禁言 -->
      <section class="mute-section">
        <h4 class="section-title">{{ t('modals.muteAll') }}</h4>
        <div class="switch-row">
          <span class="switch-desc">{{ t('modals.muteAllHint') }}</span>
          <n-switch
            :value="!!muteState.muteAll"
            :disabled="muteAllSaving"
            size="small"
            @update:value="setMuteAll"
          />
        </div>
      </section>

      <!-- 定时禁言 CRUD -->
      <section class="mute-section">
        <h4 class="section-title">{{ t('modals.scheduleMute') }}</h4>
        <p class="section-hint">{{ t('modals.scheduleMuteHint') }}</p>

        <template v-if="!scheduleEditing">
          <div v-if="activeSchedule" class="schedule-card">
            <p class="schedule-text">{{ scheduleStatusText }}</p>
            <div class="schedule-ops">
              <LxButton variant="link-md" :disabled="muteSaving" @click="startEditSchedule">
                {{ t('common.edit') }}
              </LxButton>
              <LxButton variant="link-danger" :disabled="muteSaving" @click="clearSchedule">
                {{ t('modals.scheduleMuteClear') }}
              </LxButton>
            </div>
          </div>
          <LxButton v-else variant="add-dashed" @click="startCreateSchedule">
            {{ t('modals.scheduleMuteAdd') }}
          </LxButton>
        </template>

        <div v-else class="schedule-form">
          <label class="field-label">{{ t('modals.scheduleMuteStart') }}</label>
          <input v-model="scheduleStart" type="datetime-local" class="field-input" />
          <label class="field-label">{{ t('modals.scheduleMuteEnd') }}</label>
          <input v-model="scheduleEnd" type="datetime-local" class="field-input" />
          <div class="form-actions">
            <LxButton variant="ghost" :disabled="muteSaving" @click="cancelScheduleEdit">
              {{ t('common.cancel') }}
            </LxButton>
            <LxButton variant="primary" :disabled="muteSaving" @click="saveSchedule">
              {{ t('common.save') }}
            </LxButton>
          </div>
        </div>
      </section>

      <!-- 指定成员禁言：入口按钮 -->
      <section class="mute-section">
        <h4 class="section-title">{{ t('modals.muteMembers') }}</h4>
        <p class="section-hint">{{ t('modals.muteMembersHint') }}</p>
        <LxButton variant="add-dashed" @click="openMemberPage">
          {{ t('modals.muteMembersOpen') }}
        </LxButton>
      </section>
    </div>

    <!-- 指定成员禁言子页：全部群成员 -->
    <div v-if="memberPageOpen" class="member-page">
      <div class="mute-head">
        <LxIconButton class="mute-back" :title="t('common.back')" @click="closeMemberPage">‹</LxIconButton>
        <h3>{{ t('modals.muteMembers') }}</h3>
      </div>
      <div class="mute-scroll">
        <p class="section-hint">{{ t('modals.muteMembersPageHint') }}</p>
        <div v-if="!allMembers.length" class="empty">{{ t('modals.muteMembersEmpty') }}</div>
        <div v-else class="member-list">
          <div v-for="m in allMembers" :key="m.id" class="member-row">
            <Avatar :text="m.avatarText" :color="m.avatarColor" :image-url="m.avatarUrl" :size="36" />
            <div class="member-meta">
              <span class="member-name">{{ m.name }}</span>
              <span v-if="m.badge" class="member-badge">{{ m.badge }}</span>
            </div>
            <span v-if="m.muted" class="muted-badge">{{ t('modals.mutedBadge') }}</span>
            <LxButton
              v-if="canMuteMember(m)"
              variant="sm"
              class="role-action"
              :class="{ danger: !m.muted }"
              :disabled="muteSaving"
              @click="toggleMemberMute(m.id, m.name, !!m.muted)"
            >
              {{ m.muted ? t('modals.unmuteMember') : t('modals.muteMember') }}
            </LxButton>
            <span v-else class="muted-hint">{{ memberMuteDisabledReason(m) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mute-panel {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-elevated, var(--lx-bg-card));
  z-index: var(--lx-z-raised-2);
}

.mute-head {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  padding: var(--lx-space-xl) var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border);
  flex-shrink: 0;
}

.mute-head h3 {
  margin: 0;
  font-size: var(--lx-font-xl);
  font-weight: 600;
}

.mute-back {
  font-size: var(--lx-font-5xl);
  width: 28px;
  height: 28px;
  color: var(--lx-text-primary);
}

.mute-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-lg) var(--lx-space-2xl) var(--lx-space-4xl);
}

.mute-section {
  margin-bottom: var(--lx-space-3xl-plus);
}

.section-title {
  margin: 0 0 var(--lx-space);
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-primary);
}

.section-hint {
  margin: 0 0 var(--lx-space-md);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading);
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-lg);
}

.switch-desc {
  font-size: var(--lx-font-md);
  color: var(--lx-text-secondary);
  line-height: var(--lx-leading);
}

.schedule-card {
  padding: var(--lx-space-lg);
  border-radius: var(--lx-radius-sm);
  background: var(--lx-bg-muted, rgba(0, 0, 0, 0.04));
}

.schedule-text {
  margin: 0 0 var(--lx-space-md);
  font-size: var(--lx-font-md);
  color: var(--lx-text-primary);
  line-height: var(--lx-leading);
}

.schedule-ops {
  display: flex;
  gap: var(--lx-space-xl);
}

.schedule-form {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-sm);
}

.field-label {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  margin-top: var(--lx-space-xs);
}

.field-input {
  width: 100%;
  padding: var(--lx-space) var(--lx-space-md);
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius-xs);
  font-size: var(--lx-font-md);
  background: var(--lx-bg-card);
  color: var(--lx-text-primary);
  box-sizing: border-box;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lx-space);
  margin-top: var(--lx-space-md);
}

.empty {
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
  padding: var(--lx-space) 0;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
}

.member-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  padding: var(--lx-space) 0;
}

.member-name {
  flex: 1;
  min-width: 0;
  font-size: var(--lx-font);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-2xs);
}

.member-meta .member-name {
  flex: none;
}

.member-badge {
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
}

.muted-badge {
  font-size: var(--lx-font-xs);
  color: var(--lx-danger, var(--lx-danger));
  flex-shrink: 0;
}

.muted-hint {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.member-page {
  position: absolute;
  inset: 0;
  z-index: var(--lx-z-raised-3);
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-elevated, var(--lx-bg-card));
}

.role-action {
  border: none !important;
  background: transparent !important;
  color: var(--lx-accent, var(--lx-accent));
  flex-shrink: 0;
  padding: var(--lx-space-xs) 0;
  height: auto;
}
.role-action.danger {
  color: var(--lx-danger, var(--lx-danger));
}
</style>
