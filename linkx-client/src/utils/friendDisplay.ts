/** 好友展示名：（昵称）备注；无备注时仅昵称 */
import { t } from '../i18n'

export function formatFriendDisplayName(nickname?: string | null, remark?: string | null): string {
  const nick = (nickname || '').trim() || t('modals.friend')
  const r = (remark || '').trim()
  return r ? `（${nick}）${r}` : nick
}

/** 头像字取备注或昵称首字，避免「（」 */
export function friendAvatarText(nickname?: string | null, remark?: string | null): string {
  const r = (remark || '').trim()
  const nick = (nickname || '').trim()
  return (r || nick || '?').charAt(0) || '?'
}
