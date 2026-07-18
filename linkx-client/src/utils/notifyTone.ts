/**
 * 内置提示音合成（Web Audio API）
 * <p>
 * 不依赖任何外部音频文件：用 OscillatorNode 实时合成 4 种不同音色的提示音，
 * 兼顾"零体积立即可用"和"用户能听到差异化效果"。
 * </p>
 * <p>
 * 各音色描述：
 * - default：清脆双音（短促高频 + 低频回声，主流 IM 默认音风格）
 * - chime：水晶风铃（高频正弦短琶音）
 * - bell：钟声（中频三角波慢衰减）
 * - pop：轻快气泡（低频方波快速 pop）
 * </p>
 */

export type ToneId = 'default' | 'chime' | 'bell' | 'pop'

const TONE_LIST: { id: ToneId; label: string; description: string }[] = [
  { id: 'default', label: '经典', description: '清脆双音' },
  { id: 'chime', label: '风铃', description: '水晶短琶音' },
  { id: 'bell', label: '钟声', description: '中频慢衰减' },
  { id: 'pop', label: '气泡', description: '轻快短促' }
]

export function listTones() {
  return TONE_LIST
}

let cachedCtx: AudioContext | null = null

/** 获取（或懒创建）单例 AudioContext */
function getCtx(): AudioContext | null {
  if (typeof window === 'undefined') return null
  if (cachedCtx) return cachedCtx
  const Ctor = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
  if (!Ctor) return null
  try {
    cachedCtx = new Ctor()
  } catch {
    return null
  }
  return cachedCtx
}

/**
 * 解锁 AudioContext（Chrome/Safari 要求用户交互后才能播放）。
 * 在用户首次点击设置开关时被自动调用，避免静音失败。
 */
export function unlockAudio() {
  const ctx = getCtx()
  if (!ctx) return
  if (ctx.state === 'suspended') {
    void ctx.resume().catch(() => {})
  }
}

/**
 * 播放指定音色
 * @param tone 音色 id
 * @param volume 0~1
 */
export function playTone(tone: ToneId, volume = 0.4) {
  const ctx = getCtx()
  if (!ctx) return
  if (ctx.state === 'suspended') {
    void ctx.resume().catch(() => {})
  }

  const now = ctx.currentTime
  const masterGain = ctx.createGain()
  masterGain.gain.value = volume
  masterGain.connect(ctx.destination)

  switch (tone) {
    case 'default':
      playDefault(ctx, masterGain, now)
      break
    case 'chime':
      playChime(ctx, masterGain, now)
      break
    case 'bell':
      playBell(ctx, masterGain, now)
      break
    case 'pop':
      playPop(ctx, masterGain, now)
      break
    default:
      playDefault(ctx, masterGain, now)
  }
}

/** 默认双音：C5 → E5，正弦波 */
function playDefault(ctx: AudioContext, dest: AudioNode, startAt: number) {
  const a = makeVoice(ctx, dest, 'sine', 523.25, startAt, 0.18, 0.45)
  const b = makeVoice(ctx, dest, 'sine', 659.25, startAt + 0.09, 0.18, 0.45)
  void a
  void b
}

/** 水晶风铃：C5/E5/G5/C6 高频短琶音 */
function playChime(ctx: AudioContext, dest: AudioNode, startAt: number) {
  const freqs = [523.25, 659.25, 783.99, 1046.5]
  freqs.forEach((f, i) => {
    makeVoice(ctx, dest, 'sine', f, startAt + i * 0.07, 0.22, 0.4)
  })
}

/** 钟声：单音 440Hz 三角波慢衰减 */
function playBell(ctx: AudioContext, dest: AudioNode, startAt: number) {
  makeVoice(ctx, dest, 'triangle', 440, startAt, 0.6, 0.5)
}

/** Pop：单音 220Hz 方波短促 */
function playPop(ctx: AudioContext, dest: AudioNode, startAt: number) {
  makeVoice(ctx, dest, 'square', 220, startAt, 0.08, 0.35)
}

/**
 * 创建一个声部（带 ADSR 包络）
 */
function makeVoice(
  ctx: AudioContext,
  dest: AudioNode,
  type: OscillatorType,
  freq: number,
  startAt: number,
  duration: number,
  peak: number
) {
  const osc = ctx.createOscillator()
  osc.type = type
  osc.frequency.value = freq

  const gain = ctx.createGain()
  // 简化包络：attack 5ms → sustain peak*0.6 → release 到 0
  const attack = 0.005
  const sustain = Math.max(0, duration - attack - 0.05)
  const release = duration - attack - sustain

  gain.gain.setValueAtTime(0, startAt)
  gain.gain.linearRampToValueAtTime(peak, startAt + attack)
  gain.gain.linearRampToValueAtTime(peak * 0.6, startAt + attack + sustain)
  gain.gain.linearRampToValueAtTime(0, startAt + attack + sustain + release)

  osc.connect(gain)
  gain.connect(dest)
  osc.start(startAt)
  osc.stop(startAt + duration + 0.02)
}