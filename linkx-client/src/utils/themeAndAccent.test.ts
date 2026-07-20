import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { resolveThemePreference, resolveSystemTheme } from './themeSync'
import { applyAccentColor, liveAccentColor } from './accentColor'

describe('themeSync follow system', () => {
  it('resolveThemePreference uses explicit light/dark', () => {
    expect(resolveThemePreference('light')).toBe('light')
    expect(resolveThemePreference('dark')).toBe('dark')
  })

  it('resolveThemePreference(system) matches resolveSystemTheme', () => {
    expect(resolveThemePreference('system')).toBe(resolveSystemTheme())
  })
})

describe('accentColor rainbow', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    applyAccentColor('cyan')
  })

  afterEach(() => {
    applyAccentColor('cyan')
    vi.useRealTimers()
  })

  it('rainbow cycles accent color over time', () => {
    const before = liveAccentColor.value
    applyAccentColor('rainbow')
    const start = liveAccentColor.value
    expect(start).toMatch(/^#[0-9a-f]{6}$/i)

    vi.advanceTimersByTime(400)
    const later = liveAccentColor.value
    expect(later).toMatch(/^#[0-9a-f]{6}$/i)
    expect(later).not.toBe(start)
    expect(later).not.toBe(before)
  })
})
