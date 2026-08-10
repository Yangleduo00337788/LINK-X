/**
 * 作者：yangleduo
 */
/** 去掉行内 Markdown 标记，保留可读文本 */
function stripMarkdownInline(text: string): string {
  return text
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/_([^_]+)_/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^[-*+]\s+\[[ xX]\]\s*/, '')
    .replace(/^[-*+]\s+/, '')
    .replace(/^\d+\.\s+/, '')
    .trim()
}

/** 是否为仅含媒体/附件/位置的行（不宜作为标题） */
function isNonTitleLine(line: string): boolean {
  if (/^!\[[^\]]*\]\([^)]+\)\s*$/.test(line)) return true
  if (/^\[位置[：:][^\]]*\]\s*$/i.test(line)) return true
  if (/^\[语音[^\]]*\]\([^)]+\)\s*$/i.test(line)) return true
  if (/^\[附件[：:][^\]]*\]\([^)]+\)\s*$/i.test(line)) return true
  if (/^[-*_]{3,}\s*$/.test(line)) return true
  return false
}

/**
 * 从笔记正文推导标题：优先小标题，跳过纯图片/附件行，去掉 Markdown 标记。
 */
export function deriveNoteTitle(content: string, maxLen = 80): string {
  const lines = content.split('\n')

  for (const line of lines) {
    const trimmed = line.trim()
    const heading = trimmed.match(/^#{1,6}\s+(.+)$/)
    if (heading) {
      const text = stripMarkdownInline(heading[1])
      if (text) return text.slice(0, maxLen)
    }
  }

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed || isNonTitleLine(trimmed)) continue
    const text = stripMarkdownInline(trimmed)
    if (text) return text.slice(0, maxLen)
  }

  return ''
}
