/**
 * LinkX Icon Generator
 *
 * Generates all required icon sizes from the SVG source.
 * Run: node generate-icons.mjs
 *
 * Requires: npm install sharp (dev dependency)
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const svgPath = path.join(__dirname, 'icon.svg')
const outDir = __dirname

// Target sizes for different platforms
const sizes = [
  // Windows ICO (multi-resolution)
  { name: 'icon-16.png',   size: 16 },
  { name: 'icon-24.png',   size: 24 },
  { name: 'icon-32.png',   size: 32 },
  { name: 'icon-48.png',   size: 48 },
  { name: 'icon-64.png',   size: 64 },
  { name: 'icon-128.png',  size: 128 },
  { name: 'icon-256.png',  size: 256 },
  { name: 'icon-512.png',  size: 512 },
  // electron-builder auto-generates .ico from 256 and 512, but we provide 256x256 too
  { name: 'icon.ico',      size: 256 },  // placeholder, real .ico needs a dedicated tool
]

async function generate() {
  // Check if sharp is installed
  let sharp
  try {
    sharp = (await import('sharp')).default
  } catch {
    console.error('Error: sharp is not installed.')
    console.error('Please run: npm install sharp')
    console.error('Then run this script again: node generate-icons.mjs')
    process.exit(1)
  }

  const svgBuffer = fs.readFileSync(svgPath)
  console.log('SVG source loaded:', svgPath)

  for (const { name, size } of sizes) {
    const outPath = path.join(outDir, name)
    await sharp(svgBuffer)
      .resize(size, size)
      .png({ quality: 100 })
      .toFile(outPath)
    console.log(`  Generated: ${name} (${size}x${size})`)
  }

  // Generate macOS ICNS base sizes (electron-builder will handle conversion)
  const macSizes = [16, 32, 64, 128, 256, 512, 1024]
  for (const size of macSizes) {
    const outPath = path.join(outDir, `icon-${size}.png`)
    await sharp(svgBuffer)
      .resize(size, size)
      .png({ quality: 100 })
      .toFile(outPath)
    console.log(`  Generated: icon-${size}.png`)
  }

  console.log('\nAll icons generated successfully!')
  console.log('For electron-builder, place these in linkx-client/build/')
}

generate().catch(e => { console.error(e); process.exit(1) })
