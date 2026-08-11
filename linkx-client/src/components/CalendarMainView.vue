<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 日历主视图 — 按设计稿：顶栏 + 月网格 + 底部日程卡片列表
 */
import { ref, computed, watch, onMounted } from 'vue'
import { NIcon, NDropdown, useMessage, useDialog } from 'naive-ui'
import {
  CalendarOutline,
  ChevronBackOutline,
  ChevronForwardOutline,
  AddOutline,
  EllipsisHorizontal,
  NotificationsOutline,
  ChevronDownOutline,
  LocationOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useCalendarStore } from '../stores/calendar'
import type { CalendarEvent } from '../stores/calendar'
import CalendarEventModal from './calendar/CalendarEventModal.vue'
import { useI18n } from '../i18n'
import { LxButton, LxIconButton } from './ui'
import { lxEventColors } from '../theme/vars'

const message = useMessage()
const dialog = useDialog()
const { t } = useI18n()
const calendarStore = useCalendarStore()
const { selectedDate, events, eventsOnSelected, selectedDateKey, initialized } = storeToRefs(calendarStore)
const { setSelectedDate, removeEvent, fetchEvents, toggleRemind, startReminderWatch, isRemindOn } =
  calendarStore

/** 与设计稿一致：周日为一周起始 */
const WEEK_LABELS = computed(() => t('calendar.weekdays').split(','))
const MAX_CELL_EVENTS = 2
const EVENT_COLORS = lxEventColors

const panelYear = ref(new Date().getFullYear())
const panelMonth = ref(new Date().getMonth())

const showEventModal = ref(false)
const editingEvent = ref<CalendarEvent | null>(null)
const showWeekList = ref(false)

const panelTitle = computed(() =>
  t('calendar.yearMonth', { y: panelYear.value, m: panelMonth.value + 1 })
)

const viewMenuOptions = computed(() => [
  { label: t('calendar.monthView'), key: 'month' }
])

const selectedHeading = computed(() => {
  const d = new Date(selectedDate.value)
  const week = WEEK_LABELS.value[d.getDay()]
  const today = new Date()
  const isToday =
    d.getFullYear() === today.getFullYear() &&
    d.getMonth() === today.getMonth() &&
    d.getDate() === today.getDate()
  const datePart = t('calendar.monthDayWeek', {
    m: d.getMonth() + 1,
    d: d.getDate(),
    w: week
  })
  return isToday ? `${t('calendar.today')} · ${datePart}` : datePart
})

const eventsByDate = computed(() => {
  const map: Record<string, CalendarEvent[]> = {}
  for (const e of events.value) {
    ;(map[e.date] ||= []).push(e)
  }
  for (const list of Object.values(map)) {
    list.sort((a, b) => (a.time || '').localeCompare(b.time || ''))
  }
  return map
})

/** 本周日程（周一至周日） */
const weekEvents = computed(() => {
  const d = new Date(selectedDate.value)
  const day = d.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  const monday = new Date(d)
  monday.setHours(0, 0, 0, 0)
  monday.setDate(d.getDate() + mondayOffset)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  const from = dateKey(monday)
  const to = dateKey(sunday)
  return [...events.value]
    .filter(e => e.date >= from && e.date <= to)
    .sort((a, b) => a.date.localeCompare(b.date) || (a.time || '').localeCompare(b.time || ''))
})

interface MonthCell {
  key: string
  year: number
  month: number
  date: number
  inMonth: boolean
  isToday: boolean
  isSelected: boolean
  events: CalendarEvent[]
}

const monthCells = computed<MonthCell[]>(() => {
  const y = panelYear.value
  const m = panelMonth.value
  const first = new Date(y, m, 1)
  const startPad = first.getDay() // 周日=0
  const daysInMonth = new Date(y, m + 1, 0).getDate()
  const prevDays = new Date(y, m, 0).getDate()
  const todayKey = dateKey(new Date())
  const selectedKey = selectedDateKey.value
  const cells: MonthCell[] = []

  for (let i = 0; i < 42; i++) {
    let cy = y
    let cm = m
    let cd: number
    let inMonth = true

    if (i < startPad) {
      cd = prevDays - startPad + i + 1
      cm = m - 1
      if (cm < 0) {
        cm = 11
        cy = y - 1
      }
      inMonth = false
    } else if (i >= startPad + daysInMonth) {
      cd = i - startPad - daysInMonth + 1
      cm = m + 1
      if (cm > 11) {
        cm = 0
        cy = y + 1
      }
      inMonth = false
    } else {
      cd = i - startPad + 1
    }

    const key = `${cy}-${String(cm + 1).padStart(2, '0')}-${String(cd).padStart(2, '0')}`
    cells.push({
      key,
      year: cy,
      month: cm + 1,
      date: cd,
      inMonth,
      isToday: key === todayKey,
      isSelected: key === selectedKey,
      events: eventsByDate.value[key] || []
    })
  }
  return cells
})

