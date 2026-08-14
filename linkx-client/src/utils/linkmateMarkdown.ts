/**
 * 作者：yangleduo
 */
import { Marked } from 'marked'
import DOMPurify from 'dompurify'

const linkmateMarked = new Marked({
  breaks: true,
  gfm: true
})

/** 渲染灵伴回复 Markdown 为安全 HTML */
export function renderLinkMateMarkdown(markdown: string): string {
  const source = markdown?.trim() ?? ''
  if (!source) return ''
  const rawHtml = linkmateMarked.parse(source) as string
  return DOMPurify.sanitize(rawHtml, {
    ADD_TAGS: ['pre', 'code'],
    ADD_ATTR: ['class', 'target', 'rel']
  })
}
