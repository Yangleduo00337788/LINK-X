/**
 * 日历 Store
 * 管理选中日期、日程事件及按日期筛选与 CRUD
 * 数据对接后端 /calendar API
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'
import * as calendarApi from '../api/calendar'
import { t } from '../i18n'

/** 日历事件项 */
export interface CalendarEvent {
  id: string       // 事件唯一 id
  title: string    // 事件标题
  date: string     // 日期键 YYYY-MM-DD
  time?: string    // 可选开始时间 HH:mm
  endTime?: string // 可选结束时间 HH:mm
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

/** 提前提醒分钟数 */
const REMIND_AHEAD_MINUTES = 5
const REMIND_POLL_MS = 10_000

let remindTimer: ReturnType<typeof setInterval> | null = null
const scheduledTimeouts = new Map<string, ReturnType<typeof setTimeout>>()

function eventStartMs(ev: CalendarEvent): number | null {
  if (!ev.date || !ev.time) return null
  const [y, mo, d] = ev.date.split('-').map(Number)
  const [hh, mm] = ev.time.split(':').map(Number)
  if ([y, mo, d, hh].some(n => Number.isNaN(n))) return null
  // 使用本地时区构造，避免 ISO 字符串时区歧义
  return new Date(y, mo - 1, d, hh, mm || 0, 0, 0).getTime()
}

function eventEndMs(ev: CalendarEvent): number | null {
  if (!ev.date || !ev.endTime) return null
  const [y, mo, d] = ev.date.split('-').map(Number)
  const [hh, mm] = ev.endTime.split(':').map(Number)
  if ([y, mo, d, hh].some(n => Number.isNaN(n))) return null
  return new Date(y, mo - 1, d, hh, mm || 0, 0, 0).getTime()
}

function timeFromMs(ts: number): string {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function clearEventReminderTimeouts(ev: CalendarEvent) {
  for (const phase of ['ahead', 'start'] as const) {
    const key = remindKey(ev, phase)
    const timer = scheduledTimeouts.get(key)
    if (timer) {
      clearTimeout(timer)
      scheduledTimeouts.delete(key)
    }
  }
  const snoozeKey = `snooze:${ev.id}`
  const snoozeTimer = scheduledTimeouts.get(snoozeKey)
  if (snoozeTimer) {
    clearTimeout(snoozeTimer)
    scheduledTimeouts.delete(snoozeKey)
  }
}

function remindKey(ev: CalendarEvent, phase: 'ahead' | 'start' = 'ahead') {
  return `${ev.id}@${ev.date}@${ev.time || 'allday'}@${phase}`
}

// 定义并导出 calendar Store
export const useCalendarStore = defineStore('calendar', {
  // 初始状态
  state: () => ({
    selectedDate: startOfDay(Date.now()),   // 日历当前选中日期（0 点时间戳）
    events: [] as CalendarEvent[],           // 日程事件列表（从后端加载）
    loading: false,                         // 加载状态
    initialized: false,                     // 是否已从后端加载
    /** 已开启提醒的日程 id */
    remindEventIds: [] as string[],
    /** 已触发过的提醒键，避免重复写入消息 */
    firedRemindKeys: [] as string[],
    /** 仅延迟通知：eventId -> 截止时间戳（日程时间不变） */
    snoozedUntil: {} as Record<string, number>
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
    },

    isRemindOn: state => (eventId: string) => state.remindEventIds.includes(eventId),

    /** 已开启提醒、尚未结束的日程（用于消息页预览） */
    remindedUpcomingEvents(state): CalendarEvent[] {
      const now = Date.now()
      return [...state.events]
        .filter(e => state.remindEventIds.includes(e.id) && e.time)
        .filter(e => {
          const start = eventStartMs(e)
          return start != null && now <= start + 60_000
        })
        .sort((a, b) => a.date.localeCompare(b.date) || (a.time || '').localeCompare(b.time || ''))
    }
  },

  actions: {
    /**
     * 从后端加载所有日历事件
     */
    async fetchEvents() {
      this.loading = true
      try {
        const res = await calendarApi.listEvents()
        if (res.code === 200 && res.data) {
          this.events = res.data.map(e => ({
            id: String(e.id),
            title: e.title,
            date: e.date,
            time: e.time,
            endTime: e.endTime,
            color: e.color
          }))
          this.initialized = true
          this.startReminderWatch()
        }
      } catch (e) {
        console.error('加载日历事件失败:', e)
      } finally {
        this.loading = false
      }
    },

    /**
     * 设置选中日期，并按日期从后端刷新当日事件
     */
    async setSelectedDate(ts: number) {
      this.selectedDate = startOfDay(ts)
      await this.fetchEventsForDate(dateKeyFromTs(this.selectedDate))
    },

    /** 从后端拉取指定日期的事件 */
    async fetchEventsForDate(date: string) {
      try {
        const res = await calendarApi.listEventsByDate(date)
        if (res.code === 200 && res.data) {
          const others = this.events.filter(e => e.date !== date)
          const dayEvents = res.data.map(e => ({
            id: String(e.id),
            title: e.title,
            date: e.date,
            time: e.time,
            endTime: e.endTime,
            color: e.color
          }))
          this.events = [...others, ...dayEvents]
        }
      } catch (e) {
        console.error('加载当日日程失败:', e)
      }
    },

    /** 按 ID 从后端获取单条事件 */
    async fetchEventById(eventId: string) {
      try {
        const res = await calendarApi.getEvent(eventId)
        if (res.code === 200 && res.data) {
          const event: CalendarEvent = {
            id: String(res.data.id),
            title: res.data.title,
            date: res.data.date,
            time: res.data.time,
            endTime: res.data.endTime,
            color: res.data.color
          }
          const idx = this.events.findIndex(e => e.id === event.id)
          if (idx >= 0) {
            this.events[idx] = event
          } else {
            this.events.push(event)
          }
          return event
        }
      } catch (e) {
        console.error('加载日程详情失败:', e)
      }
      return null
    },

    /** 选中日期跳转到今天 */
    goToday() {
      void this.setSelectedDate(Date.now())
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
     * 新增事件（对接后端）
     * @param payload 事件字段（不含 id）
     * @returns 新事件 id
     */
    async addEvent(payload: Omit<CalendarEvent, 'id'>) {
      try {
        const res = await calendarApi.createEvent({
          title: payload.title,
          date: payload.date,
          time: payload.time,
          endTime: payload.endTime,
          color: payload.color
        })
        if (res.code === 200 && res.data) {
          const event: CalendarEvent = {
            id: String(res.data.id),
            title: res.data.title,
            date: res.data.date,
            time: res.data.time,
            endTime: res.data.endTime,
            color: res.data.color
          }
          this.events.push(event)
          return event.id
        }
      } catch (e) {
        console.error('创建日程失败:', e)
      }
      return null
    },

    /**
     * 部分更新事件（对接后端）
     * @param id 事件 id
     * @param patch 要合并的字段
     * @returns 是否找到并更新
     */
    async updateEvent(id: string, patch: Partial<Omit<CalendarEvent, 'id'>>) {
      try {
        console.log('[Calendar] 更新日程:', id, patch)
        const res = await calendarApi.updateEvent(id, {
          title: patch.title!,
          date: patch.date!,
          time: patch.time,
          endTime: patch.endTime,
          color: patch.color
        })
        console.log('[Calendar] 更新结果:', res)
        if (res.code === 200 && res.data) {
          const event = this.events.find(e => e.id === id)
          if (event) {
            Object.assign(event, {
              title: res.data.title,
              date: res.data.date,
              time: res.data.time,
              endTime: res.data.endTime,
              color: res.data.color
            })
          }
          return true
        }
        console.error('[Calendar] 更新失败:', res.message)
      } catch (e) {
        console.error('[Calendar] 更新异常:', e)
      }
      return false
    },

    /**
     * 删除事件（对接后端）
     * @param id 事件 id
     * @returns 是否删除成功
     */
    async removeEvent(id: string) {
      try {
        const res = await calendarApi.deleteEvent(id)
        if (res.code === 200) {
          this.events = this.events.filter(e => e.id !== id)
          this.remindEventIds = this.remindEventIds.filter(x => x !== id)
          return true
        }
      } catch (e) {
        console.error('删除日程失败:', e)
      }
      return false
    },

    /**
     * 开关日程提醒。开启后会在开始前 REMIND_AHEAD_MINUTES 分钟写入消息通知。
     * @returns 'on' | 'off' | 'expired' | 'no-time'
     */
    async toggleRemind(eventId: string): Promise<'on' | 'off' | 'expired' | 'no-time'> {
      const ev = this.events.find(e => e.id === eventId)
      if (!ev) return 'off'
      const on = this.remindEventIds.includes(eventId)
      if (on) {
        this.remindEventIds = this.remindEventIds.filter(id => id !== eventId)
        for (const phase of ['ahead', 'start'] as const) {
          const key = remindKey(ev, phase)
          const t = scheduledTimeouts.get(key)
          if (t) {
            clearTimeout(t)
            scheduledTimeouts.delete(key)
          }
        }
        return 'off'
      }
      if (!ev.time) return 'no-time'
      const start = eventStartMs(ev)
      if (start == null) return 'no-time'
      if (start <= Date.now()) return 'expired'

      this.remindEventIds = [...this.remindEventIds, eventId]
      this.firedRemindKeys = this.firedRemindKeys.filter(
        k => !k.startsWith(`${ev.id}@${ev.date}@`)
      )
      this.startReminderWatch()
      this.scheduleEventReminder(ev)
      return 'on'
    },

    /** 设置提醒开关（非切换语义） */
    async setRemindEnabled(
      eventId: string,
      enabled: boolean
    ): Promise<'on' | 'off' | 'expired' | 'no-time'> {
      const on = this.remindEventIds.includes(eventId)
      if (enabled === on) return on ? 'on' : 'off'
      return this.toggleRemind(eventId)
    },

    /** 登录后或进入消息页时：拉取日程并启动提醒监听 */
    async ensureReminderWatch() {
      if (!this.initialized) {
        await this.fetchEvents()
      } else {
        this.startReminderWatch()
      }
      await this.checkReminders()
    },

    /** 为单个日程设置精确超时（提前提醒 + 到点提醒） */
    scheduleEventReminder(ev: CalendarEvent) {
      const start = eventStartMs(ev)
      if (start == null) return
      const aheadAt = start - REMIND_AHEAD_MINUTES * 60 * 1000
      this.scheduleReminderTimeout(ev, 'ahead', aheadAt)
      this.scheduleReminderTimeout(ev, 'start', start)
    },

    scheduleReminderTimeout(ev: CalendarEvent, phase: 'ahead' | 'start', fireAt: number) {
      const key = remindKey(ev, phase)
      const existing = scheduledTimeouts.get(key)
      if (existing) clearTimeout(existing)

      const delay = fireAt - Date.now()
      if (delay <= 0) {
        void this.checkReminders()
        return
      }
      if (delay > 2_147_000_000) return
      const t = setTimeout(() => {
        scheduledTimeouts.delete(key)
        void this.checkReminders()
      }, delay)
      scheduledTimeouts.set(key, t)
    },

    async fireReminderPhase(ev: CalendarEvent, phase: 'ahead' | 'start') {
      const key = remindKey(ev, phase)
      if (this.firedRemindKeys.includes(key)) return
      this.firedRemindKeys = [...this.firedRemindKeys, key]
      try {
        const res = await calendarApi.fireReminder(ev.id, phase)
        if (res.code !== 200) {
          this.firedRemindKeys = this.firedRemindKeys.filter(k => k !== key)
          console.warn('[Calendar] 写入提醒消息失败:', res.message)
          return
        }
        const timePart = `${ev.date} ${ev.time || ''}`.trim()
        const body =
          phase === 'start'
            ? t('calendar.remindStartedBody', { time: timePart, title: ev.title })
            : t('calendar.remindAheadBody', { time: timePart, title: ev.title })
        void import('./notifications').then(({ useNotificationsStore }) => {
          void useNotificationsStore().refreshFromSocket({
            type: 'calendar_remind',
            phase,
            content: body,
            relatedId: ev.id
          })
        })
      } catch (e) {
        this.firedRemindKeys = this.firedRemindKeys.filter(k => k !== key)
        console.warn('[Calendar] 写入提醒消息异常:', e)
      }
    },

    /** 扫描到期提醒并写入消息通知列表 */
    async checkReminders() {
      if (!this.remindEventIds.length) return
      const now = Date.now()
      const windowMs = REMIND_AHEAD_MINUTES * 60 * 1000
      for (const id of [...this.remindEventIds]) {
        const ev = this.events.find(e => e.id === id)
        if (!ev) {
          this.remindEventIds = this.remindEventIds.filter(x => x !== id)
          continue
        }
        const start = eventStartMs(ev)
        if (start == null) continue

        const snoozeUntil = this.snoozedUntil[id]
        if (snoozeUntil && now < snoozeUntil) continue
        if (snoozeUntil && now >= snoozeUntil) {
          delete this.snoozedUntil[id]
        }

        if (now > start + 60_000) {
          this.remindEventIds = this.remindEventIds.filter(x => x !== id)
          continue
        }

        const aheadKey = remindKey(ev, 'ahead')
        if (
          !this.firedRemindKeys.includes(aheadKey) &&
          now >= start - windowMs &&
          now < start
        ) {
          await this.fireReminderPhase(ev, 'ahead')
        }

        const startKey = remindKey(ev, 'start')
        if (
          !this.firedRemindKeys.includes(startKey) &&
          now >= start &&
          now <= start + 60_000
        ) {
          await this.fireReminderPhase(ev, 'start')
        }
      }
    },

    /** 启动本地提醒轮询（幂等） */
    startReminderWatch() {
      // 为已开启的提醒补建精确超时
      for (const id of this.remindEventIds) {
        const ev = this.events.find(e => e.id === id)
        if (ev) this.scheduleEventReminder(ev)
      }
      if (remindTimer) return
      void this.checkReminders()
      remindTimer = setInterval(() => {
        void this.checkReminders()
      }, REMIND_POLL_MS)
    },

    stopReminderWatch() {
      if (remindTimer) {
        clearInterval(remindTimer)
        remindTimer = null
      }
      for (const t of scheduledTimeouts.values()) clearTimeout(t)
      scheduledTimeouts.clear()
    },

    /** 稍后提醒：仅延迟通知，日程时间不变 */
    snoozeReminderNotification(eventId: string, minutes = 10): boolean {
      const ev = this.events.find(e => e.id === eventId)
      if (!ev) return false

      const until = Date.now() + minutes * 60 * 1000
      this.snoozedUntil = { ...this.snoozedUntil, [eventId]: until }

      const key = `snooze:${eventId}`
      const existing = scheduledTimeouts.get(key)
      if (existing) clearTimeout(existing)

      const delay = until - Date.now()
      if (delay <= 0 || delay > 2_147_000_000) return false

      const timer = setTimeout(() => {
        scheduledTimeouts.delete(key)
        void this.fireSnoozeNotification(eventId)
      }, delay)
      scheduledTimeouts.set(key, timer)
      return true
    },

    /** 延时结束后再次写入提醒（不受已触发 ahead 键限制） */
    async fireSnoozeNotification(eventId: string) {
      delete this.snoozedUntil[eventId]
      const ev = this.events.find(e => e.id === eventId)
      if (!ev || !this.remindEventIds.includes(eventId)) return

      try {
        const res = await calendarApi.fireReminder(ev.id, 'ahead')
        if (res.code !== 200) {
          console.warn('[Calendar] 稍后提醒写入失败:', res.message)
          return
        }
        const timePart = `${ev.date} ${ev.time || ''}`.trim()
        const body = t('calendar.remindAheadBody', { time: timePart, title: ev.title })
        void import('./notifications').then(({ useNotificationsStore }) => {
          void useNotificationsStore().refreshFromSocket({
            type: 'calendar_remind',
            phase: 'ahead',
            content: body,
            relatedId: ev.id
          })
        })
      } catch (e) {
        console.warn('[Calendar] 稍后提醒异常:', e)
      }
    },

    /** 推迟日程：将开始（及结束）时间顺延若干分钟 */
    async postponeEventByMinutes(eventId: string, minutes = 10): Promise<boolean> {
      const ev = this.events.find(e => e.id === eventId)
      if (!ev?.time) return false
      const start = eventStartMs(ev)
      if (start == null) return false

      const delta = minutes * 60 * 1000
      const newStartMs = start + delta
      const newDate = dateKeyFromTs(newStartMs)
      const newTime = timeFromMs(newStartMs)

      let newEndTime: string | undefined
      const endMs = eventEndMs(ev)
      if (endMs != null) {
        const newEndMs = endMs + delta
        newEndTime =
          dateKeyFromTs(newEndMs) === newDate ? timeFromMs(newEndMs) : '23:59'
      }

      clearEventReminderTimeouts(ev)
      this.firedRemindKeys = this.firedRemindKeys.filter(k => !k.startsWith(`${eventId}@`))
      delete this.snoozedUntil[eventId]

      const ok = await this.updateEvent(eventId, {
        title: ev.title,
        date: newDate,
        time: newTime,
        endTime: newEndTime ?? ev.endTime,
        color: ev.color
      })

      if (ok && this.remindEventIds.includes(eventId)) {
        const updated = this.events.find(e => e.id === eventId)
        if (updated) this.scheduleEventReminder(updated)
      }
      return ok
    },

    /** 取消某日程的提醒开关 */
    async cancelReminderForEvent(eventId: string) {
      await this.setRemindEnabled(eventId, false)
      delete this.snoozedUntil[eventId]
      const ev = this.events.find(e => e.id === eventId)
      if (ev) clearEventReminderTimeouts(ev)
    }
  },

  // 持久化事件列表与提醒开关
  persist: {
    key: 'linkx-calendar',
    paths: ['events', 'remindEventIds', 'firedRemindKeys', 'snoozedUntil']
  }
})
