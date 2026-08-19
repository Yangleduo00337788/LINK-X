/**
 * 作者：yangleduo
 */
/**
 * 聊天语音录制工具：挑选浏览器支持的 MIME，并封装 MediaRecorder 启停。
 */

export const VOICE_MAX_SECONDS = 60
export const VOICE_MIN_SECONDS = 1

const PREFERRED_MIME_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/ogg;codecs=opus',
  'audio/ogg',
  'audio/mp4',
  'audio/aac'
] as const

/** 返回当前环境 MediaRecorder 可用的 MIME（无则空串，走浏览器默认） */
export function pickVoiceMimeType(): string {
  if (typeof MediaRecorder === 'undefined' || typeof MediaRecorder.isTypeSupported !== 'function') {
    return ''
  }
  for (const mime of PREFERRED_MIME_TYPES) {
    if (MediaRecorder.isTypeSupported(mime)) return mime
  }
  return ''
}

/** 按 MIME 推断上传文件扩展名 */
export function voiceExtFromMime(mime: string): string {
  const base = mime.split(';')[0]?.trim().toLowerCase() || ''
  if (base === 'audio/ogg') return 'ogg'
  if (base === 'audio/mp4' || base === 'audio/aac' || base === 'audio/x-m4a') return 'm4a'
  if (base === 'audio/mpeg' || base === 'audio/mp3') return 'mp3'
  if (base === 'audio/wav' || base === 'audio/wave') return 'wav'
  return 'webm'
}

export interface VoiceRecordResult {
  blob: Blob
  mimeType: string
  durationSec: number
  file: File
}

/**
 * 将 Blob 转为带正确扩展名的 File，供聊天上传接口使用。
 */
export function blobToVoiceFile(blob: Blob, mimeType: string, durationSec: number): File {
  const raw = mimeType || blob.type || 'audio/webm'
  // 上传只带基础 MIME，避免 audio/webm;codecs=opus 被服务端白名单拒绝
  const baseType = raw.split(';')[0]?.trim().toLowerCase() || 'audio/webm'
  const ext = voiceExtFromMime(baseType)
  const name = `voice_${Date.now()}_${durationSec}s.${ext}`
  return new File([blob], name, { type: baseType })
}

/**
 * 是否满足最短录音时长（不足则通常应丢弃）。
 */
export function isVoiceDurationValid(durationSec: number): boolean {
  return Number.isFinite(durationSec) && durationSec >= VOICE_MIN_SECONDS
}

/** 录音已进行时长（秒，保留一位小数，上限 VOICE_MAX_SECONDS） */
export function elapsedVoiceSeconds(startedAt: number): number {
  if (!startedAt) return 0
  const sec = (Date.now() - startedAt) / 1000
  return Math.min(VOICE_MAX_SECONDS, Math.round(sec * 10) / 10)
}

/** 气泡/提示中展示语音时长 */
export function formatVoiceDurationLabel(sec?: number): string {
  const s = sec ?? 0
  if (s < 60) {
    const text = Number.isInteger(s) ? String(s) : s.toFixed(1)
    return `${text}"`
  }
  const min = Math.floor(s / 60)
  const rest = Math.round((s - min * 60) * 10) / 10
  const restText = Number.isInteger(rest) ? String(rest) : rest.toFixed(1)
  return `${min}'${restText}"`
}
