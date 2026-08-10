<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 创建多人会议弹窗：标题、音视频、可选密码、人数上限、等候室。
 */
import { reactive, watch } from 'vue'
import { useI18n } from '../../i18n'

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
              :class="{ active: form.type === 'video' }"
              @click="form.type = 'video'"
            >
              {{ t('conference.typeVideo') }}
            </button>
            <button
              type="button"
              class="seg-btn"
              :class="{ active: form.type === 'voice' }"
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
          <button type="button" class="btn ghost" @click="emit('cancel')">
            {{ t('common.cancel') }}
          </button>
          <button type="button" class="btn primary" @click="onConfirm">
            {{ t('conference.create') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.create-mask {
  position: fixed;
  inset: 0;
  z-index: 12000;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
}
.create-card {
  width: min(400px, 92vw);
  background: #2b2b2b;
  border-radius: 12px;
  padding: 22px;
  color: #f0f0f0;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
}
.create-card h3 {
  margin: 0 0 16px;
  font-size: 17px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}
.label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
}
.input {
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: #1f1f1f;
  color: #f0f0f0;
  padding: 9px 12px;
  font-size: 14px;
  outline: none;
}
.input:focus {
  border-color: #006eff;
}
.hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}
.lobby-hint {
  margin: -8px 0 14px;
}
.check-row {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  cursor: pointer;
}
.check-row input {
  width: 16px;
  height: 16px;
  accent-color: #006eff;
}
.seg {
  display: flex;
  gap: 8px;
}
.seg-btn {
  flex: 1;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  padding: 8px 10px;
  background: #1f1f1f;
  color: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  font-size: 13px;
}
.seg-btn.active {
  background: rgba(0, 110, 255, 0.25);
  border-color: #006eff;
  color: #fff;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}
.btn {
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
}
.btn.ghost {
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
}
.btn.primary {
  background: #006eff;
  color: #fff;
}
</style>
