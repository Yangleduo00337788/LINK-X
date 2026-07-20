<script setup lang="ts">
/**
 * 友链背景图裁剪弹窗
 *
 * 功能：
 * - 预览已选图片
 * - 显示目标裁剪区域（与 banner 比例一致：1000:320）
 * - 支持拖拽移动裁剪框
 * - 点击确认后导出裁剪结果并触发上传
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { CloseOutline, CheckmarkOutline, ImageOutline } from '@vicons/ionicons5'

const props = defineProps<{
  visible: boolean
  /** 原始图片文件 */
  file: File | null
  /** 原始图片对象 URL */
  imageUrl: string
  /** 目标裁剪尺寸（宽:高），默认 1000:320 */
  targetWidth?: number
  targetHeight?: number
}>()

const emit = defineEmits<{
  close: []
  confirm: [blob: Blob, file: File]
  skip: [file: File]
}>()

const message = useMessage()

const targetW = computed(() => props.targetWidth ?? 1000)
const targetH = computed(() => props.targetHeight ?? 320)
const targetRatio = computed(() => targetW.value / targetH.value) // ~3.125

const canvasRef = ref<HTMLCanvasElement | null>(null)
const previewRef = ref<HTMLDivElement | null>(null)

// 裁剪框：相对于 canvas 的百分比位置
const cropX = ref(0)
const cropY = ref(0)
const cropW = ref(80) // 百分比
const cropH = computed(() => cropW.value / targetRatio.value)

// 是否在拖拽
const dragging = ref(false)
const resizeEdge = ref<'tl' | 'tr' | 'bl' | 'br' | null>(null)
const dragStart = ref({ x: 0, y: 0, x0: 0, y0: 0, w0: 80, h0: 0 })

const imageLoaded = ref(false)

function onImageLoad() {
  imageLoaded.value = true
  // 默认居中
  cropW.value = 80
  const h = cropW.value / targetRatio.value
  cropY.value = (100 - h) / 2
  void drawCanvas()
}

function clamp(v: number, min: number, max: number) {
  return Math.max(min, Math.min(max, v))
}

function drawCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const img = new Image()
  img.onload = () => {
    const W = canvas.width
    const H = canvas.height
    ctx.clearRect(0, 0, W, H)

    // 绘制原图（cover 填充）
    const imgRatio = img.width / img.height
    const canvasRatio = W / H
    let sx = 0, sy = 0, sw = img.width, sh = img.height
    if (imgRatio > canvasRatio) {
      sw = img.height * canvasRatio
      sx = (img.width - sw) / 2
    } else {
      sh = img.width / canvasRatio
      sy = (img.height - sh) / 2
    }
    ctx.drawImage(img, sx, sy, sw, sh, 0, 0, W, H)

    // 裁剪区域（百分比 → 像素）
    const cx = cropX.value * W / 100
    const cy = cropY.value * H / 100
    const cw = cropW.value * W / 100
    const ch = cropH.value * H / 100

    // 半透明遮罩
    ctx.fillStyle = 'rgba(0,0,0,0.55)'
    ctx.fillRect(0, 0, W, H)

    // 清除裁剪框
    ctx.clearRect(cx, cy, cw, ch)
    // 重绘裁剪内容（cover 裁剪）
    const coverRatio = cw / ch
    let ix = 0, iy = 0, iw = img.width, ih = img.height
    if (imgRatio > coverRatio) {
      iw = img.height * coverRatio
      ix = (img.width - iw) / 2
    } else {
      ih = img.width / coverRatio
      iy = (img.height - ih) / 2
    }
    ctx.drawImage(img, ix, iy, iw, ih, cx, cy, cw, ch)

    // 边框
    ctx.strokeStyle = '#fff'
    ctx.lineWidth = 2
    ctx.strokeRect(cx, cy, cw, ch)

    // 辅助线（九宫格）
    ctx.strokeStyle = 'rgba(255,255,255,0.3)'
    ctx.lineWidth = 1
    for (let i = 1; i < 3; i++) {
      const px = cx + cw * i / 3
      const py = cy + ch * i / 3
      ctx.beginPath(); ctx.moveTo(px, cy); ctx.lineTo(px, cy + ch); ctx.stroke()
      ctx.beginPath(); ctx.moveTo(cx, py); ctx.lineTo(cx + cw, py); ctx.stroke()
    }

    // 四个角（稍微突出）
    const corner = 6
    ctx.strokeStyle = '#fff'
    ctx.lineWidth = 3
    // 左上
    ctx.beginPath(); ctx.moveTo(cx - corner, cy); ctx.lineTo(cx, cy); ctx.lineTo(cx, cy - corner); ctx.stroke()
    // 右上
    ctx.beginPath(); ctx.moveTo(cx + cw, cy - corner); ctx.lineTo(cx + cw, cy); ctx.lineTo(cx + cw + corner, cy); ctx.stroke()
    // 左下
    ctx.beginPath(); ctx.moveTo(cx - corner, cy + ch); ctx.lineTo(cx, cy + ch); ctx.lineTo(cx, cy + ch + corner); ctx.stroke()
    // 右下
    ctx.beginPath(); ctx.moveTo(cx + cw, cy + ch + corner); ctx.lineTo(cx + cw, cy + ch); ctx.lineTo(cx + cw + corner, cy + ch); ctx.stroke()
  }
  img.src = props.imageUrl
}

