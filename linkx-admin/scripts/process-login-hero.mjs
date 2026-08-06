/**
 * 去除 Gemini 导出图自带的棋盘格假透明底，替换为登录页背景色。
 * 用法：node scripts/process-login-hero.mjs [input.png]
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const require = createRequire(import.meta.url)
const sharp = require('../../linkx-client/node_modules/sharp')

const LOGIN_BG = { r: 232, g: 240, b: 250 } // #e8f0fa
const defaultInput = path.join(__dirname, '../src/assets/login-hero-3d-lx.png')
const input = process.argv[2] ? path.resolve(process.argv[2]) : defaultInput
const output = defaultInput

function isCheckerBg(r, g, b) {
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  if (max - min > 10) return false
  if (max >= 245) return true
  if (max >= 186 && max <= 212) return true
  return false
}

async function main() {
  if (!fs.existsSync(input)) {
    console.error('File not found:', input)
    process.exit(1)
  }
  const { data, info } = await sharp(input).raw().toBuffer({ resolveWithObject: true })
  const { width: w, height: h, channels: ch } = info
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const o = (y * w + x) * ch
      const r = data[o]
      const g = data[o + 1]
      const b = data[o + 2]
      if (isCheckerBg(r, g, b)) {
        data[o] = LOGIN_BG.r
        data[o + 1] = LOGIN_BG.g
        data[o + 2] = LOGIN_BG.b
      }
    }
  }
  await sharp(data, { raw: { width: w, height: h, channels: ch } }).png().toFile(output)
  console.log('Processed:', output)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
