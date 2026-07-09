<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <RouterView v-slot="{ Component }">
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
