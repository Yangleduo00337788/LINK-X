<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 新建 / 编辑日程独立弹窗。
 */
import { ref, watch, computed } from 'vue'
import {
  NInput,
  NDatePicker,
  NTimePicker,
  NIcon,
  NSwitch,
  useMessage
} from 'naive-ui'
import { CloseOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useCalendarStore } from '../../stores/calendar'
import type { CalendarEvent } from '../../stores/calendar'
import { useI18n } from '../../i18n'
import { LxButton, LxIconButton, LxModal } from '../ui'
import { lxEventColors } from '../../theme/vars'

const EVENT_COLORS = lxEventColors

const props = defineProps<{
  show: boolean
  editingEvent?: CalendarEvent | null
  defaultDate: string
}>()

const emit = defineEmits<{
  'update:show': [boolean]
  saved: []
}>()

const { t } = useI18n()
const message = useMessage()
const calendarStore = useCalendarStore()
const { addEvent, updateEvent, setRemindEnabled, isRemindOn } = calendarStore
const { selectedDateKey } = storeToRefs(calendarStore)

const formTitle = ref('')
const formDateTs = ref<number | null>(null)
const formStartTs = ref<number | null>(null)
const formEndTs = ref<number | null>(null)
const remindEnabled = ref(true)
const saving = ref(false)

const isEdit = computed(() => Boolean(props.editingEvent?.id))
const modalTitle = computed(() =>
  isEdit.value ? t('calendar.eventModalTitleEdit') : t('calendar.eventModalTitleAdd')
)

