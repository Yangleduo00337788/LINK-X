<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 图片预览独立窗口：缩放/旋转/系统打开/裁剪/分享/下载/更多。
 * 不含文字识别。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NDropdown, NIcon, useMessage, type DropdownOption } from 'naive-ui'
import {
  AddOutline,
  RemoveOutline,
  RefreshOutline,
  CloudDownloadOutline,
  ChevronBackOutline,
  ChevronForwardOutline,
  OpenOutline,
  CropOutline,
  ArrowRedoOutline,
  EllipsisHorizontalOutline,
  CheckmarkOutline,
  CloseOutline
} from '@vicons/ionicons5'
import WindowCaptionButtons from '../components/WindowCaptionButtons.vue'
import ForwardPickerModal from '../components/chat/ForwardPickerModal.vue'
import { useI18n } from '../i18n'
import { downloadFileWithSettings } from '../utils/downloadFile'
import { copyText } from '../utils/clipboard'
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
import { useAppStore } from '../stores/app'
import { storeToRefs } from 'pinia'
import { formatFileSize } from '../utils/chatTime'
import * as chatApi from '../api/chat'
import { recoverMediaUrlOnError } from '../utils/mediaUrl'
import { imagePreviewPlaceholder } from '../utils/messagePreviewText'

type ViewerItem = {
  url: string
  fileName?: string
  fileSize?: string
  messageId?: string
  conversationId?: string
}

const { t } = useI18n()
const message = useMessage()
const appStore = useAppStore()
const { sessions } = storeToRefs(appStore)

const items = ref<ViewerItem[]>([])
const forwardShow = ref(false)
const forwardSaving = ref(false)
const index = ref(0)
const scale = ref(1)
const rotation = ref(0)
const flipH = ref(false)
const flipV = ref(false)
const fitScale = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0, ox: 0, oy: 0 })
const imgNatural = ref({ w: 0, h: 0 })
const stageRef = ref<HTMLElement | null>(null)
const imgRef = ref<HTMLImageElement | null>(null)
const downloading = ref(false)
const busy = ref(false)
/** 裁剪/本地生成的 blob，下载时直接用，避免 fetch(blob:) 失败 */
const localBlobByUrl = new Map<string, Blob>()

/** 裁剪模式 */
const cropMode = ref(false)
const cropRect = ref({ x: 0.15, y: 0.15, w: 0.7, h: 0.7 }) // 相对 stage 比例
const cropDrag = ref<null | {
  mode: 'move' | 'nw' | 'ne' | 'sw' | 'se'
  startX: number
  startY: number
  origin: { x: number; y: number; w: number; h: number }
}>(null)

const current = computed(() => items.value[index.value] || null)
const imageUrl = computed(() => current.value?.url || '')
const fileName = computed(() => current.value?.fileName || t('chat.imageMessage'))
const fileSize = computed(() => current.value?.fileSize || '')
const hasGallery = computed(() => items.value.length > 1)
const zoomPercent = computed(() => Math.round(scale.value * 100))

/** 相对「适应窗口」的额外缩放；布局用 CSS max 约束，避免自然尺寸被 stage overflow 裁切 */
const userZoom = computed(() => {
  if (!fitScale.value) return scale.value
  return scale.value / fitScale.value
})

const frameStyle = computed(() => ({
  transform: [
    `translate(${offsetX.value}px, ${offsetY.value}px)`,
    `scale(${userZoom.value * (flipH.value ? -1 : 1)}, ${userZoom.value * (flipV.value ? -1 : 1)})`,
    `rotate(${rotation.value}deg)`
  ].join(' '),
  cursor: cropMode.value
    ? 'default'
    : dragging.value
      ? 'grabbing'
      : userZoom.value > 1.02
        ? 'grab'
        : 'default'
}))

const moreOptions = computed<DropdownOption[]>(() => [
  { label: t('viewer.copyImage'), key: 'copyImage' },
  { label: t('viewer.copyLink'), key: 'copyLink' },
  { type: 'divider', key: 'd1' },
  { label: t('viewer.flipH'), key: 'flipH' },
  { label: t('viewer.flipV'), key: 'flipV' },
  { label: t('viewer.resetView'), key: 'reset' },
  { type: 'divider', key: 'd2' },
  { label: t('viewer.openFolder'), key: 'openFolder' }
])

