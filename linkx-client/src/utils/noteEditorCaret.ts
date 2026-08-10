/**
 * 作者：yangleduo
 */
/** 保存 / 恢复 contenteditable 内的文本光标位置 */
export function getCaretTextOffset(root: HTMLElement): number {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return 0
  const range = sel.getRangeAt(0)
  if (!root.contains(range.startContainer)) return 0

  let offset = 0
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let node: Node | null
  while ((node = walker.nextNode())) {
    if (node === range.startContainer) {
      return offset + range.startOffset
    }
    offset += node.textContent?.length ?? 0
  }
  return offset
}

export function setCaretTextOffset(root: HTMLElement, targetOffset: number) {
  const sel = window.getSelection()
  if (!sel) return

  let offset = 0
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let node: Node | null
  while ((node = walker.nextNode())) {
    const len = node.textContent?.length ?? 0
    if (offset + len >= targetOffset) {
      const range = document.createRange()
      range.setStart(node, Math.max(0, targetOffset - offset))
      range.collapse(true)
      sel.removeAllRanges()
      sel.addRange(range)
      return
    }
    offset += len
  }

  const range = document.createRange()
  range.selectNodeContents(root)
  range.collapse(false)
  sel.removeAllRanges()
  sel.addRange(range)
}
