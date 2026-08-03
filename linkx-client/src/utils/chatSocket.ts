import { getToken, isWebEnvironment } from './tokenStorage'
import { parseJsonPreservingIds } from './parseJson'
import type { MessageItem, WsIncomingFrame, WsSendPayload } from '../types/chat'
import { WS_BASE_URL } from '../config/endpoints'
import { t } from '../i18n'

const WS_BASE = WS_BASE_URL

export interface ChatSocketHandlers {
  onMessage: (message: MessageItem) => void
  onAck: (clientMsgId: string, message: MessageItem) => void
  onError: (code: number, message: string, clientMsgId?: string) => void
  onOpen: () => void
  onClose: () => void
  /** 消息撤回推送 */
  onRecall?: (message: MessageItem) => void
  /** 通话信令推送 */
  onCallEvent?: (action: string, data: Record<string, unknown>) => void
  /** 通用自定义 action 推送（如 notification_refresh / presence） */
  onCustomAction?: (action: string, data: Record<string, unknown>) => void
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
    handlers?.onError(503, t('errors.wsReconnectBackground'))
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
    handlers?.onError(400, t('errors.wsBadMessageFormat'))
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
    case 'recall':
      if (frame.data) {
        handlers?.onRecall?.(frame.data as MessageItem)
      }
      break
    case 'edit':
      if (frame.data) {
        handlers?.onCustomAction?.('edit', frame.data as Record<string, unknown>)
      }
      break
    case 'deliveryReceipt':
      if (frame.data) {
        handlers?.onCustomAction?.('deliveryReceipt', frame.data as Record<string, unknown>)
      }
      break
    case 'readReceipt':
      if (frame.data) {
        handlers?.onCustomAction?.('readReceipt', frame.data as Record<string, unknown>)
      }
      break
    case 'pong':
      break
    case 'error':
      handlers?.onError(frame.code ?? 500, frame.message ?? t('errors.wsGenericError'), frame.clientMsgId)
      break
    case 'force_logout':
      shouldReconnect = false
      disconnectChatSocket()
      void import('../api/client').then(({ clearTokens }) => clearTokens())
      void import('../stores/app').then(({ useAppStore }) => {
        useAppStore().$patch({
          isLoggedIn: false,
          isLocked: false,
          isLoading: false,
          authInitializing: false
        })
      })
      handlers?.onError(401, frame.message ?? t('errors.wsForceLogout'))
      break
    case 'call_invite':
    case 'call_accept':
    case 'call_reject':
    case 'call_cancel':
    case 'call_hangup':
    case 'call_signal':
    case 'call_reconnect':
    case 'call_device_switch':
      if (frame.data) {
        handlers?.onCallEvent?.(frame.action, frame.data as Record<string, unknown>)
      }
      break
    case 'notification_refresh':
      if (frame.data) {
        handlers?.onCustomAction?.('notification_refresh', frame.data as Record<string, unknown>)
      }
      break
    case 'group_added':
      if (frame.data) {
        handlers?.onCustomAction?.('group_added', frame.data as Record<string, unknown>)
      }
      break
    case 'group_renamed':
    case 'group_announcement_updated':
    case 'group_dissolved':
    case 'sensitive_alert_clear':
    case 'group_member_role_changed':
    case 'group_mute_changed':
    case 'group_mute_all_changed':
      if (frame.data) {
        handlers?.onCustomAction?.(frame.action, frame.data as Record<string, unknown>)
      }
      break
    case 'moments_new_post':
      if (frame.data) {
        handlers?.onCustomAction?.('moments_new_post', frame.data as Record<string, unknown>)
      }
      break
    case 'group_member_added':
      if (frame.data) {
        handlers?.onCustomAction?.('group_member_added', frame.data as Record<string, unknown>)
      }
      break
    default:
      // 兜底：未知 action 也尝试透传给自定义 action
      if (frame.data) {
        handlers?.onCustomAction?.(frame.action, frame.data as Record<string, unknown>)
      }
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

  const isWeb = isWebEnvironment()
  const token = await getToken('accessToken')
  // Web 环境 token 在 HttpOnly Cookie 中（本地不可读），浏览器 WebSocket 握手自动携带同站 Cookie，无需本地 token；
  // Electron 环境需本地 token 走 Sec-WebSocket-Protocol 子协议。
  if (!isWeb && !token) {
    handlers.onError(401, t('errors.wsNotLoggedIn'))
    return
  }

  // JWT 走 Sec-WebSocket-Protocol，避免出现在 URL/代理访问日志；device 元数据仍用 query
  const { getOrCreateDeviceId, getDeviceName, getDeviceType } = await import('./deviceId')
  const deviceId = encodeURIComponent(getOrCreateDeviceId())
  const deviceName = encodeURIComponent(getDeviceName())
  const deviceType = encodeURIComponent(getDeviceType())
  const wsUrl =
    `${WS_BASE}/ws?deviceId=${deviceId}&deviceName=${deviceName}&deviceType=${deviceType}`
  console.log('[WebSocket] 正在连接:', `${WS_BASE}/ws`, window.electronAPI ? '(electron)' : '(browser)')

  // Web 环境只声明命名子协议（token 由 Cookie 携带，服务端从 Cookie 读取）；
  // Electron 环境把 token 作为第二个子协议传入。
  socket = isWeb
    ? new WebSocket(wsUrl, ['linkx-access-token'])
    : new WebSocket(wsUrl, ['linkx-access-token', token as string])

  await new Promise<void>((resolve, reject) => {
    let settled = false
    const timer = window.setTimeout(() => {
      if (settled) return
      settled = true
      reject(new Error(t('errors.wsConnectTimeout')))
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
      handlers?.onError(500, t('errors.wsConnectError'))
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
        reject(new Error(event.reason || t('errors.wsClosed')))
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
      new Error(t('errors.wsNotConnected', { url: `${WS_BASE}/ws` }))
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
        reject(new Error(t('errors.wsConnectionClosed')))
        return
      }
      if (Date.now() - start > timeoutMs) {
        window.clearInterval(timer)
        reject(new Error(t('errors.wsConnectTimeout')))
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
    throw new Error(t('errors.wsNotInitialized'))
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
    throw new Error(t('errors.wsDisconnected'))
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
    voiceDuration: payload.voiceDuration,
    ...(payload.quoteMessageId ? { quoteMessageId: payload.quoteMessageId } : {})
  }
  socket.send(JSON.stringify(msg))
}

/** 向服务端确认消息已送达（接收端调用） */
export function sendDeliveryReceipt(serverMsgId: string) {
  if (!socket || socket.readyState !== WebSocket.OPEN) return
  if (!serverMsgId || !/^\d+$/.test(serverMsgId)) return
  socket.send(JSON.stringify({ action: 'deliveryReceipt', serverMsgId }))
}

export function isChatSocketConnected() {
  return socket?.readyState === WebSocket.OPEN
}
