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
  const delay = Math.min(1000 * 2 ** reconnectAttempts, 30000)
  reconnectAttempts += 1
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    void connectChatSocket(handlers!)
  }, delay)
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
      if (frame.data) handlers?.onMessage(frame.data)
      break
    case 'ack':
      if (frame.data && frame.clientMsgId) {
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

  // 优先：将 token 通过 Sec-WebSocket-Protocol 子协议传递（后端兼容）
  // 后端 ImWebSocketAuthHandler 会优先从子协议读取，回退到 URL Query
  try {
    socket = new WebSocket(WS_BASE + '/ws', ['linkx-access-token', token])
  } catch {
    // 某些环境不支持子协议数组，回退到 URL query
    const url = `${WS_BASE}/ws?token=${encodeURIComponent(token)}`
    socket = new WebSocket(url)
  }

  socket.onopen = () => {
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
    handlers?.onError(500, 'WebSocket 连接异常')
  }

  socket.onclose = () => {
    clearTimers()
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
