/** 浏览器语音朗读（Web Speech API） */

import { usePreferencesStore } from '@/stores/preferences'

let localeHint = 'zh-CN'
let queue: string[] = []
let queueRunning = false
/** Chrome/Edge 要求曾在用户手势中触发过 speak，异步通知朗读才会生效。 */
let speechPrimed = false

export function isSpeechSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'speechSynthesis' in window &&
    'SpeechSynthesisUtterance' in window
  )
}

function resolveLang(locale?: string) {
  return locale === 'en-US' ? 'en-US' : 'zh-CN'
}

function isChineseLang(lang: string) {
  return lang === 'zh-CN' || lang.startsWith('zh')
}

function pickDefaultVoice(lang: string, voices: SpeechSynthesisVoice[]): SpeechSynthesisVoice | undefined {
  if (!voices.length) return undefined

  const wantZh = isChineseLang(lang)
  const preferred = wantZh
    ? ['Microsoft Huihui', 'Microsoft Yaoyao', 'Microsoft Kangkang', 'Google 普通话', 'Ting-Ting']
    : ['Microsoft Zira', 'Microsoft David', 'Google US English', 'Samantha']

  for (const name of preferred) {
    const hit = voices.find((v) => v.name.includes(name))
    if (hit) return hit
  }

  const byLang = voices.filter((v) =>
    wantZh ? v.lang.toLowerCase().startsWith('zh') : v.lang.toLowerCase().startsWith('en')
  )
  return byLang.find((v) => v.localService) || byLang[0] || voices[0]
}

export function resolveSpeechVoice(
  lang: string,
  voices: SpeechSynthesisVoice[],
  preferredUri?: string
): SpeechSynthesisVoice | undefined {
  const uri = (preferredUri || usePreferencesStore().speechVoiceUri || '').trim()
  if (uri) {
    const hit = voices.find((v) => v.voiceURI === uri || v.name === uri)
    if (hit) return hit
  }
  return pickDefaultVoice(lang, voices)
}

/** 按界面语言筛选可用朗读音色。 */
export function listVoicesForLang(locale: string, voices: SpeechSynthesisVoice[]) {
  const lang = resolveLang(locale)
  const wantZh = isChineseLang(lang)
  return voices.filter((v) =>
    wantZh ? v.lang.toLowerCase().startsWith('zh') : v.lang.toLowerCase().startsWith('en')
  )
}

/** 等待系统语音包加载（Chrome/Edge 首次常为空）。 */
export function ensureVoices(): Promise<SpeechSynthesisVoice[]> {
  return new Promise((resolve) => {
    if (!isSpeechSupported()) {
      resolve([])
      return
    }

    const synth = window.speechSynthesis
    const read = () => synth.getVoices()

    const tryResolve = () => {
      const voices = read()
      if (voices.length) {
        resolve(voices)
        return true
      }
      return false
    }

    if (tryResolve()) return

    const onChange = () => {
      if (tryResolve()) {
        synth.removeEventListener('voiceschanged', onChange)
      }
    }
    synth.addEventListener('voiceschanged', onChange)
    void read()

    window.setTimeout(() => {
      synth.removeEventListener('voiceschanged', onChange)
      resolve(read())
    }, 1200)
  })
}

/** 在用户点击/登录等手势回调中调用，解锁异步语音朗读并预加载语音包。 */
export function unlockSpeech(locale?: string) {
  if (locale) localeHint = resolveLang(locale)
  if (!isSpeechSupported()) return

  if (!speechPrimed) {
    const synth = window.speechSynthesis
    const utterance = new SpeechSynthesisUtterance('\u200b')
    utterance.volume = 0
    utterance.rate = 10
    synth.speak(utterance)
    speechPrimed = true
  }

  void ensureVoices()
}

export function isSpeechPrimed() {
  return speechPrimed
}

function normalizeText(text: string) {
  return text.trim().replace(/\s+/g, ' ')
}

function speakOnce(text: string, lang: string, voices: SpeechSynthesisVoice[]): Promise<boolean> {
  return new Promise((resolve) => {
    if (!isSpeechSupported()) {
      resolve(false)
      return
    }

    const synth = window.speechSynthesis
    synth.cancel()

    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = lang
    utterance.rate = 1
    utterance.pitch = 1
    utterance.volume = 1
    const voice = resolveSpeechVoice(lang, voices)
    if (voice) utterance.voice = voice

    let settled = false
    const finish = (ok: boolean) => {
      if (settled) return
      settled = true
      window.clearInterval(keepAlive)
      window.clearTimeout(fallbackTimer)
      resolve(ok)
    }

    utterance.onend = () => finish(true)
    utterance.onerror = () => finish(false)

    const keepAlive = window.setInterval(() => {
      if (!synth.speaking) return
      synth.pause()
      synth.resume()
    }, 5000)

    const fallbackTimer = window.setTimeout(() => {
      if (!synth.speaking) finish(false)
    }, Math.max(4000, text.length * 280))

    window.setTimeout(() => {
      synth.speak(utterance)
      if (synth.paused) synth.resume()
    }, 0)
  })
}

/** 用户点击「试听」：必须在点击回调里直接调用。 */
export async function previewSpeech(text: string, locale?: string): Promise<boolean> {
  const normalized = normalizeText(text)
  if (!normalized) return false
  if (!isSpeechSupported()) return false

  const lang = resolveLang(locale)
  localeHint = lang
  const voices = await ensureVoices()
  return speakOnce(normalized, lang, voices)
}

async function runQueue(locale?: string) {
  if (queueRunning) return
  queueRunning = true
  try {
    const voices = await ensureVoices()
    while (queue.length) {
      const text = queue.shift()
      if (!text) continue
      const lang = resolveLang(locale || localeHint)
      localeHint = lang
      await speakOnce(text, lang, voices)
    }
  } finally {
    queueRunning = false
    if (queue.length) void runQueue(locale)
  }
}

/** 排队朗读（用于实时通知）。 */
export function speakText(text: string, locale?: string) {
  const normalized = normalizeText(text)
  if (!normalized || !isSpeechSupported()) return
  if (locale) localeHint = resolveLang(locale)
  queue.push(normalized)
  void runQueue(locale)
}

export function cancelSpeech() {
  if (!isSpeechSupported()) return
  window.speechSynthesis.cancel()
  queue = []
  queueRunning = false
}
