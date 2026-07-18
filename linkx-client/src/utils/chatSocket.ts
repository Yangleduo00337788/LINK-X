import { getToken } from './tokenStorage'
import { parseJsonPreservingIds } from './parseJson'
import type { MessageItem, WsIncomingFrame, WsSendPayload } from '../types/chat'

const WS_BASE = import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:8081'

export interface ChatSocketHandlers {
  onMessage: (message: MessageItem) => void
  onAck: (clientMsgId: string, message: MessageItem) => void
  onError: (code: number, message: string) => void
  onOpen: () => void
  onClose: () => void
  /** 通话信令推送 */
  onCallEvent?: (action: string, data: Record<string, unknown>) => void
}

let socket: WebSocket | null = null
let handlers: ChatSocketHandlers | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let reconnectAttempts = 0
let shouldReconnect = false

const MAX_RECONNECT_ATTEMPTS = 8  // 指数退避到 30s 后最多重试 8 次（约 4 分钟）
/** 超过上限后仍以该间隔慢速重试，避免后端恢复后桌面端永久离线 */
const SLOW_RECONNECT_MS = 15000

function clearTimers() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function startHeartbeat() {
  // 心跳与重连定时器独立：重连等待期间不应清掉 reconnectTimer
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  heartbeatTimer = setInterval(() => {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ action: 'ping' }))
    }
  }, 25000)
}

function scheduleReconnect() {
  if (!shouldReconnect || reconnectTimer) return
  const delay =
    reconnectAttempts >= MAX_RECONNECT_ATTEMPTS
      ? SLOW_RECONNECT_MS
      : Math.min(1000 * 2 ** reconnectAttempts, 30000)
  reconnectAttempts += 1
  if (reconnectAttempts === MAX_RECONNECT_ATTEMPTS + 1) {
    handlers?.onError(503, '连接已断开，将在后台继续尝试重连')
  }
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    void connectChatSocket(handlers!)
  }, delay)
}

