/**
 * 将业务 CSS 中的硬编码色收敛到 --lx-* Token。
 * 跳过：CSS 变量定义行、外观色板渐变、日历事件色数组等数据源。
 */
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve('src')

const skipFiles = new Set([
  // Token 源文件：定义处必须保留字面量
  path.join(root, 'assets', 'styles.css'),
  path.join(root, 'theme', 'vars.ts')
])

/** 全局安全替换（任意上下文） */
const globalMap = [
  [/#12b7f5\b/gi, 'var(--lx-accent)'],
  [/#39c2f6\b/gi, 'var(--lx-accent-hover)'],
  [/#6eb5ff\b/gi, 'var(--lx-accent-light)'],
  [/#0aa6e0\b/gi, 'var(--lx-accent-deep)'],
  [/#fa5151\b/gi, 'var(--lx-danger)'],
  [/#ff4d4f\b/gi, 'var(--lx-danger-hover)'],
  [/#e05454\b/gi, 'var(--lx-danger)'],
  [/#e74c3c\b/gi, 'var(--lx-danger)'],
  [/#e84c3d\b/gi, 'var(--lx-danger)'],
  [/#e34d59\b/gi, 'var(--lx-danger)'],
  [/#ef4444\b/gi, 'var(--lx-danger)'],
  [/#ff6b6b\b/gi, 'var(--lx-danger)'],
  [/#c0392b\b/gi, 'var(--lx-danger-deep)'],
  [/#52c41a\b/gi, 'var(--lx-success)'],
  [/#07c160\b/gi, 'var(--lx-success-strong)'],
  [/#18a058\b/gi, 'var(--lx-success)'],
  [/#faad14\b/gi, 'var(--lx-warning)'],
  [/#ff8800\b/gi, 'var(--lx-icon-image-color)'],
  [/#fff0e6\b/gi, 'var(--lx-icon-image-bg)'],
  [/#fff7e6\b/gi, 'var(--lx-warning-bg)'],
  [/#fff0f0\b/gi, 'var(--lx-danger-bg)'],
  [/#fff1f0\b/gi, 'var(--lx-danger-bg-soft)'],
  [/#e6f2ff\b/gi, 'var(--lx-accent-bg-soft)'],
  [/#006eff\b/gi, 'var(--lx-conf-accent)'],
  [/#0056cc\b/gi, 'var(--lx-conf-accent-hover)']
]

/** 带属性上下文的中性色 */
const propMap = [
  // 白字
  [/(color\s*:\s*)#(?:fff|ffffff)\b/gi, '$1var(--lx-text-on-accent)'],
  [/(border-top-color\s*:\s*)#(?:fff|ffffff)\b/gi, '$1var(--lx-text-on-accent)'],
  // 白底
  [/(background(?:-color)?\s*:\s*)#(?:fff|ffffff)\b/gi, '$1var(--lx-bg-card)'],
  // 文字灰阶
  [/(color\s*:\s*)#(?:999|999999)\b/gi, '$1var(--lx-text-muted)'],
  [/(color\s*:\s*)#(?:888|888888)\b/gi, '$1var(--lx-text-muted)'],
  [/(color\s*:\s*)#(?:666|666666)\b/gi, '$1var(--lx-text-secondary)'],
  [/(color\s*:\s*)#(?:555|555555)\b/gi, '$1var(--lx-text-secondary)'],
  [/(color\s*:\s*)#(?:333|333333)\b/gi, '$1var(--lx-text-body)'],
  [/(color\s*:\s*)#(?:1a1a1a)\b/gi, '$1var(--lx-text-primary)'],
  [/(color\s*:\s*)#(?:ccc|cccccc)\b/gi, '$1var(--lx-border-strong)'],
  [/(color\s*:\s*)#(?:ddd|dddddd)\b/gi, '$1var(--lx-border-strong)'],
  [/(color\s*:\s*)#(?:b2b2b2)\b/gi, '$1var(--lx-text-muted)'],
  // 背景灰阶
  [/(background(?:-color)?\s*:\s*)#(?:f5f5f5)\b/gi, '$1var(--lx-bg-panel)'],
  [/(background(?:-color)?\s*:\s*)#(?:ebebeb)\b/gi, '$1var(--lx-bg-input)'],
  [/(background(?:-color)?\s*:\s*)#(?:eee|eeeeee|eeeef0)\b/gi, '$1var(--lx-bg-list)'],
  [/(background(?:-color)?\s*:\s*)#(?:f0f0f0)\b/gi, '$1var(--lx-bg-panel)'],
  [/(background(?:-color)?\s*:\s*)#(?:f7f7f7)\b/gi, '$1var(--lx-bg-panel)'],
  [/(background(?:-color)?\s*:\s*)#(?:ededed)\b/gi, '$1var(--lx-bg-panel)'],
  [/(background(?:-color)?\s*:\s*)#(?:1a1a1a)\b/gi, '$1var(--lx-bg-window)'],
  [/(background(?:-color)?\s*:\s*)#(?:2c2c2c)\b/gi, '$1var(--lx-bg-card)'],
  [/(background(?:-color)?\s*:\s*)#(?:2a2a2a)\b/gi, '$1var(--lx-bg-panel-deep)'],
  [/(background(?:-color)?\s*:\s*)#(?:3a3a3a)\b/gi, '$1var(--lx-divider)'],
  // 边框
  [/(border(?:-color)?\s*:\s*)#(?:ccc|cccccc)\b/gi, '$1var(--lx-border-strong)'],
  [/(border(?:-color)?\s*:\s*)#(?:ddd|dddddd)\b/gi, '$1var(--lx-border-strong)'],
  [/(border(?:-color)?\s*:\s*)#(?:eee|eeeeee)\b/gi, '$1var(--lx-divider)'],
  [/(border(?:-color)?\s*:\s*)#(?:ebebeb)\b/gi, '$1var(--lx-divider)'],
  [/(border(?:-[a-z]+)?-color\s*:\s*)#(?:ccc|cccccc)\b/gi, '$1var(--lx-border-strong)'],
  [/(border(?:-[a-z]+)?-color\s*:\s*)#(?:eee|eeeeee|ebebeb)\b/gi, '$1var(--lx-divider)']
]

/** 去掉已无用的 fallback：var(--lx-accent, #12b7f5) */
const stripFallback = [
  [/var\(--lx-accent,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-accent)'],
  [/var\(--lx-danger,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-danger)'],
  [/var\(--lx-success,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-success)'],
  [/var\(--lx-text-muted,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-text-muted)'],
  [/var\(--lx-text-secondary,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-text-secondary)'],
  [/var\(--lx-bg-card,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-bg-card)'],
  [/var\(--lx-bg-panel,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-bg-panel)'],
  [/var\(--lx-bg-window,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-bg-window)']
]

function shouldSkipLine(line, file) {
  const t = line.trim()
  // CSS / 局部变量定义：保留字面量
  if (/^--[a-zA-Z0-9-]+:/.test(t)) return true
  // 日历事件色 / 头像色等数据
  if (/EVENT_COLORS|avatarColor\s*:/.test(line)) return true
  // 外观设置色板渐变
  if (file.includes('AppearanceSettings') && /#(?:ff6b6b|feca57|48dbfb|ff9ff3|54a0ff)/.test(line)) {
    return true
  }
  return false
}

function transformFile(filePath) {
  if (skipFiles.has(filePath)) return false
  const ext = path.extname(filePath)
  if (!['.vue', '.css'].includes(ext)) return false

  const orig = fs.readFileSync(filePath, 'utf8')
  const lines = orig.split(/\r?\n/)
  let changed = false

  const out = lines.map((line) => {
    if (shouldSkipLine(line, filePath)) return line
    let next = line
    for (const [re, rep] of propMap) next = next.replace(re, rep)
    for (const [re, rep] of globalMap) next = next.replace(re, rep)
    for (const [re, rep] of stripFallback) next = next.replace(re, rep)
    if (next !== line) changed = true
    return next
  })

  // login 局部 alias 指向全局 accent
  let text = out.join('\n')
  const beforeAlias = text
  text = text.replace(/--lx-login-accent:\s*var\(--lx-accent\)/g, '--lx-login-accent: var(--lx-accent)')
  text = text.replace(/--lx-login-accent:\s*#[0-9a-fA-F]+/gi, '--lx-login-accent: var(--lx-accent)')
  if (text !== beforeAlias) changed = true

  if (!changed && text === orig) return false
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

const files = walk(root)
const changed = []
for (const f of files) {
  if (transformFile(f)) changed.push(path.relative(root, f))
}

console.log(`Changed ${changed.length} files`)
for (const f of changed) console.log(' -', f)
