<!-- 作者：yangleduo -->
﻿<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '../i18n'
import emptyIllustration from '../assets/logo-linkx-empty.png'

const props = withDefaults(
  defineProps<{
    hint?: string
    /** 插画最大宽度（px） */
    size?: number
    /** 铺满父容器居中 */
    fill?: boolean
  }>(),
  {
    hint: undefined,
    size: 380,
    fill: true
  }
)

const { t } = useI18n()
const displayHint = computed(() => props.hint ?? t('chat.selectChatHint'))
</script>

<template>
  <div class="logo-wrap" :class="{ 'logo-wrap--fill': fill }">
    <div v-if="fill" class="empty-atmosphere" aria-hidden="true" />
    <div class="empty-content">
      <div class="illustration-frame" :style="{ maxWidth: `${size}px` }">
        <img
          class="empty-illustration"
          :src="emptyIllustration"
          alt=""
          draggable="false"
        />
      </div>
      <p v-if="displayHint" class="hint">{{ displayHint }}</p>
    </div>
  </div>
</template>

<style scoped>
.logo-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 200px;
  padding: 24px;
  box-sizing: border-box;
}

.logo-wrap--fill {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 0;
  z-index: 0;
  overflow: hidden;
}

/* 全区域氛围渐变：从中心向四周柔和扩散，衔接插画色调 */
.empty-atmosphere {
  position: absolute;
  inset: -8% -12%;
  pointer-events: none;
  z-index: 0;
  background:
    radial-gradient(
      ellipse 90% 70% at 50% 36%,
      rgba(186, 220, 252, 0.52) 0%,
      rgba(228, 242, 255, 0.28) 38%,
      transparent 72%
    ),
    radial-gradient(
      ellipse 55% 48% at 74% 30%,
      rgba(255, 196, 224, 0.2) 0%,
      transparent 68%
    ),
    radial-gradient(
      ellipse 50% 44% at 26% 38%,
      rgba(176, 214, 255, 0.26) 0%,
      transparent 66%
    ),
    radial-gradient(
      ellipse 110% 55% at 50% 88%,
      rgba(198, 228, 255, 0.22) 0%,
      transparent 58%
    );
}

.empty-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  width: 100%;
  height: 100%;
}

.illustration-frame {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.empty-illustration {
  display: block;
  width: 100%;
  height: auto;
  object-fit: contain;
  object-position: center;
  pointer-events: none;
  user-select: none;
  /* 左右羽化，避免山丘/地面边缘生硬截断 */
  -webkit-mask-image: linear-gradient(
    to right,
    transparent 0%,
    #000 9%,
    #000 91%,
    transparent 100%
  );
  mask-image: linear-gradient(
    to right,
    transparent 0%,
    #000 9%,
    #000 91%,
    transparent 100%
  );
}

.hint {
  margin: 0;
  padding: 0 24px;
  max-width: 360px;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.7;
  letter-spacing: normal;
  text-align: center;
  font-family: 'Segoe UI Variable', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: linear-gradient(95deg, #5a9ec8 0%, #6e9fd4 42%, #9a8fd0 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  opacity: 0.88;
}

:global([data-theme='dark']) .hint {
  background: linear-gradient(95deg, #7eb8e0 0%, #9ab5e8 45%, #c4a8e8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  opacity: 0.9;
}

:global([data-theme='dark']) .empty-atmosphere {
  opacity: 0.42;
}

:global([data-theme='dark']) .empty-illustration {
  filter: brightness(1.06) saturate(1.05);
}
</style>
