/**
 * P7：字号 / 间距 / 阴影 Token 批量替换。
 * 跳过 assets/styles.css 与 theme/vars.ts 的 Token 定义源。
 */
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve('src')
const skipFiles = new Set([
  path.join(root, 'assets', 'styles.css'),
  path.join(root, 'theme', 'vars.ts')
])

const fontMap = {
  '10px': 'var(--lx-font-2xs)',
  '11px': 'var(--lx-font-xs)',
  '12px': 'var(--lx-font-sm)',
  '12.5px': 'var(--lx-font-sm)',
  '13px': 'var(--lx-font-md)',
  '14px': 'var(--lx-font)',
  '15px': 'var(--lx-font-lg)',
  '16px': 'var(--lx-font-xl)',
  '17px': 'var(--lx-font-2xl)',
  '18px': 'var(--lx-font-3xl)',
  '20px': 'var(--lx-font-4xl)',
  '22px': 'var(--lx-font-5xl)',
  '26px': 'var(--lx-font-5xl)',
  '28px': 'var(--lx-font-6xl)',
  '30px': 'var(--lx-font-6xl)',
  '32px': 'var(--lx-font-7xl)',
  '36px': 'var(--lx-font-7xl)',
  '40px': 'var(--lx-font-7xl)',
  '48px': 'var(--lx-font-7xl)',
  '64px': 'var(--lx-font-7xl)'
}

const spaceMap = {
  '2px': 'var(--lx-space-2xs)',
  '3px': 'var(--lx-space-2xs)',
  '4px': 'var(--lx-space-xs)',
  '5px': 'var(--lx-space-xs)',
  '6px': 'var(--lx-space-sm)',
  '8px': 'var(--lx-space)',
  '9px': 'var(--lx-space)',
  '10px': 'var(--lx-space-md)',
  '12px': 'var(--lx-space-lg)',
  '14px': 'var(--lx-space-xl)',
  '16px': 'var(--lx-space-2xl)',
  '18px': 'var(--lx-space-2xl)',
  '20px': 'var(--lx-space-3xl)',
  '24px': 'var(--lx-space-4xl)',
  '32px': 'var(--lx-space-5xl)',
  '48px': 'var(--lx-space-6xl)'
}

/** 整段 box-shadow 映射（规范化空白后匹配） */
const shadowExact = new Map([
  ['0 1px 3px rgba(0, 0, 0, 0.05)', 'var(--lx-shadow-soft)'],
  ['0 1px 2px rgba(0, 0, 0, 0.06)', 'var(--lx-shadow-xs)'],
  ['0 1px 2px rgba(15, 23, 42, 0.03)', 'var(--lx-shadow-xs)'],
  ['0 0 0 1px rgba(0, 0, 0, 0.04)', 'var(--lx-shadow-panel)'],
  ['0 2px 8px rgba(0, 0, 0, 0.06)', 'var(--lx-shadow-card)'],
  ['0 4px 16px rgba(0, 0, 0, 0.12)', 'var(--lx-shadow-dropdown)'],
  ['0 8px 24px rgba(0, 0, 0, 0.18)', 'var(--lx-shadow-float)'],
  ['0 8px 32px rgba(0, 0, 0, 0.08)', 'var(--lx-shadow-modal)'],
  ['0 12px 40px rgba(0, 0, 0, 0.45)', 'var(--lx-shadow-popover)'],
  ['0 16px 48px rgba(0, 0, 0, 0.45)', 'var(--lx-shadow-popover)'],
  ['0 24px 64px rgba(0, 0, 0, 0.25)', 'var(--lx-shadow-heavy)'],
  ['-4px 0 24px var(--lx-shadow-color)', 'var(--lx-shadow-drawer)'],
  ['0 0 0 3px var(--lx-accent-soft)', 'var(--lx-shadow-ring-accent)'],
  ['inset 0 0 0 1px rgba(0, 0, 0, 0.06)', 'var(--lx-shadow-inset-border)'],
  ['0 16px 48px var(--lx-bg-overlay)', 'var(--lx-shadow-popover)']
])

function normShadow(v) {
  return v.replace(/\s+/g, ' ').trim().toLowerCase()
}

function replacePxToken(value, map) {
  // 只替换独立的 Npx，保留 0 / auto / % / var()
  return value.replace(/\b(\d+(?:\.\d+)?)px\b/g, (m) => map[m] || m)
}

function transformCssValueLine(line) {
  let next = line

  // font-size
  next = next.replace(/font-size:\s*([^;]+);/gi, (_, val) => {
    const v = val.trim()
    if (fontMap[v]) return `font-size: ${fontMap[v]};`
    // e.g. font-size: 12px !important
    const m = v.match(/^(\d+(?:\.\d+)?px)(\s*!important)?$/i)
    if (m && fontMap[m[1]]) return `font-size: ${fontMap[m[1]]}${m[2] || ''};`
    return `font-size: ${val};`
  })

  // gap / row-gap / column-gap
  next = next.replace(/\b((?:row-|column-)?gap):\s*([^;]+);/gi, (full, prop, val) => {
    const replaced = replacePxToken(val.trim(), spaceMap)
    return `${prop}: ${replaced};`
  })

  // padding / margin（含 -top 等）
  next = next.replace(
    /\b((?:padding|margin)(?:-(?:top|right|bottom|left|inline|block|inline-start|inline-end|block-start|block-end))?):\s*([^;]+);/gi,
    (full, prop, val) => {
      const raw = val.trim()
      // 跳过 0 / auto-only
      if (/^(0|auto)(\s+(0|auto))*(\s*!important)?$/i.test(raw)) return full
      const replaced = replacePxToken(raw, spaceMap)
      return `${prop}: ${replaced};`
    }
  )

  // box-shadow exact
  next = next.replace(/box-shadow:\s*([^;]+);/gi, (full, val) => {
    const raw = val.trim()
    if (/^none\b/i.test(raw) || raw.startsWith('var(--lx-shadow')) return full
    const important = /\s*!important$/i.test(raw)
    const core = raw.replace(/\s*!important$/i, '').trim()
    const hit = shadowExact.get(normShadow(core))
    if (hit) return `box-shadow: ${hit}${important ? ' !important' : ''};`
    return full
  })

  return next
}

function shouldSkipLine(line) {
  const t = line.trim()
  // Token 定义
  if (/^--[a-zA-Z0-9-]+:/.test(t)) return true
  return false
}

function transformFile(filePath) {
  if (skipFiles.has(filePath)) return false
  if (!['.vue', '.css'].includes(path.extname(filePath))) return false
  const orig = fs.readFileSync(filePath, 'utf8')
  const out = orig.split(/\r?\n/).map((line) => {
    if (shouldSkipLine(line)) return line
    return transformCssValueLine(line)
  })
  const text = out.join('\n')
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
