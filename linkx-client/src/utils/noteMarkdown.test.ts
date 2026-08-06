import { describe, expect, it } from 'vitest'
import { renderNoteMarkdown, resolveLxMediaInMarkdown } from './noteMarkdown'

describe('noteMarkdown', () => {
  it('resolves lx-media keys to urls', () => {
    const md = '![a](lx-media:key1)'
    expect(resolveLxMediaInMarkdown(md, { key1: 'https://cdn/a.png' })).toBe(
      '![a](https://cdn/a.png)'
    )
  })

  it('renders images as figure blocks', () => {
    const html = renderNoteMarkdown('![壁纸](https://example.com/a.png)', {})
    expect(html).toContain('note-image-block')
    expect(html).toContain('https://example.com/a.png')
  })

  it('renders attachment links as cards', () => {
    const html = renderNoteMarkdown('[附件：文档.pdf · 18 B](https://example.com/f.pdf)', {})
    expect(html).toContain('note-attach-card')
    expect(html).toContain('文档.pdf')
    expect(html).toContain('18 B')
    expect(html).toContain('note-attach-icon--pdf')
  })
})
