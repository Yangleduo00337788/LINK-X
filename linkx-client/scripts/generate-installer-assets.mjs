/**
 * 作者：yangleduo
 */
/**
 * 从客户端 Logo 生成 NSIS 安装向导资源（侧边栏/头部图 + 许可协议文本）
 *
 * Run: npm run installer:assets
 */
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.join(__dirname, '..')
const buildDir = path.join(rootDir, 'build')
const logoPath = path.join(rootDir, 'src/assets/logo-mark.png')
const servicePath = path.join(rootDir, 'public/legal/data/service.zh-CN.js')

const BRAND_COLOR = '#12b7f5'
const BRAND_COLOR_DARK = '#0d8ec4'
const ICON_CORNER_RADIUS_RATIO = 0.22
const ICON_PADDING_RATIO = 0.12

async function createRoundedAppIcon(sharp, size, options = {}) {
  const {
    background = '#ffffff',
    cornerRadiusRatio = ICON_CORNER_RADIUS_RATIO,
    paddingRatio = ICON_PADDING_RATIO
  } = options

  const radius = Math.max(2, Math.round(size * cornerRadiusRatio))
  const padding = Math.round(size * paddingRatio)
  const inner = Math.max(1, size - padding * 2)

  const logo = await sharp(logoPath)
    .resize(inner, inner, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer()

  const bgSvg = `
    <svg width="${size}" height="${size}" xmlns="http://www.w3.org/2000/svg">
      <rect width="${size}" height="${size}" rx="${radius}" ry="${radius}" fill="${background}" />
    </svg>
  `

  const backgroundLayer = await sharp(Buffer.from(bgSvg)).png().toBuffer()
  return sharp(backgroundLayer)
    .composite([{ input: logo, top: padding, left: padding }])
    .png()
    .toBuffer()
}

async function generateAppIcons(sharp) {
  const sizes = [16, 24, 32, 48, 64, 128, 256, 512, 1024]
  const pngBuffers = []

  for (const size of sizes) {
    const buffer = await createRoundedAppIcon(sharp, size)
    if (size === 1024) {
      fs.writeFileSync(path.join(buildDir, 'icon.png'), buffer)
      fs.writeFileSync(path.join(buildDir, 'icon-1024.png'), buffer)
    } else {
      fs.writeFileSync(path.join(buildDir, `icon-${size}.png`), buffer)
    }
    if ([16, 24, 32, 48, 64, 128, 256].includes(size)) {
      pngBuffers.push(buffer)
    }
  }

  const trayBuffer = await createRoundedAppIcon(sharp, 64)
  fs.writeFileSync(path.join(buildDir, 'icon-tray.png'), trayBuffer)

  const toIco = (await import('to-ico')).default
  const icoBuffer = await toIco(pngBuffers)
  fs.writeFileSync(path.join(buildDir, 'icon.ico'), icoBuffer)
}

function flattenLegalContent(content) {
  const lines = []
  for (const block of content || []) {
    if (block.type === 'p') {
      const text = (block.text || block.html || '').replace(/<[^>]+>/g, '').trim()
      if (text) lines.push(text)
    } else if (block.type === 'ul') {
      for (const item of block.items || []) {
        lines.push(`  • ${item}`)
      }
    }
  }
  return lines
}

function generateLicenseContent() {
  const raw = fs.readFileSync(servicePath, 'utf8')
  const sandbox = { window: { __LEGAL_DOCS__: {} } }
  vm.runInNewContext(raw, sandbox)
  const doc = sandbox.window.__LEGAL_DOCS__['service.zh-CN']
  if (!doc) {
    throw new Error('无法从 service.zh-CN.js 解析许可协议')
  }

  const blocks = [{ kind: 'title', text: doc.title }]
  for (const section of doc.sections || []) {
    blocks.push({ kind: 'section', text: section.title })
    for (const line of flattenLegalContent(section.content)) {
      blocks.push({ kind: 'body', text: line })
    }
    blocks.push({ kind: 'gap' })
  }
  if (doc.footer?.lines?.length) {
    blocks.push({ kind: 'gap' })
    for (const line of doc.footer.lines) {
      blocks.push({ kind: 'footer', text: line })
    }
  }
  return blocks
}

/** NSIS 许可页读取 .txt 时按系统 ANSI 解析，中文 UTF-8 会乱码；改用 Unicode RTF */
function escapeRtfText(text) {
  let out = ''
  for (const ch of text) {
    const code = ch.charCodeAt(0)
    if (ch === '\\') out += '\\\\'
    else if (ch === '{') out += '\\{'
    else if (ch === '}') out += '\\}'
    else if (ch === '\n') out += '\\line '
    else if (code > 127) out += `\\u${code}?`
    else out += ch
  }
  return out
}

function generateLicenseRtf(blocks) {
  const parts = [
    '{\\rtf1\\ansi\\deff0',
    '{\\fonttbl{\\f0\\fnil\\fcharset134 Microsoft YaHei UI;}{\\f1\\fnil\\fcharset0 Segoe UI;}}',
    '{\\colortbl;\\red18\\green183\\blue245;\\red15\\green23\\blue42;\\red100\\green116\\blue139;}',
    '\\viewkind4\\uc1',
    '\\pard\\sa0\\sl276\\slmult1\\f0\\cf2'
  ]

  for (const block of blocks) {
    if (block.kind === 'title') {
      parts.push(`\\pard\\sa200\\b\\fs32\\cf1 ${escapeRtfText(block.text)}\\b0\\cf2\\par`)
      continue
    }
    if (block.kind === 'section') {
      parts.push(`\\pard\\sa160\\b\\fs24\\cf2 ${escapeRtfText(block.text)}\\b0\\par`)
      continue
    }
    if (block.kind === 'body') {
      parts.push(`\\pard\\sa80\\fs22\\cf2 ${escapeRtfText(block.text)}\\par`)
      continue
    }
    if (block.kind === 'footer') {
      parts.push(`\\pard\\sa60\\fs20\\cf3 ${escapeRtfText(block.text)}\\par`)
      continue
    }
    parts.push('\\pard\\sa80\\par')
  }

  parts.push('}')
  return parts.join('\n')
}

async function loadSharp() {
  try {
    const mod = await import('sharp')
    return mod.default
  } catch {
    console.error(
      '[installer:assets] 需要 sharp 依赖，请先执行: npm install --save-dev sharp'
    )
    process.exit(1)
  }
}

/** 将 RGBA 原始像素写入 24-bit BMP（NSIS 安装向导要求） */
function writeBmp(filePath, width, height, rgba) {
  const rowStride = Math.ceil((width * 3) / 4) * 4
  const pixelDataSize = rowStride * height
  const fileSize = 54 + pixelDataSize
  const buffer = Buffer.alloc(fileSize)

  buffer.write('BM', 0)
  buffer.writeUInt32LE(fileSize, 2)
  buffer.writeUInt32LE(54, 10)
  buffer.writeUInt32LE(40, 14)
  buffer.writeInt32LE(width, 18)
  buffer.writeInt32LE(height, 22)
  buffer.writeUInt16LE(1, 26)
  buffer.writeUInt16LE(24, 28)
  buffer.writeUInt32LE(pixelDataSize, 34)

  let offset = 54
  for (let y = height - 1; y >= 0; y--) {
    for (let x = 0; x < width; x++) {
      const src = (y * width + x) * 4
      buffer[offset++] = rgba[src + 2]
      buffer[offset++] = rgba[src + 1]
      buffer[offset++] = rgba[src]
    }
    const padding = rowStride - width * 3
    for (let p = 0; p < padding; p++) {
      buffer[offset++] = 0
    }
  }

  fs.writeFileSync(filePath, buffer)
}

async function renderToBmp(sharpPipeline, filePath, width, height) {
  const { data } = await sharpPipeline
    .resize(width, height, { fit: 'fill' })
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true })
  writeBmp(filePath, width, height, data)
}