function resetView() {
  scale.value = 1
  rotation.value = 0
  flipH.value = false
  flipV.value = false
  offsetX.value = 0
  offsetY.value = 0
  fitScale.value = 1
  imgNatural.value = { w: 0, h: 0 }
  exitCrop(false)
}

function applyPayload(
  data: {
    url?: string
    fileName?: string
    fileSize?: string
    items?: ViewerItem[]
    index?: number
  } | null
) {
  if (!data?.url && !(data?.items && data.items.length)) return
  if (data.items && data.items.length) {
    items.value = data.items.filter(i => !!i?.url).map(i => ({
      url: i.url,
      fileName: i.fileName,
      fileSize: i.fileSize,
      messageId: i.messageId,
      conversationId: i.conversationId
    }))
    const idx = typeof data.index === 'number' ? data.index : 0
    index.value = Math.max(0, Math.min(idx, items.value.length - 1))
    // 打开时已刷新的当前 URL 写回列表，保证与展示一致
    if (data.url && items.value[index.value]) {
      items.value[index.value] = { ...items.value[index.value], url: data.url }
    }
  } else if (data.url) {
    items.value = [
      {
        url: data.url,
        fileName: data.fileName,
        fileSize: data.fileSize
      }
    ]
    index.value = 0
  }
  resetView()
}

async function ensureItemUrl(i: number) {
  const item = items.value[i]
  if (!item?.url) return
  if (
    /^https?:\/\//i.test(item.url) ||
    item.url.startsWith('blob:') ||
    item.url.startsWith('data:')
  ) {
    return
  }
  const mid = item.messageId
  if (!mid) return
  const next = await recoverMediaUrlOnError(item.url, async () => {
    const res = await chatApi.refreshMessageMediaUrl(mid)
    if (res.code === 200 && res.data?.url) return res.data.url
    return null
  })
  if (next) {
    items.value[i] = { ...item, url: next }
  }
}

async function showAt(i: number) {
  const len = items.value.length
  if (len <= 0) return
  const target = ((i % len) + len) % len
  index.value = target
  resetView()
  await ensureItemUrl(target)
}

async function prev() {
  if (!hasGallery.value) return
  await showAt(index.value - 1)
}

async function next() {
  if (!hasGallery.value) return
  await showAt(index.value + 1)
}

function computeFitScale() {
  const stage = stageRef.value
  const { w, h } = imgNatural.value
  if (!stage || !w || !h) return
  const pad = 48
  const availW = Math.max(80, stage.clientWidth - pad)
  const availH = Math.max(80, stage.clientHeight - pad)
  // CSS 已用 max-width/height 适应窗口，fitScale 表示「适应」相对原图像素的比例
  const nextFit = Math.min(availW / w, availH / h, 1)
  fitScale.value = nextFit || 1
  scale.value = fitScale.value
  offsetX.value = 0
  offsetY.value = 0
}

function onImgLoad(e: Event) {
  const img = e.target as HTMLImageElement
  imgNatural.value = { w: img.naturalWidth, h: img.naturalHeight }
  computeFitScale()
}

function zoomBy(delta: number) {
  if (cropMode.value) return
  const nextUser = Math.min(8, Math.max(0.05, userZoom.value * delta))
  scale.value = nextUser * fitScale.value
}

function zoomToActual() {
  if (cropMode.value) return
  // 1:1 原图像素
  scale.value = 1
  offsetX.value = 0
  offsetY.value = 0
}

function zoomToFit() {
  if (cropMode.value) return
  computeFitScale()
}

function rotateCw() {
  if (cropMode.value) return
  rotation.value = (rotation.value + 90) % 360
}

function onWheel(e: WheelEvent) {
  if (cropMode.value) return
  e.preventDefault()
  zoomBy(e.deltaY > 0 ? 0.9 : 1.1)
}

function onPointerDown(e: PointerEvent) {
  if (cropMode.value || e.button !== 0) return
  const el = e.target as HTMLElement | null
  // 左右切换按钮上的按下不要当成拖拽平移
  if (el?.closest?.('.nav-btn')) return
  dragging.value = true
  dragStart.value = { x: e.clientX, y: e.clientY, ox: offsetX.value, oy: offsetY.value }
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value || cropMode.value) return
  offsetX.value = dragStart.value.ox + (e.clientX - dragStart.value.x)
  offsetY.value = dragStart.value.oy + (e.clientY - dragStart.value.y)
}

function onPointerUp() {
  dragging.value = false
}

