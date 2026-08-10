/**
 * 作者：yangleduo
 */
import { onMounted, onUnmounted, ref } from 'vue'

type FullscreenDocument = Document & {
  webkitFullscreenElement?: Element | null
  webkitExitFullscreen?: () => Promise<void>
}

type FullscreenElement = HTMLElement & {
  webkitRequestFullscreen?: () => Promise<void>
}

function getFullscreenElement(): Element | null {
  const doc = document as FullscreenDocument
  return doc.fullscreenElement ?? doc.webkitFullscreenElement ?? null
}

export function useFullscreen(target?: HTMLElement | null) {
  const isFullscreen = ref(false)

  function sync() {
    isFullscreen.value = getFullscreenElement() != null
  }

  async function enter() {
    const el = (target ?? document.documentElement) as FullscreenElement
    if (el.requestFullscreen) {
      await el.requestFullscreen()
      return
    }
    if (el.webkitRequestFullscreen) {
      await el.webkitRequestFullscreen()
    }
  }

  async function exit() {
    const doc = document as FullscreenDocument
    if (doc.exitFullscreen) {
      await doc.exitFullscreen()
      return
    }
    if (doc.webkitExitFullscreen) {
      await doc.webkitExitFullscreen()
    }
  }

  async function toggle() {
    if (isFullscreen.value) {
      await exit()
    } else {
      await enter()
    }
  }

  onMounted(() => {
    document.addEventListener('fullscreenchange', sync)
    document.addEventListener('webkitfullscreenchange', sync)
    sync()
  })

  onUnmounted(() => {
    document.removeEventListener('fullscreenchange', sync)
    document.removeEventListener('webkitfullscreenchange', sync)
  })

  return { isFullscreen, enter, exit, toggle }
}
