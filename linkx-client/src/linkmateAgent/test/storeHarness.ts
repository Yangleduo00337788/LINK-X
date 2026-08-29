/**
 * 作者：yangleduo
 */
import { createPinia, setActivePinia } from 'pinia'
import type { ChatSession, NavKey } from '../../types'
import { useAppStore } from '../../stores/app'

export type AgentTestStoreOptions = {
  sessions: ChatSession[]
  navKey?: NavKey
  currentSession: ChatSession | null
}

export function setupAgentTestStores(options: AgentTestStoreOptions) {
  setActivePinia(createPinia())
  const app = useAppStore()
  app.sessions = [...options.sessions]
  app.navKey = options.navKey ?? 'chat'
  app.currentSessionId = options.currentSession?.id ?? null
  return { app }
}
