/**
 * 作者：yangleduo
 */
import { appendSessionMessage, buildAckFromSend } from './demo-state'

class DemoWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSING = 2
  static CLOSED = 3

  readonly CONNECTING = DemoWebSocket.CONNECTING
  readonly OPEN = DemoWebSocket.OPEN
  readonly CLOSING = DemoWebSocket.CLOSING
  readonly CLOSED = DemoWebSocket.CLOSED

  readyState = DemoWebSocket.CONNECTING
  url: string
  protocol = ''
  extensions = ''
  binaryType: BinaryType = 'blob'

  onopen: ((ev: Event) => void) | null = null
  onclose: ((ev: CloseEvent) => void) | null = null
  onerror: ((ev: Event) => void) | null = null
  onmessage: ((ev: MessageEvent) => void) | null = null

  constructor(url: string) {
    this.url = url
    // 晚于 connectChatSocket 赋值 onopen（同宏任务内完成赋值）
    queueMicrotask(() => {
      if (this.readyState === DemoWebSocket.CLOSED) return
      this.readyState = DemoWebSocket.OPEN
      const ev = new Event('open')
      this.onopen?.(ev)
    })
  }

  send(data: string | ArrayBufferLike | Blob | ArrayBufferView) {
    if (typeof data !== 'string') return
    let frame: Record<string, unknown>
    try {
      frame = JSON.parse(data) as Record<string, unknown>
    } catch {
      return
    }
    const action = frame.action
    if (action === 'ping') {
      this.emit({ action: 'pong' })
      return
    }
    if (action === 'send' && frame.clientMsgId && frame.conversationId) {
      const ackItem = buildAckFromSend({
        clientMsgId: String(frame.clientMsgId),
        conversationId: String(frame.conversationId),
        msgType: String(frame.msgType || 'text'),
        content: frame.content as string | undefined,
        fileName: frame.fileName as string | undefined,
        fileUrl: frame.fileUrl as string | undefined,
        voiceDuration: frame.voiceDuration as number | undefined,
      })
      appendSessionMessage(String(frame.conversationId), ackItem)
      this.emit({
        action: 'ack',
        clientMsgId: frame.clientMsgId,
        data: ackItem,
      })
    }
  }

  private emit(payload: Record<string, unknown>) {
    const event = new MessageEvent('message', { data: JSON.stringify(payload) })
    this.onmessage?.(event)
  }

  close() {
    this.readyState = DemoWebSocket.CLOSED
    this.onclose?.(new CloseEvent('close'))
  }

  addEventListener(type: string, listener: EventListener) {
    if (type === 'open') this.onopen = listener as (ev: Event) => void
    if (type === 'close') this.onclose = listener as (ev: CloseEvent) => void
    if (type === 'error') this.onerror = listener as (ev: Event) => void
    if (type === 'message') this.onmessage = listener as (ev: MessageEvent) => void
  }

  removeEventListener() {}
}

export function installClientWebSocketMock() {
  window.WebSocket = function WebSocketProxy(url: string | URL, _protocols?: string | string[]) {
    return new DemoWebSocket(String(url)) as unknown as WebSocket
  } as typeof WebSocket
  window.WebSocket.CONNECTING = 0
  window.WebSocket.OPEN = 1
  window.WebSocket.CLOSING = 2
  window.WebSocket.CLOSED = 3
}
