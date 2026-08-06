import { Marked } from 'marked'
import DOMPurify from 'dompurify'

const LX_MEDIA_PREFIX = 'lx-media:'

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function escapeAttr(text: string): string {
  return escapeHtml(text)
}

/** 将 lx-media:key 替换为可访问 URL */
export function resolveLxMediaInMarkdown(md: string, mediaCache: Record<string, string>): string {
  let source = md ?? ''
  for (const [key, url] of Object.entries(mediaCache)) {
    if (!key || !url) continue
    source = source.split(`(${LX_MEDIA_PREFIX}${key})`).join(`(${url})`)
  }
  return source
}

function isAttachmentLabel(text: string): boolean {
  return /^附件[：:]/i.test(text) || /^attachment[：:]/i.test(text)
}

function isLocationLabel(text: string): boolean {
  return /^位置[：:]/i.test(text)
}

function parseAttachmentMeta(text: string): { fileName: string; size: string } {
  const raw = text.replace(/^附件[：:]\s*/i, '').replace(/^attachment[：:]\s*/i, '').trim()
  const sep = raw.indexOf(' · ')
  if (sep >= 0) {
    return {
      fileName: raw.slice(0, sep).trim(),
      size: raw.slice(sep + 3).trim()
    }
  }
  return { fileName: raw, size: '' }
}

type FileKind = 'pdf' | 'word' | 'ppt' | 'zip' | 'audio' | 'text' | 'file'

function fileKindFromName(name: string): FileKind {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (ext === 'pdf') return 'pdf'
  if (ext === 'doc' || ext === 'docx') return 'word'
  if (ext === 'ppt' || ext === 'pptx') return 'ppt'
  if (ext === 'zip' || ext === 'rar' || ext === '7z') return 'zip'
  if (['mp3', 'wav', 'ogg', 'm4a', 'aac', 'flac'].includes(ext)) return 'audio'
  if (['txt', 'md', 'json', 'xml', 'csv'].includes(ext)) return 'text'
  return 'file'
}

function fileKindLabel(kind: FileKind): string {
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

function attachmentCardHtml(text: string, href: string): string {
  const { fileName, size } = parseAttachmentMeta(text)
  const kind = fileKindFromName(fileName)
  const label = fileKindLabel(kind)
  return `<a class="note-attach-card" href="${escapeAttr(href)}" target="_blank" rel="noopener noreferrer">
<span class="note-attach-icon note-attach-icon--${kind}">${label}</span>
<span class="note-attach-meta">
<span class="note-attach-name">${escapeHtml(fileName)}</span>
${size ? `<span class="note-attach-size">${escapeHtml(size)}</span>` : ''}
</span>
</a>`
}

const noteMarked = new Marked({
  breaks: true,
  gfm: true,
  renderer: {
    image({ href, text }) {
      if (!href) return ''
      return `<figure class="note-image-block"><img src="${escapeAttr(href)}" alt="${escapeAttr(text || '')}" loading="lazy" /></figure>`
    },
    link({ href, title, text }) {
      const safeHref = href || '#'
      if (isAttachmentLabel(text)) {
        return attachmentCardHtml(text, safeHref)
      }
      if (isLocationLabel(text)) {
        const place = text.replace(/^位置[：:]\s*/i, '').trim()
        return `<span class="note-location-chip" title="${escapeAttr(title || place)}">📍 ${escapeHtml(place)}</span>`
      }
      const titleAttr = title ? ` title="${escapeAttr(title)}"` : ''
      return `<a href="${escapeAttr(safeHref)}"${titleAttr} target="_blank" rel="noopener noreferrer">${escapeHtml(text)}</a>`
    }
  }
})

/** 渲染笔记 Markdown 为所见即所得 HTML */
export function renderNoteMarkdown(markdown: string, mediaCache: Record<string, string>): string {
  const resolved = resolveLxMediaInMarkdown(markdown, mediaCache)
  const rawHtml = noteMarked.parse(resolved) as string
  return DOMPurify.sanitize(rawHtml, {
    ADD_TAGS: ['figure', 'u', 'input', 's', 'del'],
    ADD_ATTR: ['loading', 'target', 'rel', 'type', 'checked', 'class']
  })
}
