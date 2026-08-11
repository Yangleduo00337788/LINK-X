/**
 * P8 精细打磨：残留间距 / 行高 / z-index / transition 时长。
 */
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve('src')
const skipFiles = new Set([
  path.join(root, 'assets', 'styles.css'),
  path.join(root, 'theme', 'vars.ts')
])

const spaceMap = {
  '0px': '0',
  '1px': 'var(--lx-space-hair)',
  '2px': 'var(--lx-space-2xs)',
  '3px': 'var(--lx-space-2xs)',
  '4px': 'var(--lx-space-xs)',
  '5px': 'var(--lx-space-xs)',
  '6px': 'var(--lx-space-sm)',
  '7px': 'var(--lx-space-sm-plus)',
  '8px': 'var(--lx-space)',
  '9px': 'var(--lx-space)',
  '10px': 'var(--lx-space-md)',
  '11px': 'var(--lx-space-md-plus)',
  '12px': 'var(--lx-space-lg)',
  '14px': 'var(--lx-space-xl)',
  '16px': 'var(--lx-space-2xl)',
  '18px': 'var(--lx-space-2xl)',
  '20px': 'var(--lx-space-3xl)',
  '22px': 'var(--lx-space-3xl-plus)',
  '24px': 'var(--lx-space-4xl)',
  '26px': 'var(--lx-space-4xl-plus)',
  '28px': 'var(--lx-space-5xl-minus)',
  '30px': 'var(--lx-space-5xl-tight)',
  '32px': 'var(--lx-space-5xl)',
  '33px': 'var(--lx-space-5xl)',
  '36px': 'var(--lx-space-6xl-minus)',
  '40px': 'var(--lx-space-section)',
  '44px': 'var(--lx-space-section-plus)',
  '48px': 'var(--lx-space-6xl)',
  '54px': 'var(--lx-space-block)',
  '60px': 'var(--lx-space-block-lg)',
  '64px': 'var(--lx-space-block-xl)'
}

const leadingMap = {
  '0': '0',
  '1': 'var(--lx-leading-none)',
  '1.2': 'var(--lx-leading-tight)',
  '1.25': 'var(--lx-leading-tight)',
  '1.3': 'var(--lx-leading-snug)',
  '1.35': 'var(--lx-leading-snug)',
  '1.4': 'var(--lx-leading)',
  '1.45': 'var(--lx-leading)',
  '1.5': 'var(--lx-leading-normal)',
  '1.55': 'var(--lx-leading-normal)',
  '1.6': 'var(--lx-leading-relaxed)',
  '1.65': 'var(--lx-leading-relaxed)',
  '1.7': 'var(--lx-leading-loose)',
  '14px': 'var(--lx-font)',
  '16px': 'var(--lx-font-xl)',
  '18px': 'var(--lx-font-3xl)',
  '34px': 'var(--lx-font-8xl)'
}

const zMap = {
  '0': 'var(--lx-z-base)',
  '1': 'var(--lx-z-raised)',
  '2': 'var(--lx-z-raised-2)',
  '3': 'var(--lx-z-raised-3)',
  '4': 'var(--lx-z-raised-4)',
  '5': 'var(--lx-z-raised-5)',
  '10': 'var(--lx-z-dropdown)',
  '20': 'var(--lx-z-sticky)',
  '30': 'var(--lx-z-dock)',
  '50': 'var(--lx-z-fab)',
  '100': 'var(--lx-z-header)',
  '400': 'var(--lx-z-overlay)',
  '500': 'var(--lx-z-modal)',
  '999': 'var(--lx-z-popover)',
  '1000': 'var(--lx-z-toast)',
  '2200': 'var(--lx-z-dialog)',
  '2250': 'var(--lx-z-dialog-top)',
  '10050': 'var(--lx-z-critical)',
  '11900': 'var(--lx-z-call-backdrop)',
  '12000': 'var(--lx-z-call)'
}

