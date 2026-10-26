/**
 * 作者：yangleduo
 */
import { ref } from 'vue'

const isDemo = import.meta.env.VITE_LINKX_DEMO === '1'

/** 开屏说明是否打开；刷新页面会重新为 true。 */
export const demoWelcomeOpen = ref(isDemo)

/** 点过「知道了」后显示管理入口引导。 */
export const demoAdminHintOpen = ref(false)

export function dismissDemoWelcome() {
  demoWelcomeOpen.value = false
  demoAdminHintOpen.value = isDemo
}
