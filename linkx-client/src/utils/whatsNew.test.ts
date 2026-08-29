/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import { parseReleaseNotes } from './whatsNew'

describe('parseReleaseNotes', () => {
  it('parses bullet lines and plain text', () => {
    const blocks = parseReleaseNotes('LinkX 1.0.1 更新：\n- 灵伴 Agent\n- Design Token')
    expect(blocks).toEqual([
      { kind: 'text', content: 'LinkX 1.0.1 更新：' },
      { kind: 'list', items: ['灵伴 Agent', 'Design Token'] }
    ])
  })

  it('ignores empty lines', () => {
    expect(parseReleaseNotes('\n\n- only item\n\n')).toEqual([{ kind: 'list', items: ['only item'] }])
  })
})
