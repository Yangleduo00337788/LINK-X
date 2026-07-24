/**
 * 通知列表聚合：同类型 + 同 relatedId（如同一动态点赞）在时间窗内合并。
 */

export interface AggregatableNotif {
  id: string
  senderId: string
  senderName: string
  senderAvatar?: string
  type: string
  relatedId?: string
  content: string
  readStatus: number
  createTime: string
  category?: string
}

export interface AggregatedNotif extends AggregatableNotif {
  /** 合并条数（含自身） */
  aggregateCount: number
  /** 合并进来的其它发送者昵称（不含主条） */
  aggregateNames: string[]
  /** 合并涉及的通知 id（含自身，便于批量已读） */
  aggregateIds: string[]
}

const WINDOW_MS = 24 * 60 * 60 * 1000

function groupKey(n: AggregatableNotif): string {
  const related = n.relatedId || '_'
  return `${n.type}::${related}`
}

/**
 * 将通知按「类型 + relatedId」聚合；无 relatedId 时不合并（避免误并系统杂讯）。
 * 窗口：最新一条起往前 24h。
 */
export function aggregateNotifications(list: AggregatableNotif[]): AggregatedNotif[] {
  const sorted = [...list].sort(
    (a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime()
  )
  const used = new Set<string>()
  const out: AggregatedNotif[] = []

  for (const head of sorted) {
    if (used.has(head.id)) continue
    if (!head.relatedId) {
      used.add(head.id)
      out.push({
        ...head,
        aggregateCount: 1,
        aggregateNames: [],
        aggregateIds: [head.id]
      })
      continue
    }

    const key = groupKey(head)
    const headTs = new Date(head.createTime).getTime()
    const bucket: AggregatableNotif[] = []
    for (const n of sorted) {
      if (used.has(n.id)) continue
      if (groupKey(n) !== key) continue
      const ts = new Date(n.createTime).getTime()
      if (Math.abs(headTs - ts) > WINDOW_MS) continue
      bucket.push(n)
      used.add(n.id)
    }

    const names = [
      ...new Set(
        bucket
          .filter(n => n.id !== head.id)
          .map(n => n.senderName)
          .filter(Boolean)
      )
    ]
    const anyUnread = bucket.some(n => n.readStatus === 0)
    out.push({
      ...head,
      readStatus: anyUnread ? 0 : 1,
      aggregateCount: bucket.length,
      aggregateNames: names,
      aggregateIds: bucket.map(n => n.id)
    })
  }

  return out
}

/** 当前时刻是否处于免打扰时段（支持跨午夜，如 22:00–08:00） */
export function isInQuietHours(
  now: Date,
  enabled: boolean,
  startHm: string,
  endHm: string
): boolean {
  if (!enabled) return false
  const start = parseMinutes(startHm)
  const end = parseMinutes(endHm)
  if (start == null || end == null) return false
  const cur = now.getHours() * 60 + now.getMinutes()
  if (start === end) return true
  if (start < end) {
    return cur >= start && cur < end
  }
  // 跨午夜
  return cur >= start || cur < end
}

function parseMinutes(hm: string): number | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec((hm || '').trim())
  if (!m) return null
  const h = Number(m[1])
  const min = Number(m[2])
  if (h > 23 || min > 59) return null
  return h * 60 + min
}
