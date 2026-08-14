/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import * as linkmateApi from '../api/linkmate'
import type { LinkMateMessage, LinkMateSession, LinkMateStatus } from '../api/linkmate'

export type LinkMatePanelState = 'closed' | 'open' | 'collapsed'

const PANEL_WIDTH_STORAGE_KEY = 'linkx-linkmate-panel-width'
const DEEP_THINKING_STORAGE_KEY = 'linkx-linkmate-deep-thinking'
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

function loadDeepThinking(): boolean {
  try {
    return localStorage.getItem(DEEP_THINKING_STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function persistDeepThinking(enabled: boolean) {
  try {
    localStorage.setItem(DEEP_THINKING_STORAGE_KEY, enabled ? '1' : '0')
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
    deepThinking: loadDeepThinking(),
    showHistory: false,
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
    },
    deepThinkingSupported(state): boolean {
      return state.status?.deepThinkingSupported ?? false
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

    setDeepThinking(enabled: boolean) {
      this.deepThinking = enabled
      persistDeepThinking(enabled)
    },

    isSessionEmpty(sessionId: string): boolean {
      const msgs = this.messagesBySession[sessionId]
      return !msgs || msgs.length === 0
    },

    finalizeMessageMetrics(sessionId: string, assistantId: string, reasoningEndedAt = 0) {
      const msgs = this.messagesBySession[sessionId]
      const msg = msgs?.find(m => m.id === assistantId)
      if (!msg?.responseStartedAt) return

      if (msg.reasoningContent?.trim() && msg.reasoningDurationMs == null) {
        const end = reasoningEndedAt > 0 ? reasoningEndedAt : Date.now()
        this.setAssistantReasoningDuration(sessionId, assistantId, end - msg.responseStartedAt)
      }

      if (msg.responseDurationMs == null) {
        this.setAssistantResponseDuration(
          sessionId,
          assistantId,
          Date.now() - msg.responseStartedAt
        )
      }
    },

    finalizeActiveStreamMetrics() {
      const sessionId = this.activeSessionId
      if (!sessionId) return
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const assistant = [...msgs].reverse().find(
        m => m.role === 'assistant' && m.id.startsWith('temp-assistant')
      )
      if (!assistant) return
      this.finalizeMessageMetrics(sessionId, assistant.id)
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

    updateAssistantReasoning(sessionId: string, assistantId: string, reasoningContent: string) {
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const idx = msgs.findIndex(m => m.id === assistantId)
      if (idx < 0) return
      const next = [...msgs]
      const current = next[idx]
      next[idx] = {
        ...current,
        reasoningContent,
        responseStartedAt: current.responseStartedAt ?? Date.now()
      }
      this.patchSessionMessages(sessionId, next)
    },

    setAssistantResponseDuration(sessionId: string, assistantId: string, durationMs: number) {
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const idx = msgs.findIndex(m => m.id === assistantId)
      if (idx < 0) return
      const next = [...msgs]
      next[idx] = { ...next[idx], responseDurationMs: durationMs }
      this.patchSessionMessages(sessionId, next)
    },

    setAssistantReasoningDuration(sessionId: string, assistantId: string, durationMs: number) {
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const idx = msgs.findIndex(m => m.id === assistantId)
      if (idx < 0) return
      const next = [...msgs]
      next[idx] = { ...next[idx], reasoningDurationMs: durationMs }
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
        this.status = { enabled: false, model: '', dailyTokenLimit: 0, dailyTokenUsed: 0, deepThinkingSupported: false }
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

    async startNewChat() {
      if (this.streaming) return
      if (this.activeSessionId && this.isSessionEmpty(this.activeSessionId)) {
        this.showHistory = false
        return this.sessions.find(s => s.id === this.activeSessionId) ?? null
      }
      const session = await this.createSession()
      this.showHistory = false
      return session
    },

    async selectSession(sessionId: string) {
      if (this.streaming) return
      this.activeSessionId = sessionId
      this.showHistory = false
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
              createTime: row.createTime,
              reasoningContent: row.reasoningContent,
              responseDurationMs: row.responseDurationMs
            }))
          )
        }
      } finally {
        this.loadingMessages = false
      }
    },

    async deleteSession(sessionId: string) {
      if (this.streaming) return
      const res = await linkmateApi.deleteSession(sessionId)
      if (res.code !== 200) {
        throw new Error(res.message || '删除失败')
      }
      this.sessions = this.sessions.filter(s => s.id !== sessionId)
      const { [sessionId]: _, ...rest } = this.messagesBySession
      this.messagesBySession = rest
      if (this.activeSessionId === sessionId) {
        this.activeSessionId = this.sessions[0]?.id ?? ''
        if (this.activeSessionId && !this.messagesBySession[this.activeSessionId]) {
          await this.loadMessages(this.activeSessionId)
        }
      }
    },

    abortStream() {
      this.finalizeActiveStreamMetrics()
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
      const useDeepThinking = this.deepThinking && this.deepThinkingSupported
      const assistantId = `temp-assistant-${Date.now()}`
      const assistantMsg: LinkMateMessage = {
        id: assistantId,
        sessionId,
        role: 'assistant',
        content: '',
        createTime: '',
        responseStartedAt: Date.now()
      }
      const list = [...(this.messagesBySession[sessionId] ?? []), userMsg, assistantMsg]
      this.patchSessionMessages(sessionId, list)

      this.streaming = true
      this.streamAbort = new AbortController()
      let assistantContent = ''
      let assistantReasoning = ''
      let reasoningEndedAt = 0

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
            onReasoningDelta: chunk => {
              assistantReasoning += chunk
              this.updateAssistantReasoning(sessionId, assistantId, assistantReasoning)
            },
            onDelta: chunk => {
              if (assistantReasoning && !reasoningEndedAt) {
                reasoningEndedAt = Date.now()
                const msgs = this.messagesBySession[sessionId]
                const msg = msgs?.find(m => m.id === assistantId)
                if (msg?.responseStartedAt) {
                  this.setAssistantReasoningDuration(
                    sessionId,
                    assistantId,
                    reasoningEndedAt - msg.responseStartedAt
                  )
                }
              }
              assistantContent += chunk
              this.updateAssistantContent(sessionId, assistantId, assistantContent)
            },
            onDone: (messageId, sid) => {
              this.finalizeMessageMetrics(sessionId, assistantId, reasoningEndedAt)
              this.finalizeAssistantMessage(sessionId, assistantId, messageId, sid)
              this.activeSessionId = sid
              void this.loadSessions()
            },
            onError: message => {
              this.updateAssistantContent(sessionId, assistantId, message)
              throw new Error(message)
            }
          },
          this.streamAbort.signal,
          useDeepThinking
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
        this.finalizeMessageMetrics(sessionId, assistantId, reasoningEndedAt)
        this.streaming = false
        this.streamAbort = null
        void this.loadStatus()
      }
    }
  }
})
