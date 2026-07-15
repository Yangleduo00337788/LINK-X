/**
 * 日历 Store
 * 管理选中日期、日程事件及按日期筛选与 CRUD
 * 数据本地管理（可通过后端 API 扩展）
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'

/** 日历事件项 */
export interface CalendarEvent {
  id: string       // 事件唯一 id
  title: string    // 事件标题
  date: string     // 日期键 YYYY-MM-DD
  time?: string    // 可选开始时间 HH:mm
  color?: string   // 可选展示颜色（CSS 变量或色值）
}

/**
 * 将时间戳格式化为日期键 YYYY-MM-DD
 * @param ts 毫秒时间戳
 */
function dateKeyFromTs(ts: number) {
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0') // 月份补零
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/**
 * 取某时间戳所在日的 0 点毫秒时间戳（本地时区）
 * @param ts 任意时刻时间戳
 */
function startOfDay(ts: number) {
  const d = new Date(ts)
  d.setHours(0, 0, 0, 0) // 归零时分秒毫秒
  return d.getTime()
}

// 定义并导出 calendar Store
export const useCalendarStore = defineStore('calendar', {
  // 初始状态
  state: () => ({
    selectedDate: startOfDay(Date.now()),   // 日历当前选中日期（0 点时间戳）
    events: [] as CalendarEvent[]           // 日程事件列表（本地管理）
  }),

  getters: {
    /** 选中日期对应的 YYYY-MM-DD 字符串 */
    selectedDateKey(state): string {
      return dateKeyFromTs(state.selectedDate)
    },

    /** 选中日当天的事件，按 time 字符串排序 */
    eventsOnSelected(state): CalendarEvent[] {
      const key = dateKeyFromTs(state.selectedDate)
      return state.events
        .filter(e => e.date === key)
        .sort((a, b) => (a.time || '').localeCompare(b.time || ''))
    },

    /** 从选中日起的未来事件，最多 12 条 */
    upcomingEvents(state): CalendarEvent[] {
      const from = dateKeyFromTs(state.selectedDate)
      return [...state.events]
        .filter(e => e.date >= from) // 日期不早于选中日
        .sort((a, b) => a.date.localeCompare(b.date) || (a.time || '').localeCompare(b.time || ''))
        .slice(0, 12)
    },

    /** 所有有事件的日期键集合（用于月历打点） */
    eventDateKeys(state): Set<string> {
      return new Set(state.events.map(e => e.date))
    }
  },

  actions: {
    /**
     * 设置选中日期
     * @param ts 任意时刻时间戳，会归一化到当日 0 点
     */
    setSelectedDate(ts: number) {
      this.selectedDate = startOfDay(ts)
    },

    /** 选中日期跳转到今天 */
    goToday() {
      this.selectedDate = startOfDay(Date.now())
    },

    /**
     * 判断指定公历日期是否有事件
     * @param year 年
     * @param month 月（1-12）
     * @param date 日
     */
    hasEventOn(year: number, month: number, date: number) {
      const key = `${year}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`
      return this.eventDateKeys.has(key)
    },

    /**
     * 新增事件
     * @param payload 事件字段（不含 id）
     * @returns 新事件 id
     */
    addEvent(payload: Omit<CalendarEvent, 'id'>) {
      const id = `evt-${Date.now()}`
      this.events.push({ ...payload, id })
      return id
    },

    /**
     * 部分更新事件
     * @param id 事件 id
     * @param patch 要合并的字段
     * @returns 是否找到并更新
     */
    updateEvent(id: string, patch: Partial<Omit<CalendarEvent, 'id'>>) {
      const event = this.events.find(e => e.id === id)
      if (!event) return false
      Object.assign(event, patch)
      return true
    },

    /**
     * 删除事件
     * @param id 事件 id
     * @returns 是否删除成功
     */
    removeEvent(id: string) {
      const idx = this.events.findIndex(e => e.id === id)
      if (idx === -1) return false
      this.events.splice(idx, 1)
      return true
    }
  },

  // 仅持久化 events，选中日期每次打开默认为今天
  persist: {
    key: 'linkx-calendar',
    paths: ['events']
  }
})