/** 重置重连计数并立即再连（登录恢复 / 窗口聚焦 / 发送前调用） */
export function resetChatSocketReconnect() {
  reconnectAttempts = 0
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

// 近期消息 ID 缓存，用于在前端层面做去重。
// 容量限制避免内存膨胀；FIFO 淘汰。
const recentMessageIds: string[] = []
const MAX_RECENT_IDS = 200

function rememberMessageId(id: string): boolean {
  if (!id) return true  // 无 ID 的消息无法去重，直接放行
  if (recentMessageIds.includes(id)) {
    return false  // 重复
  }
  recentMessageIds.push(id)
  if (recentMessageIds.length > MAX_RECENT_IDS) {
    recentMessageIds.shift()
  }
  return true
}

function handleFrame(raw: string) {
  let frame: WsIncomingFrame
  try {
    frame = parseJsonPreservingIds(raw) as WsIncomingFrame
  } catch {
    handlers?.onError(400, '消息格式错误')
    return
  }

  switch (frame.action) {
    case 'message':
      if (frame.data) {
        const data = frame.data as { id?: string }
        if (!rememberMessageId(data.id ?? '')) {
          // 重复消息（重连时服务端推送的历史或重传），直接丢弃
          return
        }
        handlers?.onMessage(frame.data as MessageItem)
      }
      break
    case 'ack':
      if (frame.data && frame.clientMsgId) {
        // ack 帧不去重（ack 不携带 id 字段，clientMsgId 仅一次性使用）
        handlers?.onAck(frame.clientMsgId, frame.data as MessageItem)
      }
      break
    case 'pong':
      break
    case 'error':
      handlers?.onError(frame.code ?? 500, frame.message ?? 'WebSocket 错误')
      break
    case 'call_invite':
    case 'call_accept':
    case 'call_reject':
    case 'call_cancel':
    case 'call_hangup':
    case 'call_signal':
      if (frame.data) {
        handlers?.onCallEvent?.(frame.action, frame.data as Record<string, unknown>)
      }
      break
    default:
      break
  }
}

export async function connectChatSocket(nextHandlers: ChatSocketHandlers) {
  handlers = nextHandlers
  shouldReconnect = true

  if (socket?.readyState === WebSocket.OPEN) {
    return
  }

  if (socket?.readyState === WebSocket.CONNECTING) {
    await waitForSocketOpen(8000)
    return
  }

  const token = await getToken('accessToken')
  if (!token) {
    handlers.onError(401, '未登录')
    return
  }

  // 统一用 query 传 token + 命名子协议：Electron 把 JWT 当第二个 subprotocol 在部分内核下会握手失败；
  // 浏览器此前已验证 query 路径可用。服务端会回写 linkx-access-token 完成协商。
  const wsProtocol = 'linkx-access-token'
  const wsUrl = `${WS_BASE}/ws?token=${encodeURIComponent(token)}`
  console.log('[WebSocket] 正在连接:', `${WS_BASE}/ws`, window.electronAPI ? '(electron)' : '(browser)')

  socket = new WebSocket(wsUrl, [wsProtocol])

  await new Promise<void>((resolve, reject) => {
    let settled = false
    const timer = window.setTimeout(() => {
      if (settled) return
      settled = true
      reject(new Error('WebSocket 连接超时'))
    }, 8000)

    socket!.onopen = () => {
      if (settled) return
      settled = true
      window.clearTimeout(timer)
      console.log('[WebSocket] 连接成功!')
      reconnectAttempts = 0
      startHeartbeat()
      handlers?.onOpen()
      resolve()
    }

    socket!.onmessage = event => {
      if (typeof event.data === 'string') {
        handleFrame(event.data)
      }
    }

    socket!.onerror = () => {
      console.error('[WebSocket] 连接错误!')
      handlers?.onError(500, 'WebSocket 连接异常')
    }

    socket!.onclose = event => {
      console.log('[WebSocket] 连接关闭, code:', event.code, 'reason:', event.reason)
      clearTimers()
      // 重连前清空去重缓存，避免旧连接的消息 ID 阻塞新连接的去重判断
      recentMessageIds.length = 0
      handlers?.onClose()
      socket = null
      if (shouldReconnect) {
        scheduleReconnect()
      }
      if (!settled) {
        settled = true
        window.clearTimeout(timer)
        reject(new Error(event.reason || 'WebSocket 已关闭'))
      }
    }
  }).catch(err => {
    // 连接失败不抛给登录流程，交给重连机制；调用方可继续
    console.warn('[WebSocket] 首次连接未完成:', (err as Error).message)
  })
}

/** 等待当前 socket 变为 OPEN（用于 CONNECTING 状态） */
function waitForSocketOpen(timeoutMs: number): Promise<void> {
  if (socket?.readyState === WebSocket.OPEN) {
    return Promise.resolve()
  }
  if (!socket || socket.readyState === WebSocket.CLOSED) {
    return Promise.reject(
      new Error(`WebSocket 未连接（目标 ${WS_BASE}/ws）。请确认后端已启动：HTTP 8080 与 IM 8081 均需在监听`)
    )
  }
  return new Promise((resolve, reject) => {
    const start = Date.now()
    const timer = window.setInterval(() => {
      if (socket?.readyState === WebSocket.OPEN) {
        window.clearInterval(timer)
        resolve()
        return
      }
      if (!socket || socket.readyState === WebSocket.CLOSED) {
        window.clearInterval(timer)
        reject(new Error('WebSocket 连接被关闭'))
        return
      }
      if (Date.now() - start > timeoutMs) {
        window.clearInterval(timer)
        reject(new Error('WebSocket 连接超时'))
      }
    }, 50)
  })
}

/**
 * 确保 IM WebSocket 已连接；未连接则发起连接并等待就绪。
 * 发送消息 / 通话前应调用，避免「未连接」竞态。
 */
export async function ensureChatSocketConnected(
  nextHandlers?: ChatSocketHandlers
): Promise<void> {
  if (nextHandlers) {
    handlers = nextHandlers
  }
  if (socket?.readyState === WebSocket.OPEN) {
    return
  }
  if (!handlers) {
    throw new Error('WebSocket 未初始化')
  }
  // 用户主动操作时重置退避，避免「后端刚恢复但桌面端已停连」
  resetChatSocketReconnect()
  await connectChatSocket(handlers)
  if (socket?.readyState === WebSocket.OPEN) {
    return
  }
  await waitForSocketOpen(8000)
}

export function disconnectChatSocket() {
  shouldReconnect = false
  reconnectAttempts = 0
  clearTimers()
  if (socket) {
    socket.close()
    socket = null
  }
  handlers = null
}

export function sendChatMessage(payload: WsSendPayload) {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    throw new Error('WebSocket 未连接')
  }
  const msg = {
    action: payload.action,
    clientMsgId: payload.clientMsgId,
    conversationId: payload.conversationId,
    msgType: payload.msgType,
    content: payload.content,
    fileName: payload.fileName,
    fileSize: payload.fileSize,
    fileUrl: payload.fileUrl,
    voiceDuration: payload.voiceDuration
  }
  socket.send(JSON.stringify(msg))
}

export function isChatSocketConnected() {
  return socket?.readyState === WebSocket.OPEN
}
