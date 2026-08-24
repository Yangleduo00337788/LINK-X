<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { Play } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'
import { useI18n } from '../../../i18n'
import { buildShortVideoMediaApiUrl } from '../../../utils/shortVideoMediaAccess'

const props = defineProps<{ msg: ChatMessage }>()
const emit = defineEmits<{ (e: 'click', msg: ChatMessage): void }>()
const { t } = useI18n()

const postId = computed(() => props.msg.shortVideoPostId || props.msg.fileUrl || '')
const title = computed(() => props.msg.shortVideoTitle || props.msg.fileName || t('shortVideo.empty'))
const coverSrc = computed(() => {
  const id = postId.value?.trim()
  if (!id || !/^\d+$/.test(id)) return ''
  return buildShortVideoMediaApiUrl(id, 'cover')
})
</script>

<template>
  <button
    type="button"
    class="lx-bubble sv-chat-card"
    :class="{ self: msg.isSelf }"
    @click="emit('click', msg)"
  >
    <div class="sv-chat-card__cover-wrap">
      <img v-if="coverSrc" :src="coverSrc" class="sv-chat-card__cover" alt="" loading="lazy" />
      <div v-else class="sv-chat-card__cover-fallback" />
      <span class="sv-chat-card__play">
        <NIcon :component="Play" :size="14" />
      </span>
    </div>
    <div class="sv-chat-card__body">
      <p class="sv-chat-card__title">{{ title }}</p>
      <p class="sv-chat-card__hint">{{ t('shortVideo.shareCardHint') }}</p>
    </div>
  </button>
</template>

<style scoped>
.sv-chat-card {
  display: block;
  width: min(220px, 72vw);
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  text-align: left;
  font: inherit;
}

.sv-chat-card__cover-wrap {
  position: relative;
  aspect-ratio: 9 / 16;
  background: #1a1a1a;
}

.sv-chat-card__cover,
.sv-chat-card__cover-fallback {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sv-chat-card__cover-fallback {
  background: linear-gradient(160deg, #2a2a2a, #111);
}

.sv-chat-card__play {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
}

.sv-chat-card__body {
  padding: 8px 10px 10px;
}

.sv-chat-card__title {
  margin: 0;
  font-size: 13px;
  line-height: 1.35;
  color: inherit;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sv-chat-card__hint {
  margin: 4px 0 0;
  font-size: 11px;
  opacity: 0.65;
}
</style>