function getCanvasPos(e: MouseEvent | TouchEvent): { x: number; y: number } {
  const canvas = canvasRef.value!
  const rect = canvas.getBoundingClientRect()
  const touch = 'touches' in e ? e.touches[0] : (e as MouseEvent)
  return {
    x: (touch.clientX - rect.left) / rect.width * 100,
    y: (touch.clientY - rect.top) / rect.height * 100
  }
}

function onMouseDown(e: MouseEvent) {
  e.preventDefault()
  const pos = getCanvasPos(e)
  const cx = cropX.value
  const cy = cropY.value
  const cw = cropW.value
  const ch = cropH.value
  const edge = 8 // 边距感知（百分比）

  const nearL = Math.abs(pos.x - cx) < edge
  const nearR = Math.abs(pos.x - (cx + cw)) < edge
  const nearT = Math.abs(pos.y - cy) < edge
  const nearB = Math.abs(pos.y - (cy + ch)) < edge

  if (nearT && nearL) resizeEdge.value = 'tl'
  else if (nearT && nearR) resizeEdge.value = 'tr'
  else if (nearB && nearL) resizeEdge.value = 'bl'
  else if (nearB && nearR) resizeEdge.value = 'br'
  else {
    resizeEdge.value = null
    dragging.value = true
  }

  dragStart.value = { x: pos.x, y: pos.y, x0: cropX.value, y0: cropY.value, w0: cropW.value, h0: cropH.value }
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent) {
  const pos = getCanvasPos(e)
  const dx = pos.x - dragStart.value.x
  const dy = pos.y - dragStart.value.y

  if (dragging.value) {
    cropX.value = clamp(dragStart.value.x0 + dx, 0, 100 - cropW.value)
    cropY.value = clamp(dragStart.value.y0 + dy, 0, 100 - cropH.value)
  } else if (resizeEdge.value) {
    const edge = resizeEdge.value
    const newW = clamp(dragStart.value.w0 + dx, 10, 100)
    const newH = newW / targetRatio.value

    if (edge === 'br') {
      cropX.value = clamp(dragStart.value.x0, 0, 100 - newW)
      cropY.value = clamp(dragStart.value.y0, 0, 100 - newH)
      cropW.value = newW
    } else if (edge === 'bl') {
      cropX.value = clamp(dragStart.value.x0 + dx, 0, 100 - newW)
      cropY.value = clamp(dragStart.value.y0, 0, 100 - newH)
      cropW.value = newW
    } else if (edge === 'tr') {
      cropY.value = clamp(dragStart.value.y0 + dy, 0, 100 - newH)
      cropW.value = newW
    } else if (edge === 'tl') {
      cropX.value = clamp(dragStart.value.x0 + dx, 0, 100 - newW)
      cropY.value = clamp(dragStart.value.y0 + dy, 0, 100 - newH)
      cropW.value = newW
    }
  }
  void drawCanvas()
}

