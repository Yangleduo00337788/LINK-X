/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import {
  buildLinkMateMentionRegExp,
  extractLinkMateQuestion,
  hasLinkMateMention
} from './linkmateMention'

const AT_NAME = '灵伴 LinkMate'

describe('linkmateMention', () => {
  it('detects Chinese @ mention', () => {
    expect(hasLinkMateMention('@灵伴 你好', AT_NAME)).toBe(true)
  })

  it('detects English @ mention', () => {
    expect(hasLinkMateMention('@LinkMate summarize this', AT_NAME)).toBe(true)
  })

  it('detects combined @灵伴 LinkMate alias', () => {
    expect(hasLinkMateMention('@灵伴 LinkMate 帮我写邮件', AT_NAME)).toBe(true)
  })

  it('ignores plain text without mention', () => {
    expect(hasLinkMateMention('灵伴你好', AT_NAME)).toBe(false)
  })

  it('extracts question after mention', () => {
    expect(extractLinkMateQuestion('@灵伴  今天天气怎么样？', AT_NAME)).toBe('今天天气怎么样？')
  })

  it('returns null when mention has no question', () => {
    expect(extractLinkMateQuestion('@LinkMate   ', AT_NAME)).toBeNull()
  })

  it('builds case-insensitive regexp', () => {
    const re = buildLinkMateMentionRegExp(AT_NAME)
    expect(re.test('@linkmate hello')).toBe(true)
  })
})
