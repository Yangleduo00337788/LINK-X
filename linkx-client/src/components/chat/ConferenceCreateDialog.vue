<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 创建多人会议弹窗：标题、音视频、可选密码、人数上限、等候室。
 */
import { reactive, watch } from 'vue'
import { useI18n } from '../../i18n'
import { LxButton } from '../ui'

export type ConferenceCreatePayload = {
  title: string
  type: 'voice' | 'video'
  /** call=电话 meeting=会议；创建弹窗固定为 meeting */
  scene?: 'call' | 'meeting'
  password?: string
  maxParticipants: number
  lobbyEnabled: boolean
}

const props = defineProps<{
  show: boolean
  defaultTitle?: string
}>()

const emit = defineEmits<{
  confirm: [payload: ConferenceCreatePayload]
  cancel: []
}>()

const { t } = useI18n()

const form = reactive({
  title: '',
  type: 'video' as 'voice' | 'video',
  password: '',
  usePassword: false,
  maxParticipants: 9,
  lobbyEnabled: false
})

watch(
  () => props.show,
  open => {
    if (!open) return
    form.title = props.defaultTitle || ''
    form.type = 'video'
    form.password = ''
    form.usePassword = false
    form.maxParticipants = 9
    form.lobbyEnabled = false
  }
)

function clampMax() {
  let n = Number(form.maxParticipants) || 2
  if (n < 2) n = 2
  if (n > 16) n = 16
  form.maxParticipants = n
}

function onConfirm() {
  clampMax()
  const title = form.title.trim() || t('conference.defaultTitle')
  emit('confirm', {
    title,
    type: form.type,
    scene: 'meeting',
    password: form.usePassword && form.password.trim() ? form.password.trim() : undefined,
    maxParticipants: form.maxParticipants,
    lobbyEnabled: form.lobbyEnabled
  })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="show" class="create-mask" @click.self="emit('cancel')">
      <div class="create-card" role="dialog" aria-modal="true">
        <h3>{{ t('conference.createTitle') }}</h3>

        <label class="field">
          <span class="label">{{ t('conference.meetingTitle') }}</span>
          <input
            v-model="form.title"
            type="text"
            class="input"
            :placeholder="t('conference.titlePlaceholder')"
            maxlength="64"
          />
        </label>

        <div class="field">
          <span class="label">{{ t('conference.mediaType') }}</span>
          <div class="seg">
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': form.type === 'video' }"
              @click="form.type = 'video'"
            >
              {{ t('conference.typeVideo') }}
            </button>
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': form.type === 'voice' }"
              @click="form.type = 'voice'"
            >
              {{ t('conference.typeVoice') }}
            </button>
          </div>
        </div>

        <label class="field check-row">
          <input v-model="form.usePassword" type="checkbox" />
          <span>{{ t('conference.enablePassword') }}</span>
        </label>
        <label v-if="form.usePassword" class="field">
          <span class="label">{{ t('conference.password') }}</span>
          <input
            v-model="form.password"
            type="password"
            class="input"
            :placeholder="t('conference.passwordPlaceholder')"
            maxlength="32"
            autocomplete="new-password"
          />
        </label>

        <label class="field">
          <span class="label">{{ t('conference.maxParticipants') }}</span>
          <input
            v-model.number="form.maxParticipants"
            type="number"
            class="input"
            min="2"
            max="16"
            @blur="clampMax"
          />
          <span class="hint">{{ t('conference.maxParticipantsHint') }}</span>
        </label>

        <label class="field check-row">
          <input v-model="form.lobbyEnabled" type="checkbox" />
          <span>{{ t('conference.lobbyEnabled') }}</span>
        </label>
        <p v-if="form.lobbyEnabled" class="hint lobby-hint">{{ t('conference.lobbyHint') }}</p>

        <div class="actions">
          <LxButton variant="modal" @click="emit('cancel')">
            {{ t('common.cancel') }}
          </LxButton>
          <LxButton variant="modal-primary" @click="onConfirm">
            {{ t('conference.create') }}
          </LxButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.create-mask {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-call);
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-4xl);
}

.create-card {
  width: min(400px, 92vw);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  padding: var(--lx-space-3xl);
  color: var(--lx-text-body);
  box-shadow: var(--lx-shadow-modal);
}

.create-card h3 {
  margin: 0 0 var(--lx-space-2xl);
  font-size: var(--lx-font-2xl);
  font-weight: 600;
  color: var(--lx-text-body);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-sm);
  margin-bottom: var(--lx-space-xl);
}

.label {
  font-size: var(--lx-font-md);
  color: var(--lx-text-secondary);
}

.input {
  border: 1px solid var(--lx-border-strong);
  border-radius: var(--lx-radius);
  background: var(--lx-bg-input);
  color: var(--lx-text-body);
  padding: var(--lx-space) var(--lx-space-lg);
  font-size: var(--lx-font);
  outline: none;
  box-sizing: border-box;
}

.input:focus {
  border-color: var(--lx-accent);
}

.hint {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.lobby-hint {
  margin: -var(--lx-space) 0 var(--lx-space-xl);
}

.check-row {
  flex-direction: row;
  align-items: center;
  gap: var(--lx-space);
  font-size: var(--lx-font);
  color: var(--lx-text-body);
  cursor: pointer;
}

.check-row input {
  width: 16px;
  height: 16px;
  accent-color: var(--lx-accent);
}

.seg {
  display: flex;
  gap: var(--lx-space);
}

.seg-btn {
  flex: 1;
  border: 1px solid var(--lx-border-strong);
  border-radius: var(--lx-radius);
  padding: var(--lx-space) var(--lx-space-md);
  background: var(--lx-bg-input);
  color: var(--lx-text-secondary);
  cursor: pointer;
  font-size: var(--lx-font-md);
  transition:
    background var(--lx-duration) ease,
    border-color var(--lx-duration) ease,
    color var(--lx-duration) ease;
}

.seg-btn:hover {
  background: var(--lx-bg-hover);
}

.seg-btn.is-active {
  background: var(--lx-accent-soft);
  border-color: var(--lx-accent);
  color: var(--lx-accent-deep);
  font-weight: 500;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lx-space-md);
  margin-top: var(--lx-space);
}
</style>
