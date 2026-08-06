import { describe, expect, it } from 'vitest'
import { parseNoteBlocks, serializeNoteBlocks } from './noteBlocks'

describe('noteBlocks', () => {
  it('parses image and text blocks', () => {
    const content = '你好\n\n![壁纸](lx-media:key.png)\n\n再见'
    const blocks = parseNoteBlocks(content)
    expect(blocks).toEqual([
      { type: 'text', value: '你好\n' },
      { type: 'image', alt: '壁纸', ref: 'lx-media:key.png' },
      { type: 'text', value: '\n再见' }
    ])
  })

  it('round-trips attachment blocks', () => {
    const content = '[附件：a.pdf · 18 B](lx-media:files/a.pdf)'
    const blocks = parseNoteBlocks(content)
    expect(blocks[0]).toMatchObject({ type: 'attachment' })
    expect(serializeNoteBlocks(blocks)).toBe(content)
  })
})
