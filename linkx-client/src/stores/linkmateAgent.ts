/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import { getActionDefinition } from '../linkmateAgent/actions'
import {
  actionStepDelayMs,
  describeLinkMateAction,
  executeLinkMateAction,
  prepareActionForSimulation,
  summarizeAgentRun
} from '../linkmateAgent/executor'
import type {
  LinkMateAgentAction,
  LinkMateAgentRunState,
  LinkMateClientContext
} from '../linkmateAgent/types'
import { parseAgentAction } from '../linkmateAgent/types'
import {
  animateCursorPath,
  buildCursorSteps,
  getThinkingLabel
} from '../linkmateAgent/cursorSim'
import { useAppStore } from './app'
import { t } from '../i18n'
import { OFFICIAL_NOTIFY_SESSION_ID, SYSTEM_NOTIFY_SESSION_ID } from '../types'

const AGENT_MODE_STORAGE_KEY = 'linkx-linkmate-agent-mode'

let pendingConfirmResolver: ((approved: boolean) => void) | null = null

function loadAgentMode(): boolean {
  try {
    return localStorage.getItem(AGENT_MODE_STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function persistAgentMode(enabled: boolean) {
  try {
    localStorage.setItem(AGENT_MODE_STORAGE_KEY, enabled ? '1' : '0')
  } catch {
    /* ignore */
  }
}

function sleep(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

function createDefaultCursor() {
  return {
    visible: false,
    x: Math.round(window.innerWidth * 0.5),
    y: Math.round(window.innerHeight * 0.45),
    clicking: false
  }
}

function settlePendingConfirm(approved: boolean) {
  const resolver = pendingConfirmResolver
  pendingConfirmResolver = null
  resolver?.(approved)
}

export const useLinkMateAgentStore = defineStore('linkmateAgent', {
  state: () => ({
    agentMode: loadAgentMode(),
    run: {
      phase: 'idle',
      currentAction: null,
      plannedActions: [],
      queue: [],
      completed: [],
      cancelled: false,
      thinkingText: '',
      cursor: createDefaultCursor()
    } as LinkMateAgentRunState
  }),

  getters: {
    isRunning(state): boolean {
      return state.run.phase === 'executing' || state.run.phase === 'confirming'
    },
    isPlanning(state): boolean {
      return state.run.phase === 'planning'
    },
    isActive(state): boolean {
      return state.run.phase !== 'idle'
    },
    plannedStepLabels(state): string[] {
      return state.run.plannedActions.map(action => describeLinkMateAction(action))
    },
    currentStepLabel(state): string {
      if (!state.run.currentAction) return ''
      return describeLinkMateAction(state.run.currentAction)
    }
  },

  actions: {
    toggleAgentMode() {
      this.agentMode = !this.agentMode
      persistAgentMode(this.agentMode)
    },

    setAgentMode(enabled: boolean) {
      this.agentMode = enabled
      persistAgentMode(enabled)
    },

    buildClientContext(): LinkMateClientContext {
      const app = useAppStore()
      const session = app.currentSession
      const recentSessions = app.sessions
        .filter(
          item =>
            item.isReal &&
            !item.isSystemNotify &&
            !item.isOfficialNotify &&
            item.id !== SYSTEM_NOTIFY_SESSION_ID &&
            item.id !== OFFICIAL_NOTIFY_SESSION_ID
        )
        .slice(0, 25)
        .map(item => `${item.isGroup ? '群聊' : '好友'}「${item.name}」#${item.id}`)
        .join('；')
      return {
        currentNav: app.navKey,
        currentSessionId: session?.id,
        currentSessionTitle: session?.name,
        recentSessions: recentSessions || undefined
      }
    },

    resetRun() {
      settlePendingConfirm(false)
      this.run = {
        phase: 'idle',
        currentAction: null,
        plannedActions: [],
        queue: [],
        completed: [],
        cancelled: false,
        thinkingText: '',
        cursor: createDefaultCursor()
      }
    },

    beginPlanning() {
      this.run.plannedActions = []
      this.run.phase = 'planning'
      this.run.thinkingText = t('linkmateAgent.planningActions')
    },

    clearPlanning() {
      this.run.plannedActions = []
      if (this.run.phase === 'planning') {
        this.run.phase = 'idle'
        this.run.thinkingText = ''
      }
    },

    previewToolCall(payload: { id: string; name: string; arguments: string }) {
      const action = parseAgentAction({
        id: payload.id,
        name: payload.name,
        arguments: payload.arguments
      })
      if (!action) return
      if (this.run.phase !== 'planning' && this.run.phase !== 'idle') return

      if (this.run.phase === 'idle') {
        this.beginPlanning()
      }

      const idx = this.run.plannedActions.findIndex(item => item.id === action.id)
      if (idx >= 0) {
        this.run.plannedActions[idx] = action
      } else {
        this.run.plannedActions.push(action)
      }
      this.run.thinkingText = t('linkmateAgent.planningActions')
    },

    cancelRun() {
      this.run.cancelled = true
      settlePendingConfirm(false)
      this.run.phase = 'idle'
      this.run.currentAction = null
      this.run.queue = []
      this.run.thinkingText = ''
      this.run.cursor.visible = false
      this.run.cursor.clicking = false
    },

    setThinkingText(text: string) {
      this.run.thinkingText = text
    },

    setCursorPosition(x: number, y: number) {
      this.run.cursor.x = x
      this.run.cursor.y = y
    },

    setCursorClicking(clicking: boolean) {
      this.run.cursor.clicking = clicking
    },

    showAgentCursor() {
      this.run.cursor.visible = true
    },

    hideAgentCursor() {
      this.run.cursor.visible = false
      this.run.cursor.clicking = false
    },

    async simulateActionCursor(action: LinkMateAgentAction) {
      this.setThinkingText(getThinkingLabel(action))
      this.showAgentCursor()
      const steps = buildCursorSteps(action)
      await animateCursorPath(steps, {
        getPosition: () => ({ x: this.run.cursor.x, y: this.run.cursor.y }),
        setPosition: point => this.setCursorPosition(point.x, point.y),
        setClicking: clicking => this.setCursorClicking(clicking),
        setThinking: text => this.setThinkingText(text),
        isCancelled: () => this.run.cancelled
      })
    },

    approvePendingConfirm() {
      settlePendingConfirm(true)
    },

    rejectPendingConfirm() {
      settlePendingConfirm(false)
    },

    parseActionsFromPayload(actions: unknown): LinkMateAgentAction[] {
      if (!Array.isArray(actions)) return []
      const parsed: LinkMateAgentAction[] = []
      for (const item of actions) {
        if (!item || typeof item !== 'object') continue
        const row = item as Record<string, unknown>
        const action = parseAgentAction({
          id: row.id,
          name: row.name,
          arguments: row.arguments
        })
        if (action) parsed.push(action)
      }
      return parsed
    },

    async runActions(actions: LinkMateAgentAction[]) {
      if (!actions.length) return
      this.resetRun()
      this.run.phase = 'executing'
      this.run.queue = [...actions]

      while (this.run.queue.length > 0) {
        if (this.run.cancelled) break
        const action = this.run.queue.shift()!
        this.run.currentAction = action

        const def = getActionDefinition(action.name)
        if (def.requireConfirm) {
          this.setThinkingText(getThinkingLabel(action))
          this.run.phase = 'confirming'
          const approved = await this.waitForConfirm()
          if (!approved) {
            this.run.completed.push({
              action,
              result: { ok: false, message: t('linkmateAgent.userRejected') }
            })
            this.run.phase = 'executing'
            continue
          }
          this.run.phase = 'executing'
        }

        await prepareActionForSimulation(action)
        if (this.run.cancelled) break

        await this.simulateActionCursor(action)
        if (this.run.cancelled) break

        const result = await executeLinkMateAction(action)
        this.run.completed.push({ action, result })
        if (this.run.queue.length > 0 && !this.run.cancelled) {
          await sleep(actionStepDelayMs())
        }
      }

      const summary = summarizeAgentRun(this.run.completed)
      if (summary && !this.run.cancelled) {
        this.setThinkingText(summary)
        await sleep(1400)
      }

      this.run.currentAction = null
      this.run.thinkingText = ''
      this.hideAgentCursor()
      this.run.phase = 'idle'
    },

    waitForConfirm(): Promise<boolean> {
      settlePendingConfirm(false)
      return new Promise(resolve => {
        pendingConfirmResolver = resolve
      })
    }
  }
})
