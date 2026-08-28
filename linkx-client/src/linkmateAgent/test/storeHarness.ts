/**
 * 作者：yangleduo
 */
import { createPinia, setActivePinia } from 'pinia'
import type { ChatSession, ContactItem } from '../../types'
import { useAppStore } from '../../stores/app'
import { useContactsStore } from '../../stores/contacts'

export function setupAgentTestStores(options: {
  sessions?: ChatSession[]
  currentSession?: ChatSession | null
  navKey?: import('../../types').NavKey
  contacts?: ContactItem[]
}) {
  setActivePinia(createPinia())
  const app = useAppStore()
  const contacts = useContactsStore()

  if (options.sessions) {
    app.sessions = [...options.sessions]
  }
  if (options.currentSession !== undefined) {
    app.currentSessionId = options.currentSession?.id ?? null
  }
  if (options.navKey) {
    app.navKey = options.navKey
  }
  if (options.contacts) {
    contacts.items = [...options.contacts]
  }

  return { app, contacts }
}
