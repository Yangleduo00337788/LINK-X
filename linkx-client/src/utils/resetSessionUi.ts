// 叠加页栈 Store
import { useOverlayStore } from '../stores/overlay'
// 设置弹窗 Store
import { useSettingsStore } from '../stores/settings'
// 聊天相关模态框 Store
import { useChatModalsStore } from '../stores/chatModals'

/**
 * 退出登录或切换账号前重置 UI 层状态。
 * 关闭所有遮罩、设置面板、聊天弹窗，避免残留层挡住登录页。
 */
export function resetSessionUi() {
  useOverlayStore().closeAll()           // 清空 overlay 页面栈
  useSettingsStore().closeSettings()     // 关闭设置模态框
  useChatModalsStore().closeAllModals()  // 关闭红包/通话等聊天弹窗
}
