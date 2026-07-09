import { useOverlayStore } from '../stores/overlay'
import { useSettingsStore } from '../stores/settings'
import { useChatModalsStore } from '../stores/chatModals'

/** 退出登录或切换账号前，关闭所有遮罩/弹窗，避免残留层挡住登录页 */
export function resetSessionUi() {
  useOverlayStore().closeAll()
  useSettingsStore().closeSettings()
  useChatModalsStore().closeAllModals()
}
