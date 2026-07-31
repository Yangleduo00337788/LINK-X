import { getAccessToken } from './request'

export type AdminRealtimeEvent = {
  type?: string
  relatedId?: string
  title?: string
  ts?: number
  [key: string]: unknown
}

type Handler = (event: AdminRealtimeEvent) => void

let abort: AbortController | null = null
const handlers = new Set<Handler>()

function apiBase() {
  return (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
}

function parseEventData(raw: string): AdminRealtimeEvent | null {
  try {
    return JSON.parse(raw) as AdminRealtimeEvent
  } catch {
    return null
  }
}

function dispatch(event: AdminRealtimeEvent) {
  handlers.forEach((h) => {
    try {
      h(event)
    } catch {
      /* ignore handler errors */
    }
  })
}

async function connectLoop() {
  while (abort && !abort.signal.aborted) {
    const token = getAccessToken()
    if (!token) {
      await sleep(3000)
      continue
    }
    try {
      const res = await fetch(`${apiBase()}/admin/events/stream`, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        },
        signal: abort.signal,
      })
      if (!res.ok || !res.body) {
        await sleep(3000)
        continue
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      while (abort && !abort.signal.aborted) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const parts = buffer.split('\n\n')
        buffer = parts.pop() || ''
        for (const chunk of parts) {
          const lines = chunk.split('\n')
          let eventName = 'message'
          const dataLines: string[] = []
          for (const line of lines) {
            if (line.startsWith('event:')) eventName = line.slice(6).trim()
            else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
          }
          if (eventName !== 'admin_event' || !dataLines.length) continue
          const parsed = parseEventData(dataLines.join('\n'))
          if (parsed) dispatch(parsed)
        }
      }
    } catch (e) {
      if (abort?.signal.aborted) return
      // network / abort
    }
    await sleep(2000)
  }
}

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms))
}

/** 启动管理端实时事件流（全局单例） */
export function startAdminRealtime() {
  if (abort) return
  abort = new AbortController()
  void connectLoop()
}

export function stopAdminRealtime() {
  abort?.abort()
  abort = null
}

export function onAdminRealtimeEvent(handler: Handler) {
  handlers.add(handler)
  return () => {
    handlers.delete(handler)
  }
}
