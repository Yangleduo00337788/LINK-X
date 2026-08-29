/**
 * 作者：yangleduo
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useLinkMateAgentStore } from './linkmateAgent'

describe('linkmateAgent store storage edge cases', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('loadAgentMode returns false when localStorage.getItem throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied')
    })
    setActivePinia(createPinia())
    expect(useLinkMateAgentStore().agentMode).toBe(false)
  })

  it('persistAgentMode ignores setItem errors', () => {
    setActivePinia(createPinia())
    const store = useLinkMateAgentStore()
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota')
    })
    expect(() => store.toggleAgentMode()).not.toThrow()
  })
})