async function createSidebar(sharp) {
  const logoSize = 96
  const logo = await createRoundedAppIcon(sharp, logoSize)

  const width = 164
  const height = 314
  const logoTop = Math.round((height - logoSize) / 2) - 24

  const svg = `
    <svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="bg" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" style="stop-color:${BRAND_COLOR};stop-opacity:1" />
          <stop offset="100%" style="stop-color:${BRAND_COLOR_DARK};stop-opacity:1" />
        </linearGradient>
      </defs>
      <rect width="100%" height="100%" fill="url(#bg)" />
      <text x="82" y="${logoTop + logoSize + 36}" text-anchor="middle"
        font-family="Segoe UI, Microsoft YaHei, sans-serif" font-size="20" font-weight="700" fill="#ffffff">
        LinkX
      </text>
      <text x="82" y="${logoTop + logoSize + 58}" text-anchor="middle"
        font-family="Segoe UI, Microsoft YaHei, sans-serif" font-size="11" fill="rgba(255,255,255,0.9)">
        企业级即时通讯
      </text>
    </svg>
  `

  const background = await sharp(Buffer.from(svg)).png().toBuffer()
  await renderToBmp(
    sharp(background).composite([
      { input: logo, top: logoTop, left: Math.round((width - logoSize) / 2) }
    ]),
    path.join(buildDir, 'installer-sidebar.bmp'),
    width,
    height
  )
}

