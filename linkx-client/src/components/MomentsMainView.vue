<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * Web 主壳内嵌友链主视图（Electron 仍走独立窗）。
 */
import MomentsModal from './MomentsModal.vue'
import { useI18n } from '../i18n'

useI18n()
</script>

<template>
  <div class="moments-main">
    <MomentsModal embedded />
  </div>
</template>

<style scoped>
.moments-main {
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--lx-bg-card);
}
</style>
