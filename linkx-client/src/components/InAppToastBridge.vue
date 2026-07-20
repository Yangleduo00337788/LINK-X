<script setup lang="ts">
/**
 * 接收主进程 app:in-app-toast，用 Naive UI 通知兜底展示日程提醒。
 */
import { onMounted, onBeforeUnmount } from 'vue'
import { useNotification } from 'naive-ui'

const notification = useNotification()
let unsubscribe: (() => void) | null = null

onMounted(() => {
  unsubscribe =
    window.electronAPI?.onInAppToast?.(({ title, body }) => {
      notification.create({
        title: title || 'LinkX',
        content: body || '',
        duration: 8000,
        keepAliveOnHover: true
      })
    }) ?? null
})

onBeforeUnmount(() => {
  unsubscribe?.()
  unsubscribe = null
})
</script>

<template>
  <!-- 无 UI，仅桥接主进程 toast -->
</template>
