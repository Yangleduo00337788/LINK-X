import { describe, expect, it } from 'vitest'
import { compareMessageOrder } from './messageOrder'

describe('compareMessageOrder', () => {
  it('orders snowflake ids ascending', () => {
    expect(compareMessageOrder({ id: '100' }, { id: '200' })).toBeLessThan(0)
    expect(compareMessageOrder({ id: '200' }, { id: '100' })).toBeGreaterThan(0)
  })

  it('keeps optimistic uuid after server ids', () => {
    expect(compareMessageOrder({ id: '100' }, { id: 'uuid-a', time: '10:00' })).toBeLessThan(0)
    expect(compareMessageOrder({ id: 'uuid-a', time: '10:00' }, { id: '100' })).toBeGreaterThan(0)
  })
})