function dateKey(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function eventColor(ev: CalendarEvent, idx = 0) {
  if (ev.color && !ev.color.includes('var(')) return ev.color
  let h = 0
  for (let i = 0; i < ev.id.length; i++) h = (h * 31 + ev.id.charCodeAt(i)) >>> 0
  return EVENT_COLORS[(h + idx) % EVENT_COLORS.length]
}

/** 根据开始/结束时间推断状态徽标 */
function eventStatus(ev: CalendarEvent): { text: string; tone: 'active' | 'soon' | 'done' } | null {
  if (!ev.time || ev.date !== selectedDateKey.value) return null
  const [hh, mm] = ev.time.split(':').map(Number)
  if (Number.isNaN(hh)) return null
  const start = new Date(selectedDate.value)
  start.setHours(hh, mm || 0, 0, 0)
  let end: Date
  if (ev.endTime) {
    const [eh, em] = ev.endTime.split(':').map(Number)
    end = new Date(selectedDate.value)
    end.setHours(eh, em || 0, 0, 0)
    if (end.getTime() <= start.getTime()) {
      end = new Date(start.getTime() + 60 * 60 * 1000)
    }
  } else {
    end = new Date(start.getTime() + 60 * 60 * 1000)
  }
  const now = Date.now()
  if (now >= start.getTime() && now < end.getTime()) return { text: t('calendar.ongoing'), tone: 'active' }
  if (now < start.getTime() && start.getTime() - now <= 2 * 60 * 60 * 1000) {
    return { text: t('calendar.upcoming'), tone: 'soon' }
  }
  if (now >= end.getTime()) return { text: t('calendar.ended'), tone: 'done' }
  return null
}

function formatTimeRange(ev: CalendarEvent) {
  if (!ev.time) return t('calendar.allDay')
  const [hh, mm] = ev.time.split(':').map(Number)
  if (Number.isNaN(hh)) return ev.time
  const pad = (n: number) => String(n).padStart(2, '0')
  const startLabel = `${pad(hh)}:${pad(mm || 0)}`
  if (ev.endTime) {
    const [eh, em] = ev.endTime.split(':').map(Number)
    if (!Number.isNaN(eh)) {
      return `${startLabel} - ${pad(eh)}:${pad(em || 0)}`
    }
  }
  const endH = (hh + 1) % 24
  return `${startLabel} - ${pad(endH)}:${pad(mm || 0)}`
}

function syncPanelToSelected() {
  const d = new Date(selectedDate.value)
  panelYear.value = d.getFullYear()
  panelMonth.value = d.getMonth()
}

watch(selectedDate, () => {
  const d = new Date(selectedDate.value)
  if (d.getFullYear() !== panelYear.value || d.getMonth() !== panelMonth.value) {
    panelYear.value = d.getFullYear()
    panelMonth.value = d.getMonth()
  }
  showWeekList.value = false
})

onMounted(() => {
  syncPanelToSelected()
  if (!initialized.value) fetchEvents().catch(() => {})
  else startReminderWatch()
})

async function onToggleRemind(event: CalendarEvent) {
  const result = await toggleRemind(event.id)
  if (result === 'on') {
    message.success(t('calendar.remindOn'))
  } else if (result === 'off') {
    message.info(t('calendar.remindOff'))
  } else if (result === 'no-time') {
    message.warning(t('calendar.remindNoTime'))
  } else if (result === 'expired') {
    message.warning(t('calendar.remindExpired'))
  }
}

function shiftMonth(delta: number) {
  const d = new Date(panelYear.value, panelMonth.value + delta, 1)
  panelYear.value = d.getFullYear()
  panelMonth.value = d.getMonth()
}

function goToday() {
  setSelectedDate(Date.now()).catch(() => {})
  syncPanelToSelected()
  showWeekList.value = false
}

function selectCell(cell: MonthCell) {
  setSelectedDate(new Date(cell.year, cell.month - 1, cell.date).getTime()).catch(() => {})
  showWeekList.value = false
}

function resetForm() {
  editingEvent.value = null
  showEventModal.value = false
}

function openAddForm() {
  editingEvent.value = null
  showEventModal.value = true
  showWeekList.value = false
}

function openEditForm(event: CalendarEvent) {
  editingEvent.value = event
  showEventModal.value = true
}

function onEventSaved() {
  resetForm()
}

function confirmDelete(event: CalendarEvent) {
  dialog.warning({
    title: t('calendar.deleteTitle'),
    content: t('calendar.deleteConfirm', { title: event.title }),
    positiveText: t('common.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      const ok = await removeEvent(event.id)
      if (ok) message.success(t('calendar.deleted'))
      if (editingEvent.value?.id === event.id) resetForm()
    }
  })
}

function onMoreAction(event: CalendarEvent, key: string) {
  if (key === 'edit') openEditForm(event)
  else if (key === 'delete') confirmDelete(event)
}

const moreOptions = computed(() => [
  { label: t('calendar.edit'), key: 'edit' },
  { label: t('common.delete'), key: 'delete' }
])

const agendaList = computed(() => (showWeekList.value ? weekEvents.value : eventsOnSelected.value))
</script>

<template>
  <div class="cal-page">
    <!-- 顶栏 -->
    <header class="page-toolbar">
      <div class="toolbar-left">
        <n-icon :component="CalendarOutline" :size="20" class="brand-icon" />
        <span class="page-name">{{ t('calendar.title') }}</span>
        <LxButton variant="sm-primary" class="create-btn" @click="openAddForm">
          <n-icon :component="AddOutline" />
          {{ t('calendar.newEvent') }}
        </LxButton>
      </div>
      <div class="toolbar-right">
        <LxButton variant="outline" @click="goToday">{{ t('calendar.today') }}</LxButton>
        <LxIconButton variant="calendar-nav" :title="t('calendar.prevMonth')" @click="shiftMonth(-1)">
          <n-icon :component="ChevronBackOutline" :size="16" />
        </LxIconButton>
        <LxIconButton variant="calendar-nav" :title="t('calendar.nextMonth')" @click="shiftMonth(1)">
          <n-icon :component="ChevronForwardOutline" :size="16" />
        </LxIconButton>
        <n-dropdown :options="viewMenuOptions" trigger="click">
          <LxButton variant="outline" class="view-btn">
            {{ t('calendar.monthView') }}
            <n-icon :component="ChevronDownOutline" :size="14" />
          </LxButton>
        </n-dropdown>
      </div>
    </header>

    <div class="page-body">
      <!-- 月视图 -->
      <section class="month-section">
        <h2 class="month-label">{{ panelTitle }}</h2>

        <div class="weekday-row">
          <span v-for="w in WEEK_LABELS" :key="w" class="weekday">{{ w }}</span>
        </div>

        <div class="month-grid">
          <button
            v-for="cell in monthCells"
            :key="cell.key"
            type="button"
            class="day-cell"
            :class="{
              'out-month': !cell.inMonth,
              today: cell.isToday,
              selected: cell.isSelected
            }"
            @click="selectCell(cell)"
            @dblclick="openAddForm"
          >
            <span class="day-num">{{ cell.date }}</span>
            <div class="cell-events">
              <div
                v-for="(ev, i) in cell.events.slice(0, MAX_CELL_EVENTS)"
                :key="ev.id"
                class="cell-event"
                :style="{ color: eventColor(ev, i) }"
              >
                <i class="cell-dot" :style="{ background: eventColor(ev, i) }" />
                <span class="cell-event-title">{{ ev.title }}</span>
              </div>
              <div v-if="cell.events.length > MAX_CELL_EVENTS" class="cell-more">
                +{{ cell.events.length - MAX_CELL_EVENTS }}
              </div>
            </div>
          </button>
        </div>
      </section>

      <!-- 底部日程列表 -->
      <section class="agenda-section">
        <header class="agenda-toolbar">
          <button type="button" class="lx-link-btn lx-link-btn--agenda" @click="showWeekList = false">
            {{ showWeekList ? t('calendar.weekAgenda') : selectedHeading }}
            <n-icon :component="ChevronDownOutline" :size="14" />
          </button>
          <button type="button" class="lx-link-btn lx-link-btn--manage" @click="showWeekList = !showWeekList">
            {{ showWeekList ? t('calendar.dayAgenda') : t('calendar.manage') }}
          </button>
        </header>

        <div v-if="agendaList.length" class="card-list">
          <article
            v-for="(event, idx) in agendaList"
            :key="event.id"
            class="event-card"
          >
            <i class="card-dot" :style="{ background: eventColor(event, idx) }" />
            <div class="card-main">
              <div class="card-title-row">
                <h4 class="card-title">{{ event.title }}</h4>
                <span
                  v-if="eventStatus(event)"
                  class="status-pill"
                  :class="eventStatus(event)!.tone"
                >
                  {{ eventStatus(event)!.text }}
                </span>
              </div>
              <div class="card-meta">
                <span>{{ formatTimeRange(event) }}</span>
                <span v-if="showWeekList" class="meta-date">{{ event.date.slice(5).replace('-', '/') }}</span>
              </div>
              <div class="card-loc">
                <n-icon :component="LocationOutline" :size="13" />
                <span>{{ t('calendar.personal') }}</span>
              </div>
            </div>
            <div class="card-actions">
              <LxIconButton
                variant="card-icon"
                :active="isRemindOn(event.id)"
                :title="isRemindOn(event.id) ? t('calendar.remindOffTitle') : t('calendar.remindOnTitle')"
                @click="onToggleRemind(event)"
              >
                <n-icon :component="NotificationsOutline" :size="16" />
              </LxIconButton>
              <n-dropdown
                :options="moreOptions"
                trigger="click"
                @select="(k: string) => onMoreAction(event, k)"
              >
                <LxIconButton variant="card-icon" :title="t('calendar.more')">
                  <n-icon :component="EllipsisHorizontal" :size="16" />
                </LxIconButton>
              </n-dropdown>
            </div>
          </article>
        </div>
        <div v-else class="agenda-empty">
          <p>{{ t('calendar.emptyDay') }}</p>
          <button type="button" class="empty-link" @click="openAddForm">{{ t('calendar.newEvent') }}</button>
        </div>

        <LxButton
          v-if="!showWeekList"
          variant="week-footer"
          @click="showWeekList = true"
        >
          {{ t('calendar.viewWeek') }}
        </LxButton>
      </section>
    </div>

    <CalendarEventModal
      v-model:show="showEventModal"
      :editing-event="editingEvent"
      :default-date="selectedDateKey"
      @saved="onEventSaved"
    />
  </div>
