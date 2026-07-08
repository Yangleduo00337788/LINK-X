<script setup lang="ts">
defineProps<{
  url: string
  title?: string
}>()
</script>

<template>
  <div class="app-webview-wrap">
    <iframe
      :src="url"
      :title="title || '应用'"
      class="app-webview"
      sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
      referrerpolicy="no-referrer"
    />
  </div>
</template>

<style scoped>
.app-webview-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: var(--lx-shadow-card);
}

.app-webview {
  flex: 1;
  width: 100%;
  border: none;
  background: var(--lx-bg-card);
}
</style>
