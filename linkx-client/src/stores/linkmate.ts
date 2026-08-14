/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import * as linkmateApi from '../api/linkmate'
import type { LinkMateMessage, LinkMateSession, LinkMateStatus } from '../api/linkmate'

export type LinkMatePanelState = 'closed' | 'open' | 'collapsed'

const PANEL_WIDTH_STORAGE_KEY = 'linkx-linkmate-panel-width'
export const LINKMATE_PANEL_WIDTH_MIN = 280
export const LINKMATE_PANEL_WIDTH_MAX = 640
export const LINKMATE_PANEL_WIDTH_DEFAULT = 380

function clampPanelWidth(width: number): number {
  return Math.min(LINKMATE_PANEL_WIDTH_MAX, Math.max(LINKMATE_PANEL_WIDTH_MIN, width))
}

function loadPanelWidth(): number {
  try {
    const raw = localStorage.getItem(PANEL_WIDTH_STORAGE_KEY)
    const parsed = raw ? Number(raw) : NaN
    if (Number.isFinite(parsed)) {
      return clampPanelWidth(parsed)
    }
  } catch {
    /* ignore */
  }
  return LINKMATE_PANEL_WIDTH_DEFAULT
}

function persistPanelWidth(width: number) {
  try {
    localStorage.setItem(PANEL_WIDTH_STORAGE_KEY, String(width))
  } catch {
    /* ignore */
  }
}