</template>

<style scoped>
.cal-page {
  flex: 1;
  height: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-panel);
  overflow: hidden;
}

/* —— 顶栏 —— */
.page-toolbar {
  flex-shrink: 0;
  height: 56px;
  padding: 0 var(--lx-space-3xl);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-lg);
  background: var(--lx-bg-card);
  border-bottom: 1px solid var(--lx-border-light);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
}

.brand-icon {
  color: var(--lx-accent);
}

.page-name {
  font-size: var(--lx-font-xl);
  font-weight: 600;
  color: var(--lx-text-body);
  margin-right: var(--lx-space-xs);
}

.create-btn {
  border-radius: var(--lx-radius-sm) !important;
}

.view-btn {
  min-width: 76px;
}

/* —— 主体 —— */
.page-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow: hidden;
}

.month-section {
  flex: 2 1 0;
  min-height: 380px;
  background: var(--lx-bg-card);
  padding: var(--lx-space-2xl) var(--lx-space-3xl) var(--lx-space-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-bottom: 1px solid var(--lx-border-light);
}

.month-label {
  margin: 0 0 var(--lx-space-xl);
  font-size: var(--lx-font-3xl);
  font-weight: 600;
  color: var(--lx-text-body);
  flex-shrink: 0;
}

.weekday-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: var(--lx-space-sm);
  flex-shrink: 0;
}

