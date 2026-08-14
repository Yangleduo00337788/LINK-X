/**
 * 聊天本地库 + 媒体缓存 IPC 注册
 */
import { ipcMain } from 'electron'
import fs from 'node:fs'
import path from 'node:path'
import {
  clearAllChatData,
  clearSessionMessages,
  countAllMessages,
  getChatDbPath,
  getLastMessageId,
  getMediaCachePath,
  getMessagesBefore,
  getRecentMessages,
  getSessionMeta,
  hasOlderMessages,
  initChatDatabase,
  mediaCacheDir,
  migrateFromLegacySessionStorage,
  setMediaCachePath,
  setSessionMeta,
  upsertMessages
} from './chatDb'

export function registerChatDbIpc(): void {
  void initChatDatabase().catch(err => {
    console.error('[chatDb] init failed', err)
  })

  ipcMain.handle('chat-db:get-path', async () => {
    await initChatDatabase()
    return getChatDbPath()
  })

  ipcMain.handle('chat-db:count', async (_e, sessionId?: string) => {
    if (sessionId) {
      const database = await initChatDatabase()
      const stmt = database.prepare('SELECT COUNT(*) AS c FROM messages WHERE session_id = ?')
      stmt.bind([sessionId])
      stmt.step()
      const row = stmt.getAsObject() as { c?: number }
      stmt.free()
      return Number(row.c) || 0
    }
    return countAllMessages()
  })

  ipcMain.handle('chat-db:upsert-messages', async (_e, sessionId: string, rows) => {
    await upsertMessages(sessionId, rows)
    return true
  })

  ipcMain.handle('chat-db:get-recent', async (_e, sessionId: string, limit?: number) => {
    return getRecentMessages(sessionId, limit)
  })

  ipcMain.handle('chat-db:get-before', async (_e, sessionId: string, beforeId: string, limit?: number) => {
    return getMessagesBefore(sessionId, beforeId, limit)
  })

  ipcMain.handle('chat-db:get-last-id', async (_e, sessionId: string) => {
    return getLastMessageId(sessionId)
  })

  ipcMain.handle('chat-db:has-older', async (_e, sessionId: string, oldestId: string) => {
    return hasOlderMessages(sessionId, oldestId)
  })

  ipcMain.handle('chat-db:get-session-meta', async (_e, sessionId: string) => {
    return getSessionMeta(sessionId)
  })

  ipcMain.handle('chat-db:set-session-meta', async (_e, sessionId: string, patch) => {
    await setSessionMeta(sessionId, patch)
    return true
  })

  ipcMain.handle('chat-db:clear-session', async (_e, sessionId: string) => {
    await clearSessionMessages(sessionId)
    return true
  })

  ipcMain.handle('chat-db:clear-all', async () => {
    await clearAllChatData()
    return true
  })

  ipcMain.handle('chat-db:migrate-legacy', async (_e, messagesBySession) => {
    return migrateFromLegacySessionStorage(messagesBySession)
  })

  ipcMain.handle('chat-media:get-path', async (_e, messageId: string, kind: 'thumb' | 'full' = 'thumb') => {
    return getMediaCachePath(messageId, kind)
  })

  ipcMain.handle('chat-media:save-bytes', async (_e, payload) => {
    const messageId = payload?.messageId
    if (!messageId) return null
    const kind = payload.kind === 'full' ? 'full' : 'thumb'
    const ext = (payload.ext || 'bin').replace(/[^a-z0-9]/gi, '') || 'bin'
    const dir = mediaCacheDir()
    const filePath = path.join(dir, `${messageId}-${kind}.${ext}`)
    const buf = Buffer.from(payload.bytes)
    fs.writeFileSync(filePath, buf)
    await setMediaCachePath(messageId, kind, filePath)
    return filePath
  })
}