/** 加载图片（用于 canvas 处理）；失败时抛错 */
function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('image load failed'))
    img.src = src
  })
}

/** 将当前变换（旋转/翻转）烘焙到 canvas */
async function bakeTransformsToCanvas(): Promise<HTMLCanvasElement> {
  const src = imageUrl.value
  if (!src) throw new Error('no image')
  const img = await loadImage(src)
  const rad = ((rotation.value % 360) + 360) % 360
  const swap = rad === 90 || rad === 270
  const w = img.naturalWidth
  const h = img.naturalHeight
  const canvas = document.createElement('canvas')
  canvas.width = swap ? h : w
  canvas.height = swap ? w : h
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('no ctx')
  ctx.translate(canvas.width / 2, canvas.height / 2)
  ctx.rotate((rad * Math.PI) / 180)
  ctx.scale(flipH.value ? -1 : 1, flipV.value ? -1 : 1)
  ctx.drawImage(img, -w / 2, -h / 2)
  return canvas
}

async function openExternally() {
  if (!imageUrl.value || busy.value) return
  busy.value = true
  try {
    // 优先：下载到本机并用系统默认程序打开
    const result = await downloadFileWithSettings(
      imageUrl.value,
      fileName.value || t('overlay.downloadName'),
      { openAfter: true }
    )
    if (result.canceled) return
    if (result.ok && result.path && window.electronAPI?.openPath) {
      await window.electronAPI.openPath(result.path)
      return
    }
    if (result.ok) {
      message.success(result.path ? t('files.downloadSaved', { path: result.path }) : t('files.downloadOk'))
      return
    }
    // 回退：浏览器打开
    if (/^https?:\/\//i.test(imageUrl.value)) {
      const ok = window.electronAPI?.openExternal
        ? await window.electronAPI.openExternal(imageUrl.value)
        : !!window.open(imageUrl.value, '_blank', 'noopener,noreferrer')
      if (!ok) message.error(t('viewer.openFail'))
      return
    }
    message.error(result.message || t('viewer.openFail'))
  } finally {
    busy.value = false
  }
}

function openForward() {
  const item = current.value
  if (!item?.messageId || !item.conversationId) {
    message.warning(t('viewer.forwardUnavailable'))
    return
  }
  forwardShow.value = true
}

async function confirmForward(payload: { targetIds: string[]; leaveMessage: string }) {
  const item = current.value
  if (!item?.messageId || !item.conversationId || !payload.targetIds.length || forwardSaving.value) {
    return
  }
  forwardSaving.value = true
  const prevSession = appStore.currentSessionId
  try {
    for (const targetId of payload.targetIds) {
      const res = await chatApi.forwardMessage(item.conversationId, item.messageId, targetId)
      if (res.code !== 200) {
        throw new Error(res.message || t('chat.forwardFail'))
      }
      if (payload.leaveMessage) {
        appStore.currentSessionId = targetId
        await appStore.sendMessage(payload.leaveMessage, { type: 'text' })
      }
    }
    message.success(t('chat.forwardOk'))
    forwardShow.value = false
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('chat.forwardFail'))
  } finally {
    if (prevSession) appStore.currentSessionId = prevSession
    forwardSaving.value = false
  }
}

function onForwardCreateGroup() {
  // 独立预览窗未挂载建群流程，提示回主窗口操作
  message.info(t('viewer.forwardCreateGroupHint'))
}

async function copyImage() {
  if (!imageUrl.value) return
  const ok = window.electronAPI?.clipboardWriteImage
    ? await window.electronAPI.clipboardWriteImage({ url: imageUrl.value })
    : false
  if (ok) message.success(t('viewer.copiedImage'))
  else message.error(t('viewer.copyImageFail'))
}

async function copyLink() {
  if (!imageUrl.value) return
  const ok = await copyText(imageUrl.value)
  if (ok) message.success(t('viewer.copiedLink'))
  else message.error(t('viewer.copyLinkFail'))
}

async function onMoreSelect(key: string | number) {
  switch (String(key)) {
    case 'copyImage':
      await copyImage()
      break
    case 'copyLink':
      await copyLink()
      break
    case 'flipH':
      flipH.value = !flipH.value
      break
    case 'flipV':
      flipV.value = !flipV.value
      break
    case 'reset':
      resetView()
      computeFitScale()
      break
    case 'openFolder': {
      const result = await downloadFileWithSettings(
        imageUrl.value,
        fileName.value || t('overlay.downloadName')
      )
      if (result.canceled) return
      if (result.ok && result.path) {
        message.success(t('files.downloadSaved', { path: result.path }))
        // 打开下载目录
        const dir = result.path.replace(/[\\/][^\\/]+$/, '')
        if (dir && window.electronAPI?.openPath) {
          await window.electronAPI.openPath(dir)
        }
      } else if (!result.ok) {
        message.error(result.message || t('files.downloadFail'))
      }
      break
    }
  }
}

