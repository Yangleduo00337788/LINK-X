<!-- 作者：yangleduo -->
<template>
  <div class="showcase">
    <iframe
      class="client-frame"
      title="LinkX"
      :src="clientSrc"
      allow="clipboard-read; clipboard-write"
    />
  </div>
</template>

<script setup lang="ts">
const clientSrc = '/client/#/'
</script>

<style scoped>
.showcase {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 100vh;
  overflow: hidden;
  background: #e8f3fc;
}

.client-frame {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 100vh;
  border: 0;
  background: #e8f3fc;
}
</style>
