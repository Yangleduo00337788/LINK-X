<script setup lang="ts">
/**
 * 日历主视图。
 * <p>
 * 含月历组件与选中日期的日程 CRUD（新建、编辑、删除）。
 * </p>
 */
import { ref, computed, onMounted } from 'vue'
import { NCalendar, NInput, NButton, useMessage, useDialog } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useCalendarStore } from '../stores/calendar'
import type { CalendarEvent } from '../stores/calendar'

const message = useMessage()
const dialog = useDialog()
const calendarStore = useCalendarStore()
const { selectedDate, eventsOnSelected, selectedDateKey, initialized } = storeToRefs(calendarStore)
const { setSelectedDate, hasEventOn, addEvent, updateEvent, removeEvent, fetchEvents } = calendarStore

// 是否显示新建/编辑表单
const showForm = ref(false)
// 编辑中的事件 id，null 表示新建
const editingId = ref<string | null>(null)
// 表单：标题与时间
const formTitle = ref('')
const formTime = ref('09:00')

/** 选中日期的中文标签（含星期） */
const selectedLabel = computed(() => {
  const d = new Date(selectedDate.value)
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 周${week}`
})

/** 组件挂载时加载日历数据 */
onMounted(() => {
  if (!initialized.value) {
    void fetchEvents()
  }
})

/** 月历选中日期变化 */
function onDateChange(ts: number) {
  setSelectedDate(ts)
}

/** 关闭表单并重置字段 */
function resetForm() {
  editingId.value = null
  formTitle.value = ''
  formTime.value = '09:00'
  showForm.value = false
}

/** 打开新建日程表单 */
function openAddForm() {
  editingId.value = null
  formTitle.value = ''
  formTime.value = '09:00'
  showForm.value = true
}

/** 打开编辑表单并填充事件数据 */
function openEditForm(event: CalendarEvent) {
  editingId.value = event.id
  formTitle.value = event.title
  formTime.value = event.time || '09:00'
  showForm.value = true
}

/** 保存新建或更新日程 */
async function saveEvent() {
  const title = formTitle.value.trim()
  if (!title) {
    message.warning('请输入日程标题')
    return
  }
  const payload = {
    title,
    date: selectedDateKey.value,
    time: formTime.value.trim() || undefined,
    color: 'var(--lx-accent)'
  }
  if (editingId.value) {
    const ok = await updateEvent(editingId.value, payload)
    if (ok) {
      message.success('日程已更新')
    } else {
      message.error('更新日程失败，请重试')
    }
  } else {
    const id = await addEvent(payload)
    if (id) {
      message.success('日程已添加')
    } else {
      message.error('添加日程失败，请重试')
    }
  }
  resetForm()
}

/** 二次确认删除日程 */
function confirmDelete(event: CalendarEvent) {
  dialog.warning({
    title: '删除日程',
    content: `确定删除「${event.title}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const ok = await removeEvent(event.id)
      if (ok) {
        message.success('日程已删除')
      }
      if (editingId.value === event.id) resetForm()
    }
  })
}
</script>

<template>
  <div class="calendar-main">
    <!-- 月历卡片：有日程的日期显示圆点 -->
    <div class="calendar-card">
      <n-calendar
        :value="selectedDate"
        class="lx-calendar"
        @update:value="onDateChange"
      >
        <template #default="{ year, month, date }">
          <div class="cell-inner">
            <span>{{ date }}</span>
            <i v-if="hasEventOn(year, month, date)" class="cell-dot" />
          </div>
        </template>
      </n-calendar>
    </div>

    <!-- 选中日期的日程详情与表单 -->
    <section class="day-detail">
      <header class="day-head">
        <div>
          <h3>{{ selectedLabel }}</h3>
          <span class="day-count">{{ eventsOnSelected.length }} 个日程</span>
        </div>
        <n-button size="small" type="primary" @click="openAddForm">新建</n-button>
      </header>

      <!-- 新建/编辑表单 -->
      <div v-if="showForm" class="event-form">
        <n-input v-model:value="formTitle" placeholder="日程标题" />
        <n-input v-model:value="formTime" placeholder="时间，如 09:00" />
        <div class="form-actions">
          <n-button size="small" @click="resetForm">取消</n-button>
          <n-button size="small" type="primary" @click="saveEvent">保存</n-button>
        </div>
      </div>

      <!-- 当日日程列表 -->
      <ul v-if="eventsOnSelected.length" class="day-events">
        <li v-for="event in eventsOnSelected" :key="event.id" class="day-event">
          <span class="day-event-time">{{ event.time || '全天' }}</span>
          <span class="day-event-bar" :style="{ background: event.color || 'var(--lx-accent)' }" />
          <span class="day-event-title">{{ event.title }}</span>
          <div class="day-event-actions">
            <button type="button" class="mini-btn" @click="openEditForm(event)">编辑</button>
            <button type="button" class="mini-btn danger" @click="confirmDelete(event)">删除</button>
          </div>
        </li>
      </ul>
      <p v-else class="day-empty">这一天没有安排，点击「新建」添加日程</p>
    </section>
  </div>
</template>

<style scoped>
.calendar-main {
  flex: 1;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px 18px;
  gap: 14px;
  overflow: hidden;
}

.calendar-card {
  flex: 1;
  min-height: 280px;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--lx-shadow-soft);
  display: flex;
  flex-direction: column;
}

.lx-calendar {
  flex: 1;
  --n-border-color: transparent;
}

.lx-calendar :deep(.n-calendar-header) {
  padding: 14px 16px 8px;
}

.lx-calendar :deep(.n-calendar-header__title) {
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.lx-calendar :deep(.n-calendar-dates) {
  padding: 0 12px 12px;
}

.lx-calendar :deep(.n-calendar-date) {
  border-radius: 8px;
}

.lx-calendar :deep(.n-calendar-date--selected) {
  background: var(--lx-accent-soft) !important;
}

.lx-calendar :deep(.n-calendar-date--selected .n-calendar-date__date) {
  color: var(--lx-accent);
  font-weight: 600;
}

.lx-calendar :deep(.n-calendar-date--current .n-calendar-date__date) {
  color: var(--lx-accent);
}

.cell-inner {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 28px;
}

.cell-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--lx-accent);
  margin-top: 2px;
}

.day-detail {
  flex: 1;
  min-height: 0;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: 12px;
  padding: 14px 16px;
  overflow-y: auto;
}

.day-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.day-head h3 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.day-count {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.event-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px;
  background: var(--lx-bg-list, var(--lx-bg-panel));
  border-radius: var(--lx-radius);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.day-events {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.day-event {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-list, var(--lx-bg-panel));
}

.day-event-time {
  width: 44px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.day-event-bar {
  width: 3px;
  height: 28px;
  border-radius: 2px;
  flex-shrink: 0;
}

.day-event-title {
  flex: 1;
  font-size: 14px;
  color: var(--lx-text-body);
}

.day-event-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.mini-btn {
  border: none;
  background: transparent;
  font-size: 12px;
  color: var(--lx-accent);
  cursor: pointer;
  padding: 2px 4px;
}

.mini-btn.danger {
  color: var(--lx-danger);
}

.day-empty {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--lx-text-muted);
}
</style>
