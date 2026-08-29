/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import {
  inferDefaultEndTime,
  inferDefaultStartTime,
  resolveEventDate,
  todayDateKey
} from './dateResolve'

const ref = new Date(2026, 7, 28, 15, 30, 0)

describe('dateResolve', () => {
  it('formats today date key', () => {
    expect(todayDateKey(ref)).toBe('2026-08-28')
  })

  it('resolves relative chinese dates', () => {
    expect(resolveEventDate('今天', ref)).toBe('2026-08-28')
    expect(resolveEventDate('明天', ref)).toBe('2026-08-29')
    expect(resolveEventDate('后天', ref)).toBe('2026-08-30')
    expect(resolveEventDate('昨天', ref)).toBe('2026-08-27')
  })

  it('resolves relative english dates', () => {
    expect(resolveEventDate('tomorrow', ref)).toBe('2026-08-29')
    expect(resolveEventDate('today', ref)).toBe('2026-08-28')
  })

  it('resolves date with time phrase suffix', () => {
    expect(resolveEventDate('明天下午', ref)).toBe('2026-08-29')
  })

  it('accepts valid YYYY-MM-DD', () => {
    expect(resolveEventDate('2026-08-29', ref)).toBe('2026-08-29')
  })

  it('rejects invalid date', () => {
    expect(resolveEventDate('2026-02-30', ref)).toBeNull()
    expect(resolveEventDate('', ref)).toBeNull()
    expect(resolveEventDate('不是日期', ref)).toBeNull()
  })

  it('extracts embedded yyyy-mm-dd from text', () => {
    expect(resolveEventDate('安排在2026-09-15开会', ref)).toBe('2026-09-15')
  })

  it('infers morning and noon default times', () => {
    expect(inferDefaultStartTime('明天上午')).toBe('09:00')
    expect(inferDefaultStartTime('明天中午')).toBe('12:00')
    expect(inferDefaultStartTime()).toBe('14:00')
    expect(inferDefaultEndTime('invalid')).toBe('15:00')
  })
})
