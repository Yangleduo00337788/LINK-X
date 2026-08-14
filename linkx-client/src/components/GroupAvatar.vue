<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群聊头像：有自定义群头像则展示；否则展示群主头像；均无则项目 Logo。
 */
import { computed } from 'vue'
import Avatar from './Avatar.vue'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../utils/mediaUrl'

const props = defineProps<{
  size?: number
  /** 自定义群头像（上传后才有） */
  imageUrl?: string
  /** 无自定义群头像时的默认图（通常为群主头像） */
  defaultImageUrl?: string
}>()

const size = computed(() => props.size ?? 44)

const displayImageUrl = computed(() => {
  const custom = normalizeMediaUrl(props.imageUrl)
  if (custom && isDisplayableMediaUrl(custom)) return custom
  const fallback = normalizeMediaUrl(props.defaultImageUrl)
  if (fallback && isDisplayableMediaUrl(fallback)) return fallback
  return ''
})
</script>

<template>
  <Avatar
    :size="size"
    color="var(--lx-bg-card)"
    :image-url="displayImageUrl || undefined"
    fallback="logo"
  />
</template>
