/**
 * 作者：yangleduo
 */
/**
 * 消息顺序：先按 createTime，再按数字 id。
 * 发送中的本地消息用本地时间戳占位，确认回包后仍按原时间，避免位置跳动。
 */
export function compareMessageOrder(
  a: { id: string; time?: string; createTime?: number },
  b: { id: string; time?: string; createTime?: number }
): number {
  const ta = a.createTime ?? 0
  const tb = b.createTime ?? 0
  if (ta !== tb) return ta < tb ? -1 : 1

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
  const sa = a.time || ''
  const sb = b.time || ''
  if (sa === sb) return a.id > b.id ? 1 : a.id < b.id ? -1 : 0
  return sa > sb ? 1 : -1
}

/** 将消息按会话顺序插入列表（跳过已存在 id） */
export function insertMessageInOrder<T extends { id: string; time?: string; createTime?: number }>(
  list: T[],
  msg: T
): boolean {
  if (list.some(m => m.id === msg.id)) return false
  let insertAt = list.length
  for (let i = 0; i < list.length; i++) {
    if (compareMessageOrder(msg, list[i]) < 0) {
      insertAt = i
      break
    }
  }
  list.splice(insertAt, 0, msg)
  return true
}

export function sortMessagesInOrder<T extends { id: string; time?: string; createTime?: number }>(list: T[]): T[] {
  if (list.length < 2) return list
  list.sort(compareMessageOrder)
  return list
}
