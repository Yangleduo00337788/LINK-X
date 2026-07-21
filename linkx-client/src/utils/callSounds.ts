/**
 * 通话提示音（Web Audio API 合成，无需外部音频文件）
 * - ring：振铃循环（主叫等待 / 被叫来电）
 * - connect：接通短音
 * - end：挂断 / 拒接短音
 */

let cachedCtx: AudioContext | null = null
let ringTimer: ReturnType<typeof setInterval> | null = null
let lastEndAt = 0

function getCtx(): AudioContext | null {
  if (typeof window === 'undefined') return null
  if (cachedCtx) return cachedCtx
  const Ctor =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
  if (!Ctor) return null
  try {
    cachedCtx = new Ctor()
  } catch {
    return null
  }
  return cachedCtx
}

function ensureRunning(): AudioContext | null {
  const ctx = getCtx()
  if (!ctx) return null
  if (ctx.state === 'suspended') {
    void ctx.resume().catch(() => {})
  }
  return ctx
}

function tone(
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
  const attack = 0.01
  gain.gain.setValueAtTime(0, startAt)
  gain.gain.linearRampToValueAtTime(peak, startAt + attack)
  gain.gain.linearRampToValueAtTime(peak * 0.55, startAt + duration * 0.55)
  gain.gain.linearRampToValueAtTime(0, startAt + duration)
  osc.connect(gain)
  gain.connect(dest)
  osc.start(startAt)
  osc.stop(startAt + duration + 0.02)
}

/** 一段双音振铃（约 1.2s） */
function playRingBurst(volume = 0.28) {
  const ctx = ensureRunning()
  if (!ctx) return
  const now = ctx.currentTime
  const master = ctx.createGain()
  master.gain.value = volume
  master.connect(ctx.destination)
  // 经典电话双频感：440 + 480 近似
  tone(ctx, master, 'sine', 440, now, 0.35, 0.7)
  tone(ctx, master, 'sine', 480, now, 0.35, 0.55)
  tone(ctx, master, 'sine', 440, now + 0.4, 0.35, 0.7)
  tone(ctx, master, 'sine', 480, now + 0.4, 0.35, 0.55)
}

/** 开始循环振铃（呼叫中 / 来电） */
export function startCallRing() {
  stopCallRing()
  playRingBurst()
  ringTimer = setInterval(() => {
    playRingBurst()
  }, 2200)
}

/** 停止振铃 */
export function stopCallRing() {
  if (ringTimer) {
    clearInterval(ringTimer)
    ringTimer = null
  }
}

/** 接通提示音 */
export function playCallConnect() {
  stopCallRing()
  const ctx = ensureRunning()
  if (!ctx) return
  const now = ctx.currentTime
  const master = ctx.createGain()
  master.gain.value = 0.35
  master.connect(ctx.destination)
  tone(ctx, master, 'sine', 660, now, 0.12, 0.6)
  tone(ctx, master, 'sine', 880, now + 0.1, 0.18, 0.55)
}

/** 挂断 / 拒接 / 取消提示音 */
export function playCallEnd() {
  stopCallRing()
  // 短时间去重，避免 hangup 与 remote end 连续触发叠音
  const nowMs = Date.now()
  if (nowMs - lastEndAt < 400) return
  lastEndAt = nowMs
  const ctx = ensureRunning()
  if (!ctx) return
  const now = ctx.currentTime
  const master = ctx.createGain()
  master.gain.value = 0.32
  master.connect(ctx.destination)
  tone(ctx, master, 'sine', 520, now, 0.14, 0.55)
  tone(ctx, master, 'sine', 360, now + 0.12, 0.22, 0.5)
}
