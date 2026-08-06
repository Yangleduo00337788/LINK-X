import { describe, expect, it } from 'vitest'
import { htmlToNoteMarkdown } from './noteHtmlToMarkdown'
import { renderNoteMarkdown } from './noteMarkdown'
import { renderNoteTextBlockHtml } from './noteTextPreview'

describe('noteHtmlToMarkdown', () => {
  it('converts bold html to markdown', () => {
    expect(htmlToNoteMarkdown('<p><strong>德萨的</strong></p>')).toBe('**德萨的**')
  })

  it('round-trips inline formats', () => {
    const md = '**bold** _italic_ <u>line</u>'
    const html = renderNoteMarkdown(md, {})
    expect(htmlToNoteMarkdown(html)).toBe(md)
  })

  it('converts heading and list blocks', () => {
    expect(htmlToNoteMarkdown('<h2>Title</h2><ul><li>a</li><li>b</li></ul>')).toBe(
      '## Title\n- a\n- b'
    )
  })

  it('converts task list html to markdown', () => {
    expect(htmlToNoteMarkdown('<ul><li><input type="checkbox" class="note-task-checkbox">todo</li></ul>')).toBe(
      '- [ ] todo'
    )
    expect(
      htmlToNoteMarkdown('<ul><li><input type="checkbox" class="note-task-checkbox" checked>done</li></ul>')
    ).toBe('- [x] done')
  })

  it('round-trips task list markdown', () => {
    const md = '- [ ] todo\n- item'
    const html = renderNoteTextBlockHtml(md, {})
    expect(html).toContain('note-task-checkbox')
    expect(htmlToNoteMarkdown(html)).toBe(md)
  })

  it('returns empty for blank editor html', () => {
    expect(htmlToNoteMarkdown('<p><br></p>')).toBe('')
  })
})
