import { describe, expect, it } from 'vitest'
import { parseJsonPreservingIds } from './parseJson'

describe('parseJsonPreservingIds', () => {
  it('keeps snowflake ids as strings before JSON.parse loses precision', () => {
    const raw =
      '{"code":200,"data":[{"id":18446744073709551615,"fromUserId":18446744073709551614,"toUserId":2,"status":0}]}'

    const parsed = parseJsonPreservingIds<{
      data: Array<{ id: string; fromUserId: string; toUserId: string }>
    }>(raw)

    expect(parsed.data[0].id).toBe('18446744073709551615')
    expect(parsed.data[0].fromUserId).toBe('18446744073709551614')
    expect(parsed.data[0].toUserId).toBe(2)
  })
})
