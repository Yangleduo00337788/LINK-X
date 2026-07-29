/**
 * Generate proper Windows .ico from PNG sources
 * Run: node generate-ico.mjs
 */
import pngToIco from 'png-to-ico'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const buildDir = __dirname

async function main() {
  // ICO requires 16x16, 32x32, 48x48, and optionally 256x256
  const sizes = [16, 32, 48, 256]
  const pngPaths = sizes.map(s => path.join(buildDir, `icon-${s}.png`))

  // Verify all files exist
  for (const p of pngPaths) {
    if (!fs.existsSync(p)) {
      console.error(`Missing: ${p}`)
      process.exit(1)
    }
  }

  const icoBuffer = await pngToIco(pngPaths)
  fs.writeFileSync(path.join(buildDir, 'icon.ico'), icoBuffer)
  console.log('icon.ico generated successfully (multi-resolution: 16/32/48/256)')
}

main().catch(e => { console.error(e); process.exit(1) })