.weekday {
  text-align: center;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  padding: var(--lx-space-2xs) 0 var(--lx-space-md);
  font-weight: 500;
}

.month-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(6, minmax(64px, 1fr));
  gap: var(--lx-space-xs);
}

.day-cell {
  border: none;
  background: transparent;
  border-radius: var(--lx-radius-xl);
  padding: var(--lx-space-sm) var(--lx-space) var(--lx-space-sm);
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  min-height: 64px;
  overflow: hidden;
  transition: background var(--lx-duration-fast);
}
.day-cell:hover {
  background: var(--lx-bg-hover);
}
.day-cell.selected {
  background: var(--lx-accent-soft);
}
.day-cell.out-month .day-num,
.day-cell.out-month .cell-event {
  opacity: 0.4;
}

.day-num {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--lx-font-md);
  font-weight: 500;
  color: var(--lx-text-body);
  margin-bottom: var(--lx-space-sm);
  flex-shrink: 0;
}

.day-cell.today .day-num {
  background: var(--lx-accent);
  color: var(--lx-text-on-accent);
  font-weight: 600;
}

.day-cell.selected:not(.today) .day-num {
  background: color-mix(in srgb, var(--lx-accent) 18%, transparent);
  color: var(--lx-accent);
  font-weight: 700;
}

