<script setup lang="ts">
/**
 * 消息虚拟列表：直接用 vueuc VirtualList + 原生 overflow 滚动
 *（比 NVirtualList 外包的 NxScrollbar 更跟手）。
 * <p>
 * 内容高度不足一屏时，通过动态 paddingTop 将消息贴底，避免下方大块空白。
 * </p>
 */
import { nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { VirtualList } from 'vueuc'
import type { ChatMessage } from '../../types'
import type { VirtualListInst } from 'vueuc'

const props = defineProps<{
  items: ChatMessage[]
}>()

const emit = defineEmits<{
  (e: 'scroll', payload: { scrollTop: number; scrollHeight: number; clientHeight: number }): void
}>()

/** 默认行高：按系统提示/时间分割线偏小估计，气泡由 itemResizable 撑开 */
const ITEM_SIZE = 40
const PAD_BOTTOM = 8
const PAD_TOP_MIN = 4

const listRef = ref<VirtualListInst | null>(null)
/** shallow：避免滚动时深度追踪大数组 */
const listItems = shallowRef<ChatMessage[]>(props.items)
const padTop = ref(PAD_TOP_MIN)
let scrollBottomToken = 0
let scrollRaf = 0
let alignRaf = 0

watch(
  () => props.items,
  items => {
    listItems.value = items
    // 列表变化后先清贴底 padding，待布局完成再重算，避免沿用旧空白
    padTop.value = PAD_TOP_MIN
    scheduleAlignBottom()
  }
)

function getScrollElement(): HTMLElement | null {
  return listRef.value?.listElRef ?? null
}

function getItemsElement(): HTMLElement | null {
  return listRef.value?.itemsElRef ?? null
}

/**
 * 内容不足一屏时增大 paddingTop，把消息推到可视区域底部。
 */
function alignBottomIfNeeded() {
  const listEl = getScrollElement()
  const itemsEl = getItemsElement()
  if (!listEl || !itemsEl || listItems.value.length === 0) {
    padTop.value = PAD_TOP_MIN
    return
  }
  const viewport = listEl.clientHeight
  if (viewport <= 0) return

  // content-box：offsetHeight = minHeight(内容) + paddingTop + paddingBottom
  const contentH = Math.max(0, itemsEl.offsetHeight - padTop.value - PAD_BOTTOM)
  const nextPad = contentH + PAD_BOTTOM < viewport
    ? Math.max(PAD_TOP_MIN, viewport - contentH - PAD_BOTTOM)
    : PAD_TOP_MIN

  if (Math.abs(nextPad - padTop.value) > 1) {
    padTop.value = nextPad
  }
}

function scheduleAlignBottom() {
  if (alignRaf) cancelAnimationFrame(alignRaf)
  alignRaf = requestAnimationFrame(() => {
    alignRaf = 0
    nextTick(() => {
      alignBottomIfNeeded()
      // 二次校正：itemResizable 测量后再贴一次
      requestAnimationFrame(() => alignBottomIfNeeded())
    })
  })
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

function onListResize() {
  scheduleAlignBottom()
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
      scheduleAlignBottom()
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

onBeforeUnmount(() => {
  if (scrollRaf) cancelAnimationFrame(scrollRaf)
  if (alignRaf) cancelAnimationFrame(alignRaf)
})

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
    :item-size="ITEM_SIZE"
    :item-resizable="true"
    key-field="id"
    :padding-top="padTop"
    :padding-bottom="PAD_BOTTOM"
    :show-scrollbar="true"
    @scroll="onScroll"
    @resize="onListResize"
  >
    <template #default="{ item }">
      <div
        class="msg-vl-item"
        :class="{
          'is-tip':
            (item as ChatMessage).type === 'system' ||
            (item as ChatMessage).type === 'time' ||
            (item as ChatMessage).type === 'recall'
        }"
      >
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

.msg-vl-item.is-tip {
  padding: 4px 0;
}
</style>
