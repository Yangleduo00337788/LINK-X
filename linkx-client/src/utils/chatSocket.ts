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
}

let socket: WebSocket | null = null
let handlers: ChatSocketHandlers | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let reconnectAttempts = 0
let shouldReconnect = false

const MAX_RECONNECT_ATTEMPTS = 8  // 指数退避到 30s 后最多重试 8 次（约 4 分钟）

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
  clearTimers()
  heartbeatTimer = setInterval(() => {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ action: 'ping' }))
    }
  }, 25000)
}

function scheduleReconnect() {
  if (!shouldReconnect || reconnectTimer) return
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    // 超过上限停止重连，避免在服务宕机时无限循环消耗资源
    handlers?.onError(503, '连接已断开，请稍后手动刷新页面')
    return
  }
  const delay = Math.min(1000 * 2 ** reconnectAttempts, 30000)
  reconnectAttempts += 1
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    void connectChatSocket(handlers!)
  }, delay)
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
        handlers?.onMessage(frame.data)
      }
      break
    case 'ack':
      if (frame.data && frame.clientMsgId) {
        // ack 帧不去重（ack 不携带 id 字段，clientMsgId 仅一次性使用）
        handlers?.onAck(frame.clientMsgId, frame.data)
      }
      break
    case 'pong':
      break
    case 'error':
      handlers?.onError(frame.code ?? 500, frame.message ?? 'WebSocket 错误')
      break
    default:
      break
  }
}

export async function connectChatSocket(nextHandlers: ChatSocketHandlers) {
  handlers = nextHandlers
  shouldReconnect = true

  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return
  }

  const token = await getToken('accessToken')
  if (!token) {
    handlers.onError(401, '未登录')
    return
  }

  // 使用 Sec-WebSocket-Protocol 子协议传递 token，避免 JWT 出现在 URL/日志/Referer 中。
  // 浏览器在握手时自动附加该头，服务端须回写相同值以完成协议协商。
  const wsProtocol = 'linkx-access-token'
  const wsUrl = `${WS_BASE}/ws`
  console.log('[WebSocket] 正在连接:', wsUrl)
  // 第三个参数 protocols 即 Sec-WebSocket-Protocol，会同时用于握手请求与响应
  socket = new WebSocket(wsUrl, [wsProtocol, token])

  socket.onopen = () => {
    console.log('[WebSocket] 连接成功!')
    reconnectAttempts = 0
    startHeartbeat()
    handlers?.onOpen()
  }

  socket.onmessage = event => {
    if (typeof event.data === 'string') {
      handleFrame(event.data)
    }
  }

  socket.onerror = () => {
    console.error('[WebSocket] 连接错误!')
    handlers?.onError(500, 'WebSocket 连接异常')
  }

  socket.onclose = event => {
    console.log('[WebSocket] 连接关闭, code:', event.code, 'reason:', event.reason)
    clearTimers()
    // 重连前清空去重缓存，避免旧连接的消息 ID 阻塞新连接的去重判断
    recentMessageIds.length = 0
    handlers?.onClose()
    socket = null
    if (shouldReconnect) {
      scheduleReconnect()
    }
  }
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
