/**
 * 作者：yangleduo
 */

export type ShortVideoTranscodeStatus =
  | 'pending'
  | 'processing'
  | 'completed'
  | 'failed'
  | 'skipped'
  | string

export function isShortVideoTranscodeActive(status?: string | null) {
  const normalized = (status || '').trim().toLowerCase()
  return normalized === 'pending' || normalized === 'processing'
}

export function isShortVideoTranscodeFailed(status?: string | null) {
  return (status || '').trim().toLowerCase() === 'failed'
}

export function shouldShowShortVideoTranscodeBadge(status?: string | null) {
  return isShortVideoTranscodeActive(status) || isShortVideoTranscodeFailed(status)
}
