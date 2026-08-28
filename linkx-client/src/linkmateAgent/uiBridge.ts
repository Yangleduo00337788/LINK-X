/**
 * 作者：yangleduo
 */
import { t } from '../i18n'

export type AgentChatInputBridge = {
  setInputText: (text: string) => void
  getInputText: () => string
  focusInput: () => void
  clearInput: () => void
}

export type AgentCalendarModalBridge = {
  isOpen: () => boolean
  focusTitle: () => void
  setTitle: (title: string) => void
  setDate: (dateKey: string) => void
  setStartTime: (hm: string) => void
  setEndTime: (hm: string) => void
  save: () => Promise<boolean>
}

export type AgentNoteEditorBridge = {
  isOpen: () => boolean
  focusContent: () => void
  setContent: (text: string) => void
  save: () => Promise<boolean>
}

export type AgentComprehensiveSearchBridge = {
  isOpen: () => boolean
  focusKeyword: () => void
  setKeyword: (text: string) => void
  search: () => Promise<boolean>
}

let chatInputBridge: AgentChatInputBridge | null = null
let calendarModalBridge: AgentCalendarModalBridge | null = null
let noteEditorBridge: AgentNoteEditorBridge | null = null
let comprehensiveSearchBridge: AgentComprehensiveSearchBridge | null = null

export function registerAgentChatInputBridge(bridge: AgentChatInputBridge | null) {
  chatInputBridge = bridge
}

export function registerAgentCalendarModalBridge(bridge: AgentCalendarModalBridge | null) {
  calendarModalBridge = bridge
}

export function registerAgentNoteEditorBridge(bridge: AgentNoteEditorBridge | null) {
  noteEditorBridge = bridge
}

export function registerAgentComprehensiveSearchBridge(
  bridge: AgentComprehensiveSearchBridge | null
) {
  comprehensiveSearchBridge = bridge
}

export function getCalendarModalBridge(): AgentCalendarModalBridge | null {
  return calendarModalBridge
}

export function getNoteEditorBridge(): AgentNoteEditorBridge | null {
  return noteEditorBridge
}

export function getComprehensiveSearchBridge(): AgentComprehensiveSearchBridge | null {
  return comprehensiveSearchBridge
}

function charDelayMs(char: string): number {
  if (char === ' ' || char === '\n') return 72
  if (/[,.!?，。！？、；：]/.test(char)) return 210
  if (/[a-zA-Z0-9]/.test(char)) return 95
  return 118
}

function sleep(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

export async function simulateTyping(
  text: string,
  options: {
    isCancelled: () => boolean
    onProgress?: (partial: string) => void
  }
): Promise<boolean> {
  if (!chatInputBridge || !text) return false

  chatInputBridge.focusInput()
  chatInputBridge.setInputText('')
  await sleep(220)
  if (options.isCancelled()) return false

  let partial = ''
  for (const char of text) {
    if (options.isCancelled()) return false
    partial += char
    chatInputBridge.setInputText(partial)
    options.onProgress?.(partial)
    await sleep(charDelayMs(char))
  }
  return true
}

export async function simulateTypingInto(
  text: string,
  options: {
    isCancelled: () => boolean
    setText: (text: string) => void
    focus: () => void
    onProgress?: (partial: string) => void
  }
): Promise<boolean> {
  if (!text) return false
  options.focus()
  options.setText('')
  await sleep(220)
  if (options.isCancelled()) return false

  let partial = ''
  for (const char of text) {
    if (options.isCancelled()) return false
    partial += char
    options.setText(partial)
    options.onProgress?.(partial)
    await sleep(charDelayMs(char))
  }
  return true
}

export function clearSimulatedInput() {
  chatInputBridge?.clearInput()
}

export function getTypingThinkingLabel(): string {
  return t('linkmateAgent.thinkingTyping')
}
