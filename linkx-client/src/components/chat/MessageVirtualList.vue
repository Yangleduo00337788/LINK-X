<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 消息列表：底部锚定。
 * <p>
 * 内容不足一屏时用 min-height + flex-end 把消息贴在输入框上方；
 * 超出一屏时正常滚动，贴底时 scrollTop 跟到内容底部。
 * 发送只追加，不重排；确认回包不改变位置。
 * </p>
 */
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import type { ChatMessage } from '../../types'
import { useI18n } from '../../i18n'

useI18n()

const props = defineProps<{
  items: ChatMessage[]
  sessionId?: string
  stickToBottom?: boolean
}>()

const emit = defineEmits<{
  (e: 'scroll', payload: { scrollTop: number; scrollHeight: number; clientHeight: number }): void
}>()

function ensureListKey(items: ChatMessage[]) {
  for (const m of items) {
    if (!m.listKey) m.listKey = m.clientMsgId || m.id
  }
}

const scrollerRef = ref<HTMLElement | null>(null)
const listItems = shallowRef<ChatMessage[]>(props.items)
let scrollRaf = 0
let pinTimer = 0
let pinToken = 0

watch(
  () => [props.sessionId, props.items] as const,
  ([sid, items], oldVal) => {
    const prevItems = oldVal?.[1] ?? []
    const prevSid = oldVal?.[0] ?? ''
    const sessionChanged = sid !== prevSid

    listItems.value = items
    ensureListKey(items)

    if (sessionChanged) {
      scrollToBottom(true)
      return
    }
    if (items.length === 0) return

    const prevFirst = prevItems[0]?.id
    const nextFirst = items[0]?.id
    const prevLast = prevItems[prevItems.length - 1]
    const nextLast = items[items.length - 1]
    const isPrepend =
      prevFirst && nextFirst && prevFirst !== nextFirst && items.length >= prevItems.length
    const isTailAppend =
      items.length > prevItems.length && prevFirst === nextFirst && prevLast?.id !== nextLast?.id

    if (isPrepend) return
    if (isTailAppend && props.stickToBottom !== false) {
      scrollToBottom(true)
    }
  }
)

onMounted(() => {
  scrollToBottom(true)
})

function getScrollElement(): HTMLElement | null {
  return scrollerRef.value
}

function stickNow() {
  const el = scrollerRef.value
  if (!el || listItems.value.length === 0) return
  el.scrollTop = el.scrollHeight
}

function scrollToBottom(_force = false) {
  const token = ++pinToken
  if (pinTimer) window.clearTimeout(pinTimer)

  const run = () => {
    if (token !== pinToken) return
    stickNow()
  }

  nextTick(() => {
    run()
    requestAnimationFrame(run)
  })
  pinTimer = window.setTimeout(run, 50)
}

function scrollToKey(key: string | number) {
  pinToken++
  const keyStr = String(key)
  nextTick(() => {
    const root = scrollerRef.value
    if (!root) return
    const node = root.querySelector(`[data-msg-key="${CSS.escape(keyStr)}"]`) as HTMLElement | null
    node?.scrollIntoView({ block: 'center', inline: 'nearest' })
  })
}

function restoreAfterPrepend(prevScrollHeight: number, prevScrollTop: number) {
  const el = scrollerRef.value
  if (!el) return
  nextTick(() => {
    const delta = el.scrollHeight - prevScrollHeight
    if (delta > 0) el.scrollTop = prevScrollTop + delta
  })
}

function setScrollTop(scrollTop: number) {
  pinToken++
  nextTick(() => {
    const el = scrollerRef.value
    if (el) el.scrollTop = Math.max(0, scrollTop)
  })
}

function onScroll() {
  if (scrollRaf) return
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0
    const el = scrollerRef.value
    if (!el) return
    emit('scroll', {
      scrollTop: el.scrollTop,
      scrollHeight: el.scrollHeight,
      clientHeight: el.clientHeight
    })
  })
}

onBeforeUnmount(() => {
  if (scrollRaf) cancelAnimationFrame(scrollRaf)
  if (pinTimer) window.clearTimeout(pinTimer)
})

defineExpose({
  scrollToBottom,
  scrollToKey,
  setScrollTop,
  getScrollElement,
  restoreAfterPrepend
})
</script>

<template>
  <div ref="scrollerRef" class="msg-scroller" @scroll="onScroll">
    <div class="msg-inner">
      <div
        v-for="item in listItems"
        :key="item.listKey || item.id"
        class="msg-item"
        :class="{
          'is-tip': item.type === 'system' || item.type === 'time' || item.type === 'recall'
        }"
        :data-msg-key="item.listKey || item.id"
      >
        <slot :msg="item" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg-scroller {
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.msg-inner {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  box-sizing: border-box;
  padding-bottom: 8px;
}

.msg-item {
  padding: var(--lx-space-sm-plus) 0;
  box-sizing: border-box;
  flex-shrink: 0;
}

.msg-item.is-tip {
  padding: var(--lx-space-xs) 0;
}
</style>
