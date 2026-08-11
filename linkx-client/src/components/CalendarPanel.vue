<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 日历侧栏面板。
 * <p>
 * 展示当前月份与近期日程列表，点击日程同步主区选中日期。
 * </p>
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { CalendarOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useCalendarStore } from '../stores/calendar'
import type { CalendarEvent } from '../stores/calendar'
import { useI18n } from '../i18n'
import { LxButton } from './ui'

const calendarStore = useCalendarStore()
const { t } = useI18n()
const { selectedDate, selectedDateKey } = storeToRefs(calendarStore)
const { setSelectedDate, goToday } = calendarStore
const { upcomingEvents } = storeToRefs(calendarStore)

/** 当前选中日期所在月份文案 */
const monthLabel = computed(() => {
  const d = new Date(selectedDate.value)
  return t('calendar.yearMonth', { y: d.getFullYear(), m: d.getMonth() + 1 })
})

/**
 * 将 yyyy-MM-dd 日期键转为时间戳。
 *
 * @param key 日期键字符串
 */
function parseDateKey(key: string) {
  const [y, m, day] = key.split('-').map(Number)
  return new Date(y, m - 1, day).getTime()
}

/**
 * 格式化日程日期显示：今天或「M月D日」。
 */
function formatDayLabel(key: string) {
  const d = new Date(parseDateKey(key))
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  if (key === today) return t('calendar.today')
  return t('calendar.monthDay', { m: d.getMonth() + 1, d: d.getDate() })
}

/** 点击日程：将主日历选中到该事件日期 */
function selectEvent(event: CalendarEvent) {
  setSelectedDate(parseDateKey(event.date))
}

/** 判断日程是否对应当前选中日期（高亮用） */
function isActiveEvent(event: CalendarEvent) {
  return event.date === selectedDateKey.value
}
</script>

<template>
  <!-- 日历侧栏：标题、月份、近期日程 -->
  <div class="calendar-panel">
    <div class="panel-head">
      <div class="head-title">
        <n-icon :component="CalendarOutline" :size="18" />
        <span>{{ t('calendar.title') }}</span>
      </div>
      <LxButton variant="sm" class="today-btn" @click="goToday">{{ t('calendar.today') }}</LxButton>
    </div>

    <div class="month-bar">{{ monthLabel }}</div>

    <div class="event-list">
      <template v-if="upcomingEvents.length">
        <button
          v-for="event in upcomingEvents"
          :key="event.id"
          type="button"
          class="event-row"
          :class="{ 'is-active': isActiveEvent(event) }"
          @click="selectEvent(event)"
        >
          <span class="event-dot" :style="{ background: event.color || 'var(--lx-accent)' }" />
          <div class="event-meta">
            <div class="event-title">{{ event.title }}</div>
            <div class="event-sub">
              <template v-if="event.time">{{ event.time }} · </template>{{ formatDayLabel(event.date) }}
            </div>
          </div>
        </button>
      </template>
      <div v-else class="empty-tip">{{ t('calendar.noEvents') }}</div>
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
  padding: 0 var(--lx-space-xl);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.head-title {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  font-size: var(--lx-font-lg);
  font-weight: 600;
  color: var(--lx-text-body);
}

.today-btn {
  background: var(--lx-accent-soft) !important;
  color: var(--lx-accent) !important;
  border: none !important;
  font-size: var(--lx-font-sm);
  font-weight: 500;
  padding: var(--lx-space-xs) var(--lx-space-md);
}

.today-btn:hover:not(:disabled) {
  background: rgba(18, 183, 245, 0.22) !important;
}

.month-bar {
  padding: 0 var(--lx-space-xl) var(--lx-space-md);
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.event-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--lx-space) var(--lx-space-lg);
}

.event-row {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: stretch;
  gap: 0;
  padding: 0;
  margin-bottom: var(--lx-space-sm);
  border-radius: var(--lx-radius-sm);
  cursor: pointer;
  text-align: left;
  overflow: hidden;
  transition: background var(--lx-duration);
}

.event-row:hover {
  background: var(--lx-bg-hover);
}

.event-row.active {
  background: var(--lx-accent-soft);
}

.event-dot {
  width: 3px;
  align-self: stretch;
  border-radius: 0;
  margin: 0;
  flex-shrink: 0;
}

.event-meta {
  min-width: 0;
  flex: 1;
  padding: var(--lx-space-md) var(--lx-space-lg);
}

.event-title {
  font-size: var(--lx-font);
  color: var(--lx-text-body);
  font-weight: 500;
  line-height: var(--lx-leading-snug);
}

.event-sub {
  margin-top: var(--lx-space-2xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.empty-tip {
  padding: var(--lx-space-4xl) var(--lx-space-xl);
  text-align: center;
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}
</style>
