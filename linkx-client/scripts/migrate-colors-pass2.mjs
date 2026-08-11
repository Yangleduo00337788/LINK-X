/**
 * P6 第二轮：会议深色 / slate / 登录辅助色 / 中性残留收敛。
 */
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve('src')
const skipFiles = new Set([
  path.join(root, 'assets', 'styles.css'),
  path.join(root, 'theme', 'vars.ts')
])

const globalMap = [
  [/#1f1f1f\b/gi, 'var(--lx-conf-bg)'],
  [/#1e1e1e\b/gi, 'var(--lx-conf-bg-deep)'],
  [/#111111\b/gi, 'var(--lx-conf-bg-void)'],
  [/#111\b/gi, 'var(--lx-conf-bg-void)'],
  [/#1f2329\b/gi, 'var(--lx-conf-surface)'],
  [/#64748b\b/gi, 'var(--lx-slate)'],
  [/#94a3b8\b/gi, 'var(--lx-slate-muted)'],
  [/#f1f5f9\b/gi, 'var(--lx-slate-soft)'],
  [/#5c6370\b/gi, 'var(--lx-ink-soft)'],
  [/#f5f7fa\b/gi, 'var(--lx-bg-soft)'],
  [/#e8eef5\b/gi, 'var(--lx-bg-mist)'],
  [/#f0f4f8\b/gi, 'var(--lx-bg-logo)'],
  [/#088fc4\b/gi, 'var(--lx-login-link-hover)'],
  [/#a855f7\b/gi, 'var(--lx-brand-purple)'],
  [/#5b8cff\b/gi, 'var(--lx-brand-blue-mid)'],
  [/#0ea5e0\b/gi, 'var(--lx-accent-sky)'],
  [/#e81123\b/gi, 'var(--lx-caption-close)'],
  [/#c0c4cc\b/gi, 'var(--lx-border-strong)']
]

const propMap = [
  [/(color\s*:\s*)#(?:1a1a1a)\b/gi, '$1var(--lx-text-primary)'],
  [/(color\s*:\s*)#(?:111|111111)\b/gi, '$1var(--lx-text-primary)'],
  [/(color\s*:\s*)#(?:f0f0f0)\b/gi, '$1var(--lx-text-primary)'],
  [/(color\s*:\s*)#(?:e5e5e5|e0e0e0)\b/gi, '$1var(--lx-text-body)'],
  [/(background(?:-color)?\s*:\s*)#(?:f0f0f0)\b/gi, '$1var(--lx-bg-panel)'],
  [/(background(?:-color)?\s*:\s*)#(?:f5f5f5)\b/gi, '$1var(--lx-bg-panel)'],
  [/(background(?:-color)?\s*:\s*)#(?:ebebeb)\b/gi, '$1var(--lx-bg-input)'],
  [/(background(?:-color)?\s*:\s*)#(?:eee|eeeeee)\b/gi, '$1var(--lx-bg-list)'],
  [/(background(?:-color)?\s*:\s*)#(?:e5e5e5|e0e0e0)\b/gi, '$1var(--lx-divider)'],
  [/(background(?:-color)?\s*:\s*)#(?:2a2a2a)\b/gi, '$1var(--lx-bg-panel-deep)'],
  [/(background(?:-color)?\s*:\s*)#(?:3a3a3a)\b/gi, '$1var(--lx-divider)'],
  [/(background(?:-color)?\s*:\s*)#(?:1a1a1a)\b/gi, '$1var(--lx-bg-window)'],
  [/(background(?:-color)?\s*:\s*)#(?:000|000000)\b/gi, '$1var(--lx-black)'],
  [/(border(?:-color)?\s*:\s*)#(?:ddd|dddddd)\b/gi, '$1var(--lx-border-strong)'],
  [/(border(?:-color)?\s*:\s*)#(?:eee|eeeeee)\b/gi, '$1var(--lx-divider)'],
  [/(border(?:-color)?\s*:\s*)#(?:ebebeb|e5e5e5|e0e0e0)\b/gi, '$1var(--lx-divider)'],
  [/(border(?:-[a-z]+)?-color\s*:\s*)#(?:ddd|dddddd|ccc|cccccc)\b/gi, '$1var(--lx-border-strong)'],
  [/(border(?:-[a-z]+)?-color\s*:\s*)#(?:eee|eeeeee|ebebeb|e5e5e5|e0e0e0)\b/gi, '$1var(--lx-divider)'],
  // 白字/白边残留
  [/(color\s*:\s*)#(?:fff|ffffff)\b/gi, '$1var(--lx-text-on-accent)'],
  [/(border(?:-[a-z]+)?(?:-color)?\s*:\s*[^;]*?)#(?:fff|ffffff)\b/gi, '$1var(--lx-text-on-accent)'],
  [/(background(?:-color)?\s*:\s*)#(?:fff|ffffff)\b/gi, '$1var(--lx-bg-card)']
]

const stripFallback = [
  [/var\(--lx-text-body,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-text-body)'],
  [/var\(--lx-text-primary,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-text-primary)'],
  [/var\(--lx-bg-app,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-bg-soft)'],
  [/var\(--lx-bg-panel,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-bg-panel)'],
  [/var\(--lx-conf-surface,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-conf-surface)'],
  [/var\(--lx-slate-muted,\s*#[0-9a-fA-F]+\)/g, 'var(--lx-slate-muted)']
]

function shouldSkipLine(line, file) {
  const t = line.trim()
  if (/^--[a-zA-Z0-9-]+:/.test(t)) return true
  if (/EVENT_COLORS|avatarColor\s*:|newTagColor|tag\.color|naiveThemeColors|cardColor|modalColor|popoverColor|textColor1/.test(line)) {
    return true
  }
  if (file.includes('AppearanceSettings') && /#(?:ff6b6b|feca57|48dbfb|ff9ff3|54a0ff)/.test(line)) {
    return true
  }
  // AppRoot Naive 主题必须用字面量
  if (file.includes('AppRoot.vue') && /isDark\s*\?/.test(line)) return true
  return false
}

function transformFile(filePath) {
  if (skipFiles.has(filePath)) return false
  if (!['.vue', '.css'].includes(path.extname(filePath))) return false

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

  let text = out.join('\n')
  const before = text
  // 登录局部 alias 挂到全局
  text = text.replace(/--lx-login-ink:\s*#[0-9a-fA-F]+/gi, '--lx-login-ink: var(--lx-login-ink)')
  text = text.replace(/--lx-login-muted:\s*#[0-9a-fA-F]+/gi, '--lx-login-muted: var(--lx-login-muted)')
  // 上面会自引用，改为不在局部重复定义——直接删除局部 ink/muted 定义行，改用全局
  // 更稳妥：局部值指向同名全局在 CSS 里是循环。改为：
  text = text.replace(/--lx-login-ink:\s*var\(--lx-login-ink\)\s*;?/g, '')
  text = text.replace(/--lx-login-muted:\s*var\(--lx-login-muted\)\s*;?/g, '')
  if (text !== before) changed = true

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
