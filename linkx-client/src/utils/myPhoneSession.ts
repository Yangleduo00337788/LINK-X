/**
 * 「我的手机」会话识别：后端名称固定为中文，英文界面用 i18n 展示名比对。
 */
import { t } from '../i18n'

export const MY_PHONE_SESSION_NAME_ZH = '我的手机'

export function isMyPhoneSessionName(name?: string | null): boolean {
  if (!name) return false
  const trimmed = name.trim()
  return trimmed === MY_PHONE_SESSION_NAME_ZH || trimmed === t('chat.myPhone')
}
