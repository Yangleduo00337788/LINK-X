<script setup lang="ts">
/**
 * 消息虚拟列表：直接用 vueuc VirtualList + 原生 overflow 滚动
 *（比 NVirtualList 外包的 NxScrollbar 更跟手）。
 */
import { nextTick, ref, shallowRef, watch } from 'vue'
import { VirtualList } from 'vueuc'
import type { ChatMessage } from '../../types'
import type { VirtualListInst } from 'vueuc'

const props = defineProps<{
  items: ChatMessage[]
}>()

const emit = defineEmits<{
  (e: 'scroll', payload: { scrollTop: number; scrollHeight: number; clientHeight: number }): void
}>()

const listRef = ref<VirtualListInst | null>(null)
/** shallow：避免滚动时深度追踪大数组 */
const listItems = shallowRef<ChatMessage[]>(props.items)
let scrollBottomToken = 0
let scrollRaf = 0

watch(
  () => props.items,
  items => {
    listItems.value = items
  }
)

function getScrollElement(): HTMLElement | null {
  return listRef.value?.listElRef ?? null
}

function onScroll(e: Event) {
  if (scrollRaf) return
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0
    const el = (e.target as HTMLElement) || getScrollElement()
    if (!el) return
    const dist = el.scrollHeight - el.scrollTop - el.clientHeight
    if (dist > 24) scrollBottomToken++
    emit('scroll', {
      scrollTop: el.scrollTop,
      scrollHeight: el.scrollHeight,
      clientHeight: el.clientHeight
    })
  })
}

function scrollToBottom() {
  const token = ++scrollBottomToken
  nextTick(() => {
    if (token !== scrollBottomToken) return
    if (listItems.value.length === 0) return
    try {
      listRef.value?.scrollTo({ position: 'bottom', debounce: false })
    } catch {
      const box = getScrollElement()
      if (box) box.scrollTop = box.scrollHeight
    }
    requestAnimationFrame(() => {
      if (token !== scrollBottomToken) return
      const box = getScrollElement()
      if (!box) return
      if (box.scrollHeight - box.scrollTop - box.clientHeight > 80) return
      box.scrollTop = box.scrollHeight
    })
  })
}

function restoreAfterPrepend(prevScrollHeight: number, prevScrollTop: number) {
  const el = getScrollElement()
  if (!el) return
  nextTick(() => {
    const delta = el.scrollHeight - prevScrollHeight
    if (delta > 0) el.scrollTop = prevScrollTop + delta
  })
}

defineExpose({
  scrollToBottom,
  getScrollElement,
  restoreAfterPrepend
})
</script>

<template>
  <VirtualList
    ref="listRef"
    class="msg-vl"
    :items="listItems"
    :item-size="56"
    :item-resizable="true"
    key-field="id"
    :padding-top="4"
    :padding-bottom="8"
    :show-scrollbar="true"
    @scroll="onScroll"
  >
    <template #default="{ item }">
      <div class="msg-vl-item">
        <slot :msg="(item as ChatMessage)" />
      </div>
    </template>
  </VirtualList>
</template>

<style scoped>
.msg-vl {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: auto !important;
  overscroll-behavior: contain;
}

.msg-vl-item {
  padding: 7px 0;
  box-sizing: border-box;
}
</style>