export const useLinkMateStore = defineStore('linkmate', {
  state: () => ({
    status: null as LinkMateStatus | null,
    sessions: [] as LinkMateSession[],
    activeSessionId: '' as string,
    messagesBySession: {} as Record<string, LinkMateMessage[]>,
    loadingSessions: false,
    loadingMessages: false,
    streaming: false,
    streamAbort: null as AbortController | null,
    inputDraft: '',
    /** 右侧对话面板：closed 未打开 / open 展开 / collapsed 已折叠（侧栏可恢复） */
    panelState: 'closed' as LinkMatePanelState,
    /** 侧栏宽度（px），持久化至 localStorage */
    panelWidth: loadPanelWidth()
  }),

  getters: {
    panelExpanded(state): boolean {
      return state.panelState === 'open'
    },
    panelCollapsed(state): boolean {
      return state.panelState === 'collapsed'
    },
    activeSession(state): LinkMateSession | null {
      return state.sessions.find(s => s.id === state.activeSessionId) ?? null
    },
    activeMessages(state): LinkMateMessage[] {
      if (!state.activeSessionId) return []
      return state.messagesBySession[state.activeSessionId] ?? []
    },
    enabled(state): boolean {
      return state.status?.enabled ?? false
    }
  },

  actions: {
    openPanel() {
      this.panelState = 'open'
    },

    collapsePanel() {
      if (this.panelState === 'open') {
        this.panelState = 'collapsed'
      }
    },

    expandPanel() {
      if (this.panelState === 'collapsed') {
        this.panelState = 'open'
      }
    },

    setPanelWidth(width: number) {
      const next = clampPanelWidth(width)
      this.panelWidth = next
      persistPanelWidth(next)
    },

    async ensurePanelReady() {
      await this.loadStatus()
      if (!this.enabled) return
      await this.loadSessions()
      if (!this.activeSessionId && this.sessions.length > 0) {
        await this.selectSession(this.sessions[0].id)
      }
    },

    patchSessionMessages(sessionId: string, messages: LinkMateMessage[]) {
      this.messagesBySession = { ...this.messagesBySession, [sessionId]: messages }
    },

    updateAssistantContent(sessionId: string, assistantId: string, content: string) {
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const idx = msgs.findIndex(m => m.id === assistantId)
      if (idx < 0) return
      const next = [...msgs]
      next[idx] = { ...next[idx], content }
      this.patchSessionMessages(sessionId, next)
    },

    finalizeAssistantMessage(
      sessionId: string,
      assistantId: string,
      messageId: string,
      sid: string
    ) {
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const idx = msgs.findIndex(m => m.id === assistantId)
      if (idx < 0) return
      const next = [...msgs]
      next[idx] = { ...next[idx], id: messageId, sessionId: sid }
      this.patchSessionMessages(sessionId, next)
    },

    async loadStatus() {
      try {
        const res = await linkmateApi.getStatus()
        if (res.code === 200 && res.data) {
          this.status = res.data
        }
      } catch {
        this.status = { enabled: false, model: '', dailyTokenLimit: 0, dailyTokenUsed: 0 }
      }
    },

    async loadSessions() {
      this.loadingSessions = true
      try {
        const res = await linkmateApi.listSessions()
        if (res.code === 200 && Array.isArray(res.data)) {
          this.sessions = res.data.map(row => ({
            id: String(row.id),
            title: row.title,
            updateTime: row.updateTime
          }))
        }
      } finally {
        this.loadingSessions = false
      }
    },

    async createSession() {
      const res = await linkmateApi.createSession()
      if (res.code !== 200 || !res.data) {
        throw new Error(res.message || '创建对话失败')
      }
      const session: LinkMateSession = {
        id: String(res.data.id),
        title: res.data.title,
        updateTime: res.data.updateTime
      }
      this.sessions.unshift(session)
      this.activeSessionId = session.id
      this.patchSessionMessages(session.id, [])
      return session
    },

    async selectSession(sessionId: string) {
      this.activeSessionId = sessionId
      if (!this.messagesBySession[sessionId]) {
        await this.loadMessages(sessionId)
      }
    },

    async loadMessages(sessionId: string) {
      this.loadingMessages = true
      try {
        const res = await linkmateApi.listMessages(sessionId)
        if (res.code === 200 && Array.isArray(res.data)) {
          this.patchSessionMessages(
            sessionId,
            res.data.map(row => ({
              id: String(row.id),
              sessionId: String(row.sessionId),
              role: row.role as LinkMateMessage['role'],
              content: row.content,
              createTime: row.createTime
            }))
          )
        }
      } finally {
        this.loadingMessages = false
      }
    },

    async deleteSession(sessionId: string) {
      const res = await linkmateApi.deleteSession(sessionId)
      if (res.code !== 200) {
        throw new Error(res.message || '删除失败')
      }
      this.sessions = this.sessions.filter(s => s.id !== sessionId)
      const { [sessionId]: _, ...rest } = this.messagesBySession
      this.messagesBySession = rest
      if (this.activeSessionId === sessionId) {
        this.activeSessionId = this.sessions[0]?.id ?? ''
      }
    },

    abortStream() {
      if (this.streamAbort) {
        this.streamAbort.abort()
        this.streamAbort = null
      }
      this.streaming = false
    },

    async sendMessage(text: string) {
      const content = text.trim()
      if (!content || this.streaming) return

      let sessionId = this.activeSessionId
      if (!sessionId) {
        const session = await this.createSession()
        sessionId = session.id
      }

      const userMsg: LinkMateMessage = {
        id: `temp-user-${Date.now()}`,
        sessionId,
        role: 'user',
        content,
        createTime: ''
      }
      const assistantId = `temp-assistant-${Date.now()}`
      const assistantMsg: LinkMateMessage = {
        id: assistantId,
        sessionId,
        role: 'assistant',
        content: '',
        createTime: ''
      }
      const list = [...(this.messagesBySession[sessionId] ?? []), userMsg, assistantMsg]
      this.patchSessionMessages(sessionId, list)

      this.streaming = true
      this.streamAbort = new AbortController()
      let assistantContent = ''

      try {
        await linkmateApi.streamChat(
          content,
          sessionId,
          {
            onStart: id => {
              if (!this.activeSessionId) {
                this.activeSessionId = id
              }
              const idx = this.sessions.findIndex(s => s.id === sessionId)
              if (idx === -1) {
                this.sessions.unshift({
                  id,
                  title: content.slice(0, 40),
                  updateTime: ''
                })
              }
            },
            onDelta: chunk => {
              assistantContent += chunk
              this.updateAssistantContent(sessionId, assistantId, assistantContent)
            },
            onDone: (messageId, sid) => {
              this.finalizeAssistantMessage(sessionId, assistantId, messageId, sid)
              this.activeSessionId = sid
              void this.loadSessions()
            },
            onError: message => {
              this.updateAssistantContent(sessionId, assistantId, message)
              throw new Error(message)
            }
          },
          this.streamAbort.signal
        )
      } catch (err) {
        if (!assistantContent) {
          this.updateAssistantContent(
            sessionId,
            assistantId,
            err instanceof Error ? err.message : '发送失败，请稍后重试'
          )
        }
      } finally {
        this.streaming = false
        this.streamAbort = null
        void this.loadStatus()
      }
    }
  }
})
