import { defineStore } from 'pinia'

export interface CalendarEvent {
  id: string
  title: string
  date: string
  time?: string
  color?: string
}

function dateKeyFromTs(ts: number) {
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function startOfDay(ts: number) {
  const d = new Date(ts)
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}

const today = startOfDay(Date.now())
const day = 86400000

const initialEvents: CalendarEvent[] = [
  { id: 'e1', title: '项目周会', date: dateKeyFromTs(today), time: '10:00', color: 'var(--lx-accent)' },
  { id: 'e2', title: '午餐 · 张三', date: dateKeyFromTs(today), time: '12:30', color: 'var(--lx-success)' },
  { id: 'e3', title: '代码评审', date: dateKeyFromTs(today + day), time: '15:00', color: 'var(--lx-accent)' },
  { id: 'e4', title: 'LinkX 迭代规划', date: dateKeyFromTs(today + day * 2), time: '09:30', color: '#722ed1' },
  { id: 'e5', title: '健身', date: dateKeyFromTs(today + day * 3), time: '19:00', color: 'var(--lx-success)' },
  { id: 'e6', title: '友链活动筹备', date: dateKeyFromTs(today + day * 5), time: '14:00', color: '#fa8c16' }
]

export const useCalendarStore = defineStore('calendar', {
  state: () => ({
    selectedDate: startOfDay(Date.now()),
    events: [...initialEvents] as CalendarEvent[]
  }),

  getters: {
    selectedDateKey(state): string {
      return dateKeyFromTs(state.selectedDate)
    },

    eventsOnSelected(state): CalendarEvent[] {
      const key = dateKeyFromTs(state.selectedDate)
      return state.events
        .filter(e => e.date === key)
        .sort((a, b) => (a.time || '').localeCompare(b.time || ''))
    },

    upcomingEvents(state): CalendarEvent[] {
      const from = dateKeyFromTs(state.selectedDate)
      return [...state.events]
        .filter(e => e.date >= from)
        .sort((a, b) => a.date.localeCompare(b.date) || (a.time || '').localeCompare(b.time || ''))
        .slice(0, 12)
    },

    eventDateKeys(state): Set<string> {
      return new Set(state.events.map(e => e.date))
    }
  },

  actions: {
    setSelectedDate(ts: number) {
      this.selectedDate = startOfDay(ts)
    },

    goToday() {
      this.selectedDate = startOfDay(Date.now())
    },

    hasEventOn(year: number, month: number, date: number) {
      const key = `${year}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`
      return this.eventDateKeys.has(key)
    },

    addEvent(payload: Omit<CalendarEvent, 'id'>) {
      const id = `evt-${Date.now()}`
      this.events.push({ ...payload, id })
      return id
    },

    updateEvent(id: string, patch: Partial<Omit<CalendarEvent, 'id'>>) {
      const event = this.events.find(e => e.id === id)
      if (!event) return false
      Object.assign(event, patch)
      return true
    },

    removeEvent(id: string) {
      const idx = this.events.findIndex(e => e.id === id)
      if (idx === -1) return false
      this.events.splice(idx, 1)
      return true
    }
  },

  persist: {
    key: 'linkx-calendar',
    paths: ['events']
  }
})
