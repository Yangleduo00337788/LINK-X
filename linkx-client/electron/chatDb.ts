/**
 * Electron 主进程：聊天消息本地 SQLite（sql.js WASM，免原生编译）
 */
import initSqlJs, { type Database } from 'sql.js'
import path from 'node:path'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import { app } from 'electron'

const require = createRequire(import.meta.url)

export const HOT_MESSAGE_LIMIT = 200

export type SessionMetaRow = {
  sessionId: string
  scrollTop: number
  lastSyncId: string | null
  updatedAt: number
}

let db: Database | null = null
let dbPath = ''
let initPromise: Promise<Database> | null = null
let saveTimer: ReturnType<typeof setTimeout> | null = null

function sortKeyForMessageId(id: string): string {
  if (/^\d+$/.test(id)) return id.padStart(20, '0')
  return `z-${id}`
}

function partitionMonth(createTime: number): string {
  const d = createTime ? new Date(createTime) : new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function schedulePersist() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    saveTimer = null
    persistToDisk()
  }, 400)
}

function persistToDisk() {
  if (!db || !dbPath) return
  try {
    const data = db.export()
    fs.writeFileSync(dbPath, Buffer.from(data))
  } catch (e) {
    console.error('[chatDb] persist failed', e)
  }
}

function ensureSchema(database: Database) {
  database.run(`
    CREATE TABLE IF NOT EXISTS messages (
      id TEXT NOT NULL,
      session_id TEXT NOT NULL,
      sort_key TEXT NOT NULL,
      payload TEXT NOT NULL,
      create_time INTEGER NOT NULL DEFAULT 0,
      partition_month TEXT NOT NULL DEFAULT '',
      PRIMARY KEY (session_id, id)
    );
    CREATE INDEX IF NOT EXISTS idx_messages_session_sort ON messages(session_id, sort_key);
    CREATE TABLE IF NOT EXISTS session_meta (
      session_id TEXT PRIMARY KEY,
      scroll_top REAL NOT NULL DEFAULT 0,
      last_sync_id TEXT,
      updated_at INTEGER NOT NULL DEFAULT 0
    );
    CREATE TABLE IF NOT EXISTS media_cache (
      message_id TEXT PRIMARY KEY,
      thumb_path TEXT,
      full_path TEXT,
      updated_at INTEGER NOT NULL DEFAULT 0
    );
  `)
}

function resolveSqlWasmDir(): string {
  const candidates = [
    path.dirname(require.resolve('sql.js/dist/sql-wasm.js')),
    path.join(app.getAppPath(), 'node_modules', 'sql.js', 'dist')
  ]
  if (process.resourcesPath) {
    candidates.push(
      path.join(process.resourcesPath, 'app.asar.unpacked', 'node_modules', 'sql.js', 'dist')
    )
  }
  for (const dir of candidates) {
    if (fs.existsSync(path.join(dir, 'sql-wasm.wasm'))) return dir
  }
  return candidates[0]
}

export async function initChatDatabase(): Promise<Database> {
  if (db) return db
  if (initPromise) return initPromise

  initPromise = (async () => {
    const wasmDir = resolveSqlWasmDir()
    const SQL = await initSqlJs({
      locateFile: file => path.join(wasmDir, file)
    })

    const dir = path.join(app.getPath('userData'), 'chat-db')
    fs.mkdirSync(dir, { recursive: true })
    dbPath = path.join(dir, 'messages.db')

    if (fs.existsSync(dbPath)) {
      db = new SQL.Database(fs.readFileSync(dbPath))
    } else {
      db = new SQL.Database()
    }
    ensureSchema(db)
    persistToDisk()
    console.info('[chatDb] ready:', dbPath)
    return db
  })()

  return initPromise
}

export function getChatDbPath(): string {
  return dbPath || path.join(app.getPath('userData'), 'chat-db', 'messages.db')
}

