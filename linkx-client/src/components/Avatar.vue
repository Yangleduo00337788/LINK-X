<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 通用头像组件。
 * <p>
 * 支持文字头像、图标头像、图片头像三种展示方式，
 * 可自定义尺寸与背景色。
 * </p>
 */
// Naive UI 图标组件
import { NIcon } from 'naive-ui'
// Vue 计算属性 / 响应式
import { computed, ref, watch } from 'vue'
// Vue 组件类型定义
import type { Component } from 'vue'
import { DEFAULT_AVATAR_URL } from '../utils/defaultAvatar'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../utils/mediaUrl'
import { useI18n } from '../i18n'

useI18n()

// 定义组件属性：文字、背景色、尺寸、图标、图片 URL
const props = defineProps<{
  text?: string // 文字头像显示的字符
  color: string // 背景色（CSS 颜色值）
  size?: number // 头像尺寸（像素），默认 44
  icon?: Component // 可选图标组件
  imageUrl?: string // 可选图片 URL，优先级高于 icon 和 text
  /** logo：无头像时用项目 Logo；initial：用首字/图标 */
  fallback?: 'logo' | 'initial'
}>()

// 计算实际尺寸，未传入时使用默认值 44
const size = computed(() => props.size ?? 44)
// 根据尺寸计算文字字号（约为尺寸的 38%）
const fontSize = computed(() => `${size.value * 0.38}px`)
const imgFailed = ref(false)
const useLogoFallback = computed(() => (props.fallback ?? 'logo') === 'logo' && !props.icon)

watch(
  () => props.imageUrl,
  () => {
    imgFailed.value = false
  }
)

const hasCustomImage = computed(() => {
  const url = normalizeMediaUrl(props.imageUrl)
  return !!url && isDisplayableMediaUrl(url) && !imgFailed.value
})

const displayImageUrl = computed(() => {
  if (hasCustomImage.value) return normalizeMediaUrl(props.imageUrl)
  if (useLogoFallback.value) return DEFAULT_AVATAR_URL
  return ''
})

const showImage = computed(() => !!displayImageUrl.value)
const isLogoDisplay = computed(() => showImage.value && !hasCustomImage.value)

function onImgError() {
  imgFailed.value = true
}
</script>

<template>
  <!-- 头像容器，动态设置宽高、背景色、字号 -->
  <div
    class="avatar"
    :class="{ 'avatar--logo': isLogoDisplay }"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      backgroundColor: isLogoDisplay ? '#f0f4f8' : color,
      fontSize: fontSize
    }"
  >
    <!-- 优先展示图片；无头像或加载失败时回退项目 Logo -->
    <img
      v-if="showImage"
      :src="displayImageUrl"
      alt=""
      class="avatar-img"
      :class="{ 'avatar-img--logo': isLogoDisplay }"
      decoding="async"
      referrerpolicy="no-referrer"
      @error="onImgError"
    />
    <!-- 其次展示图标 -->
    <n-icon v-else-if="icon" :component="icon" :size="size * 0.45" />
    <!-- 最后展示文字 -->
    <template v-else>{{ text }}</template>
  </div>
</template>

<style scoped>
.avatar {
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-bg-card);
  font-weight: 500;
  flex-shrink: 0;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-img--logo {
  object-fit: contain;
  padding: 10%;
  box-sizing: border-box;
}
</style>
