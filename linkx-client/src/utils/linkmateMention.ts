/**
 * 作者：yangleduo
 */
import { t } from '../i18n'

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 构建 @小助手 提及正则（兼容中英文 locale 及历史写法） */
export function buildLinkMateMentionRegExp(atName?: string): RegExp {
  const name = (atName ?? t('linkmate.atName')).trim()
  const alts = new Set<string>([
    '灵伴(?:\\s*LinkMate)?',
    'LinkMate',
    '群聊小助手',
    'Group\\s+assistant'
  ])
  if (name && name !== 'LinkMate') {
    alts.add(escapeRegExp(name))
  }
  return new RegExp(`@(?:${[...alts].join('|')})`, 'i')
}

export function hasLinkMateMention(text: string, atName?: string): boolean {
  return buildLinkMateMentionRegExp(atName).test(text)
}

export function extractLinkMateQuestion(text: string, atName?: string): string | null {
  const re = buildLinkMateMentionRegExp(atName)
  if (!re.test(text)) return null
  const question = text.replace(re, ' ').replace(/\s+/g, ' ').trim()
  return question || null
}
