import { describe, expect, it } from 'vitest'
import {
  isInlineFormatActive,
  isLinePrefixActive,
  toggleInlineFormat,
  toggleLinePrefix
} from './noteTextFormat'

describe('noteTextFormat', () => {
  it('inserts empty bold pair for collapsed cursor', () => {
    const result = toggleInlineFormat('hello', 5, 5, '**')
    expect(result).toEqual({ value: 'hello****', cursor: 7 })
    expect(isInlineFormatActive(result.value, result.cursor, '**')).toBe(true)
  })

  it('removes empty bold pair when toggled again', () => {
    const text = 'hello****'
    const result = toggleInlineFormat(text, 7, 7, '**')
    expect(result).toEqual({ value: 'hello', cursor: 5 })
  })

  it('wraps and unwraps selected text', () => {
    const wrapped = toggleInlineFormat('hello world', 0, 5, '**')
    expect(wrapped.value).toBe('**hello** world')
    const unwrapped = toggleInlineFormat(wrapped.value, 0, 9, '**')
    expect(unwrapped.value).toBe('hello world')
  })

  it('closes bold at cursor inside formatted text', () => {
    const text = '**hello world**'
    const result = toggleInlineFormat(text, 7, 7, '**')
    expect(result.value).toBe('**hello** world**')
    expect(result.cursor).toBe(9)
  })

  it('toggles heading prefix on current line', () => {
    const on = toggleLinePrefix('title\nbody', 0, 0, 'heading')
    expect(on.value).toBe('## title\nbody')
    const off = toggleLinePrefix(on.value, 3, 3, 'heading')
    expect(off.value).toBe('title\nbody')
  })

  it('detects line prefix active state', () => {
    expect(isLinePrefixActive('## title', 3, '## ')).toBe(true)
    expect(isLinePrefixActive('- item', 2, '- ')).toBe(true)
    expect(isLinePrefixActive('1. item', 3, 'ordered')).toBe(true)
  })
})