function enterCrop() {
  if (!imageUrl.value) return
  cropMode.value = true
  cropRect.value = { x: 0.18, y: 0.18, w: 0.64, h: 0.64 }
  dragging.value = false
}

function exitCrop(apply: boolean) {
  cropMode.value = false
  cropDrag.value = null
  if (!apply) return
}

function onCropPointerDown(e: PointerEvent, mode: 'move' | 'nw' | 'ne' | 'sw' | 'se') {
  e.preventDefault()
  e.stopPropagation()
  cropDrag.value = {
    mode,
    startX: e.clientX,
    startY: e.clientY,
    origin: { ...cropRect.value }
  }
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
}

function onCropPointerMove(e: PointerEvent) {
  if (!cropDrag.value || !stageRef.value) return
  const rect = stageRef.value.getBoundingClientRect()
  const dx = (e.clientX - cropDrag.value.startX) / rect.width
  const dy = (e.clientY - cropDrag.value.startY) / rect.height
  const o = cropDrag.value.origin
  const min = 0.08
  let { x, y, w, h } = o

  switch (cropDrag.value.mode) {
    case 'move':
      x = Math.min(Math.max(0, o.x + dx), 1 - o.w)
      y = Math.min(Math.max(0, o.y + dy), 1 - o.h)
      break
    case 'nw':
      x = Math.min(Math.max(0, o.x + dx), o.x + o.w - min)
      y = Math.min(Math.max(0, o.y + dy), o.y + o.h - min)
      w = o.w + (o.x - x)
      h = o.h + (o.y - y)
      break
    case 'ne':
      y = Math.min(Math.max(0, o.y + dy), o.y + o.h - min)
      w = Math.min(Math.max(min, o.w + dx), 1 - o.x)
      h = o.h + (o.y - y)
      break
    case 'sw':
      x = Math.min(Math.max(0, o.x + dx), o.x + o.w - min)
      w = o.w + (o.x - x)
      h = Math.min(Math.max(min, o.h + dy), 1 - o.y)
      break
    case 'se':
      w = Math.min(Math.max(min, o.w + dx), 1 - o.x)
      h = Math.min(Math.max(min, o.h + dy), 1 - o.y)
      break
  }
  cropRect.value = { x, y, w, h }
}

function onCropPointerUp() {
  cropDrag.value = null
}

async function confirmCrop() {
  if (!imageUrl.value || !stageRef.value || !imgRef.value || busy.value) return
  busy.value = true
  try {
    const baked = await bakeTransformsToCanvas()
    const stage = stageRef.value.getBoundingClientRect()
    const imgEl = imgRef.value.getBoundingClientRect()

    // 裁剪框相对 stage → 映射到图片显示区域
    const cropAbs = {
      left: stage.left + cropRect.value.x * stage.width,
      top: stage.top + cropRect.value.y * stage.height,
      right: stage.left + (cropRect.value.x + cropRect.value.w) * stage.width,
      bottom: stage.top + (cropRect.value.y + cropRect.value.h) * stage.height
    }

    const intersect = {
      left: Math.max(cropAbs.left, imgEl.left),
      top: Math.max(cropAbs.top, imgEl.top),
      right: Math.min(cropAbs.right, imgEl.right),
      bottom: Math.min(cropAbs.bottom, imgEl.bottom)
    }
    if (intersect.right <= intersect.left || intersect.bottom <= intersect.top) {
      message.warning(t('viewer.cropEmpty'))
      return
    }

    const relX = (intersect.left - imgEl.left) / imgEl.width
    const relY = (intersect.top - imgEl.top) / imgEl.height
    const relW = (intersect.right - intersect.left) / imgEl.width
    const relH = (intersect.bottom - intersect.top) / imgEl.height

    const sx = Math.round(relX * baked.width)
    const sy = Math.round(relY * baked.height)
    const sw = Math.max(1, Math.round(relW * baked.width))
    const sh = Math.max(1, Math.round(relH * baked.height))

    const out = document.createElement('canvas')
    out.width = sw
    out.height = sh
    const ctx = out.getContext('2d')
    if (!ctx) throw new Error('no ctx')
    ctx.drawImage(baked, sx, sy, sw, sh, 0, 0, sw, sh)

    const blob = await new Promise<Blob | null>(resolve => out.toBlob(resolve, 'image/png'))
    if (!blob) throw new Error('toBlob failed')
    const objectUrl = URL.createObjectURL(blob)
    const base = (fileName.value || 'image').replace(/\.[^.]+$/, '')
    const nextName = `${base}_crop.png`
    const nextSize = formatFileSize(blob.size)

    const prev = items.value[index.value]
    if (prev?.url.startsWith('blob:')) {
      localBlobByUrl.delete(prev.url)
      URL.revokeObjectURL(prev.url)
    }
    localBlobByUrl.set(objectUrl, blob)
    items.value[index.value] = {
      url: objectUrl,
      fileName: nextName,
      fileSize: nextSize
    }
    rotation.value = 0
    flipH.value = false
    flipV.value = false
    exitCrop(false)
    message.success(t('viewer.cropOk'))
  } catch {
    message.error(t('viewer.cropFail'))
  } finally {
    busy.value = false
  }
}

