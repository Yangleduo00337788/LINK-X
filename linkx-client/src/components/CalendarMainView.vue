<script setup lang="ts">
/**
 * 日历主视图 — 按设计稿：顶栏 + 月网格 + 底部日程卡片列表
 */
import { ref, computed, watch, onMounted } from 'vue'
import { NInput, NButton, NIcon, NDropdown, useMessage, useDialog } from 'naive-ui'
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

const message = useMessage()
const dialog = useDialog()
const calendarStore = useCalendarStore()
const { selectedDate, events, eventsOnSelected, selectedDateKey, initialized } = storeToRefs(calendarStore)
const { setSelectedDate, addEvent, updateEvent, removeEvent, fetchEvents, toggleRemind, startReminderWatch, isRemindOn } =
  calendarStore

/** 与设计稿一致：周日为一周起始 */
const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']
const MAX_CELL_EVENTS = 2
const EVENT_COLORS = ['#3370ff', '#f54a45', '#ff8800', '#7b61ff', '#00b578', '#12b7f5']

const panelYear = ref(new Date().getFullYear())
const panelMonth = ref(new Date().getMonth())

const showForm = ref(false)
const editingId = ref<string | null>(null)
const formTitle = ref('')
const formTime = ref('09:00')
const showWeekList = ref(false)

const panelTitle = computed(() => `${panelYear.value}年${panelMonth.value + 1}月`)

const viewMenuOptions = [
  { label: '月视图', key: 'month' }
]

const selectedHeading = computed(() => {
  const d = new Date(selectedDate.value)
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const today = new Date()
  const isToday =
    d.getFullYear() === today.getFullYear() &&
    d.getMonth() === today.getMonth() &&
    d.getDate() === today.getDate()
  const datePart = `${d.getMonth() + 1}月${d.getDate()}日 周${week}`
  return isToday ? `今天 · ${datePart}` : datePart
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

/** 根据开始时间推断状态徽标 */
function eventStatus(ev: CalendarEvent): { text: string; tone: 'active' | 'soon' | 'done' } | null {
  if (!ev.time || ev.date !== selectedDateKey.value) return null
  const [hh, mm] = ev.time.split(':').map(Number)
  if (Number.isNaN(hh)) return null
  const start = new Date(selectedDate.value)
  start.setHours(hh, mm || 0, 0, 0)
  const end = new Date(start.getTime() + 60 * 60 * 1000)
  const now = Date.now()
  if (now >= start.getTime() && now < end.getTime()) return { text: '进行中', tone: 'active' }
  if (now < start.getTime() && start.getTime() - now <= 2 * 60 * 60 * 1000) {
    return { text: '即将开始', tone: 'soon' }
  }
  if (now >= end.getTime()) return { text: '已结束', tone: 'done' }
  return null
}

function formatTimeRange(ev: CalendarEvent) {
  if (!ev.time) return '全天'
  const [hh, mm] = ev.time.split(':').map(Number)
  if (Number.isNaN(hh)) return ev.time
  const endH = (hh + 1) % 24
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(hh)}:${pad(mm || 0)} - ${pad(endH)}:${pad(mm || 0)}`
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
  if (!initialized.value) void fetchEvents()
  else startReminderWatch()
})

async function onToggleRemind(event: CalendarEvent) {
  const result = await toggleRemind(event.id)
  if (result === 'on') {
    message.success(`已开启提醒：开始前 5 分钟会写入消息通知`)
  } else if (result === 'off') {
    message.info('已关闭提醒')
  } else if (result === 'no-time') {
    message.warning('该日程没有开始时间，无法提醒')
  } else if (result === 'expired') {
    message.warning('日程已开始或已过期，无法提醒')
  }
}

function shiftMonth(delta: number) {
  const d = new Date(panelYear.value, panelMonth.value + delta, 1)
  panelYear.value = d.getFullYear()
  panelMonth.value = d.getMonth()
}

function goToday() {
  void setSelectedDate(Date.now())
  syncPanelToSelected()
  showWeekList.value = false
}

function selectCell(cell: MonthCell) {
  void setSelectedDate(new Date(cell.year, cell.month - 1, cell.date).getTime())
  showWeekList.value = false
}

function resetForm() {
  editingId.value = null
  formTitle.value = ''
  formTime.value = '09:00'
  showForm.value = false
}

function openAddForm() {
  editingId.value = null
  formTitle.value = ''
  formTime.value = '09:00'
  showForm.value = true
  showWeekList.value = false
}

function openEditForm(event: CalendarEvent) {
  editingId.value = event.id
  formTitle.value = event.title
  formTime.value = event.time || '09:00'
  showForm.value = true
}

async function saveEvent() {
  const title = formTitle.value.trim()
  if (!title) {
    message.warning('请输入日程标题')
    return
  }
  const time = formTime.value.trim()
  const normalizedTime = time
    ? time.replace(/^(\d):/, '0$1:').replace(/:(\d)$/, ':0$1')
    : undefined
  const color = EVENT_COLORS[Math.floor(Math.random() * EVENT_COLORS.length)]
  const payload = {
    title,
    date: selectedDateKey.value,
    time: normalizedTime,
    color
  }
  if (editingId.value) {
    const ok = await updateEvent(editingId.value, {
      title: payload.title,
      date: payload.date,
      time: payload.time,
      color: undefined
    })
    if (ok) message.success('日程已更新')
    else message.error('更新日程失败，请重试')
  } else {
    const id = await addEvent(payload)
    if (id) message.success('日程已添加')
    else message.error('添加日程失败，请重试')
  }
  resetForm()
}

function confirmDelete(event: CalendarEvent) {
  dialog.warning({
    title: '删除日程',
    content: `确定删除「${event.title}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const ok = await removeEvent(event.id)
      if (ok) message.success('日程已删除')
      if (editingId.value === event.id) resetForm()
    }
  })
}