function dateKeyFromTs(ts: number) {
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function tsFromDateKey(key: string) {
  const [y, m, day] = key.split('-').map(Number)
  return new Date(y, m - 1, day).getTime()
}

function normalizeTime(raw: string) {
  const time = raw.trim()
  if (!time) return undefined
  return time.replace(/^(\d):/, '0$1:').replace(/:(\d)$/, ':0$1')
}

function parseTimeParts(raw: string) {
  const normalized = normalizeTime(raw)
  if (!normalized) return null
  const [hh, mm] = normalized.split(':').map(Number)
  if (Number.isNaN(hh) || Number.isNaN(mm)) return null
  return { hh, mm }
}

function parseHm(hm: string): number | null {
  const normalized = normalizeTime(hm)
  if (!normalized) return null
  const [hh, mm] = normalized.split(':').map(Number)
  if (Number.isNaN(hh) || Number.isNaN(mm) || hh > 23 || mm > 59) return null
  const base = formDateTs.value ?? Date.now()
  const d = new Date(base)
  d.setHours(hh, mm, 0, 0)
  return d.getTime()
}

function formatHm(ts: number | null): string {
  if (ts == null) return ''
  const d = new Date(ts)
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

function defaultTsOnFormDate(hh: number, mm: number) {
  const base = formDateTs.value ?? Date.now()
  const d = new Date(base)
  d.setHours(hh, mm, 0, 0)
  return d.getTime()
}

function defaultEndTimeFromStartTs(startTs: number) {
  return startTs + 60 * 60 * 1000
}

function compareTime(startRaw: string, endRaw: string) {
  const start = parseTimeParts(startRaw)
  const end = parseTimeParts(endRaw)
  if (!start || !end) return 0
  const startVal = start.hh * 60 + start.mm
  const endVal = end.hh * 60 + end.mm
  return endVal - startVal
}

function syncForm() {
  if (props.editingEvent) {
    formTitle.value = props.editingEvent.title
    formDateTs.value = tsFromDateKey(props.editingEvent.date)
    formStartTs.value = props.editingEvent.time ? parseHm(props.editingEvent.time) : null
    if (formStartTs.value) {
      const endRaw =
        props.editingEvent.endTime ||
        defaultEndTimeFromStartTs(formStartTs.value)
      formEndTs.value = parseHm(String(endRaw)) ?? defaultEndTimeFromStartTs(formStartTs.value)
    } else {
      formEndTs.value = null
    }
    remindEnabled.value = isRemindOn(props.editingEvent.id)
    return
  }
  formTitle.value = ''
  const dateKey = props.defaultDate || selectedDateKey.value
  formDateTs.value = tsFromDateKey(dateKey)
  formStartTs.value = defaultTsOnFormDate(9, 0)
  formEndTs.value = defaultTsOnFormDate(10, 0)
  remindEnabled.value = true
}

function onStartTimeChange(v: number | null) {
  formStartTs.value = v
  if (!v) {
    formEndTs.value = null
    remindEnabled.value = false
    return
  }
  if (!formEndTs.value || compareTime(formatHm(v), formatHm(formEndTs.value)) >= 0) {
    formEndTs.value = defaultEndTimeFromStartTs(v)
  }
}

function onEndTimeChange(v: number | null) {
  formEndTs.value = v
}

watch(
  () => props.show,
  open => {
    if (open) syncForm()
  }
)

function closeModal() {
  emit('update:show', false)
}

async function saveEvent() {
  const title = formTitle.value.trim()
  if (!title) {
    message.warning(t('calendar.titleRequired'))
    return
  }
  if (!formDateTs.value) {
    message.warning(t('calendar.dateRequired'))
    return
  }

  const date = dateKeyFromTs(formDateTs.value)
  const normalizedTime = formStartTs.value ? formatHm(formStartTs.value) : undefined
  const normalizedEndTime =
    formStartTs.value && formEndTs.value ? formatHm(formEndTs.value) : undefined

  if (normalizedTime && normalizedEndTime && compareTime(normalizedTime, normalizedEndTime) <= 0) {
    message.warning(t('calendar.endTimeInvalid'))
    return
  }
  if (normalizedTime && !normalizedEndTime) {
    message.warning(t('calendar.endTimeRequired'))
    return
  }

  saving.value = true
  try {
    const wantRemind = remindEnabled.value && Boolean(normalizedTime)
    if (isEdit.value && props.editingEvent) {
      const ok = await updateEvent(props.editingEvent.id, {
        title,
        date,
        time: normalizedTime,
        endTime: normalizedEndTime,
        color: undefined
      })
      if (ok) {
        const remindResult = await setRemindEnabled(props.editingEvent.id, wantRemind)
        if (wantRemind && remindResult === 'expired') {
          message.warning(t('calendar.remindExpired'))
        }
        message.success(t('calendar.updated'))
        emit('saved')
        closeModal()
      } else {
        message.error(t('calendar.updateFail'))
      }
    } else {
      const color = EVENT_COLORS[Math.floor(Math.random() * EVENT_COLORS.length)]
      const id = await addEvent({
        title,
        date,
        time: normalizedTime,
        endTime: normalizedEndTime,
        color
      })
      if (id) {
        if (wantRemind) {
          const remindResult = await setRemindEnabled(id, true)
          if (remindResult === 'expired') {
            message.warning(t('calendar.remindExpired'))
          }
        }
        message.success(t('calendar.added'))
        emit('saved')
        closeModal()
      } else {
        message.error(t('calendar.addFail'))
      }
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <LxModal
    :show="show"
    class="calendar-event-modal"
    preset="card"
    to="body"
    :bordered="false"
    :show-icon="false"
    :closable="false"
    :mask-closable="!saving"
    :z-index="10002"
    style="width: 440px; max-width: 94vw; border-radius: var(--lx-radius-lg); padding: 0;"
    @update:show="emit('update:show', $event)"
  >
    <div class="modal-shell">
      <header class="modal-header">
        <h2 class="modal-title">{{ modalTitle }}</h2>
        <LxIconButton
          variant="close"
          class="close-btn"
          :title="t('modals.close')"
          :disabled="saving"
          @click="closeModal"
        >
          <n-icon :component="CloseOutline" :size="20" />
        </LxIconButton>
      </header>

      <div class="form-body">
        <div class="form-row">
          <label class="form-label">{{ t('calendar.titlePh') }}</label>
          <n-input
            v-model:value="formTitle"
            data-lm-calendar-event-title
            :placeholder="t('calendar.titlePh')"
            maxlength="100"
            show-count
            class="form-control"
          />
        </div>

        <div class="form-row">
          <label class="form-label">{{ t('calendar.dateLabel') }}</label>
          <n-date-picker
            v-model:value="formDateTs"
            type="date"
            clearable
            class="form-control"
            to="body"
          />
        </div>

        <div class="form-row">
          <label class="form-label">{{ t('calendar.timeRangeLabel') }}</label>
          <div class="time-range">
            <n-time-picker
              :value="formStartTs"
              format="HH:mm"
              clearable
              class="time-picker"
              to="body"
              :actions="null"
              :placeholder="t('calendar.timePh')"
              @update:value="onStartTimeChange"
            />
            <span class="time-sep">{{ t('notifications.quietTo') }}</span>
            <n-time-picker
              :value="formEndTs"
              format="HH:mm"
              :clearable="false"
              :disabled="!formStartTs"
              class="time-picker"
              to="body"
              :actions="null"
              :placeholder="t('calendar.endTimePh')"
              @update:value="onEndTimeChange"
            />
          </div>
          <p class="form-hint">{{ t('calendar.allDayHint') }}</p>
        </div>

        <div v-if="formStartTs" class="form-row remind-row">
          <div class="remind-label-wrap">
            <span class="form-label">{{ t('calendar.remindSwitch') }}</span>
            <span class="form-hint inline-hint">{{ t('calendar.remindSwitchDesc') }}</span>
          </div>
          <n-switch v-model:value="remindEnabled" size="small" />
        </div>
      </div>

      <footer class="modal-footer">
        <LxButton variant="modal" :disabled="saving" @click="closeModal">{{ t('common.cancel') }}</LxButton>
        <LxButton variant="modal-primary" data-lm-calendar-event-save :disabled="saving" @click="saveEvent">
          {{ t('common.save') }}
        </LxButton>
      </footer>
    </div>
  </LxModal>
</template>

<style scoped>
.modal-shell {
  padding: 0 var(--lx-space-xs) var(--lx-space-xs);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lx-space-xs) var(--lx-space-xs) var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border-light);
}

.modal-title {
  margin: 0;
  font-size: var(--lx-font-2xl);
  font-weight: 600;
  color: var(--lx-text-body);
}

.close-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius-sm);
  color: var(--lx-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover:not(:disabled) {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.close-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.form-body {
  padding: var(--lx-space-3xl) var(--lx-space-xs) var(--lx-space);
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-2xl);
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space);
}

.form-label {
  font-size: var(--lx-font-md);
  font-weight: 500;
  color: var(--lx-text-body);
}

.form-control {
  width: 100%;
}

.form-hint {
  margin: 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading);
}

.time-range {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
}

.time-picker {
  flex: 1;
  min-width: 0;
}

.time-sep {
  flex-shrink: 0;
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}

.remind-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-lg);
}

.remind-label-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
  min-width: 0;
}

.inline-hint {
  margin: 0;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--lx-space);
  padding: var(--lx-space-lg) var(--lx-space-xs) 0;
  border-top: 1px solid var(--lx-border-light);
}
</style>
