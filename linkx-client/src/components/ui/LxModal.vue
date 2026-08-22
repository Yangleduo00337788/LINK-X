<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * Naive UI Modal 封装：打开时右上角显示与主界面相同的窗口置顶按钮。
 */
import { NModal } from 'naive-ui'
import ModalOverlayCaption from '../ModalOverlayCaption.vue'

defineOptions({ inheritAttrs: false })

const show = defineModel<boolean>('show', { default: false })
</script>

<template>
  <Teleport to="body">
    <ModalOverlayCaption v-if="show" fixed />
  </Teleport>
  <n-modal v-model:show="show" v-bind="$attrs">
    <template v-for="(_, name) in $slots" #[name]="slotData">
      <slot :name="name" v-bind="slotData || {}" />
    </template>
  </n-modal>
</template>
