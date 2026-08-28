/**
 * 作者：yangleduo
 */
import { describe, expect, it, vi } from 'vitest'
import { dispatchLinkMateSseEvent, parseLinkMateSseEvent } from './linkmateSse'

describe('linkmateSse', () => {
  it('parses delta event', () => {
    const onDelta = vi.fn()
    parseLinkMateSseEvent('event: delta\ndata: {"content":"你好"}\n\n', { onDelta })
    expect(onDelta).toHaveBeenCalledWith('你好')
  })

  it('parses reasoning_delta event', () => {
    const onReasoningDelta = vi.fn()
    parseLinkMateSseEvent('event: reasoning_delta\ndata: {"content":"思考中"}\n\n', {
      onReasoningDelta
    })
    expect(onReasoningDelta).toHaveBeenCalledWith('思考中')
  })

  it('parses start and done events', () => {
    const onStart = vi.fn()
    const onDone = vi.fn()
    parseLinkMateSseEvent('event: start\ndata: {"sessionId":"1"}\n\n', { onStart })
    parseLinkMateSseEvent('event: done\ndata: {"messageId":"2","sessionId":"1","totalTokens":"42"}\n\n', {
      onDone
    })
    expect(onStart).toHaveBeenCalledWith({ sessionId: '1' })
    expect(onDone).toHaveBeenCalledWith({
      messageId: '2',
      sessionId: '1',
      totalTokens: '42'
    })
  })

  it('ignores empty delta chunks', () => {
    const onDelta = vi.fn()
    dispatchLinkMateSseEvent('delta', { content: '' }, { onDelta })
    expect(onDelta).not.toHaveBeenCalled()
  })

  it('routes error event to handler', () => {
    const onError = vi.fn()
    dispatchLinkMateSseEvent('error', { message: '额度不足' }, { onError })
    expect(onError).toHaveBeenCalledWith('额度不足')
  })

  it('routes tool_call event to handler', () => {
    const onToolCall = vi.fn()
    dispatchLinkMateSseEvent(
      'tool_call',
      { id: 'call_1', name: 'navigate', arguments: '{"nav":"calendar"}' },
      { onToolCall }
    )
    expect(onToolCall).toHaveBeenCalledWith({
      id: 'call_1',
      name: 'navigate',
      arguments: '{"nav":"calendar"}'
    })
  })

  it('parses double-encoded JSON payload', () => {
    const onDelta = vi.fn()
    parseLinkMateSseEvent('event: delta\ndata: "{\\"content\\":\\"ok\\"}"\n\n', { onDelta })
    expect(onDelta).toHaveBeenCalledWith('ok')
  })

  it('parses done event with agent actions', () => {
    const onDone = vi.fn()
    parseLinkMateSseEvent(
      'event: done\ndata: {"messageId":"m1","sessionId":"s1","totalTokens":"64","actions":[{"id":"c1","name":"navigate","arguments":"{\\"nav\\":\\"chat\\"}"}]}\n\n',
      { onDone }
    )
    expect(onDone).toHaveBeenCalledWith({
      messageId: 'm1',
      sessionId: 's1',
      totalTokens: '64',
      actions: [{ id: 'c1', name: 'navigate', arguments: '{"nav":"chat"}' }]
    })
  })
})