export async function countAllMessages(): Promise<number> {
  const database = await getDb()
  const stmt = database.prepare('SELECT COUNT(*) AS c FROM messages')
  stmt.step()
  const row = stmt.getAsObject() as { c?: number }
  stmt.free()
  return Number(row.c) || 0
}

export function closeChatDatabase(): void {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
  }
  persistToDisk()
  if (db) {
    db.close()
    db = null
  }
  initPromise = null
}

async function getDb(): Promise<Database> {
  return initChatDatabase()
}

export async function upsertMessages(
  sessionId: string,
  payloads: Array<{ id: string; payload: string; createTime?: number }>
): Promise<void> {
  if (!payloads.length) return
  const database = await getDb()
  database.run('BEGIN')
  try {
    const stmt = database.prepare(`
      INSERT OR REPLACE INTO messages (id, session_id, sort_key, payload, create_time, partition_month)
      VALUES (?, ?, ?, ?, ?, ?)
    `)
    for (const row of payloads) {
      const createTime = row.createTime ?? 0
      stmt.run([
        row.id,
        sessionId,
        sortKeyForMessageId(row.id),
        row.payload,
        createTime,
        partitionMonth(createTime)
      ])
    }
    stmt.free()
    database.run('COMMIT')
    schedulePersist()
  } catch (e) {
    database.run('ROLLBACK')
    throw e
  }
}

export async function getRecentMessages(sessionId: string, limit = HOT_MESSAGE_LIMIT): Promise<string[]> {
  const database = await getDb()
  const stmt = database.prepare(
    `SELECT payload FROM messages WHERE session_id = ? ORDER BY sort_key DESC LIMIT ?`
  )
  stmt.bind([sessionId, limit])
  const rows: string[] = []
  while (stmt.step()) {
    rows.push(String(stmt.getAsObject().payload))
  }
  stmt.free()
  return rows.reverse()
}

export async function getMessagesBefore(
  sessionId: string,
  beforeId: string,
  limit = 50
): Promise<string[]> {
  const database = await getDb()
  const sortKey = sortKeyForMessageId(beforeId)
  const stmt = database.prepare(
    `SELECT payload FROM messages WHERE session_id = ? AND sort_key < ? ORDER BY sort_key DESC LIMIT ?`
  )
  stmt.bind([sessionId, sortKey, limit])
  const rows: string[] = []
  while (stmt.step()) {
    rows.push(String(stmt.getAsObject().payload))
  }
  stmt.free()
  return rows.reverse()
}

export async function getLastMessageId(sessionId: string): Promise<string | null> {
  const database = await getDb()
  const stmt = database.prepare(
    `SELECT id FROM messages WHERE session_id = ? AND id GLOB '[0-9]*' ORDER BY sort_key DESC LIMIT 1`
  )
  stmt.bind([sessionId])
  let id: string | null = null
  if (stmt.step()) {
    id = String(stmt.getAsObject().id)
  }
  stmt.free()
  return id
}

export async function hasOlderMessages(sessionId: string, oldestId: string): Promise<boolean> {
  const database = await getDb()
  const sortKey = sortKeyForMessageId(oldestId)
  const stmt = database.prepare(
    `SELECT 1 AS ok FROM messages WHERE session_id = ? AND sort_key < ? LIMIT 1`
  )
  stmt.bind([sessionId, sortKey])
  const ok = stmt.step()
  stmt.free()
  return ok
}

export async function getSessionMeta(sessionId: string): Promise<SessionMetaRow> {
  const database = await getDb()
  const stmt = database.prepare(
    `SELECT session_id, scroll_top, last_sync_id, updated_at FROM session_meta WHERE session_id = ?`
  )
  stmt.bind([sessionId])
  if (stmt.step()) {
    const row = stmt.getAsObject() as Record<string, unknown>
    stmt.free()
    return {
      sessionId,
      scrollTop: Number(row.scroll_top ?? 0),
      lastSyncId: row.last_sync_id ? String(row.last_sync_id) : null,
      updatedAt: Number(row.updated_at ?? 0)
    }
  }
  stmt.free()
  return { sessionId, scrollTop: 0, lastSyncId: null, updatedAt: 0 }
}

