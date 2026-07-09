/**
 * 将字节数格式化为可读的文件大小字符串。
 *
 * @param bytes 文件字节数
 * @returns 如 "512 B" / "1.2 KB" / "3.5 MB"
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`                           // 小于 1KB 显示 B
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB` // 小于 1MB 显示 KB
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`                 // 否则显示 MB
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

/** 本地图片消息大小上限（2MB），超过则不应写入 localStorage */
export const MAX_IMAGE_BYTES = 2 * 1024 * 1024
