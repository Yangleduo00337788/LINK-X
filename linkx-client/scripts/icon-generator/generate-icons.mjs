/**
 * LinkX Icon Generator
 *
 * Generates tray, window, installer and favicon assets from logo-mark.png
 * (same artwork as the top-left BrandMarkIcon).
 *
 * Run: npm run icons:generate
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.join(__dirname, '../..')
const sourcePath = path.join(rootDir, 'src/assets/logo-mark.png')
const buildDir = path.join(rootDir, 'build')
const publicDir = path.join(rootDir, 'public')

const pngSizes = [16, 24, 32, 48, 64, 128, 256, 512, 1024]

/** 任务栏 / 应用图标圆角（px），不超过画布一半 */
const APP_ICON_CORNER_RADIUS = 36

/** 托盘专用尺寸：圆形裁剪，小尺寸下最圆润 */
const TRAY_ICON_SIZE = 128

function cornerRadiusForSize(size, maxRadius = APP_ICON_CORNER_RADIUS) {
  return Math.min(maxRadius, Math.floor(size / 2))
}

function roundedRectMaskSvg(size, radius) {
  return Buffer.from(
    `<svg width="${size}" height="${size}" xmlns="http://www.w3.org/2000/svg">
      <rect x="0" y="0" width="${size}" height="${size}" rx="${radius}" ry="${radius}" fill="white"/>
    </svg>`
  )
}

function circleMaskSvg(size) {
  const r = size / 2
  return Buffer.from(
    `<svg width="${size}" height="${size}" xmlns="http://www.w3.org/2000/svg">
      <circle cx="${r}" cy="${r}" r="${r}" fill="white"/>
    </svg>`
  )
}

async function renderRoundedIcon(sharp, sourceBuffer, size, maxRadius = APP_ICON_CORNER_RADIUS) {
  const radius = cornerRadiusForSize(size, maxRadius)
  const resized = await sharp(sourceBuffer)
    .resize(size, size, { fit: 'contain', background: { r: 253, g: 253, b: 253, alpha: 1 } })
    .png()
    .toBuffer()

  return sharp(resized)
    .composite([{ input: roundedRectMaskSvg(size, radius), blend: 'dest-in' }])
    .png()
}

/** 托盘：圆形裁剪，缩到任务栏时仍显圆润 */
async function renderTrayIcon(sharp, sourceBuffer) {
  const size = TRAY_ICON_SIZE
  const resized = await sharp(sourceBuffer)
    .resize(size, size, { fit: 'contain', background: { r: 253, g: 253, b: 253, alpha: 1 } })
    .png()
    .toBuffer()

  return sharp(resized)
    .composite([{ input: circleMaskSvg(size), blend: 'dest-in' }])
    .png()
}

async function generate() {
  let sharp
  try {
    sharp = (await import('sharp')).default
  } catch {
    console.error('Error: sharp is not installed. Run: npm install')
    process.exit(1)
  }

  if (!fs.existsSync(sourcePath)) {
    console.error('Missing source logo:', sourcePath)
    process.exit(1)
  }

  fs.mkdirSync(buildDir, { recursive: true })
  fs.mkdirSync(publicDir, { recursive: true })

  const sourceBuffer = fs.readFileSync(sourcePath)
  console.log('Source logo:', sourcePath)

  for (const size of pngSizes) {
    const outPath = path.join(buildDir, `icon-${size}.png`)
    await renderRoundedIcon(sharp, sourceBuffer, size).then(img => img.toFile(outPath))
    console.log(`  build/icon-${size}.png (r=${cornerRadiusForSize(size)})`)
  }

  await renderRoundedIcon(sharp, sourceBuffer, 512).then(img =>
    img.toFile(path.join(buildDir, 'icon.png'))
  )
  console.log(`  build/icon.png (512, r=${cornerRadiusForSize(512)})`)

  await renderTrayIcon(sharp, sourceBuffer).then(img =>
    img.toFile(path.join(buildDir, 'icon-tray.png'))
  )
  console.log(`  build/icon-tray.png (${TRAY_ICON_SIZE}, circle)`)

  for (const size of [32, 192]) {
    const outPath = path.join(publicDir, size === 32 ? 'favicon.png' : 'apple-touch-icon.png')
    await renderRoundedIcon(sharp, sourceBuffer, size).then(img => img.toFile(outPath))
    console.log(`  public/${path.basename(outPath)}`)
  }

  console.log('\nPNG icons generated. Run generate-ico.mjs for Windows .ico')
}

generate().catch((e) => {
  console.error(e)
  process.exit(1)
})
