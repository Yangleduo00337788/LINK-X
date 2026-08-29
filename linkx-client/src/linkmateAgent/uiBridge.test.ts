/**
 * 作者：yangleduo
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearSimulatedInput,
  getTypingThinkingLabel,
  registerAgentChatInputBridge,
  simulateTyping,
  simulateTypingInto
} from './uiBridge'

describe('uiBridge', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    window.__LM_CURSOR_FAST = true
  })

  afterEach(() => {
    registerAgentChatInputBridge(null)
    vi.useRealTimers()
  })

  it('simulateTyping updates chat bridge progressively', async () => {
    const values: string[] = []
    registerAgentChatInputBridge({
      setInputText: text => values.push(text),
      getInputText: () => values[values.length - 1] ?? '',
      focusInput: vi.fn(),
      clearInput: () => {
        values.length = 0
      }
    })

    const promise = simulateTyping('你好', { isCancelled: () => false })
    await vi.runAllTimersAsync()
    const ok = await promise

    expect(ok).toBe(true)
    expect(values[values.length - 1]).toBe('你好')
  })

  it('simulateTyping returns false without bridge or text', async () => {
    expect(await simulateTyping('x', { isCancelled: () => false })).toBe(false)
    registerAgentChatInputBridge({
      setInputText: vi.fn(),
      getInputText: () => '',
      focusInput: vi.fn(),
      clearInput: vi.fn()
    })
    expect(await simulateTyping('', { isCancelled: () => false })).toBe(false)
  })

  it('simulateTyping handles mixed character delays and progress', async () => {
    const progress: string[] = []
    registerAgentChatInputBridge({
      setInputText: vi.fn(),
      getInputText: () => '',
      focusInput: vi.fn(),
      clearInput: vi.fn()
    })
    const promise = simulateTyping('a，1 ', {
      isCancelled: () => false,
      onProgress: partial => progress.push(partial)
    })
    await vi.runAllTimersAsync()
    expect(await promise).toBe(true)
    expect(progress.at(-1)).toBe('a，1 ')
  })

  it('simulateTypingInto handles punctuation and newline', async () => {
    const values: string[] = []
    const promise = simulateTypingInto('hi.\n', {
      isCancelled: () => false,
      setText: text => values.push(text),
      focus: vi.fn()
    })
    await vi.runAllTimersAsync()
    expect(await promise).toBe(true)
    expect(values.at(-1)).toBe('hi.\n')
  })

  it('simulateTypingInto respects cancellation', async () => {
    let cancelled = false
    const setText = vi.fn()
    const promise = simulateTypingInto('abc', {
      isCancelled: () => cancelled,
      setText,
      focus: vi.fn()
    })
    cancelled = true
    await vi.runAllTimersAsync()
    const ok = await promise
    expect(ok).toBe(false)
  })

  it('clearSimulatedInput clears registered bridge', () => {
    const clearInput = vi.fn()
    registerAgentChatInputBridge({
      setInputText: vi.fn(),
      getInputText: () => '',
      focusInput: vi.fn(),
      clearInput
    })
    clearSimulatedInput()
    expect(clearInput).toHaveBeenCalledOnce()
  })

  it('getTypingThinkingLabel returns localized text', () => {
    expect(getTypingThinkingLabel()).toBeTruthy()
  })
})
