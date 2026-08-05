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
    await sharp(sourceBuffer)
      .resize(size, size, { fit: 'contain', background: { r: 253, g: 253, b: 253, alpha: 1 } })
      .png()
      .toFile(outPath)
    console.log(`  build/icon-${size}.png`)
  }

  const icon512 = path.join(buildDir, 'icon-512.png')
  await sharp(sourceBuffer)
    .resize(512, 512, { fit: 'contain', background: { r: 253, g: 253, b: 253, alpha: 1 } })
    .png()
    .toFile(path.join(buildDir, 'icon.png'))
  console.log('  build/icon.png (512)')

  for (const size of [32, 192]) {
    const outPath = path.join(publicDir, size === 32 ? 'favicon.png' : 'apple-touch-icon.png')
    await sharp(sourceBuffer)
      .resize(size, size, { fit: 'contain', background: { r: 253, g: 253, b: 253, alpha: 1 } })
      .png()
      .toFile(outPath)
    console.log(`  public/${path.basename(outPath)}`)
  }

  console.log('\nPNG icons generated. Run generate-ico.mjs for Windows .ico')
}

generate().catch((e) => {
  console.error(e)
  process.exit(1)
})
