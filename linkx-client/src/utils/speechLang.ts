/**
 * 作者：yangleduo
 */
import { getLocale } from '../i18n'
import { resolveTranslateTargetLang } from './translateLang'

export type SpeechLangHint = 'zh' | 'en' | 'ja' | 'ko'

/** 语音转写语言提示：优先跟随翻译目标语言设置 */
export function resolveSpeechLanguageHint(translateTargetPref?: string | null): SpeechLangHint {
  const lang = resolveTranslateTargetLang(translateTargetPref)
  if (lang === 'zh' || lang === 'en' || lang === 'ja' || lang === 'ko') {
    return lang
  }
  return getLocale() === 'en-US' ? 'en' : 'zh'
}
