<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 应用路由根组件。
 * <p>
 * 作为 Vue Router 的出口，负责渲染当前路由对应的页面组件。
 * </p>
 */
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { useI18n } from './i18n'
import { scheduleNotifyElectronWindowReady } from './utils/bootSplash'

useI18n()

onMounted(() => {
  scheduleNotifyElectronWindowReady()
})
</script>

<template>
  <RouterView v-slot="{ Component }">
    <component :is="Component" class="route-view" />
  </RouterView>
</template>

<style>
.route-view {
  width: 100%;
  height: 100%;
  min-height: 100%;
}
</style>
