/**
 * 灵伴 Markdown 懒加载：highlight.js + marked 仅在需要渲染代码块时加载，减轻首屏包体。
 */
type MarkdownModule = typeof import('./linkmateMarkdown')

let mod: MarkdownModule | null = null
let loading: Promise<MarkdownModule> | null = null

export function loadLinkMateMarkdown(): Promise<MarkdownModule> {
  if (mod) return Promise.resolve(mod)
  loading ??= import('./linkmateMarkdown').then(m => {
    mod = m
    return m
  })
  return loading
}

export async function renderLinkMateMarkdownLazy(content: string): Promise<string> {
  const m = await loadLinkMateMarkdown()
  return m.renderLinkMateMarkdown(content)
}

export async function copyCodeFromButtonLazy(btn: HTMLElement): Promise<boolean> {
  const m = await loadLinkMateMarkdown()
  return m.copyCodeFromButton(btn)
}
