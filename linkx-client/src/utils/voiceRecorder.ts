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
  const ext = voiceExtFromMime(mimeType || blob.type)
  const name = `voice_${Date.now()}_${durationSec}s.${ext}`
  return new File([blob], name, { type: mimeType || blob.type || 'audio/webm' })
}

/**
 * 是否满足最短录音时长（不足则通常应丢弃）。
 */
export function isVoiceDurationValid(durationSec: number): boolean {
  return Number.isFinite(durationSec) && durationSec >= VOICE_MIN_SECONDS
}