function onMoreAction(event: CalendarEvent, key: string) {
  if (key === 'edit') openEditForm(event)
  else if (key === 'delete') confirmDelete(event)
}

const moreOptions = [
  { label: '编辑', key: 'edit' },
  { label: '删除', key: 'delete' }
]

const agendaList = computed(() => (showWeekList.value ? weekEvents.value : eventsOnSelected.value))
</script>

<template>
  <div class="cal-page">
    <!-- 顶栏 -->
    <header class="page-toolbar">
      <div class="toolbar-left">
        <n-icon :component="CalendarOutline" :size="20" class="brand-icon" />
        <span class="page-name">日历</span>
        <n-button type="primary" size="small" class="create-btn" @click="openAddForm">
          <template #icon>
            <n-icon :component="AddOutline" />
          </template>
          新建日程
        </n-button>
      </div>
      <div class="toolbar-right">
        <button type="button" class="ghost-btn" @click="goToday">今天</button>
        <button type="button" class="icon-btn" title="上一月" @click="shiftMonth(-1)">
          <n-icon :component="ChevronBackOutline" :size="16" />
        </button>
        <button type="button" class="icon-btn" title="下一月" @click="shiftMonth(1)">
          <n-icon :component="ChevronForwardOutline" :size="16" />
        </button>
        <n-dropdown :options="viewMenuOptions" trigger="click">
          <button type="button" class="ghost-btn view-btn">
            月视图
            <n-icon :component="ChevronDownOutline" :size="14" />
          </button>
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
          <button type="button" class="agenda-date-btn" @click="showWeekList = false">
            {{ showWeekList ? '本周日程' : selectedHeading }}
            <n-icon :component="ChevronDownOutline" :size="14" />
          </button>
          <button type="button" class="manage-btn" @click="showWeekList = !showWeekList">
            {{ showWeekList ? '当日日程' : '日程管理' }}
          </button>
        </header>

        <div v-if="showForm" class="event-form">
          <n-input v-model:value="formTitle" placeholder="日程标题" maxlength="100" />
          <n-input v-model:value="formTime" placeholder="开始时间，如 09:00" maxlength="5" />
          <div class="form-actions">
            <n-button size="small" @click="resetForm">取消</n-button>
            <n-button size="small" type="primary" @click="saveEvent">保存</n-button>
          </div>
        </div>

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
                <span>个人日程</span>
              </div>
            </div>
            <div class="card-actions">
              <button
                type="button"
                class="card-icon-btn"
                :class="{ active: isRemindOn(event.id) }"
                :title="isRemindOn(event.id) ? '关闭提醒' : '开启开始前提醒'"
                @click="onToggleRemind(event)"
              >
                <n-icon :component="NotificationsOutline" :size="16" />
              </button>
              <n-dropdown
                :options="moreOptions"
                trigger="click"
                @select="(k: string) => onMoreAction(event, k)"
              >
                <button type="button" class="card-icon-btn" title="更多">
                  <n-icon :component="EllipsisHorizontal" :size="16" />
                </button>
              </n-dropdown>
            </div>
          </article>
        </div>
        <div v-else class="agenda-empty">
          <p>这一天还没有日程</p>
          <button type="button" class="empty-link" @click="openAddForm">新建日程</button>
        </div>

        <button
          v-if="!showWeekList"
          type="button"
          class="week-footer-btn"
          @click="showWeekList = true"
        >
          查看本周日程
        </button>
      </section>
    </div>
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
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--lx-bg-card);
  border-bottom: 1px solid var(--lx-border-light);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-icon {
  color: var(--lx-accent);
}

