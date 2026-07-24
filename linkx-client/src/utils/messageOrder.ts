/**
 * 消息顺序：优先按雪花数字 id 升序；非数字（乐观 UUID / temp-）排到末尾并按时间兜底。
 * 与后端 listMessages 的 id 游标语义一致。
 */
export function compareMessageOrder(
  a: { id: string; time?: string },
  b: { id: string; time?: string }
): number {
  const aNum = /^\d+$/.test(a.id)
  const bNum = /^\d+$/.test(b.id)
  if (aNum && bNum) {
    const diff = BigInt(a.id) - BigInt(b.id)
    if (diff < 0n) return -1
    if (diff > 0n) return 1
    return 0
  }
  if (aNum && !bNum) return -1
  if (!aNum && bNum) return 1
  const ta = a.time || ''
  const tb = b.time || ''
  if (ta === tb) return 0
  return ta > tb ? 1 : -1
}
