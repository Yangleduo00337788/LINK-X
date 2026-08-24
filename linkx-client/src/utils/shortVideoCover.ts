/**
 * 作者：yangleduo
 */
/** 从本地视频文件截取封面帧（JPEG）。 */
export function captureVideoCover(file: File): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const video = document.createElement('video')
    video.preload = 'auto'
    video.muted = true
    video.playsInline = true

    const cleanup = () => {
      try {
        URL.revokeObjectURL(video.src)
      } catch {
        /* ignore */
      }
    }

    video.onloadeddata = () => {
      const seekTo = Number.isFinite(video.duration) && video.duration > 0
        ? Math.min(0.1, video.duration / 2)
        : 0
      video.currentTime = seekTo
    }

    video.onseeked = () => {
      const canvas = document.createElement('canvas')
      const width = video.videoWidth || 720
      const height = video.videoHeight || 1280
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        cleanup()
        reject(new Error('canvas'))
        return
      }
      ctx.drawImage(video, 0, 0, width, height)
      canvas.toBlob(
        blob => {
          cleanup()
          if (blob) resolve(blob)
          else reject(new Error('blob'))
        },
        'image/jpeg',
        0.85
      )
    }

    video.onerror = () => {
      cleanup()
      reject(new Error('video'))
    }

    video.src = URL.createObjectURL(file)
  })
}
