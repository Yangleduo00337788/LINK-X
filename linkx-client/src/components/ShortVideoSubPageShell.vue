<!-- 作者：yangleduo -->
<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { ChevronBackOutline } from '@vicons/ionicons5'
import { useI18n } from '../i18n'

const props = withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    navClass?: string
    bodyClass?: string
    /** 叠在其它子页之上（如从「我的」进入的关注列表） */
    elevated?: boolean
  }>(),
  {
    navClass: '',
    bodyClass: '',
    elevated: false
  }
)

const emit = defineEmits<{
  close: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="sv-subpage" :class="{ 'sv-subpage--elevated': props.elevated }">
    <header class="sv-subpage__nav" :class="navClass">
      <slot name="nav">
        <button type="button" class="sv-subpage__back" :aria-label="t('common.back')" @click="emit('close')">
          <NIcon :component="ChevronBackOutline" :size="22" />
        </button>
        <div v-if="props.title" class="sv-subpage__nav-title">
          <h2 class="sv-subpage__title">{{ props.title }}</h2>
          <p v-if="props.subtitle" class="sv-subpage__subtitle">{{ props.subtitle }}</p>
        </div>
        <slot v-else name="nav-title" />
        <slot name="nav-right">
          <span class="sv-subpage__nav-spacer" aria-hidden="true" />
        </slot>
      </slot>
    </header>
    <div class="sv-subpage__body" :class="bodyClass">
      <slot />
    </div>
  </div>
</template>
