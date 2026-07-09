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
  <!-- 路由出口：根据当前路由动态渲染对应页面 -->
  <RouterView v-slot="{ Component }">
    <!-- 路由切换时的淡入淡出过渡 -->
    <transition name="fade-app" mode="out-in">
      <component :is="Component" />
    </transition>
  </RouterView>
</template>

<style>
.fade-app-enter-active,
.fade-app-leave-active {
  transition: opacity 0.2s ease;
}

.fade-app-enter-from,
.fade-app-leave-to {
  opacity: 0;
}
</style>
