<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 通用头像组件。
 * 有真实头像 URL 时直接展示；仅无头像时展示项目 Logo 或首字。
 */
import { NIcon } from 'naive-ui'
import { computed, ref, watch } from 'vue'
import type { Component } from 'vue'
import { DEFAULT_AVATAR_URL } from '../utils/defaultAvatar'
import { pickDisplayableImageUrl } from '../utils/displayImage'
import {
  isAvatarImageCached,
  markAvatarImageCached,
  primeAvatarImageCache
} from '../utils/avatarImageCache'
import { useI18n } from '../i18n'

useI18n()

const props = defineProps<{
  text?: string
  color: string
  size?: number
  icon?: Component
  imageUrl?: string
  /** logo：无头像时用项目 Logo（默认）；initial：用首字（仅特殊场景） */
  fallback?: 'logo' | 'initial'
}>()

const emit = defineEmits<{ (e: 'image-error'): void }>()

const size = computed(() => props.size ?? 44)
const fontSize = computed(() => `${size.value * 0.38}px`)
const imgFailed = ref(false)

const effectiveFallback = computed<'logo' | 'initial'>(() => props.fallback ?? 'logo')

const showInitialFallback = computed(
  () => effectiveFallback.value === 'initial' && !!props.text && !props.icon
)

const customImageUrl = computed(() => {
  const url = pickDisplayableImageUrl(props.imageUrl)
  if (!url || url === DEFAULT_AVATAR_URL) return ''
  return url
})

const hasCustomImage = computed(
  () => !!customImageUrl.value && !imgFailed.value
)
const showCustomImageTag = computed(
  () =>
    hasCustomImage.value ||
    (!!customImageUrl.value && isAvatarImageCached(customImageUrl.value) && !imgFailed.value)
)
const showLogoFallback = computed(
  () =>
    effectiveFallback.value === 'logo' &&
    !showCustomImageTag.value &&
    !showInitialFallback.value &&
    !props.icon
)

function primeCustomImageCache() {
  imgFailed.value = false
  const url = customImageUrl.value
  if (!url) return
  if (isAvatarImageCached(url)) return
  primeAvatarImageCache(url)
}

watch(
  customImageUrl,
  (url, prev) => {
    if (url !== prev) imgFailed.value = false
    primeCustomImageCache()
  },
  { immediate: true }
)

function onImgLoad() {
  markAvatarImageCached(customImageUrl.value)
}

function onImgError() {
  imgFailed.value = true
  emit('image-error')
}

const containerBg = computed(() => {
  if (showCustomImageTag.value) return props.color
  if (showInitialFallback.value) return props.color
  return 'var(--lx-bg-card)'
})
</script>

<template>
  <div
    class="avatar"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      backgroundColor: containerBg,
      fontSize: fontSize
    }"
  >
    <n-icon v-if="icon && !showCustomImageTag" :component="icon" :size="size * 0.45" />
    <template v-else-if="showInitialFallback && !showCustomImageTag">{{ text }}</template>

    <img
      v-if="showLogoFallback"
      :src="DEFAULT_AVATAR_URL"
      alt=""
      class="avatar-img avatar-img--logo"
      decoding="async"
      referrerpolicy="no-referrer"
    />

    <img
      v-if="showCustomImageTag"
      :key="customImageUrl"
      :src="customImageUrl"
      alt=""
      class="avatar-img"
      decoding="async"
      referrerpolicy="no-referrer"
      @load="onImgLoad"
      @error="onImgError"
    />
  </div>
</template>

<style scoped>
.avatar {
  border-radius: var(--lx-avatar-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-bg-card);
  font-weight: 500;
  flex-shrink: 0;
  overflow: hidden;
  position: relative;
}

.avatar-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-img--logo {
  object-fit: cover;
}
</style>