function onMouseUp() {
  dragging.value = false
  resizeEdge.value = null
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

async function confirmCrop() {
  const canvas = canvasRef.value
  if (!canvas || !props.file) return

  // 创建目标尺寸 canvas
  const outCanvas = document.createElement('canvas')
  outCanvas.width = targetW.value
  outCanvas.height = targetH.value
  const ctx = outCanvas.getContext('2d')
  if (!ctx) return

  const img = new Image()
  img.crossOrigin = 'anonymous'
  await new Promise<void>((resolve) => {
    img.onload = () => resolve()
    img.onerror = () => resolve()
    img.src = props.imageUrl
  })

  // 根据裁剪百分比计算原图裁剪区域
  const cx = cropX.value / 100
  const cy = cropY.value / 100
  const cw = cropW.value / 100
  const ch = cropH.value / 100

  // 原图 cover 到 canvas 的对应关系
  const imgRatio = img.width / img.height
  const canvasRatio = canvas.width / canvas.height

  let sx = 0, sy = 0, sw = img.width, sh = img.height
  if (imgRatio > canvasRatio) {
    sw = img.height * canvasRatio
    sx = (img.width - sw) / 2
  } else {
    sh = img.width / canvasRatio
    sy = (img.height - sh) / 2
  }

  // 映射到原图
  const srcX = sx + sw * cx
  const srcY = sy + sh * cy
  const srcW = sw * cw
  const srcH = sh * ch

  ctx.drawImage(img, srcX, srcY, srcW, srcH, 0, 0, targetW.value, targetH.value)

  outCanvas.toBlob((blob) => {
    if (!blob) {
      message.error('裁剪失败')
      return
    }
    emit('confirm', blob, props.file!)
    emit('close')
  }, props.file.type, 0.92)
}

function onClose() {
  emit('close')
}

onMounted(async () => {
  await nextTick()
  void drawCanvas()
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})

watch(() => props.imageUrl, async () => {
  imageLoaded.value = false
  await nextTick()
  void drawCanvas()
})
</script>

<template>
  <Transition name="fade">
    <div v-if="visible" class="crop-overlay" @click.self="onClose">
      <div class="crop-modal">
        <!-- 标题栏 -->
        <div class="crop-header">
          <span class="crop-title">裁剪友链背景</span>
          <span class="crop-ratio">比例 {{ targetW }}:{{ targetH }}</span>
          <button class="close-btn" @click="onClose">
            <n-icon :component="CloseOutline" :size="18" />
          </button>
        </div>

        <!-- 裁剪画布 -->
        <div class="crop-body">
          <div class="canvas-wrap" ref="previewRef">
            <img
              v-if="imageUrl"
              :src="imageUrl"
              class="crop-source"
              @load="onImageLoad"
              alt=""
            />
            <canvas
              ref="canvasRef"
              class="crop-canvas"
              width="800"
              height="256"
              :style="{ aspectRatio: `${targetW}/${targetH}` }"
              @mousedown="onMouseDown"
            />
          </div>
          <p class="crop-hint">拖拽移动裁剪区域，拖动边角调整大小</p>
        </div>

        <!-- 底部按钮 -->
        <div class="crop-footer">
          <button class="btn-skip" @click="emit('skip', props.file!)">跳过裁剪直接上传</button>
          <div class="footer-right">
            <button class="btn-cancel" @click="onClose">取消</button>
            <button class="btn-confirm" @click="confirmCrop">
              <n-icon :component="CheckmarkOutline" :size="16" />
              确认上传
            </button>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.crop-overlay {
  position: fixed;
  inset: 0;
  z-index: 500;
  background: rgba(0, 0, 0, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.crop-modal {
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  width: 100%;
  max-width: 860px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
}

.crop-header {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--lx-border-light);
  gap: 10px;
}

.crop-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text);
}

.crop-ratio {
  font-size: 12px;
  color: var(--lx-text-muted);
  background: var(--lx-bg-panel);
  padding: 2px 8px;
  border-radius: 10px;
}

.close-btn {
  margin-left: auto;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--lx-text-muted);
  transition: all 0.2s;
}
.close-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text);
}

.crop-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.canvas-wrap {
  position: relative;
  width: 100%;
  max-width: 800px;
  border-radius: 8px;
  overflow: hidden;
  background: #000;
}

.crop-source {
  position: absolute;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  pointer-events: none;
}

.crop-canvas {
  display: block;
  width: 100%;
  height: auto;
  cursor: crosshair;
}

.crop-hint {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin: 0;
}

.crop-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid var(--lx-border-light);
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-skip {
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  font-size: 13px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: var(--lx-radius);
  transition: all 0.2s;
}
.btn-skip:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text);
}

.btn-cancel {
  border: 1px solid var(--lx-border-light);
  background: transparent;
  color: var(--lx-text-muted);
  padding: 8px 20px;
  border-radius: var(--lx-radius);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-cancel:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text);
}

.btn-confirm {
  border: none;
  background: var(--lx-accent);
  color: #fff;
  padding: 8px 20px;
  border-radius: var(--lx-radius);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
}
.btn-confirm:hover {
  opacity: 0.88;
  transform: translateY(-1px);
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
