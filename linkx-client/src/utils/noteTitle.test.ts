import { describe, it, expect } from 'vitest'
import { deriveNoteTitle } from './noteTitle'

describe('deriveNoteTitle', () => {
  it('应跳过纯图片行，取后续正文', () => {
    const content = `![壁纸](lx-media:key.png)
**撒撒**`
    expect(deriveNoteTitle(content)).toBe('撒撒')
  })

  it('应优先使用 Markdown 标题', () => {
    const content = `![x](a)
正文
## 小标题`
    expect(deriveNoteTitle(content)).toBe('小标题')
  })

  it('应跳过位置/语音/附件行', () => {
    const content = `[位置：兰州]
[语音 3"](lx-media:a.webm)
[附件：文件.png](lx-media:b.png)
第一条笔记`
    expect(deriveNoteTitle(content)).toBe('第一条笔记')
  })
})
