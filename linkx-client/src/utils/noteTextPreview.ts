import { renderNoteMarkdown } from './noteMarkdown'

/** 将笔记文本块 Markdown 渲染为编辑器 HTML，并启用可交互待办 */
export function renderNoteTextBlockHtml(markdown: string, mediaCache: Record<string, string>): string {
  const trimmed = (markdown ?? '').trim()
  if (!trimmed) return ''

  let html = renderNoteMarkdown(trimmed, mediaCache)
  html = html.replace(
    /<input\b([^>]*)\btype="checkbox"([^>]*)>/gi,
    (_match, before, after) => {
      const attrs = `${before}${after}`.replace(/\bdisabled\b/gi, '').trim()
      return `<input type="checkbox" class="note-task-checkbox"${attrs ? ` ${attrs}` : ''}>`
    }
  )
  return html
}
