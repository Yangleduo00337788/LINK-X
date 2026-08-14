/**
 * 作者：yangleduo
 */
import { apiClient } from './client'
import type { ApiResult } from '../types/auth'
import { API_BASE_URL } from '../config/endpoints'
import { getToken, isWebEnvironment } from '../utils/tokenStorage'
import { getDeviceName, getDeviceType, getOrCreateDeviceId } from '../utils/deviceId'
import type { LinkMateImContext } from '../utils/buildImChatContext'
import type { MessageItem } from '../types/chat'

export interface LinkMateStatus {
  enabled: boolean
  model: string
  dailyTokenLimit: number
  dailyTokenUsed: number
  deepThinkingSupported: boolean
}

export interface LinkMateSession {
  id: string
  title: string
  updateTime: string
}

export interface LinkMateMessage {
  id: string
  sessionId: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createTime: string
  reasoningContent?: string
  /** 回复开始时间戳（流式中用于实时计时） */
  responseStartedAt?: number
  /** 回复总耗时（毫秒） */
  responseDurationMs?: number
  /** 深度思考阶段耗时（毫秒），仅深度思考时有值 */
  reasoningDurationMs?: number
  /** 本条消息 token 用量（done 事件回填） */
  tokenCount?: number
}

export interface LinkMateImStreamHandlers {
  onStart?: (conversationId: string) => void
  onDelta?: (content: string) => void
  onReasoningDelta?: (content: string) => void
  onDone?: (messageId: string, conversationId: string, totalTokens?: number) => void
  onError?: (message: string) => void
}

export interface LinkMateStreamRequest {
  sessionId?: string
  message?: string
  deepThinking?: boolean
  regenerate?: boolean
  regenerateMessageId?: string
  imContext?: LinkMateImContext
}

export function getStatus() {
  return apiClient.get<unknown, ApiResult<LinkMateStatus>>('/linkmate/status')
}

/** 群聊/单聊 @灵伴：AI 回复落入 IM 消息时间线（非流式，保留兼容） */
export function replyInGroup(conversationId: string, question: string) {
  return apiClient.post<unknown, ApiResult<MessageItem>>('/linkmate/group/reply', {
    conversationId,
    question
  })
}

