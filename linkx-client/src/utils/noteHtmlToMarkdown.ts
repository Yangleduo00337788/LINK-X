/**
 * 作者：yangleduo
 */
/** 将笔记文本块 HTML 转回 Markdown（供 contenteditable 同步） */
export function htmlToNoteMarkdown(html: string): string {
  const trimmed = (html ?? '').trim()
  if (!trimmed || trimmed === '<br>' || trimmed === '<p><br></p>' || trimmed === '<div><br></div>') {
    return ''
  }
  const doc = new DOMParser().parseFromString(trimmed, 'text/html')
  const lines: string[] = []
  for (const child of [...doc.body.childNodes]) {
    const line = serializeBlockNode(child)
    if (line !== null) lines.push(line)
  }
  return lines.join('\n').replace(/\n{3,}/g, '\n\n')
}

function serializeListItem(li: HTMLElement): string {
  const checkbox = li.querySelector('input[type="checkbox"]')
  if (checkbox) {
    const clone = li.cloneNode(true) as HTMLElement
    clone.querySelector('input')?.remove()
    const text = serializeInline(clone).trim()
    const checked = (checkbox as HTMLInputElement).checked
    return `- [${checked ? 'x' : ' '}] ${text}`
  }
  return `- ${serializeInline(li).trim()}`
}

function serializeBlockNode(node: Node): string | null {
  if (node.nodeType === Node.TEXT_NODE) {
    const text = node.textContent ?? ''
    return text.trim() ? text : null
  }
  if (node.nodeType !== Node.ELEMENT_NODE) return null
  const el = node as HTMLElement
  const tag = el.tagName

  if (tag === 'BR') return ''

  if (tag === 'UL') {
    return [...el.children]
      .map(li => (li.tagName === 'LI' ? serializeListItem(li as HTMLElement) : ''))
      .filter(Boolean)
      .join('\n')
  }

  if (tag === 'OL') {
    return [...el.children]
      .map((li, i) => (li.tagName === 'LI' ? `${i + 1}. ${serializeInline(li).trim()}` : ''))
      .filter(Boolean)
      .join('\n')
  }

  if (tag === 'LI') {
    return serializeListItem(el)
  }

  if (/^H[1-6]$/.test(tag)) {
    const level = Number(tag[1])
    const prefix = '#'.repeat(Math.min(level, 6)) + ' '
    return prefix + serializeInline(el).trim()
  }

  if (tag === 'P' || tag === 'DIV') {
    return serializeInline(el).trimEnd()
  }

  if (tag === 'HR') return '---'

  return serializeInline(el).trim() || null
}

function serializeInline(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent ?? ''
  if (node.nodeType !== Node.ELEMENT_NODE) return ''
  const el = node as HTMLElement
  if (el.tagName === 'INPUT') return ''
  const inner = [...el.childNodes].map(serializeInline).join('')
  switch (el.tagName) {
    case 'STRONG':
    case 'B':
      return `**${inner}**`
    case 'EM':
    case 'I':
      return `_${inner}_`
    case 'U':
      return `<u>${inner}</u>`
    case 'S':
    case 'DEL':
    case 'STRIKE':
      return `~~${inner}~~`
    case 'BR':
      return '\n'
    case 'A':
      return inner
    default:
      return inner
  }
}
