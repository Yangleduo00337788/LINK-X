/**
 * 作者：yangleduo
 */
export type FormatMutation = {
  value: string
  cursor: number
  selectionStart?: number
  selectionEnd?: number
}

/** 光标是否处于行首标记（标题、列表等）所在行 */
export function isLinePrefixActive(text: string, cursor: number, prefix: string): boolean {
  const lineStart = text.lastIndexOf('\n', cursor - 1) + 1
  const lineEndIdx = text.indexOf('\n', cursor)
  const lineEnd = lineEndIdx === -1 ? text.length : lineEndIdx
  const line = text.slice(lineStart, lineEnd)
  if (prefix === 'ordered') return /^\d+\.\s/.test(line)
  if (prefix === 'todo') return /^- \[[ xX]\]\s/.test(line)
  return line.startsWith(prefix)
}

/** 光标是否位于成对标记内部（含空的 **|**） */
export function isInlineFormatActive(
  text: string,
  cursor: number,
  prefix: string,
  suffix: string = prefix
): boolean {
  if (cursor >= prefix.length) {
    const before = text.slice(cursor - prefix.length, cursor)
    const after = text.slice(cursor, cursor + suffix.length)
    if (before === prefix && after === suffix) return true
  }
  return findEnclosingFormat(text, cursor, prefix, suffix) !== null
}

export function findEnclosingFormat(
  text: string,
  cursor: number,
  prefix: string,
  suffix: string
): { open: number; close: number } | null {
  const before = text.slice(0, cursor)
  let searchFrom = before.length
  while (searchFrom >= prefix.length) {
    const open = before.lastIndexOf(prefix, searchFrom - prefix.length)
    if (open < 0) break
    const contentStart = open + prefix.length
    const close = text.indexOf(suffix, contentStart)
    if (close >= 0 && cursor >= contentStart && cursor <= close) {
      return { open, close }
    }
    searchFrom = open
  }
  return null
}

/**
 * 切换行内格式：选中则包裹/取消；无选中则插入空标记对，便于继续输入。
 * 再次点击时：空标记对会移除；处于格式内部则在光标处闭合标记。
 */
export function toggleInlineFormat(
  text: string,
  start: number,
  end: number,
  prefix: string,
  suffix: string = prefix
): FormatMutation {
  if (start !== end) {
    const selected = text.slice(start, end)
    if (
      selected.length >= prefix.length + suffix.length &&
      selected.startsWith(prefix) &&
      selected.endsWith(suffix)
    ) {
      const inner = selected.slice(prefix.length, selected.length - suffix.length)
      return { value: text.slice(0, start) + inner + text.slice(end), cursor: start + inner.length }
    }
    const value = text.slice(0, start) + prefix + selected + suffix + text.slice(end)
    return { value, cursor: start + prefix.length + selected.length + suffix.length }
  }

  const cursor = start
  const before = text.slice(cursor - prefix.length, cursor)
  const after = text.slice(cursor, cursor + suffix.length)
  if (before === prefix && after === suffix) {
    return {
      value: text.slice(0, cursor - prefix.length) + text.slice(cursor + suffix.length),
      cursor: cursor - prefix.length
    }
  }

  const enclosed = findEnclosingFormat(text, cursor, prefix, suffix)
  if (enclosed && cursor > enclosed.open + prefix.length && cursor < enclosed.close) {
    const value = text.slice(0, cursor) + suffix + text.slice(cursor)
    return { value, cursor: cursor + suffix.length }
  }

  const value = text.slice(0, cursor) + prefix + suffix + text.slice(cursor)
  return { value, cursor: cursor + prefix.length }
}

/** 切换当前行行首标记（标题、无序/有序列表、待办） */
export function toggleLinePrefix(
  text: string,
  start: number,
  end: number,
  kind: 'heading' | 'unordered' | 'ordered' | 'todo'
): FormatMutation {
  const lineStart = text.lastIndexOf('\n', start - 1) + 1
  const lineEndIdx = text.indexOf('\n', start)
  const lineEnd = lineEndIdx === -1 ? text.length : lineEndIdx
  const line = text.slice(lineStart, lineEnd)

  const prefixes: Record<typeof kind, { on: string; off: RegExp | string }> = {
    heading: { on: '## ', off: /^##\s+/ },
    unordered: { on: '- ', off: /^- (?!\[[ xX]\]\s)/ },
    ordered: { on: '1. ', off: /^\d+\.\s/ },
    todo: { on: '- [ ] ', off: /^- \[[ xX]\]\s/ }
  }

  const { on, off } = prefixes[kind]
  let newLine: string
  let delta: number
  if (typeof off === 'string' ? line.startsWith(off) : off.test(line)) {
    newLine = typeof off === 'string' ? line.slice(off.length) : line.replace(off, '')
    delta = newLine.length - line.length
  } else {
    newLine = on + line
    delta = on.length
  }

  const value = text.slice(0, lineStart) + newLine + text.slice(lineEnd)
  const cursor = Math.max(lineStart, Math.min(value.length, start + delta))
  const selEnd = Math.max(cursor, end + delta)
  return { value, cursor, selectionStart: lineStart, selectionEnd: selEnd }
}
