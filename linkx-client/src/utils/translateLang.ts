/**
 * 作者：yangleduo
 */
import { getLocale } from '../i18n'

export type TranslateLangCode = 'zh' | 'en' | 'ja' | 'ko'
export type TranslateTargetPref = TranslateLangCode | 'auto'

export const TRANSLATE_TARGET_PREFS: TranslateTargetPref[] = ['auto', 'zh', 'en', 'ja', 'ko']

/** 根据界面语言推断默认翻译目标语言 */
export function defaultTranslateTargetLang(): TranslateLangCode {
  return getLocale() === 'en-US' ? 'en' : 'zh'
}

export function normalizeTranslateTargetPref(value?: string | null): TranslateTargetPref {
  if (value === 'zh' || value === 'en' || value === 'ja' || value === 'ko' || value === 'auto') {
    return value
  }
  return 'auto'
}

/** 根据用户偏好解析实际翻译目标语言 */
export function resolveTranslateTargetLang(pref?: string | null): TranslateLangCode {
  const normalized = normalizeTranslateTargetPref(pref)
  if (normalized === 'auto') {
    return defaultTranslateTargetLang()
  }
  return normalized
}

export function translateLangLabel(code: TranslateLangCode): string {
  switch (code) {
    case 'zh':
      return '简体中文'
    case 'en':
      return 'English'
    case 'ja':
      return '日本語'
    case 'ko':
      return '한국어'
    default:
      return code
  }
}

export function translateTargetPrefLabel(
  pref: TranslateTargetPref,
  t: (key: string) => string
): string {
  if (pref === 'auto') return t('chatSettings.translateTargetAuto')
  return translateLangLabel(pref)
}
