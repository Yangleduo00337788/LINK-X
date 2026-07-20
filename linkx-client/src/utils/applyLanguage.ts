/**
 * 全局语言切换：更新 i18n、本地偏好、服务端同步、Electron 托盘文案。
 */
import { useMessage } from 'naive-ui'
import { useAppSettingsStore } from '../stores/appSettings'
import { setLocale, t } from '../i18n'

export const LANGUAGE_OPTIONS = [
  { label: '简体中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
] as const

export async function applyAppLanguage(value: string, opts?: { silent?: boolean }) {
  const settings = useAppSettingsStore()
  const applied = setLocale(value)
  settings.language = applied
  settings.scheduleSave('language')
  await settings.syncDesktopPrefs()
  if (!opts?.silent) {
    try {
      const message = useMessage()
      message.success(t('general.languageApplied'))
    } catch {
      // 无 message provider 时忽略 toast
    }
  }
  return applied
}