async function download() {
  if (!imageUrl.value || downloading.value) return
  downloading.value = true
  try {
    let name = fileName.value || t('overlay.downloadName')
    let data: ArrayBuffer | undefined

    // 旋转/翻转：烘焙为 PNG 二进制再存（避开 blob fetch）
    if (rotation.value || flipH.value || flipV.value) {
      try {
        const canvas = await bakeTransformsToCanvas()
        const blob = await new Promise<Blob | null>(r => canvas.toBlob(r, 'image/png'))
        if (blob) {
          data = await blob.arrayBuffer()
          name = name.replace(/\.[^.]+$/, '') + '.png'
        }
      } catch {
        /* 用原图下载 */
      }
    }

    // 裁剪后的本地 blob：优先用缓存，避免 Electron 下 fetch(blob) → Failed to fetch
    if (!data && imageUrl.value.startsWith('blob:')) {
      const cached = localBlobByUrl.get(imageUrl.value)
      if (cached) {
        data = await cached.arrayBuffer()
      } else {
        const el = imgRef.value
        if (el && el.naturalWidth) {
          const canvas = document.createElement('canvas')
          canvas.width = el.naturalWidth
          canvas.height = el.naturalHeight
          const ctx = canvas.getContext('2d')
          ctx?.drawImage(el, 0, 0)
          const out = await new Promise<Blob | null>(r => canvas.toBlob(r, 'image/png'))
          if (out) {
            data = await out.arrayBuffer()
            name = name.replace(/\.[^.]+$/, '') + '.png'
          }
        }
      }
    }

    const result = data
      ? await downloadFileWithSettings(imageUrl.value || 'download.png', name, { data })
      : await downloadFileWithSettings(imageUrl.value, name)

    if (result.canceled) return
    if (result.ok) {
      message.success(
        result.path ? t('files.downloadSaved', { path: result.path }) : t('files.downloadOk')
      )
    } else {
      message.error(result.message || t('files.downloadFail'))
    }
  } finally {
    downloading.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if (cropMode.value) {
    if (e.key === 'Escape') exitCrop(false)
    if (e.key === 'Enter') void confirmCrop()
    return
  }
  if (e.key === 'ArrowLeft') prev()
  if (e.key === 'ArrowRight') next()
  if (e.key === '+' || e.key === '=') zoomBy(1.1)
  if (e.key === '-') zoomBy(0.9)
  if (e.key === '0') zoomToFit()
  if (e.key === '1') zoomToActual()
  if (e.key === 'r' || e.key === 'R') rotateCw()
}

let unsubPayload: (() => void) | null = null
let resizeObs: ResizeObserver | null = null

onMounted(async () => {
  document.documentElement.classList.add('lx-image-viewer')
  // 跟随主应用主题，不再写死暗色
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
  window.addEventListener('keydown', onKeydown)

  // 独立窗口可能无登录态：自动登录并拉会话列表（供转发选目标）
  if (!appStore.isLoggedIn) {
    await appStore.tryAutoLogin().catch(() => undefined)
  }
  if (appStore.isLoggedIn && !(sessions.value || []).length) {
    await appStore.loadSocialData?.().catch(() => undefined)
  }

  const payload = await window.electronAPI?.getImageViewerPayload?.()
  applyPayload(payload || null)
  // 预解析相邻图片 URL，减少切换等待
  if (items.value.length > 1) {
    const cur = index.value
    void ensureItemUrl((cur + 1) % items.value.length)
    void ensureItemUrl((cur - 1 + items.value.length) % items.value.length)
  }

  unsubPayload =
    window.electronAPI?.onImageViewerPayload?.(data => {
      applyPayload(data)
    }) || null

  if (stageRef.value) {
    resizeObs = new ResizeObserver(() => {
      if (!cropMode.value && Math.abs(scale.value - fitScale.value) < 0.02) {
        computeFitScale()
      }
    })
    resizeObs.observe(stageRef.value)
  }
})

onBeforeUnmount(() => {
  document.documentElement.classList.remove('lx-image-viewer')
  window.removeEventListener('keydown', onKeydown)
  unsubPayload?.()
  resizeObs?.disconnect()
  for (const it of items.value) {
    if (it.url.startsWith('blob:')) {
      localBlobByUrl.delete(it.url)
      URL.revokeObjectURL(it.url)
    }
  }
  localBlobByUrl.clear()
  applyDocumentTheme(appStore.theme)
})

watch(imageUrl, () => {
  offsetX.value = 0
  offsetY.value = 0
})

watch(
  () => appStore.theme,
  theme => {
    applyDocumentTheme(theme)
    notifyElectronTheme(theme)
  }
)
</script>

<template>
  <div class="viewer-shell" :data-theme="appStore.theme">
    <header class="viewer-header">
      <div class="header-meta">
        <span class="file-title" :title="fileName">{{ fileName }}</span>
        <span v-if="fileSize" class="file-size">{{ fileSize }}</span>
        <span v-if="items.length > 1" class="file-index">{{ index + 1 }} / {{ items.length }}</span>
      </div>
      <div class="header-right">
        <WindowCaptionButtons />
      </div>
    </header>

    <div
      ref="stageRef"
      class="viewer-stage"
      @wheel="onWheel"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
      @dblclick="zoomToFit"
    >
      <button
        v-show="hasGallery && !cropMode"
        type="button"
        class="nav-btn nav-prev"
        :title="t('viewer.prev')"
        @pointerdown.stop
        @click.stop="prev"
      >
        <n-icon :component="ChevronBackOutline" :size="28" />
      </button>
      <button
        v-show="hasGallery && !cropMode"
        type="button"
        class="nav-btn nav-next"
        :title="t('viewer.next')"
        @pointerdown.stop
        @click.stop="next"
      >
        <n-icon :component="ChevronForwardOutline" :size="28" />
      </button>

      <div
        v-if="imageUrl"
        :key="`${index}-${imageUrl}`"
        class="viewer-img-frame"
        :style="frameStyle"
      >
        <img
          ref="imgRef"
          :src="imageUrl"
          class="viewer-img"
          :alt="fileName"
          draggable="false"
          referrerpolicy="no-referrer"
          @load="onImgLoad"
        />
      </div>
      <div v-else class="viewer-empty">{{ t('overlay.unknownFile') }}</div>

      <!-- 裁剪遮罩 -->
      <div
        v-if="cropMode"
        class="crop-layer"
        @pointermove="onCropPointerMove"
        @pointerup="onCropPointerUp"
        @pointercancel="onCropPointerUp"
      >
        <div
          class="crop-dim crop-dim-t"
          :style="{ height: cropRect.y * 100 + '%' }"
        />
        <div
          class="crop-dim crop-dim-b"
          :style="{ height: (1 - cropRect.y - cropRect.h) * 100 + '%' }"
        />
        <div
          class="crop-dim crop-dim-l"
          :style="{
            top: cropRect.y * 100 + '%',
            height: cropRect.h * 100 + '%',
            width: cropRect.x * 100 + '%'
          }"
        />
        <div
          class="crop-dim crop-dim-r"
          :style="{
            top: cropRect.y * 100 + '%',
            height: cropRect.h * 100 + '%',
            width: (1 - cropRect.x - cropRect.w) * 100 + '%'
          }"
        />
        <div
          class="crop-box"
          :style="{
            left: cropRect.x * 100 + '%',
            top: cropRect.y * 100 + '%',
            width: cropRect.w * 100 + '%',
            height: cropRect.h * 100 + '%'
          }"
          @pointerdown="onCropPointerDown($event, 'move')"
        >
          <span class="crop-handle nw" @pointerdown="onCropPointerDown($event, 'nw')" />
          <span class="crop-handle ne" @pointerdown="onCropPointerDown($event, 'ne')" />
          <span class="crop-handle sw" @pointerdown="onCropPointerDown($event, 'sw')" />
          <span class="crop-handle se" @pointerdown="onCropPointerDown($event, 'se')" />
        </div>
      </div>
    </div>

    <!-- 裁剪确认条 -->
    <div v-if="cropMode" class="crop-actions">
      <button type="button" class="crop-action" @click="exitCrop(false)">
        <n-icon :component="CloseOutline" :size="18" />
        {{ t('common.cancel') }}
      </button>
      <button type="button" class="crop-action primary" :disabled="busy" @click="confirmCrop">
        <n-icon :component="CheckmarkOutline" :size="18" />
        {{ t('viewer.cropConfirm') }}
      </button>
    </div>

    <div v-else class="viewer-toolbar">
      <button type="button" class="tool-btn" :title="t('viewer.zoomOut')" @click="zoomBy(0.9)">
        <n-icon :component="RemoveOutline" :size="18" />
      </button>
      <button type="button" class="tool-pct" :title="t('viewer.fit')" @click="zoomToFit">
        {{ zoomPercent }}%
      </button>
      <button type="button" class="tool-btn" :title="t('viewer.zoomIn')" @click="zoomBy(1.1)">
        <n-icon :component="AddOutline" :size="18" />
      </button>
      <button type="button" class="tool-btn tool-text" :title="t('viewer.actual')" @click="zoomToActual">
        <span>1:1</span>
      </button>
      <span class="tool-sep" />
      <button type="button" class="tool-btn" :title="t('viewer.rotate')" @click="rotateCw">
        <n-icon :component="RefreshOutline" :size="18" />
      </button>
      <button
        type="button"
        class="tool-btn"
        :title="t('viewer.openExternal')"
        :disabled="busy || !imageUrl"
        @click="openExternally"
      >
        <n-icon :component="OpenOutline" :size="18" />
      </button>
      <button type="button" class="tool-btn" :title="t('viewer.crop')" :disabled="!imageUrl" @click="enterCrop">
        <n-icon :component="CropOutline" :size="18" />
      </button>
      <span class="tool-sep" />
      <button
        type="button"
        class="tool-btn"
        :title="t('chat.forward')"
        :disabled="busy || !imageUrl"
        @click="openForward"
      >
        <n-icon :component="ArrowRedoOutline" :size="18" />
      </button>
      <button
        type="button"
        class="tool-btn"
        :title="t('overlay.download')"
        :disabled="downloading || !imageUrl"
        @click="download"
      >
        <n-icon :component="CloudDownloadOutline" :size="18" />
      </button>
      <n-dropdown trigger="click" placement="top" :options="moreOptions" @select="onMoreSelect">
        <button type="button" class="tool-btn" :title="t('viewer.more')">
          <n-icon :component="EllipsisHorizontalOutline" :size="18" />
        </button>
      </n-dropdown>
    </div>

    <ForwardPickerModal
      v-model:show="forwardShow"
      :exclude-session-id="current?.conversationId"
      :loading="forwardSaving"
      :preview-text="imagePreviewPlaceholder()"
      :preview-image-url="imageUrl"
      @confirm="confirmForward"
      @create-group="onForwardCreateGroup"
    />
  </div>