/** 群聊/单聊 @灵伴：SSE 流式回复 */
export async function streamImReply(
  conversationId: string,
  question: string,
  handlers: LinkMateImStreamHandlers,
  signal?: AbortSignal,
  deepThinking = false
): Promise<void> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    'Cache-Control': 'no-cache',
    'X-Device-Id': getOrCreateDeviceId(),
    'X-Device-Name': getDeviceName(),
    'X-Device-Type': getDeviceType()
  }

  if (!isWebEnvironment()) {
    const token = await getToken('accessToken')
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
  }

  const response = await fetch(`${API_BASE_URL}/linkmate/group/reply/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ conversationId, question, deepThinking: !!deepThinking }),
    credentials: isWebEnvironment() ? 'include' : 'omit',
    signal
  })

  if (!response.ok) {
    let errMsg = '请求失败'
    try {
      const json = await response.json()
      errMsg = json.message || errMsg
    } catch {
      // ignore
    }
    handlers.onError?.(errMsg)
    return
  }

  const reader = response.body?.getReader()
  if (!reader) {
    handlers.onError?.('无法读取响应流')
    return
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    buffer = buffer.replace(/\r\n/g, '\n')

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const rawEvent = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      parseImSseEvent(rawEvent, handlers)
      boundary = buffer.indexOf('\n\n')
    }
  }

  if (buffer.trim()) {
    parseImSseEvent(buffer.replace(/\r\n/g, '\n'), handlers)
  }
}

function parseImSseEvent(raw: string, handlers: LinkMateImStreamHandlers) {
  let eventName = 'message'
  let data = ''
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      const piece = line.slice(5).trimStart()
      data += data ? '\n' + piece : piece
    }
  }
  if (!data) return

  try {
    let payload = JSON.parse(data) as Record<string, string>
    if (typeof payload === 'string') {
      payload = JSON.parse(payload) as Record<string, string>
    }
    switch (eventName) {
      case 'start':
        if (payload.conversationId) handlers.onStart?.(payload.conversationId)
        break
      case 'delta':
        if (payload.content != null && payload.content !== '') handlers.onDelta?.(payload.content)
        break
      case 'reasoning_delta':
        if (payload.content != null && payload.content !== '')
          handlers.onReasoningDelta?.(payload.content)
        break
      case 'done': {
        if (payload.messageId && payload.conversationId) {
          const totalTokens = payload.totalTokens ? Number(payload.totalTokens) : undefined
          handlers.onDone?.(payload.messageId, payload.conversationId, totalTokens)
        }
        break
      }
      case 'error':
        handlers.onError?.(payload.message || 'AI 服务错误')
        break
      default:
        break
    }
  } catch {
    // 非 JSON data 忽略
  }
}

export function listSessions() {
  return apiClient.get<unknown, ApiResult<LinkMateSession[]>>('/linkmate/sessions')
}

export function createSession() {
  return apiClient.post<unknown, ApiResult<LinkMateSession>>('/linkmate/sessions')
}

export function deleteSession(sessionId: string) {
  return apiClient.delete<unknown, ApiResult<null>>(`/linkmate/sessions/${sessionId}`)
}

export function listMessages(sessionId: string) {
  return apiClient.get<unknown, ApiResult<LinkMateMessage[]>>(
    `/linkmate/sessions/${sessionId}/messages`
  )
}

export interface LinkMateStreamHandlers {
  onStart?: (sessionId: string) => void
  onDelta?: (content: string) => void
  onReasoningDelta?: (content: string) => void
  onDone?: (messageId: string, sessionId: string, totalTokens?: number) => void
  onError?: (message: string) => void
}

/**
 * SSE 流式对话（fetch + ReadableStream，axios 不适合 event-stream）。
 */
export async function streamChat(
  request: LinkMateStreamRequest,
  handlers: LinkMateStreamHandlers,
  signal?: AbortSignal
): Promise<void> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    'Cache-Control': 'no-cache',
    'X-Device-Id': getOrCreateDeviceId(),
    'X-Device-Name': getDeviceName(),
    'X-Device-Type': getDeviceType()
  }

  if (!isWebEnvironment()) {
    const token = await getToken('accessToken')
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
  }

  const response = await fetch(`${API_BASE_URL}/linkmate/chat/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      sessionId: request.sessionId,
      message: request.message,
      deepThinking: !!request.deepThinking,
      regenerate: !!request.regenerate,
      regenerateMessageId: request.regenerateMessageId,
      imContext: request.imContext
    }),
    credentials: isWebEnvironment() ? 'include' : 'omit',
    signal
  })

  if (!response.ok) {
    let errMsg = '请求失败'
    try {
      const json = await response.json()
      errMsg = json.message || errMsg
    } catch {
      // ignore
    }
    handlers.onError?.(errMsg)
    return
  }

  const reader = response.body?.getReader()
  if (!reader) {
    handlers.onError?.('无法读取响应流')
    return
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    buffer = buffer.replace(/\r\n/g, '\n')

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const rawEvent = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      parseSseEvent(rawEvent, handlers)
      boundary = buffer.indexOf('\n\n')
    }
  }

  if (buffer.trim()) {
    parseSseEvent(buffer.replace(/\r\n/g, '\n'), handlers)
  }
}

function parseSseEvent(raw: string, handlers: LinkMateStreamHandlers) {
  let eventName = 'message'
  let data = ''
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      const piece = line.slice(5).trimStart()
      data += data ? '\n' + piece : piece
    }
  }
  if (!data) return

  try {
    let payload = JSON.parse(data) as Record<string, string>
    if (typeof payload === 'string') {
      payload = JSON.parse(payload) as Record<string, string>
    }
    switch (eventName) {
      case 'start':
        if (payload.sessionId) handlers.onStart?.(payload.sessionId)
        break
      case 'delta':
        if (payload.content != null && payload.content !== '') handlers.onDelta?.(payload.content)
        break
      case 'reasoning_delta':
        if (payload.content != null && payload.content !== '')
          handlers.onReasoningDelta?.(payload.content)
        break
      case 'done': {
        if (payload.messageId && payload.sessionId) {
          const totalTokens = payload.totalTokens ? Number(payload.totalTokens) : undefined
          handlers.onDone?.(payload.messageId, payload.sessionId, totalTokens)
        }
        break
      }
      case 'error':
        handlers.onError?.(payload.message || 'AI 服务错误')
        break
      default:
        break
    }
  } catch {
    // 非 JSON data 忽略
  }
}
