/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import { compareSnowflakeId, maxNumericMessageId, nextLastSyncId } from './messageOrder'

describe('nextLastSyncId', () => {
  it('advances when incoming ids are newer', () => {
    expect(nextLastSyncId('100', [{ id: '101' }, { id: '150' }])).toBe('150')
  })

  it('does not move backward when persisting older pages', () => {
    expect(nextLastSyncId('1000', [{ id: '800' }, { id: '849' }])).toBe('1000')
  })

  it('ignores optimistic uuid ids', () => {
    expect(nextLastSyncId('100', [{ id: 'client-uuid' }, { id: '101' }])).toBe('101')
  })

  it('uses incoming max when current cursor is missing', () => {
    expect(nextLastSyncId(null, [{ id: '9' }, { id: '12' }])).toBe('12')
  })

  it('compares snowflake ids beyond Number.MAX_SAFE_INTEGER', () => {
    const low = '9007199254740993'
    const high = '9007199254740994'
    expect(compareSnowflakeId(high, low)).toBe(1)
    expect(nextLastSyncId(low, [{ id: high }])).toBe(high)
    expect(nextLastSyncId(high, [{ id: low }])).toBe(high)
  })
})

describe('maxNumericMessageId', () => {
  it('picks the largest numeric id', () => {
    expect(maxNumericMessageId([{ id: '2' }, { id: 'temp' }, { id: '10' }])).toBe('10')
  })
})
