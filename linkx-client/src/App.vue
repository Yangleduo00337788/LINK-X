<script setup lang="ts">
/**
 * 应用路由根组件。
 * <p>
 * 作为 Vue Router 的出口，负责渲染当前路由对应的页面组件，
 * 并在路由切换时应用淡入淡出过渡动画。
 * </p>
 */
// Vue Router 路由视图组件，用于渲染匹配的路由页面
import { RouterView } from 'vue-router'
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
