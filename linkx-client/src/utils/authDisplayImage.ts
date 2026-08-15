/**
 * 鉴权图片异步加载：有 fallback 时先展示真实图，鉴权结果返回后再无缝替换。
 */
import { ref, watch, onBeforeUnmount, type WatchSource } from 'vue'
import { pickDisplayableImageUrl } from './displayImage'

export type AuthDisplayResolveResult = {
  src: string
  blobUrlToRevoke?: string | null
}

export function useAuthDisplayImage(options: {
  watchKeys: WatchSource<readonly unknown[]>
  getFallbackUrl?: () => string | undefined | null
  resolveSrc: () => Promise<AuthDisplayResolveResult>
}) {
  const displaySrc = ref('')
  let authBlobUrl: string | null = null
  let loadSeq = 0

  function revokeAuthBlob() {
    if (authBlobUrl) {
      URL.revokeObjectURL(authBlobUrl)
      authBlobUrl = null
    }
  }

  function syncImmediateFallback() {
    displaySrc.value = pickDisplayableImageUrl(options.getFallbackUrl?.())
  }

  async function loadDisplaySrc() {
    const seq = ++loadSeq
    const immediate = pickDisplayableImageUrl(options.getFallbackUrl?.())
    if (immediate) displaySrc.value = immediate
    revokeAuthBlob()
    const resolved = await options.resolveSrc()
    if (seq !== loadSeq) {
      if (resolved.blobUrlToRevoke) URL.revokeObjectURL(resolved.blobUrlToRevoke)
      return
    }
    if (resolved.blobUrlToRevoke) authBlobUrl = resolved.blobUrlToRevoke
    if (resolved.src) displaySrc.value = resolved.src
  }

  watch(
    options.watchKeys,
    () => {
      syncImmediateFallback()
      void loadDisplaySrc()
    },
    { immediate: true }
  )

  onBeforeUnmount(() => {
    loadSeq += 1
    revokeAuthBlob()
  })

  function onImgErrorApplyFallback(): boolean {
    const fallback = pickDisplayableImageUrl(options.getFallbackUrl?.())
    if (fallback && fallback !== displaySrc.value) {
      revokeAuthBlob()
      displaySrc.value = fallback
      return true
    }
    return false
  }

  return { displaySrc, onImgErrorApplyFallback }
}
