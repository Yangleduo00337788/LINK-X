export type NoteTextBlock = { type: 'text'; value: string }
export type NoteImageBlock = { type: 'image'; alt: string; ref: string }
export type NoteAttachmentBlock = { type: 'attachment'; label: string; ref: string }
export type NoteLocationBlock = { type: 'location'; place: string }

export type NoteBlock = NoteTextBlock | NoteImageBlock | NoteAttachmentBlock | NoteLocationBlock

const IMAGE_LINE = /^!\[([^\]]*)\]\(([^)]+)\)\s*$/
const LINK_LINE = /^\[([^\]]+)\]\(([^)]+)\)\s*$/
const LOCATION_LINE = /^\[位置[：:]\s*([^\]]+)\]\s*$/i

function isAttachmentLabel(label: string): boolean {
  return /^附件[：:]/i.test(label) || /^attachment[：:]/i.test(label)
}

/** 将笔记 Markdown 拆成文本 / 图片 / 附件 / 位置块 */
export function parseNoteBlocks(content: string): NoteBlock[] {
  const lines = (content ?? '').split('\n')
  const blocks: NoteBlock[] = []
  let textBuffer: string[] = []

  const flushText = () => {
    if (!textBuffer.length) return
    blocks.push({ type: 'text', value: textBuffer.join('\n') })
    textBuffer = []
  }

  for (const line of lines) {
    const trimmed = line.trim()
    const image = trimmed.match(IMAGE_LINE)
    if (image) {
      flushText()
      blocks.push({ type: 'image', alt: image[1], ref: image[2] })
      continue
    }
    const link = trimmed.match(LINK_LINE)
    if (link && isAttachmentLabel(link[1])) {
      flushText()
      blocks.push({ type: 'attachment', label: link[1], ref: link[2] })
      continue
    }
    const location = trimmed.match(LOCATION_LINE)
    if (location) {
      flushText()
      blocks.push({ type: 'location', place: location[1].trim() })
      continue
    }
    textBuffer.push(line)
  }

  flushText()
  if (!blocks.length) {
    blocks.push({ type: 'text', value: '' })
  }
  return blocks
}

/** 将块序列还原为 Markdown 笔记正文 */
export function serializeNoteBlocks(blocks: NoteBlock[]): string {
  const parts: string[] = []
  for (const block of blocks) {
    if (block.type === 'text') {
      if (block.value.length) parts.push(block.value)
      continue
    }
    if (block.type === 'image') {
      parts.push(`![${block.alt}](${block.ref})`)
      continue
    }
    if (block.type === 'attachment') {
      parts.push(`[${block.label}](${block.ref})`)
      continue
    }
    if (block.type === 'location') {
      parts.push(`[位置：${block.place}]`)
    }
  }
  return parts.join('\n')
}

export function mediaKeyFromRef(ref: string): string {
  const trimmed = ref.trim()
  if (trimmed.startsWith('lx-media:')) return trimmed.slice('lx-media:'.length)
  return trimmed
}

export function parseAttachmentMeta(label: string): { fileName: string; size: string } {
  const raw = label.replace(/^附件[：:]\s*/i, '').replace(/^attachment[：:]\s*/i, '').trim()
  const sep = raw.indexOf(' · ')
  if (sep >= 0) {
    return { fileName: raw.slice(0, sep).trim(), size: raw.slice(sep + 3).trim() }
  }
  return { fileName: raw, size: '' }
}

export type FileKind = 'pdf' | 'word' | 'ppt' | 'zip' | 'audio' | 'text' | 'file'

export function fileKindFromName(name: string): FileKind {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (ext === 'pdf') return 'pdf'
  if (ext === 'doc' || ext === 'docx') return 'word'
  if (ext === 'ppt' || ext === 'pptx') return 'ppt'
  if (ext === 'zip' || ext === 'rar' || ext === '7z') return 'zip'
  if (['mp3', 'wav', 'ogg', 'm4a', 'aac', 'flac'].includes(ext)) return 'audio'
  if (['txt', 'md', 'json', 'xml', 'csv'].includes(ext)) return 'text'
  return 'file'
}

export function fileKindLabel(kind: FileKind): string {
  switch (kind) {
    case 'pdf':
      return 'PDF'
    case 'word':
      return 'W'
    case 'ppt':
      return 'P'
    case 'zip':
      return 'ZIP'
    case 'audio':
      return '♪'
    default:
      return 'FILE'
  }
}
