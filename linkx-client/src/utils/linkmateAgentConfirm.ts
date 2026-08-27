/**
 * 作者：yangleduo
 */
import { t } from '../i18n'

let dialogProvider: {
  warning: (options: {
    title: string
    content: string
    positiveText: string
    negativeText: string
    onPositiveClick: () => void
    onNegativeClick: () => void
  }) => void
} | null = null

export function registerLinkMateAgentDialog(provider: typeof dialogProvider) {
  dialogProvider = provider
}

export async function confirmLinkMateAction(label: string, risk: 'medium' | 'high' = 'medium'): Promise<boolean> {
  if (!dialogProvider) return false
  const title =
    risk === 'high' ? t('linkmateAgent.confirmTitleHigh') : t('linkmateAgent.confirmTitle')
  return new Promise(resolve => {
    dialogProvider!.warning({
      title,
      content: t('linkmateAgent.confirmBody', { action: label }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => {
        resolve(true)
        return true
      },
      onNegativeClick: () => {
        resolve(false)
        return true
      }
    })
  })
}
