<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '../i18n'
import BrandMarkIcon from './BrandMarkIcon.vue'

const props = withDefaults(
  defineProps<{
    hint?: string
    /** Logo 尺寸（px） */
    size?: number
    /** 铺满父容器居中 */
    fill?: boolean
  }>(),
  {
    hint: undefined,
    size: 128,
    fill: true
  }
)

const { t } = useI18n()
const displayHint = computed(() => props.hint ?? t('chat.selectChatHint'))
</script>

<template>
  <div class="logo-wrap" :class="{ 'logo-wrap--fill': fill }">
    <BrandMarkIcon :size="size" />
    <p v-if="displayHint" class="hint">{{ displayHint }}</p>
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
}

.hint {
  margin: 0;
  padding: 0 16px;
  font-size: 14px;
  font-weight: 400;
  line-height: 1.65;
  letter-spacing: 0.14em;
  text-align: center;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--lx-text-muted);
}
</style>
