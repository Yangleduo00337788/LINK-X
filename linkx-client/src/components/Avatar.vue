<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 通用头像组件。
 * 无自定义头像时统一展示项目 Logo；有 URL 时加载完成后淡入（已缓存则立即展示）。
 */
import { NIcon } from 'naive-ui'
import { computed, ref, watch } from 'vue'
import type { Component } from 'vue'
import { DEFAULT_AVATAR_URL } from '../utils/defaultAvatar'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../utils/mediaUrl'
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

const size = computed(() => props.size ?? 44)
const fontSize = computed(() => `${size.value * 0.38}px`)
const imgFailed = ref(false)
const imgLoaded = ref(false)

const effectiveFallback = computed<'logo' | 'initial'>(() => props.fallback ?? 'logo')

const showInitialFallback = computed(
  () => effectiveFallback.value === 'initial' && !!props.text && !props.icon
)

const customImageUrl = computed(() => {
  const url = normalizeMediaUrl(props.imageUrl)
  if (!url || !isDisplayableMediaUrl(url) || url === DEFAULT_AVATAR_URL) return ''
  return url
})

const hasCustomImage = computed(() => !!customImageUrl.value && !imgFailed.value)
const showLoadedImage = computed(() => hasCustomImage.value && imgLoaded.value)

function syncImageLoadedState() {
  imgFailed.value = false
  const url = customImageUrl.value
  if (!url) {
    imgLoaded.value = false
    return
  }
  imgLoaded.value = isAvatarImageCached(url) || primeAvatarImageCache(url)
}

watch(() => props.imageUrl, syncImageLoadedState, { immediate: true })

function onImgLoad() {
  imgLoaded.value = true
  markAvatarImageCached(customImageUrl.value)
}

function onImgError() {
  imgFailed.value = true
  imgLoaded.value = false
}

const containerBg = computed(() => {
  if (hasCustomImage.value && showLoadedImage.value) return props.color
  if (!hasCustomImage.value || !showLoadedImage.value) return 'var(--lx-bg-card)'
  return props.color
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
    <n-icon v-if="icon && !showLoadedImage" :component="icon" :size="size * 0.45" />
    <template v-else-if="showInitialFallback && !showLoadedImage">{{ text }}</template>

    <img
      v-if="!hasCustomImage || !showLoadedImage"
      :src="DEFAULT_AVATAR_URL"
      alt=""
      class="avatar-img avatar-img--logo"
      decoding="async"
      referrerpolicy="no-referrer"
    />

    <img
      v-if="hasCustomImage"
      :src="customImageUrl"
      alt=""
      class="avatar-img"
      :class="{ 'avatar-img--loaded': showLoadedImage }"
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
  opacity: 0;
  transition: opacity 0.12s ease;
}

.avatar-img--loaded,
.avatar-img--logo {
  opacity: 1;
}

.avatar-img--logo {
  object-fit: cover;
}
</style>
