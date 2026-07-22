/**
 * 将字节数格式化为可读的文件大小字符串。
 *
 * @param bytes 文件字节数
 * @returns 如 "512 B" / "1.2 KB" / "3.5 MB" / "1.2 GB"
 */
export function formatFileSize(bytes: number): string {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n < 0) return '0 B'
  if (n < 1024) return `${Math.round(n)} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(1)} MB`
  return `${(n / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

/**
 * 使用 FileReader 将本地 File 对象读取为 Data URL。
 * 用于图片消息本地预览与持久化（base64）。
 *
 * @param file 浏览器 File 对象
 */
export function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string) // 读取成功返回 data:... 字符串
    reader.onerror = () => reject(reader.error)           // 读取失败 reject
    reader.readAsDataURL(file)                             // 以 Data URL 形式读取
  })
}

/**
 * 将 Data URL 转为 File，不经过 fetch（Electron 下 fetch(data:) 常报 Failed to fetch）。
 */
export function dataUrlToFile(dataUrl: string, fileName: string): File {
  const comma = dataUrl.indexOf(',')
  if (comma < 0) {
    throw new Error('无效的图片数据')
  }
  const header = dataUrl.slice(0, comma)
  const base64 = dataUrl.slice(comma + 1)
  const mime = header.match(/data:([^;]+)/)?.[1] || 'image/jpeg'
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new File([bytes], fileName, { type: mime })
}

/** 本地图片消息大小上限（2MB），超过则不应写入 localStorage */
export const MAX_IMAGE_BYTES = 2 * 1024 * 1024

/**
 * 朋友圈/笔记发布上传的图片硬上限：与后端 spring.servlet.multipart.max-file-size 对齐。
 * 超过该值的图片在选择阶段直接拒绝。
 */
export const MAX_PUBLISH_IMAGE_BYTES = 10 * 1024 * 1024

/**
 * 朋友圈/笔记发布上传的视频硬上限。
 */
export const MAX_PUBLISH_VIDEO_BYTES = 50 * 1024 * 1024

/**
 * 压缩目标：客户端压缩后低于该阈值即直接上传，避免无谓的二次压缩。
 */
const COMPRESS_TARGET_BYTES = 1.8 * 1024 * 1024

/**
 * 将图片压缩到指定字节上限以内。算法思路：
 *   1. 先尝试只调低 JPEG/WebP quality（最快、画质最好）
 *   2. 若仍超阈值，则按比例缩小最长边后再次压缩
 *   3. 最长边 ≤ 1080px（朋友圈在移动端常见上限）
 *
 * 不支持的格式（image/heic 等）会原样返回，由上层抛错。
 *
 * @param file 原始图片 File 对象
 * @param maxBytes 压缩目标上限字节
 * @returns 压缩后的 Blob 文件名固定为原名 + .jpg
 */
export async function compressImage(file: File, maxBytes = COMPRESS_TARGET_BYTES): Promise<Blob> {
  // PNG/GIF 等无损格式不参与压缩（避免破坏透明通道），由调用方判断
  if (!/image\/(jpeg|webp)/i.test(file.type) || file.size <= maxBytes) {
    return file
  }

  const dataUrl = await readFileAsDataUrl(file)
  const img = await loadImage(dataUrl)

  let { width, height } = img
  const MAX_DIMENSION = 1080
  let quality = 0.92

  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  if (!ctx) return file

  for (let attempt = 0; attempt < 6; attempt++) {
    let w = width
    let h = height
    if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
      const scale = MAX_DIMENSION / Math.max(w, h)
      w = Math.round(w * scale)
      h = Math.round(h * scale)
    }
    canvas.width = w
    canvas.height = h
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, w, h)
    ctx.drawImage(img, 0, 0, w, h)
    const blob = await canvasToBlob(canvas, 'image/jpeg', quality)
    if (blob && blob.size <= maxBytes) {
      return blob
    }
    // 缩小质量 / 尺寸,继续下一次
    if (quality > 0.5) {
      quality -= 0.12
    } else {
      width = Math.round(width * 0.85)
      height = Math.round(height * 0.85)
    }
  }
  // 6 次仍不达标,返回最后一次结果
  const lastBlob = await canvasToBlob(canvas, 'image/jpeg', 0.5)
  return lastBlob ?? file
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('图片解码失败'))
    img.src = src
  })
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => {
    canvas.toBlob((b) => resolve(b), type, quality)
  })
}
