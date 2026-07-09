<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useOverlayStore } from '../../../stores/overlay'
import AppWebView from '../../AppWebView.vue'

const overlayStore = useOverlayStore()
const { overlayApp } = storeToRefs(overlayStore)
</script>

<template>
  <div class="page-wrap app-runner-page" v-if="overlayApp">
    <div v-if="overlayApp.url" class="app-run-embed">
      <AppWebView :url="overlayApp.url" :title="overlayApp.name" />
    </div>
    <section v-else class="group-card app-run">
      <div class="app-icon-lg" :style="{ background: overlayApp.color }">{{ overlayApp.icon }}</div>
      <h2>{{ overlayApp.name }}</h2>
      <p>{{ overlayApp.desc }}</p>
      <p class="tip">该应用暂未配置内嵌 URL。</p>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