.cell-events {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-2xs);
  min-height: 0;
  overflow: hidden;
}

.cell-event {
  display: flex;
  align-items: center;
  gap: var(--lx-space-xs);
  min-width: 0;
  height: 18px;
  flex-shrink: 0;
  font-size: var(--lx-font-sm);
  line-height: var(--lx-font-3xl);
  font-weight: 500;
}

.cell-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
}

.cell-event-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-more {
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
  padding-left: var(--lx-space);
  line-height: var(--lx-font-xl);
  flex-shrink: 0;
}

/* —— 底部议程 —— */
.agenda-section {
  flex: 0.75 1 0;
  min-height: 180px;
  max-height: 38%;
  background: var(--lx-bg-panel);
  padding: var(--lx-space-lg) var(--lx-space-3xl) var(--lx-space-2xl);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.agenda-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--lx-space-lg);
  flex-shrink: 0;
}

.card-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-md);
  padding-bottom: var(--lx-space);
}

.event-card {
  display: flex;
  align-items: flex-start;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-xl) var(--lx-space-xl) var(--lx-space-xl) var(--lx-space-lg);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-lg);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--lx-border-light);
  transition: box-shadow var(--lx-duration);
}
.event-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.card-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: var(--lx-space-sm);
  flex-shrink: 0;
}

.card-main {
  flex: 1;
  min-width: 0;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  flex-wrap: wrap;
}

.card-title {
  margin: 0;
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-body);
  line-height: var(--lx-leading-snug);
}

.status-pill {
  font-size: var(--lx-font-xs);
  font-weight: 500;
  padding: var(--lx-space-2xs) var(--lx-space);
  border-radius: var(--lx-radius-pill);
  line-height: var(--lx-leading);
}
.status-pill.active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}
.status-pill.soon {
  background: rgba(51, 112, 255, 0.12);
  color: var(--lx-event-blue);
}
.status-pill.done {
  background: rgba(0, 0, 0, 0.05);
  color: var(--lx-text-muted);
}

.card-meta {
  margin-top: var(--lx-space-xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  display: flex;
  gap: var(--lx-space-md);
  font-variant-numeric: tabular-nums;
}

.meta-date {
  color: var(--lx-text-muted);
}

.card-loc {
  margin-top: var(--lx-space-xs);
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.card-actions {
  display: flex;
  align-items: center;
  gap: var(--lx-space-2xs);
  flex-shrink: 0;
  opacity: 0.7;
}
.event-card:hover .card-actions {
  opacity: 1;
}

.agenda-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--lx-space);
  color: var(--lx-text-muted);
  min-height: 80px;
}
.agenda-empty p {
  margin: 0;
  font-size: var(--lx-font-md);
}

.empty-link {
  border: none;
  background: transparent;
  color: var(--lx-accent);
  font-size: var(--lx-font-md);
  cursor: pointer;
}
</style>
