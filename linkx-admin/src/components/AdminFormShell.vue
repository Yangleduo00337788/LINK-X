<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed } from 'vue'
import { NDrawer, NDrawerContent, NModal } from 'naive-ui'
import { usePreferencesStore } from '@/stores/preferences'

const props = withDefaults(
  defineProps<{
    show: boolean
    title?: string
    width?: number
    preset?: 'card' | 'dialog'
    maskClosable?: boolean
  }>(),
  {
    width: 520,
    preset: 'card',
    maskClosable: true,
  }
)

const emit = defineEmits<{ 'update:show': [boolean] }>()

const prefs = usePreferencesStore()

const drawerWidth = computed(() => Math.min(Math.max(props.width, 360), 720))
const modalStyle = computed(() => ({
  width: `${props.width}px`,
  maxWidth: '92vw',
}))

function onShowUpdate(v: boolean) {
  emit('update:show', v)
}
</script>

<template>
  <NDrawer
    v-if="prefs.formStyle === 'drawer'"
    :show="show"
    :width="drawerWidth"
    placement="right"
    :mask-closable="maskClosable"
    @update:show="onShowUpdate"
  >
    <NDrawerContent :title="title" closable :native-scrollbar="false">
      <slot />
      <template v-if="$slots.footer" #footer>
        <slot name="footer" />
      </template>
    </NDrawerContent>
  </NDrawer>
  <NModal
    v-else
    :show="show"
    :preset="preset"
    :title="title"
    :style="modalStyle"
    :mask-closable="maskClosable"
    @update:show="onShowUpdate"
  >
    <slot />
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </NModal>
</template>
