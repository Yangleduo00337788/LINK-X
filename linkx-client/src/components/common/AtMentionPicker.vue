<script setup lang="ts">
/**
 * @ 好友/成员自动补全面板
 * 用法:
 *   - 外层提供候选列表 (props.friends)
 *   - 输入框 input 时检测 caret 前是否有 '@xxx',弹出面板
 *   - 选择后通过 @apply 回调接收 {id, name};父组件负责插入 "@nickname " 并维护 mention 列表
 *
 * 该组件自身只负责面板渲染与选择,父组件负责光标定位/插值/状态。
 */
import { computed, ref, watch } from 'vue'
import type { ContactItem } from '../../types'
import { useI18n } from '../../i18n'

const { t } = useI18n()

interface MentionPick {
  id: string
  name: string
}

const props = withDefaults(
  defineProps<{
    /** 候选列表(已经搜索过滤后的命中项) */
    friends: ContactItem[]
    /** 当前 caret 位置 */
    caretIndex?: number
    /** 整段文本(用来判断是否在 @ 触发中) */
    text?: string
    /** 面板标题 */
    title?: string
    /** 空列表文案 */
    emptyText?: string
    /** 向上弹出（聊天输入框在底部时使用） */
    placement?: 'bottom' | 'top'
  }>(),
  {
    caretIndex: 0,
    text: '',
    placement: 'bottom'
  }
)

const emit = defineEmits<{
  (e: 'apply', friend: MentionPick): void
  (e: 'close'): void
}>()

const activeIndex = ref(0)

const hasFriends = computed(() => props.friends.length > 0)
const headerTitle = computed(() => props.title || t('extra.selectFriend'))
const emptyLabel = computed(() => props.emptyText || t('extra.noFriendsToAt'))

watch(() => props.friends, () => {
  activeIndex.value = 0
})

function resolveFriendId(friend: ContactItem): string {
  return String(friend.userId || friend.id || '').trim()
}

function pick(friend: ContactItem) {
  const id = resolveFriendId(friend)
  const name = (friend.name || '').trim()
  if (!id || !name) return
  emit('apply', { id, name })
}

defineExpose({
  /** 通过键盘事件父组件驱动 activeIndex 变更 */
  move(delta: number) {
    if (!hasFriends.value) return
    const len = props.friends.length
    activeIndex.value = (activeIndex.value + delta + len) % len
  },
  /** 键盘事件确认 */
  confirm(): MentionPick | null {
    const f = props.friends[activeIndex.value]
    if (!f) return null
    const id = resolveFriendId(f)
    const name = (f.name || '').trim()
    if (!id || !name) return null
    return { id, name }
  }
})
</script>

<template>
  <div
    class="at-mention-popover"
    :class="{ 'placement-top': placement === 'top' }"
    @mousedown.prevent
  >
    <div class="at-header">{{ headerTitle }}</div>
    <ul v-if="hasFriends" class="at-list">
      <li
        v-for="(friend, idx) in friends"
        :key="friend.id"
        class="at-item"
        :class="{ active: idx === activeIndex, 'at-all': friend.id === '__all__' }"
        @mouseenter="activeIndex = idx"
        @click="pick(friend)"
      >
        <span class="at-avatar" :style="{ background: friend.avatarColor || 'var(--lx-accent)' }">
          <img v-if="friend.avatarUrl" :src="friend.avatarUrl" alt="" />
          <span v-else>{{ (friend.avatarText || friend.name || '?').charAt(0).toUpperCase() }}</span>
        </span>
        <div class="at-info">
          <div class="at-name">{{ friend.name }}</div>
          <div v-if="friend.group" class="at-meta">{{ friend.group }}</div>
        </div>
      </li>
    </ul>
    <div v-else class="at-empty">{{ emptyLabel }}</div>
  </div>
</template>

<style scoped>
.at-mention-popover {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: 30;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
  width: 240px;
  max-height: 220px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  font-size: 13px;
}

.at-mention-popover.placement-top {
  top: auto;
  bottom: calc(100% + 4px);
}

.at-header {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--lx-text-muted);
  border-bottom: 1px solid var(--lx-border-light);
}

.at-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
  overflow-y: auto;
  flex: 1;
}

.at-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 10px;
  cursor: pointer;
  user-select: none;
}

.at-item.active,
.at-item:hover {
  background: var(--lx-bg-hover);
}

.at-item.at-all .at-name {
  color: var(--lx-accent);
  font-weight: 600;
}

.at-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  flex-shrink: 0;
}

.at-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.at-info {
  min-width: 0;
  flex: 1;
}

.at-name {
  color: var(--lx-text-body);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.at-meta {
  font-size: 11px;
  color: var(--lx-text-muted);
}

.at-empty {
  padding: 16px 12px;
  text-align: center;
  color: var(--lx-text-muted);
  font-size: 12px;
}
</style>
