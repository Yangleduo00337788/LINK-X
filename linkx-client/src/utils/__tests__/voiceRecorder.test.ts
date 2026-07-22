import { describe, expect, it } from 'vitest'
import {
  VOICE_MIN_SECONDS,
  blobToVoiceFile,
  isVoiceDurationValid,
  voiceExtFromMime
} from '../../utils/voiceRecorder'

describe('voiceRecorder', () => {
  it('按 MIME 推断扩展名', () => {
    expect(voiceExtFromMime('audio/webm;codecs=opus')).toBe('webm')
    expect(voiceExtFromMime('audio/ogg')).toBe('ogg')
    expect(voiceExtFromMime('audio/mp4')).toBe('m4a')
    expect(voiceExtFromMime('audio/mpeg')).toBe('mp3')
    expect(voiceExtFromMime('')).toBe('webm')
  })

  it('校验最短录音时长', () => {
    expect(isVoiceDurationValid(0)).toBe(false)
    expect(isVoiceDurationValid(VOICE_MIN_SECONDS - 0.1)).toBe(false)
    expect(isVoiceDurationValid(VOICE_MIN_SECONDS)).toBe(true)
    expect(isVoiceDurationValid(12)).toBe(true)
  })

  it('Blob 转为可上传 File', () => {
    const blob = new Blob(['fake-audio'], { type: 'audio/webm' })
    const file = blobToVoiceFile(blob, 'audio/webm', 3)
    expect(file).toBeInstanceOf(File)
    expect(file.name).toMatch(/voice_\d+_3s\.webm$/)
    expect(file.type).toBe('audio/webm')
    expect(file.size).toBeGreaterThan(0)
  })
})