async function createHeader(sharp) {
  const logoSize = 36
  const logo = await createRoundedAppIcon(sharp, logoSize)

  const width = 150
  const height = 57
  const svg = `
    <svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="hdr" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" style="stop-color:${BRAND_COLOR};stop-opacity:1" />
          <stop offset="100%" style="stop-color:${BRAND_COLOR_DARK};stop-opacity:1" />
        </linearGradient>
      </defs>
      <rect width="100%" height="100%" fill="url(#hdr)" />
      <text x="54" y="35" font-family="Segoe UI, Microsoft YaHei, sans-serif"
        font-size="18" font-weight="700" fill="#ffffff">LinkX</text>
    </svg>
  `

  const background = await sharp(Buffer.from(svg)).png().toBuffer()
  await renderToBmp(
    sharp(background).composite([
      { input: logo, top: Math.round((height - logoSize) / 2), left: 12 }
    ]),
    path.join(buildDir, 'installer-header.bmp'),
    width,
    height
  )
}

async function main() {
  if (!fs.existsSync(logoPath)) {
    throw new Error(`Logo 源文件不存在: ${logoPath}`)
  }
  if (!fs.existsSync(buildDir)) {
    fs.mkdirSync(buildDir, { recursive: true })
  }

  const sharp = await loadSharp()
  await generateAppIcons(sharp)
  await Promise.all([createSidebar(sharp), createHeader(sharp)])

  const licenseBlocks = generateLicenseContent()
  fs.writeFileSync(path.join(buildDir, 'license.rtf'), generateLicenseRtf(licenseBlocks), 'utf8')

  const installerSrcDir = path.join(rootDir, 'installer/src')
  if (!fs.existsSync(installerSrcDir)) {
    fs.mkdirSync(installerSrcDir, { recursive: true })
  }
  fs.writeFileSync(
    path.join(installerSrcDir, 'license-data.json'),
    JSON.stringify(licenseBlocks, null, 2),
    'utf8'
  )

  console.log('[installer:assets] 已生成:')
  console.log('  - build/icon.png / icon.ico / icon-*.png / icon-tray.png')
  console.log('  - build/installer-sidebar.bmp')
  console.log('  - build/installer-header.bmp')
  console.log('  - build/license.rtf')
  console.log('  - installer/src/license-data.json')
}

main().catch(err => {
  console.error('[installer:assets] 失败:', err)
  process.exit(1)
})
