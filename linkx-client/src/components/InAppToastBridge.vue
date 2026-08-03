<script setup lang="ts">
/**
 * 接收主进程 app:in-app-toast，以及好友上线等应用内通知。
 */
import { onMounted, onBeforeUnmount } from 'vue'
import { useNotification } from 'naive-ui'
import { useI18n } from '../i18n'

useI18n()

const notification = useNotification()
let unsubscribe: (() => void) | null = null

function onFriendOnline(ev: Event) {
  const detail = (ev as CustomEvent<{ title?: string; body?: string }>).detail || {}
  notification.create({
    title: detail.title || 'LinkX',
    content: detail.body || '',
    duration: 4500,
    keepAliveOnHover: true
  })
}

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
  window.addEventListener('linkx:friend-online', onFriendOnline)
})

onBeforeUnmount(() => {
  unsubscribe?.()
  unsubscribe = null
  window.removeEventListener('linkx:friend-online', onFriendOnline)
})
</script>

<template>
  <!-- 无 UI，仅桥接主进程 toast / 好友上线事件 -->
</template>
