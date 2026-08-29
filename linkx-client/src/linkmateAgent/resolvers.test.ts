/**
 * 作者：yangleduo
 */
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCalendarStore } from '../stores/calendar'
import { useFavoritesStore } from '../stores/favorites'
import { useNotificationsStore } from '../stores/notifications'
import { INVITE_STATUS } from '../types/inviteStatus'
import {
  buildMessageContentWithMentions,
  resolveCalendarEvent,
  resolveFavoriteItem,
  resolveFriendRequestId,
  resolveGroupInvitationId
} from './resolvers'

describe('linkmateAgent resolvers', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('buildMessageContentWithMentions prepends @ names', () => {
    expect(buildMessageContentWithMentions('你好', ['张三', '李四'])).toBe('@张三 @李四 你好')
    expect(buildMessageContentWithMentions('已有 @张三 内容', ['张三'])).toBe('已有 @张三 内容')
  })

  it('resolveCalendarEvent by id or title+date', () => {
    const calendar = useCalendarStore()
    calendar.events = [
      { id: 'e1', title: '周会', date: '2026-08-29', time: '14:00', endTime: '15:00' }
    ]
    expect(resolveCalendarEvent({ eventId: 'e1' })?.title).toBe('周会')
    expect(resolveCalendarEvent({ title: '周会', date: '2026-08-29' })?.id).toBe('e1')
    expect(resolveCalendarEvent({ title: '不存在' })).toBeNull()
  })

  it('resolveFavoriteItem by id or title', () => {
    const favorites = useFavoritesStore()
    favorites.items = [
      {
        id: 'f1',
        title: 'E2E 收藏',
        content: '内容',
        preview: '内容',
        type: 'note',
        time: '12:00'
      }
    ]
    expect(resolveFavoriteItem({ favoriteId: 'f1' })?.title).toBe('E2E 收藏')
    expect(resolveFavoriteItem({ title: 'E2E' })?.id).toBe('f1')
  })

  it('resolveFriendRequestId by requestId or fromName', () => {
    const notifications = useNotificationsStore()
    notifications.friendNotifs = [
      {
        id: 'r1',
        requestId: 'r1',
        fromUserId: 'u1',
        peerUserId: 'u1',
        direction: 'incoming',
        avatar: '',
        name: '王五',
        action: '请求加好友',
        date: '2026/08/28',
        createTime: '',
        message: '',
        source: '',
        status: INVITE_STATUS.PENDING
      }
    ]
    expect(resolveFriendRequestId({ requestId: 'r1' })).toBe('r1')
    expect(resolveFriendRequestId({ fromName: '王五' })).toBe('r1')
  })

  it('resolveGroupInvitationId by invitationId or groupName', () => {
    const notifications = useNotificationsStore()
    notifications.groupNotifs = [
      {
        id: 'g1',
        invitationId: 'g1',
        conversationId: '201',
        groupName: '项目群',
        inviterUserId: 'u2',
        inviter: '李四',
        date: '2026/08/28',
        createTime: '',
        status: INVITE_STATUS.PENDING
      }
    ]
    expect(resolveGroupInvitationId({ invitationId: 'g1' })).toBe('g1')
    expect(resolveGroupInvitationId({ groupName: '项目' })).toBe('g1')
  })
})
