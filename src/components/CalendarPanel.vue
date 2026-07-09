<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { CalendarOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useCalendarStore } from '../stores/calendar'
import type { CalendarEvent } from '../stores/calendar'

const calendarStore = useCalendarStore()
const { selectedDate, selectedDateKey } = storeToRefs(calendarStore)
const { setSelectedDate, goToday } = calendarStore
const { upcomingEvents } = storeToRefs(calendarStore)

const monthLabel = computed(() => {
  const d = new Date(selectedDate.value)
  return `${d.getFullYear()}年${d.getMonth() + 1}月`
})

function parseDateKey(key: string) {
  const [y, m, day] = key.split('-').map(Number)
  return new Date(y, m - 1, day).getTime()
}

function formatDayLabel(key: string) {
  const d = new Date(parseDateKey(key))
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  if (key === today) return '今天'
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

function selectEvent(event: CalendarEvent) {
  setSelectedDate(parseDateKey(event.date))
}

function isActiveEvent(event: CalendarEvent) {
  return event.date === selectedDateKey.value
}
</script>

<template>
  <div class="calendar-panel">
    <div class="panel-head">
      <div class="head-title">
        <n-icon :component="CalendarOutline" :size="18" />
        <span>日历</span>
      </div>
      <button type="button" class="today-btn" @click="goToday">今天</button>
    </div>

    <div class="month-bar">{{ monthLabel }}</div>

    <div class="event-list">
      <template v-if="upcomingEvents.length">
        <button
          v-for="event in upcomingEvents"
          :key="event.id"
          type="button"
          class="event-row"
          :class="{ active: isActiveEvent(event) }"
          @click="selectEvent(event)"
        >
          <span class="event-dot" :style="{ background: event.color || 'var(--lx-accent)' }" />
          <div class="event-meta">
            <div class="event-title">{{ event.title }}</div>
            <div class="event-sub">
              {{ formatDayLabel(event.date) }}
              <span v-if="event.time"> · {{ event.time }}</span>
            </div>
          </div>
        </button>
      </template>
      <div v-else class="empty-tip">近期暂无日程</div>
    </div>
  </div>
</template>

<style scoped>
.calendar-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
}

.panel-head {
  height: 52px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.head-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.today-btn {
  border: none;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-size: 12px;
  font-weight: 500;
  padding: 5px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}

.today-btn:hover {
  background: rgba(18, 183, 245, 0.22);
}

.month-bar {
  padding: 0 14px 10px;
  font-size: 13px;
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.event-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.event-row {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  margin-bottom: 4px;
  border-radius: var(--lx-radius);
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}

.event-row:hover {
  background: var(--lx-bg-hover);
}

.event-row.active {
  background: rgba(18, 183, 245, 0.12);
}

.event-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.event-meta {
  min-width: 0;
  flex: 1;
}

.event-title {
  font-size: 14px;
  color: var(--lx-text-body);
  font-weight: 500;
  line-height: 1.35;
}

.event-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.empty-tip {
  padding: 24px 14px;
  text-align: center;
  font-size: 13px;
  color: var(--lx-text-muted);
}
</style>
