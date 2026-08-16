/**
 * 聊天消息本地存储门面：Electron 走 SQLite IPC，Web 降级 sessionStorage。
 */
import type { ChatMessage } from '../types'
import { sanitizeMessageForPersist } from '../utils/persistSanitize'

export const HOT_MESSAGE_LIMIT = 200

/** 单会话内存中最多持有的消息条数（含向上翻页加载的历史） */
export const IN_MEMORY_MESSAGE_LIMIT = 800

type ChatDbApi = NonNullable<Window['electronAPI']>['chatDb']

function api(): ChatDbApi | undefined {
  return window.electronAPI?.chatDb
}

export function isChatLocalDbEnabled(): boolean {
  return !!api()
}

export function trimHotMessages(messages: ChatMessage[]): ChatMessage[] {
  if (messages.length <= HOT_MESSAGE_LIMIT) return messages
  return messages.slice(-HOT_MESSAGE_LIMIT)
}

/** 向上翻页后限制内存：丢弃较新的一段（可从本地库/服务端恢复） */
export function trimInMemoryHistory(messages: ChatMessage[]): ChatMessage[] {
  if (messages.length <= IN_MEMORY_MESSAGE_LIMIT) return messages
  return messages.slice(0, IN_MEMORY_MESSAGE_LIMIT)
}

/** 收到新消息时限制内存：丢弃较旧的历史，保留最新一段 */
export function trimInMemoryTail(messages: ChatMessage[]): ChatMessage[] {
  if (messages.length <= IN_MEMORY_MESSAGE_LIMIT) return messages
  return messages.slice(-IN_MEMORY_MESSAGE_LIMIT)
}

function serializeMessage(msg: ChatMessage): string {
  return JSON.stringify(sanitizeMessageForPersist(msg))
}

function parsePayloads(payloads: string[]): ChatMessage[] {
  const out: ChatMessage[] = []
  for (const raw of payloads) {
    try {
      out.push(JSON.parse(raw) as ChatMessage)
    } catch {
      /* skip corrupt row */
    }
  }
  return out
}

export async function migrateLegacySessionStorageIfNeeded(): Promise<void> {
  const chatDb = api()
  if (!chatDb) return
  try {
    const raw = sessionStorage.getItem('linkx-app-msgs')
    if (!raw) return
    const parsed = JSON.parse(raw) as { messagesBySession?: Record<string, ChatMessage[]> }
    const map = parsed?.messagesBySession
    if (!map || !Object.keys(map).length) return
    const count = await chatDb.migrateLegacy(
      map as unknown as Record<string, Array<Record<string, unknown>>>
    )
    if (count > 0) {
      sessionStorage.removeItem('linkx-app-msgs')
    }
  } catch {
    /* ignore */
  }
}

export async function loadRecentMessages(sessionId: string, limit = HOT_MESSAGE_LIMIT): Promise<ChatMessage[]> {
  const chatDb = api()
  if (!chatDb) return []
  const payloads = await chatDb.getRecent(sessionId, limit)
  return parsePayloads(payloads)
}

export async function loadOlderMessagesLocal(
  sessionId: string,
  beforeId: string,
  limit = 50
): Promise<ChatMessage[]> {
  const chatDb = api()
  if (!chatDb) return []
  const payloads = await chatDb.getBefore(sessionId, beforeId, limit)
  return parsePayloads(payloads)
}

export async function persistMessages(sessionId: string, messages: ChatMessage[]): Promise<void> {
  const chatDb = api()
  if (!chatDb || !messages.length) return
  const rows = messages.map(m => ({
    id: m.id,
    payload: serializeMessage(m),
    createTime: m.createTime ?? 0
  }))
  await chatDb.upsertMessages(sessionId, rows)
  const lastNumeric = [...messages].reverse().find(m => /^\d+$/.test(m.id))
  if (lastNumeric) {
    await chatDb.setSessionMeta(sessionId, { lastSyncId: lastNumeric.id })
  }
}

export async function persistMessage(sessionId: string, msg: ChatMessage): Promise<void> {
  await persistMessages(sessionId, [msg])
}

export async function getLastSyncMessageId(sessionId: string): Promise<string | null> {
  const chatDb = api()
  if (!chatDb) return null
  const meta = await chatDb.getSessionMeta(sessionId)
  if (meta.lastSyncId) return meta.lastSyncId
  return chatDb.getLastId(sessionId)
}

export async function hasOlderLocal(sessionId: string, oldestId: string): Promise<boolean> {
  const chatDb = api()
  if (!chatDb) return false
  return chatDb.hasOlder(sessionId, oldestId)
}

export async function getSessionScrollTop(sessionId: string): Promise<number> {
  const chatDb = api()
  if (!chatDb) return 0
  const meta = await chatDb.getSessionMeta(sessionId)
  return meta.scrollTop ?? 0
}

export async function saveSessionScrollTop(sessionId: string, scrollTop: number): Promise<void> {
  const chatDb = api()
  if (!chatDb) return
  await chatDb.setSessionMeta(sessionId, { scrollTop })
}

export async function clearLocalChatDb(): Promise<void> {
  const chatDb = api()
  if (!chatDb) return
  await chatDb.clearAll()
}

export async function getCachedMediaPath(
  messageId: string,
  kind: 'thumb' | 'full' = 'thumb'
): Promise<string | null> {
  const media = window.electronAPI?.chatMedia
  if (!media) return null
  return media.getPath(messageId, kind)
}

export async function saveMediaBytes(
  messageId: string,
  bytes: ArrayBuffer,
  opts?: { kind?: 'thumb' | 'full'; ext?: string }
): Promise<string | null> {
  const media = window.electronAPI?.chatMedia
  if (!media) return null
  return media.saveBytes({
    messageId,
    kind: opts?.kind ?? 'thumb',
    bytes,
    ext: opts?.ext
  })
}

export function toMediaFileUrl(filePath: string): string {
  const normalized = filePath.replace(/\\/g, '/')
  return normalized.startsWith('file://') ? normalized : `file://${normalized}`
}