</template>

<style scoped>
.viewer-shell {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-panel, #f5f5f5);
  color: var(--lx-text-body, #1f2329);
  overflow: hidden;
}

.viewer-header {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  flex-shrink: 0;
  min-height: 40px;
  padding: 0 0 0 14px;
  background: var(--lx-bg-card, #fff);
  border-bottom: 1px solid var(--lx-border-light, rgba(0, 0, 0, 0.06));
  -webkit-app-region: drag;
}

.header-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 12px;
}

.file-title {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size,
.file-index {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--lx-text-muted, #8f959e);
}

.header-right {
  display: flex;
  align-items: stretch;
  -webkit-app-region: no-drag;
}

.viewer-stage {
  flex: 1;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  touch-action: none;
  user-select: none;
  background: var(--lx-bg-panel-deep, var(--lx-bg-panel, #eceff3));
}

/*
 * 用 CSS max 约束适应窗口，再用 scale(userZoom) 放大缩小。
 * 禁止把 img 按自然像素撑开再 overflow 裁切——那会造成「自动裁剪」。
 */
.viewer-img-frame {
  line-height: 0;
  transform-origin: center center;
  transition: transform 0.05s linear;
  will-change: transform;
  max-width: calc(100% - 48px);
  max-height: calc(100% - 48px);
  border-radius: 16px;
  box-shadow: 0 0 0 1px var(--lx-border-light, rgba(0, 0, 0, 0.08));
}

.viewer-img {
  display: block;
  width: auto;
  height: auto;
  max-width: calc(100vw - 96px);
  max-height: calc(100vh - 140px);
  object-fit: contain;
  pointer-events: none;
  border: none;
  border-radius: 16px;
}

.viewer-empty {
  color: var(--lx-text-muted, #999);
  font-size: 14px;
}

.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 4;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: color-mix(in srgb, var(--lx-bg-card, #fff) 88%, transparent);
  color: var(--lx-text-body, #1f2329);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  -webkit-app-region: no-drag;
  backdrop-filter: blur(6px);
}

.nav-btn:hover {
  background: var(--lx-bg-card, #fff);
}

.nav-prev {
  left: 20px;
}

.nav-next {
  right: 20px;
}

.crop-layer {
  position: absolute;
  inset: 0;
  z-index: 6;
  -webkit-app-region: no-drag;
}

.crop-dim {
  position: absolute;
  background: rgba(0, 0, 0, 0.55);
  pointer-events: none;
}

.crop-dim-t {
  left: 0;
  right: 0;
  top: 0;
}

.crop-dim-b {
  left: 0;
  right: 0;
  bottom: 0;
}

.crop-dim-l {
  left: 0;
}

.crop-dim-r {
  right: 0;
}

.crop-box {
  position: absolute;
  border: 1.5px solid rgba(18, 183, 245, 0.95);
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.35);
  cursor: move;
  box-sizing: border-box;
}

.crop-handle {
  position: absolute;
  width: 12px;
  height: 12px;
  background: #12b7f5;
  border: 2px solid #fff;
  border-radius: 2px;
  box-sizing: border-box;
}

.crop-handle.nw {
  left: -6px;
  top: -6px;
  cursor: nwse-resize;
}

.crop-handle.ne {
  right: -6px;
  top: -6px;
  cursor: nesw-resize;
}

.crop-handle.sw {
  left: -6px;
  bottom: -6px;
  cursor: nesw-resize;
}

.crop-handle.se {
  right: -6px;
  bottom: -6px;
  cursor: nwse-resize;
}

.crop-actions {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  z-index: 7;
  display: flex;
  gap: 10px;
  -webkit-app-region: no-drag;
}

.crop-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 16px;
  border: none;
  border-radius: 999px;
  background: var(--lx-bg-card, #fff);
  color: var(--lx-text-body, #1f2329);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  font-size: 13px;
  cursor: pointer;
}

.crop-action.primary {
  background: var(--lx-accent, #12b7f5);
  color: #fff;
}

.crop-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.viewer-toolbar {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 8px 14px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--lx-bg-card, #fff) 92%, transparent);
  border: 1px solid var(--lx-border-light, rgba(0, 0, 0, 0.08));
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.12);
  z-index: 5;
  -webkit-app-region: no-drag;
}

.tool-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--lx-text-body, #1f2329);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  gap: 4px;
}

.tool-btn:hover {
  background: var(--lx-bg-hover, rgba(0, 0, 0, 0.06));
}

.tool-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.tool-text {
  width: auto;
  min-width: 40px;
  padding: 0 8px;
  border-radius: 18px;
  font-size: 12px;
  font-weight: 700;
}

.tool-pct {
  min-width: 52px;
  height: 36px;
  border: none;
  border-radius: 18px;
  background: transparent;
  color: var(--lx-text-body, #1f2329);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.tool-pct:hover {
  background: var(--lx-bg-hover, rgba(0, 0, 0, 0.06));
}

.tool-sep {
  width: 1px;
  height: 18px;
  margin: 0 4px;
  background: var(--lx-border-light, rgba(0, 0, 0, 0.12));
}

</style>

<style>
html.lx-image-viewer,
html.lx-image-viewer body,
html.lx-image-viewer #app {
  background: var(--lx-bg-panel, #f5f5f5) !important;
}
</style>
