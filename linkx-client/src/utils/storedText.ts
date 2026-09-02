/**
 * 作者：yangleduo
 */

/**
 * 解码服务端「写时 htmlEscape」存储的用户文本实体，用于以纯文本插值展示。
 *
 * 服务端对经文本插值呈现的字段（聊天文本、群公告、昵称/签名等）在入库时调用
 * HtmlUtils.htmlEscape，而客户端用 Vue 文本插值（{{ }}）渲染又会转义一次，
 * 导致如 "a < b" 显示为字面 "a &lt; b"。本函数在渲染前把实体还原为原文，
 * 纠正双转义。
 *
 * - 对不含实体的字符串是 no-op，故对已按纯文本存储的字段（如朋友圈）调用也零影响。
 * - 与 linkmateMarkdown 中 Markdown 渲染前的归一化一致：先在入口还原，再按各自
 *   （纯文本插值 / Markdown + DOMPurify）方式渲染，避免「写时实体 + 读时还原」叠加。
 */
export function decodeStoredText(text: string): string {
  if (!text || !/&(?:#x?[0-9a-f]+|[a-z]+);/i.test(text)) return text
  if (typeof document === 'undefined') return text
  const el = document.createElement('textarea')
  el.innerHTML = text
  return el.value
}