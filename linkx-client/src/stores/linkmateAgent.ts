/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import { getActionDefinition } from '../linkmateAgent/actions'
import {
  actionStepDelayMs,
  describeLinkMateAction,
  executeLinkMateAction
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
    isActive(state): boolean {
      return state.run.phase !== 'idle'
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
      return {
        currentNav: app.navKey,
        currentSessionId: session?.id,
        currentSessionTitle: session?.name
      }
    },

    resetRun() {
      settlePendingConfirm(false)
      this.run = {
        phase: 'idle',
        currentAction: null,
        queue: [],
        completed: [],
        cancelled: false,
        thinkingText: '',
        cursor: createDefaultCursor()
      }
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

        await this.simulateActionCursor(action)
        if (this.run.cancelled) break

        const result = await executeLinkMateAction(action)
        this.run.completed.push({ action, result })
        if (this.run.queue.length > 0 && !this.run.cancelled) {
          await sleep(actionStepDelayMs())
        }
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
