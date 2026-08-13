/**
 * 作者：yangleduo
 */
/** 将 linkmate-logo 外圈纯黑底替换为白底，供浅色 UI 展示 */
let cachedWhiteLogo: string | null = null

export async function loadLinkMateLogoOnWhite(): Promise<string> {
  if (cachedWhiteLogo) return cachedWhiteLogo

  const img = new Image()
  img.crossOrigin = 'anonymous'
  img.src = '/linkmate-logo.png'
  await img.decode()

  const canvas = document.createElement('canvas')
  canvas.width = img.width
  canvas.height = img.height
  const ctx = canvas.getContext('2d')
  if (!ctx) return '/linkmate-logo.png'

  ctx.drawImage(img, 0, 0)
  const data = ctx.getImageData(0, 0, canvas.width, canvas.height)
  for (let i = 0; i < data.data.length; i += 4) {
    const r = data.data[i]
    const g = data.data[i + 1]
    const b = data.data[i + 2]
    // 仅替换近纯黑背景，保留机器人深色面罩等细节
    if (r < 28 && g < 28 && b < 28) {
      data.data[i] = 255
      data.data[i + 1] = 255
      data.data[i + 2] = 255
      data.data[i + 3] = 255
    }
  }
  ctx.putImageData(data, 0, 0)
  cachedWhiteLogo = canvas.toDataURL('image/png')
  return cachedWhiteLogo
}