const durationMap = {
  '0.05s': 'var(--lx-duration-instant)',
  '0.1s': 'var(--lx-duration-faster)',
  '0.12s': 'var(--lx-duration-fast)',
  '0.14s': 'var(--lx-duration-fast)',
  '0.15s': 'var(--lx-duration)',
  '0.16s': 'var(--lx-duration)',
  '0.18s': 'var(--lx-duration-md)',
  '0.2s': 'var(--lx-duration-md)',
  '0.25s': 'var(--lx-duration-md)',
  '0.28s': 'var(--lx-duration-slow)',
  '0.3s': 'var(--lx-duration-slow)',
  '0.4s': 'var(--lx-duration-slower)',
  '0.45s': 'var(--lx-duration-slower)',
  '0.5s': 'var(--lx-duration-slowest)',
  '0.55s': 'var(--lx-duration-slowest)',
  '0.6s': 'var(--lx-duration-slowest)',
  '0.7s': 'var(--lx-duration-emphasis)'
}

function replacePxInValue(val, map) {
  return val.replace(/\b(\d+(?:\.\d+)?)px\b/g, (m) => map[m] || m)
}

function replaceDurationsInValue(val) {
  // 只替换短动效，跳过 4s/8s/14s 等长动画
  return val.replace(/\b(\d*\.?\d+)s\b/g, (m) => durationMap[m] || m)
}

function transformLine(line) {
  let next = line

  next = next.replace(
    /\b((?:padding|margin)(?:-(?:top|right|bottom|left|inline|block|inline-start|inline-end|block-start|block-end))?):\s*([^;]+);/gi,
    (full, prop, val) => {
      const raw = val.trim()
      if (/^(0|auto)(\s+(0|auto))*(\s*!important)?$/i.test(raw)) return full
      return `${prop}: ${replacePxInValue(raw, spaceMap)};`
    }
  )

  next = next.replace(/\b((?:row-|column-)?gap):\s*([^;]+);/gi, (full, prop, val) => {
    return `${prop}: ${replacePxInValue(val.trim(), spaceMap)};`
  })

  next = next.replace(/line-height:\s*([^;]+);/gi, (full, val) => {
    const v = val.trim().replace(/\s*!important$/i, '')
    const important = /\s*!important$/i.test(val.trim())
    const hit = leadingMap[v]
    if (hit) return `line-height: ${hit}${important ? ' !important' : ''};`
    return full
  })

  next = next.replace(/z-index:\s*([^;]+);/gi, (full, val) => {
    const v = val.trim().replace(/\s*!important$/i, '')
    const important = /\s*!important$/i.test(val.trim())
    if (v.startsWith('var(')) return full
    const hit = zMap[v]
    if (hit) return `z-index: ${hit}${important ? ' !important' : ''};`
    return full
  })

  next = next.replace(/\b(transition(?:-[a-z]+)?):\s*([^;]+);/gi, (full, prop, val) => {
    return `${prop}: ${replaceDurationsInValue(val.trim())};`
  })

  next = next.replace(/\b(animation(?:-[a-z]+)?):\s*([^;]+);/gi, (full, prop, val) => {
    // animation-duration / animation shorthand
    if (prop.toLowerCase() === 'animation-name') return full
    return `${prop}: ${replaceDurationsInValue(val.trim())};`
  })

  return next
}

function transformFile(filePath) {
  if (skipFiles.has(filePath)) return false
  if (!['.vue', '.css'].includes(path.extname(filePath))) return false
  const orig = fs.readFileSync(filePath, 'utf8')
  const text = orig
    .split(/\r?\n/)
    .map((line) => {
      if (/^\s*--[a-zA-Z0-9-]+:/.test(line)) return line
      return transformLine(line)
    })
    .join('\n')
  if (text !== orig) {
    fs.writeFileSync(filePath, text)
    return true
  }
  return false
}

function walk(dir, acc = []) {
  for (const name of fs.readdirSync(dir)) {
    if (name === 'node_modules' || name === 'dist' || name === 'dist-electron') continue
    const p = path.join(dir, name)
    const st = fs.statSync(p)
    if (st.isDirectory()) walk(p, acc)
    else acc.push(p)
  }
  return acc
}

const changed = []
for (const f of walk(root)) {
  if (transformFile(f)) changed.push(path.relative(root, f))
}
console.log(`Changed ${changed.length} files`)
for (const c of changed) console.log(' -', c)
