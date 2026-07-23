import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useContactsStore } from './contacts'

vi.mock('../api/friend', () => ({
  listFriends: vi.fn(async () => ({
    code: 200,
    data: [{ userId: '1', username: 'u1', nickname: 'N1', online: true }]
  })),
  deleteFriend: vi.fn(async () => ({ code: 200, data: null })),
  searchUsers: vi.fn(async () => ({ code: 200, data: [] }))
}))

describe('stores/contacts', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('add/remove/search/deleteFriend', async () => {
    const store = useContactsStore()
    store.addContact({
      id: '1',
      userId: '1',
      name: 'Alice',
      avatarText: 'A',
      avatarColor: '#12b7f5',
      group: '我的好友'
    })
    expect(store.friends).toHaveLength(1)
    expect(store.searchUsers('ali')).toHaveLength(1)
    expect(store.searchUsers('')).toHaveLength(1)
    store.syncFriendFromSession({
      id: '2',
      name: 'Bob',
      avatarText: 'B',
      avatarColor: '#000'
    })
    expect(store.items.length).toBeGreaterThanOrEqual(2)
    await store.deleteFriend('1')
    expect(store.items.every(c => c.id !== '1')).toBe(true)
    store.remove('2')
    expect(store.items.every(c => c.id !== '2')).toBe(true)
  })
})
