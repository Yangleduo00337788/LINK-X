/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import { collectShortVideoCoverPreviews } from './shortVideoContactPreview'

describe('collectShortVideoCoverPreviews', () => {
  it('builds cover urls for recent posts', () => {
    const covers = collectShortVideoCoverPreviews(
      [
        { id: '101', description: 'a' } as never,
        { id: '102', description: 'b' } as never
      ],
      4
    )
    expect(covers).toHaveLength(2)
    expect(covers[0]).toContain('/short-video/101/cover/content')
    expect(covers[1]).toContain('/short-video/102/cover/content')
  })

  it('skips invalid ids and respects limit', () => {
    const covers = collectShortVideoCoverPreviews(
      [
        { id: '', description: 'x' } as never,
        { id: '1', description: 'a' } as never,
        { id: '2', description: 'b' } as never,
        { id: '3', description: 'c' } as never
      ],
      2
    )
    expect(covers).toHaveLength(2)
    expect(covers[0]).toContain('/short-video/1/cover/content')
    expect(covers[1]).toContain('/short-video/2/cover/content')
  })
})
