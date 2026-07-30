// 叠加页栈 Store
import { useOverlayStore } from '../stores/overlay'
// 设置弹窗 Store
import { useSettingsStore } from '../stores/settings'
// 聊天相关模态框 Store
import { useChatModalsStore } from '../stores/chatModals'
// 通话 Store
import { useCallStore } from '../stores/call'

/**
 * 清理 Naive UI 挂到 body 的残留层（下拉菜单、模态遮罩等）。
 * 从菜单项触发登出时，dropdown 可能尚未完成卸载，会留下透明遮罩导致白屏卡死。
 */
export function cleanupNaiveUiOverlays() {
  const remove = () => {
    document.querySelectorAll('body > .n-modal-mask').forEach(el => el.remove())
    document.querySelectorAll('body > .v-binder-follower-container').forEach(el => el.remove())
    document.querySelectorAll('body > .n-dropdown-menu').forEach(el => el.remove())
    document.querySelectorAll('body > .n-popover-shared').forEach(el => el.remove())
  }
  remove()
  requestAnimationFrame(remove)
  setTimeout(remove, 200)
}

/**
 * 退出登录或切换账号前重置 UI 层状态。
 * 关闭所有遮罩、设置面板、聊天弹窗，结束 1v1 / 会议 WebRTC，避免残留层挡住登录页。
 */
export function resetSessionUi() {
  useOverlayStore().closeAll()
  useSettingsStore().closeSettings()
  useChatModalsStore().closeAllModals()
  void useCallStore().hangup()
  // 会议流与 peer 连接：登出时必须释放，否则摄像头/麦克风会继续占用
  void import('../stores/conference').then(({ useConferenceStore }) => {
    const conference = useConferenceStore()
    conference.cleanupLocal()
    conference.phase = 'idle'
  })
  cleanupNaiveUiOverlays()
}

/**
 * 登出时重置业务 Store，避免下一账号看到上一账号缓存数据。
 */
export async function resetSessionStores() {
  const [
    { useContactsStore },
    { useNotificationsStore },
    { useMomentsStore },
    { useFavoritesStore },
    { useDriveStore },
    { useFilesStore },
    { useCalendarStore },
    { useNoteStore },
    { useGroupMetaStore },
    { useAppSettingsStore }
  ] = await Promise.all([
    import('../stores/contacts'),
    import('../stores/notifications'),
    import('../stores/moments'),
    import('../stores/favorites'),
    import('../stores/drive'),
    import('../stores/files'),
    import('../stores/calendar'),
    import('../stores/note'),
    import('../stores/groupMeta'),
    import('../stores/appSettings')
  ])

  useContactsStore().reset()
  useNotificationsStore().resetFriends()
  useNotificationsStore().clearMessageNotifs()
  useMomentsStore().$reset()
  useFavoritesStore().$reset()
  useDriveStore().$reset()
  useFilesStore().$reset()
  useCalendarStore().$reset()
  useNoteStore().$reset()
  useGroupMetaStore().$reset()
  useAppSettingsStore().reset()
}
