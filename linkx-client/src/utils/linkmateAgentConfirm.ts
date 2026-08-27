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

export async function confirmLinkMateAction(label: string): Promise<boolean> {
  if (!dialogProvider) return false
  return new Promise(resolve => {
    dialogProvider!.warning({
      title: t('linkmateAgent.confirmTitle'),
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
