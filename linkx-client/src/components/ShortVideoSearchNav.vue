<!-- 作者：yangleduo -->
<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { ChevronBackOutline, CloseOutline, SearchOutline } from '@vicons/ionicons5'
import { useI18n } from '../i18n'

const props = defineProps<{
  modelValue: string
  autofocus?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  back: []
  submit: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="sv-search-nav">
    <button type="button" class="sv-subpage__back" :aria-label="t('common.back')" @click="emit('back')">
      <NIcon :component="ChevronBackOutline" :size="22" />
    </button>
    <div class="sv-search-bar">
      <NIcon class="sv-search-bar__icon" :component="SearchOutline" :size="18" />
      <input
        class="sv-search-bar__input"
        type="text"
        :value="props.modelValue"
        :placeholder="t('shortVideo.searchPh')"
        :autofocus="autofocus"
        @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
        @keydown.enter="emit('submit')"
      />
      <button
        v-if="props.modelValue"
        type="button"
        class="sv-search-bar__clear"
        :aria-label="t('shortVideo.clearSearch')"
        @click="emit('update:modelValue', '')"
      >
        <NIcon :component="CloseOutline" :size="16" />
      </button>
    </div>
    <button type="button" class="sv-search-submit" @click="emit('submit')">
      {{ t('shortVideo.search') }}
    </button>
  </div>
</template>

<style scoped>
.sv-search-nav {
  display: grid;
  grid-template-columns: var(--lx-size-control) 1fr auto;
  align-items: center;
  gap: var(--lx-space);
  width: 100%;
  min-width: 0;
}
</style>
