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

let chatInputBridge: AgentChatInputBridge | null = null

export function registerAgentChatInputBridge(bridge: AgentChatInputBridge | null) {
  chatInputBridge = bridge
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

export function clearSimulatedInput() {
  chatInputBridge?.clearInput()
}

export function getTypingThinkingLabel(): string {
  return t('linkmateAgent.thinkingTyping')
}
