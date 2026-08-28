/**
 * 作者：yangleduo
 */
import type { LinkMateAgentAction, LinkMateAgentToolName } from './types'

/** 会话解析基准用例 */
export interface SessionResolveBenchmarkCase {
  id: string
  description: string
  args: Record<string, unknown>
  expectedSessionId: string | null
}

/** 动作解析基准用例 */
export interface ActionParseBenchmarkCase {
  id: string
  description: string
  raw: { id?: string; name?: string; arguments?: unknown }
  expected: Pick<LinkMateAgentAction, 'name' | 'arguments'> | null
}

/** 动作参数匹配基准（服务端/客户端契约） */
export interface ActionMatchBenchmarkCase {
  id: string
  description: string
  name: LinkMateAgentToolName
  arguments: Record<string, unknown>
  /** 必须包含的键 */
  requiredKeys: string[]
}

export const SESSION_RESOLVE_BENCHMARK: SessionResolveBenchmarkCase[] = [
  {
    id: 'sr-01',
    description: '按会话 ID 精确匹配',
    args: { conversationId: '101' },
    expectedSessionId: '101'
  },
  {
    id: 'sr-02',
    description: '好友名 + direct 优先单聊',
    args: { name: '张三', chatType: 'direct' },
    expectedSessionId: '101'
  },
  {
    id: 'sr-03',
    description: '群名 + group 优先群聊',
    args: { name: '项目群', chatType: 'group' },
    expectedSessionId: '201'
  },
  {
    id: 'sr-04',
    description: '好友名不误选含该名的群',
    args: { name: '张三', chatType: 'direct' },
    expectedSessionId: '101'
  },
  {
    id: 'sr-05',
    description: '李四单聊',
    args: { name: '李四', chatType: 'direct' },
    expectedSessionId: '102'
  },
  {
    id: 'sr-06',
    description: '群名部分匹配',
    args: { name: '技术', chatType: 'group' },
    expectedSessionId: '203'
  },
  {
    id: 'sr-07',
    description: '不存在的目标',
    args: { name: '赵六', chatType: 'direct' },
    expectedSessionId: null
  },
  {
    id: 'sr-08',
    description: '群聊名逗号分隔成员',
    args: { name: '李四', chatType: 'group' },
    expectedSessionId: '202'
  }
]

export const ACTION_PARSE_BENCHMARK: ActionParseBenchmarkCase[] = [
  {
    id: 'ap-01',
    description: 'navigate 对象参数',
    raw: { id: 'c1', name: 'navigate', arguments: { nav: 'chat' } },
    expected: { name: 'navigate', arguments: { nav: 'chat' } }
  },
  {
    id: 'ap-02',
    description: 'send_message JSON 字符串参数',
    raw: {
      id: 'c2',
      name: 'send_message',
      arguments: '{"name":"张三","content":"你好","chatType":"direct"}'
    },
    expected: {
      name: 'send_message',
      arguments: { name: '张三', content: '你好', chatType: 'direct' }
    }
  },
  {
    id: 'ap-03',
    description: 'create_calendar_event 必填字段',
    raw: {
      name: 'create_calendar_event',
      arguments: { title: '会议', date: '2026-08-27' }
    },
    expected: {
      name: 'create_calendar_event',
      arguments: { title: '会议', date: '2026-08-27' }
    }
  },
  {
    id: 'ap-04',
    description: '未知工具名拒绝',
    raw: { name: 'hack_client', arguments: {} },
    expected: null
  },
  {
    id: 'ap-05',
    description: 'add_favorite 标题',
    raw: { name: 'add_favorite', arguments: { title: '笔记', content: '内容' } },
    expected: { name: 'add_favorite', arguments: { title: '笔记', content: '内容' } }
  }
]

export const ACTION_MATCH_BENCHMARK: ActionMatchBenchmarkCase[] = [
  {
    id: 'am-01',
    description: 'navigate 需要 nav',
    name: 'navigate',
    arguments: { nav: 'calendar' },
    requiredKeys: ['nav']
  },
  {
    id: 'am-02',
    description: 'send_message 需要 content',
    name: 'send_message',
    arguments: { content: 'hi', name: '张三', chatType: 'direct' },
    requiredKeys: ['content']
  },
  {
    id: 'am-03',
    description: 'create_calendar_event 需要 title+date',
    name: 'create_calendar_event',
    arguments: { title: '周会', date: '2026-08-27' },
    requiredKeys: ['title', 'date']
  },
  {
    id: 'am-04',
    description: 'add_favorite 需要 title',
    name: 'add_favorite',
    arguments: { title: '链接', type: 'link' },
    requiredKeys: ['title']
  }
]

/** 最低通过率阈值（精准率基线） */
export const BENCHMARK_MIN_PASS_RATE = 0.9
