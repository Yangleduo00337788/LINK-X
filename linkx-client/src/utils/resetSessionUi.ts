// 叠加页栈 Store
import { useOverlayStore } from '../stores/overlay'
// 设置弹窗 Store
import { useSettingsStore } from '../stores/settings'
// 聊天相关模态框 Store
import { useChatModalsStore } from '../stores/chatModals'

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
 * 关闭所有遮罩、设置面板、聊天弹窗，避免残留层挡住登录页。
 */
export function resetSessionUi() {
  useOverlayStore().closeAll()           // 清空 overlay 页面栈
  useSettingsStore().closeSettings()     // 关闭设置模态框
  useChatModalsStore().closeAllModals()  // 关闭红包/通话等聊天弹窗
  cleanupNaiveUiOverlays()
}
