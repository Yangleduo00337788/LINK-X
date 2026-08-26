/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import {
  isShortVideoTranscodeActive,
  isShortVideoTranscodeFailed,
  shouldShowShortVideoTranscodeBadge
} from './shortVideoTranscode'

describe('shortVideoTranscode', () => {
  it('detects active transcode states', () => {
    expect(isShortVideoTranscodeActive('pending')).toBe(true)
    expect(isShortVideoTranscodeActive('processing')).toBe(true)
    expect(isShortVideoTranscodeActive('completed')).toBe(false)
    expect(isShortVideoTranscodeActive(null)).toBe(false)
  })

  it('detects failed transcode', () => {
    expect(isShortVideoTranscodeFailed('failed')).toBe(true)
    expect(isShortVideoTranscodeFailed('pending')).toBe(false)
  })

  it('shows badge only for active or failed', () => {
    expect(shouldShowShortVideoTranscodeBadge('pending')).toBe(true)
    expect(shouldShowShortVideoTranscodeBadge('failed')).toBe(true)
    expect(shouldShowShortVideoTranscodeBadge('completed')).toBe(false)
    expect(shouldShowShortVideoTranscodeBadge('skipped')).toBe(false)
  })
})