.page-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-text-body);
  margin-right: 4px;
}

.create-btn {
  border-radius: 8px !important;
}

.ghost-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  border-radius: 8px;
  font-size: 13px;
  color: var(--lx-text-body);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: background 0.15s, border-color 0.15s;
}
.ghost-btn:hover {
  background: var(--lx-bg-hover);
  border-color: var(--lx-accent);
  color: var(--lx-accent);
}

.icon-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  border-radius: 8px;
  color: var(--lx-text-body);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.icon-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-accent);
  border-color: var(--lx-accent);
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
  padding: 16px 20px 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-bottom: 1px solid var(--lx-border-light);
}

.month-label {
  margin: 0 0 14px;
  font-size: 18px;
  font-weight: 600;
  color: var(--lx-text-body);
  flex-shrink: 0;
}

.weekday-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 6px;
  flex-shrink: 0;
}

.weekday {
  text-align: center;
  font-size: 12px;
  color: var(--lx-text-muted);
  padding: 2px 0 10px;
  font-weight: 500;
}

.month-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(6, minmax(64px, 1fr));
  gap: 4px;
}

.day-cell {
  border: none;
  background: transparent;
  border-radius: 10px;
  padding: 6px 8px 6px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  min-height: 64px;
  overflow: hidden;
  transition: background 0.12s;
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
  font-size: 13px;
  font-weight: 500;
  color: var(--lx-text-body);
  margin-bottom: 6px;
  flex-shrink: 0;
}

.day-cell.today .day-num {
  background: var(--lx-accent);
  color: #fff;
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
  gap: 3px;
  min-height: 0;
  overflow: hidden;
}

.cell-event {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  height: 18px;
  flex-shrink: 0;
  font-size: 12px;
  line-height: 18px;
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
  font-size: 11px;
  color: var(--lx-text-muted);
  padding-left: 9px;
  line-height: 16px;
  flex-shrink: 0;
}

/* —— 底部议程 —— */
.agenda-section {
  flex: 0.75 1 0;
  min-height: 180px;
  max-height: 38%;
  background: var(--lx-bg-panel);
  padding: 12px 20px 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.agenda-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.agenda-date-btn {
  border: none;
  background: transparent;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
}

.manage-btn {
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--lx-accent);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}
.manage-btn:hover {
  background: var(--lx-accent-soft);
}

.event-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px;
  background: var(--lx-bg-card);
  border-radius: 12px;
  border: 1px solid var(--lx-border-light);
  flex-shrink: 0;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.card-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 8px;
}

.event-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 14px 14px 12px;
  background: var(--lx-bg-card);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--lx-border-light);
  transition: box-shadow 0.15s;
}
.event-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.card-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.card-main {
  flex: 1;
  min-width: 0;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
  line-height: 1.35;
}

.status-pill {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 999px;
  line-height: 1.4;
}
.status-pill.active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}
.status-pill.soon {
  background: rgba(51, 112, 255, 0.12);
  color: #3370ff;
}
.status-pill.done {
  background: rgba(0, 0, 0, 0.05);
  color: var(--lx-text-muted);
}

.card-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
  display: flex;
  gap: 10px;
  font-variant-numeric: tabular-nums;
}

.meta-date {
  color: var(--lx-text-muted);
}

.card-loc {
  margin-top: 4px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0.7;
}
.event-card:hover .card-actions {
  opacity: 1;
}

.card-icon-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.card-icon-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-accent);
}
.card-icon-btn.active {
  color: var(--lx-accent);
  background: var(--lx-accent-soft);
}

.agenda-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--lx-text-muted);
  min-height: 80px;
}
.agenda-empty p {
  margin: 0;
  font-size: 13px;
}

.empty-link {
  border: none;
  background: transparent;
  color: var(--lx-accent);
  font-size: 13px;
  cursor: pointer;
}

.week-footer-btn {
  flex-shrink: 0;
  margin-top: 10px;
  width: 100%;
  height: 40px;
  border: none;
  border-radius: 10px;
  background: var(--lx-bg-card);
  color: var(--lx-text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.week-footer-btn:hover {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}
</style>