export async function setSessionMeta(
  sessionId: string,
  patch: Partial<Pick<SessionMetaRow, 'scrollTop' | 'lastSyncId'>>
): Promise<void> {
  const database = await getDb()
  const prev = await getSessionMeta(sessionId)
  database.run(
    `INSERT OR REPLACE INTO session_meta (session_id, scroll_top, last_sync_id, updated_at)
     VALUES (?, ?, ?, ?)`,
    [
      sessionId,
      patch.scrollTop ?? prev.scrollTop,
      patch.lastSyncId !== undefined ? patch.lastSyncId : prev.lastSyncId,
      Date.now()
    ]
  )
  schedulePersist()
}

export async function clearSessionMessages(sessionId: string): Promise<void> {
  const database = await getDb()
  database.run(`DELETE FROM messages WHERE session_id = ?`, [sessionId])
  database.run(`DELETE FROM session_meta WHERE session_id = ?`, [sessionId])
  schedulePersist()
}

export async function clearAllChatData(): Promise<void> {
  const database = await getDb()
  database.run(`DELETE FROM messages`)
  database.run(`DELETE FROM session_meta`)
  database.run(`DELETE FROM media_cache`)
  schedulePersist()
}

export async function migrateFromLegacySessionStorage(
  messagesBySession: Record<string, Array<Record<string, unknown>>>
): Promise<number> {
  let count = 0
  for (const [sessionId, list] of Object.entries(messagesBySession)) {
    if (!Array.isArray(list) || !list.length) continue
    const rows = list
      .filter(m => m && typeof m.id === 'string')
      .map(m => ({
        id: String(m.id),
        payload: JSON.stringify(m),
        createTime: typeof m.createTime === 'number' ? m.createTime : 0
      }))
    await upsertMessages(sessionId, rows)
    count += rows.length
    const lastNumeric = [...list].reverse().find(m => /^\d+$/.test(String(m.id)))
    if (lastNumeric) {
      await setSessionMeta(sessionId, { lastSyncId: String(lastNumeric.id) })
    }
  }
  return count
}

export async function getMediaCachePath(messageId: string, kind: 'thumb' | 'full'): Promise<string | null> {
  const database = await getDb()
  const stmt = database.prepare(`SELECT thumb_path, full_path FROM media_cache WHERE message_id = ?`)
  stmt.bind([messageId])
  let pathValue: string | null = null
  if (stmt.step()) {
    const row = stmt.getAsObject() as Record<string, unknown>
    pathValue = kind === 'thumb' ? String(row.thumb_path || '') : String(row.full_path || '')
  }
  stmt.free()
  return pathValue && fs.existsSync(pathValue) ? pathValue : null
}

export async function setMediaCachePath(
  messageId: string,
  kind: 'thumb' | 'full',
  filePath: string
): Promise<void> {
  const database = await getDb()
  const prevStmt = database.prepare(`SELECT thumb_path, full_path FROM media_cache WHERE message_id = ?`)
  prevStmt.bind([messageId])
  let thumbPath: string | null = null
  let fullPath: string | null = null
  if (prevStmt.step()) {
    const row = prevStmt.getAsObject() as Record<string, unknown>
    thumbPath = row.thumb_path ? String(row.thumb_path) : null
    fullPath = row.full_path ? String(row.full_path) : null
  }
  prevStmt.free()
  if (kind === 'thumb') thumbPath = filePath
  else fullPath = filePath
  database.run(
    `INSERT OR REPLACE INTO media_cache (message_id, thumb_path, full_path, updated_at) VALUES (?, ?, ?, ?)`,
    [messageId, thumbPath, fullPath, Date.now()]
  )
  schedulePersist()
}

export function mediaCacheDir(): string {
  const dir = path.join(app.getPath('userData'), 'media-cache')
  fs.mkdirSync(dir, { recursive: true })
  return dir
}
