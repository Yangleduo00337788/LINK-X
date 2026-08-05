/**
 * Generate Windows .ico from PNG sources in build/
 * Run: node scripts/icon-generator/generate-ico.mjs
 */
import pngToIco from 'png-to-ico'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const buildDir = path.join(__dirname, '../../build')

async function main() {
  const sizes = [16, 32, 48, 256]
  const pngPaths = sizes.map((s) => path.join(buildDir, `icon-${s}.png`))

  for (const p of pngPaths) {
    if (!fs.existsSync(p)) {
      console.error(`Missing: ${p} — run generate-icons.mjs first`)
      process.exit(1)
    }
  }

  const icoBuffer = await pngToIco(pngPaths)
  fs.writeFileSync(path.join(buildDir, 'icon.ico'), icoBuffer)
  console.log('build/icon.ico generated (16/32/48/256)')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
