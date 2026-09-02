/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import { decodeStoredText } from './storedText'

describe('decodeStoredText', () => {
  it('解码服务端写时转义的实体，纠正双转义', () => {
    expect(decodeStoredText('a &lt; b')).toBe('a < b')
    expect(decodeStoredText('a &amp; b')).toBe('a & b')
    expect(decodeStoredText('&quot;quoted&quot;')).toBe('"quoted"')
    expect(decodeStoredText("it&#x27;s")).toBe("it's")
    expect(decodeStoredText('&#39;apos&#39;')).toBe("'apos'")
  })

  it('对不含实体的纯文本是逐字返回（no-op），可安全用于存裸文本字段', () => {
    expect(decodeStoredText('普通文本 a < b')).toBe('普通文本 a < b')
    expect(decodeStoredText('')).toBe('')
    expect(decodeStoredText('hello world')).toBe('hello world')
  })

  it('多实体连续解码', () => {
    expect(decodeStoredText('&lt;script&gt; &amp; &lt;b&gt;')).toBe('<script> & <b>')
  })
})