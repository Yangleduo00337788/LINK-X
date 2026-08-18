import { nextTick, onMounted, watch, type Ref } from 'vue'

/** Naive NVirtualList 滚动条配置（内层仍用 .v-vl 原生滚动条） */
export const virtualListScrollbarProps = {
  trigger: 'none' as const
}

type VirtualListHost = {
  getScrollContainer?: () => HTMLElement | null | undefined
}

/** 让 NVirtualList 内层 .v-vl 显示原生滚动条（vueuc 默认会隐藏） */
export function refreshNaiveVirtualListScrollbar(
  listRef: Ref<VirtualListHost | null>
): void {
  const el = listRef.value?.getScrollContainer?.()
  if (!el) return
  el.classList.add('v-vl--show-scrollbar')
  el.style.overflowY = 'auto'
  el.style.removeProperty('scrollbar-width')
}

/** 挂载后自动为 NVirtualList 打开原生滚动条 */
export function useNaiveVirtualListNativeScrollbar(listRef: Ref<VirtualListHost | null>) {
  const schedule = () => nextTick(() => refreshNaiveVirtualListScrollbar(listRef))
  onMounted(schedule)
  watch(listRef, schedule, { flush: 'post' })
  return { refreshVirtualListScrollbar: schedule }
}
