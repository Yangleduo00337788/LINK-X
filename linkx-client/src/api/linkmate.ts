/**
 * 作者：yangleduo
 */
import { apiClient } from './client'
import type { ApiResult } from '../types/auth'
import { API_BASE_URL } from '../config/endpoints'
import { getToken, isWebEnvironment } from '../utils/tokenStorage'
import { getDeviceName, getDeviceType, getOrCreateDeviceId } from '../utils/deviceId'
import type { LinkMateImContext } from '../utils/buildImChatContext'
import { readLinkMateSseStream } from '../utils/linkmateSse'

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

  await readLinkMateSseStream(response.body, {
    onStart: payload => {
      if (payload.conversationId) handlers.onStart?.(payload.conversationId)
    },
    onDelta: handlers.onDelta,
    onReasoningDelta: handlers.onReasoningDelta,
    onDone: payload => {
      if (payload.messageId && payload.conversationId) {
        const totalTokens = payload.totalTokens ? Number(payload.totalTokens) : undefined
        handlers.onDone?.(payload.messageId, payload.conversationId, totalTokens)
      }
    },
    onError: handlers.onError
  })
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

export function renameSession(sessionId: string, title: string) {
  return apiClient.patch<unknown, ApiResult<LinkMateSession>>(`/linkmate/sessions/${sessionId}`, {
    title
  })
}

export function listMessages(sessionId: string, before?: string, limit = 50) {
  return apiClient.get<unknown, ApiResult<LinkMateMessage[]>>(
    `/linkmate/sessions/${sessionId}/messages`,
    {
      params: {
        ...(before ? { before } : {}),
        limit
      }
    }
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

  await readLinkMateSseStream(response.body, {
    onStart: payload => {
      if (payload.sessionId) handlers.onStart?.(payload.sessionId)
    },
    onDelta: handlers.onDelta,
    onReasoningDelta: handlers.onReasoningDelta,
    onDone: payload => {
      if (payload.messageId && payload.sessionId) {
        const totalTokens = payload.totalTokens ? Number(payload.totalTokens) : undefined
        handlers.onDone?.(payload.messageId, payload.sessionId, totalTokens)
      }
    },
    onError: handlers.onError
  })
}
