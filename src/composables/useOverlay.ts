import { ref, computed } from 'vue'
import type { OverlayPage, AppItem } from '../types'

const stack = ref<OverlayPage[]>([])
const overlayApp = ref<AppItem | null>(null)
const overlayFileName = ref('')

export function useOverlay() {
  const currentPage = computed(() => stack.value[stack.value.length - 1] ?? null)
  const isOpen = computed(() => stack.value.length > 0)

  function open(page: OverlayPage, payload?: { app?: AppItem; fileName?: string }) {
    if (page === 'app-runner' && payload?.app) {
      overlayApp.value = payload.app
    }
    if (page === 'file-preview' && payload?.fileName) {
      overlayFileName.value = payload.fileName
    }
    if (stack.value[stack.value.length - 1] !== page) {
      stack.value.push(page)
    }
  }

  function close() {
    const top = stack.value.pop()
    if (top === 'app-runner') overlayApp.value = null
    if (top === 'file-preview') overlayFileName.value = ''
  }

  function closeAll() {
    stack.value = []
    overlayApp.value = null
    overlayFileName.value = ''
  }

  return {
    stack,
    overlayApp,
    overlayFileName,
    currentPage,
    isOpen,
    open,
    close,
    closeAll
  }
}