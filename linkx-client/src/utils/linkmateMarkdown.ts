/**
 * 作者：yangleduo
 */
import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import go from 'highlight.js/lib/languages/go'
import rust from 'highlight.js/lib/languages/rust'
import csharp from 'highlight.js/lib/languages/csharp'
import yaml from 'highlight.js/lib/languages/yaml'
import markdown from 'highlight.js/lib/languages/markdown'
import 'highlight.js/styles/github.css'
import { t } from '../i18n'
import { copyText } from './clipboard'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('sh', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('go', go)
hljs.registerLanguage('rust', rust)
hljs.registerLanguage('csharp', csharp)
hljs.registerLanguage('cs', csharp)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('md', markdown)

const linkmateMarked = new Marked({
  breaks: true,
  gfm: true
})

linkmateMarked.use({
  renderer: {
    code({ text, lang }: { text: string; lang?: string }) {
      const language = lang && hljs.getLanguage(lang) ? lang : undefined
      let highlighted: string
      if (language) {
        highlighted = hljs.highlight(text, { language }).value
      } else {
        const auto = hljs.highlightAuto(text)
        highlighted = auto.value
      }
      const langLabel = language || 'text'
      const copyLabel = t('linkmate.copyCode')
      return (
        `<div class="lm-code-wrap">` +
        `<button type="button" class="lm-code-copy" data-lm-copy="1">${copyLabel}</button>` +
        `<pre><code class="hljs language-${langLabel}">${highlighted}</code></pre>` +
        `</div>`
      )
    }
  }
})

/** 解码历史消息中可能存在的 HTML 实体（如 &ldquo; &quot;） */
function decodeHtmlEntities(text: string): string {
  if (!text || !/&(?:#x?[0-9a-f]+|[a-z]+);/i.test(text)) return text
  if (typeof document === 'undefined') return text
  const el = document.createElement('textarea')
  el.innerHTML = text
  return el.value
}

/** 渲染灵伴回复 Markdown 为安全 HTML */
export function renderLinkMateMarkdown(markdown: string): string {
  const source = decodeHtmlEntities(markdown?.trim() ?? '')
  if (!source) return ''
  const rawHtml = linkmateMarked.parse(source) as string
  return DOMPurify.sanitize(rawHtml, {
    ADD_TAGS: ['pre', 'code', 'button', 'span'],
    ADD_ATTR: ['class', 'target', 'rel', 'type', 'data-lm-copy']
  })
}

/** 从代码块复制按钮获取源码文本 */
export async function copyCodeFromButton(button: HTMLElement): Promise<boolean> {
  const wrap = button.closest('.lm-code-wrap')
  const code = wrap?.querySelector('code')
  if (!code?.textContent) return false
  return copyText(code.textContent)
}
