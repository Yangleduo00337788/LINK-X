/**
 * 作者：yangleduo
 */
import type { MessageKey } from '../i18n'

/** 将服务端中文错误映射为当前语言文案 */
export function resolveLinkMateErrorMessage(
  raw: string,
  t: (key: MessageKey | string) => string
): string {
  const msg = raw.trim()
  if (!msg) return t('linkmate.sendFailed')

  if (msg.includes('过于频繁') || /too many/i.test(msg)) {
    return t('linkmate.streamTooMany')
  }
  if (msg.includes('额度') || /quota/i.test(msg)) {
    return t('linkmate.dailyQuotaExhausted')
  }
  if (msg.includes('未启用') || /not enabled/i.test(msg)) {
    return t('linkmate.serviceDisabled')
  }
  if (msg.includes('转写') || /transcri/i.test(msg)) {
    return t('chat.transcribeFail')
  }
  return msg
}
