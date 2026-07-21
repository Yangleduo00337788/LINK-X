<script setup lang="ts">
/**
 * 文本消息气泡。
 * <p>
 * 展示纯文本或链接样式，支持回复引用预览；群聊 @ 提及高亮显示。
 * </p>
 */
import { NIcon } from 'naive-ui'
import { LinkOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../../stores/app'
import { splitMentionContent } from '../../../utils/messageNotify'

const props = defineProps<{ msg: ChatMessage }>()

const { userProfile } = storeToRefs(useAppStore())

/** 是否为链接类消息：type=link、含 http(s) URL 或含「抖音」关键字 */
const isLinkMsg = computed(() => {
  const msg = props.msg
  return msg.type === 'link' || /https?:\/\//.test(msg.content) || msg.content.includes('抖音')
})

/** 拆分正文，高亮 @成员 / @全体成员；@到自己时额外强调 */
const contentSegments = computed(() =>
  splitMentionContent(props.msg.content || '', [
    userProfile.value.nickname,
    userProfile.value.username
  ])
)
</script>

<template>
  <!-- 文本/链接气泡：自己侧加 self 样式 -->
  <div class="lx-bubble" :class="{ self: msg.isSelf, link: isLinkMsg }">
    <!-- 回复引用条 -->
    <div v-if="msg.replyTo" class="lx-bubble-reply">
      {{ msg.replyTo.senderName }}: {{ msg.replyTo.content }}
    </div>
    <p class="lx-bubble-text">
      <template v-for="(seg, i) in contentSegments" :key="i">
        <span
          v-if="seg.mention"
          class="lx-mention"
          :class="{ 'lx-mention--me': seg.atMe }"
        >{{ seg.text }}</span>
        <template v-else>{{ seg.text }}</template>
      </template>
    </p>
    <n-icon v-if="isLinkMsg" class="lx-link-ico" :component="LinkOutline" :size="14" />
  </div>
</template>
