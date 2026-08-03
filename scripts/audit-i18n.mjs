import fs from 'fs'
import path from 'path'

function walk(dir, acc = []) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name)
    if (ent.isDirectory()) {
      if (!['node_modules', '__tests__', 'dist', 'dist-electron'].includes(ent.name)) walk(p, acc)
    } else if (ent.name.endsWith('.vue') || ent.name.endsWith('.ts')) {
      acc.push(p)
    }
  }
  return acc
}

function stripComments(s) {
  return s
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/.*$/gm, '')
    .replace(/<!--[\s\S]*?-->/g, '')
}

function auditVue(root, label) {
  const files = walk(root).filter(f => f.endsWith('.vue'))
  const zhRe = /[\u4e00-\u9fff]/
  const noI18n = []
  const hardcodedTpl = []
  const hardcodedScript = []

  for (const f of files) {
    const rel = path.relative(root, f).replace(/\\/g, '/')
    const src = fs.readFileSync(f, 'utf8')
    const hasI18n = /useI18n|from ['"].*i18n/.test(src)
    const template = (src.match(/<template[\s\S]*?<\/template>/i) || [''])[0]
    const script = (src.match(/<script[\s\S]*?<\/script>/i) || [''])[0]

    if (!hasI18n && zhRe.test(template)) noI18n.push(rel)

    const tplLines = template.split('\n').filter(l => !l.trim().startsWith('<!--'))
    const badTpl = tplLines.filter(l => {
      if (!zhRe.test(l)) return false
      if (/\bt\(|\.t\(|\$t\(|\{\{/.test(l)) return false
      return true
    })
    if (badTpl.length) hardcodedTpl.push({ rel, n: badTpl.length, sample: badTpl[0].trim().slice(0, 100) })

    const scriptClean = stripComments(script)
    const userFacingPatterns = [
      /message\.(success|error|warning|info)\(\s*['"`][^'"`]*[\u4e00-\u9fff]/,
      /title:\s*['"`][^'"`]*[\u4e00-\u9fff]/,
      /placeholder=["'][^"']*[\u4e00-\u9fff]/,
      /label=["'][^"']*[\u4e00-\u9fff]/,
    ]
    for (const re of userFacingPatterns) {
      if (re.test(scriptClean) && !/\bt\(/.test(scriptClean.match(re)?.[0] || '')) {
        hardcodedScript.push({ rel, sample: scriptClean.match(re)?.[0]?.slice(0, 100) })
        break
      }
    }
  }

  hardcodedTpl.sort((a, b) => b.n - a.n)
  console.log(`\n=== ${label} Vue (${files.length} files) ===`)
  console.log(`无 i18n 但模板含中文: ${noI18n.length}`)
  noI18n.forEach(f => console.log(`  - ${f}`))
  console.log(`模板硬编码中文 (未用 t/$t): ${hardcodedTpl.length}`)
  hardcodedTpl.slice(0, 30).forEach(x => console.log(`  - ${x.rel} (${x.n}) ${x.sample}`))
  console.log(`脚本疑似硬编码 UI 文案: ${hardcodedScript.length}`)
  hardcodedScript.slice(0, 20).forEach(x => console.log(`  - ${x.rel} ${x.sample}`))
}

function auditTs(root, label) {
  const files = walk(root).filter(f => {
    const norm = f.replace(/\\/g, '/')
    return (
      f.endsWith('.ts') &&
      !norm.includes('i18n/locales') &&
      !norm.includes('.test.') &&
      !norm.includes('.spec.')
    )
  })
  const zhRe = /[\u4e00-\u9fff]/
  const hits = []
  for (const f of files) {
    const rel = path.relative(root, f).replace(/\\/g, '/')
    if (rel === 'types/profileGender.ts') continue
    const src = stripComments(fs.readFileSync(f, 'utf8'))
    if (!zhRe.test(src)) continue
    const hasI18n = /from ['"].*i18n|useI18n|\bt\(/.test(src)
    const lines = src.split('\n').filter(l => zhRe.test(l) && !l.includes('console.') && !l.trim().startsWith('*') && !l.trim().startsWith('//'))
    if (lines.length && !hasI18n) hits.push({ rel, n: lines.length, sample: lines[0].trim().slice(0, 90) })
  }
  hits.sort((a, b) => b.n - a.n)
  console.log(`\n=== ${label} TS 无 i18n 但含中文 (${hits.length} files) ===`)
  hits.slice(0, 35).forEach(x => console.log(`  - ${x.rel} (${x.n}) ${x.sample}`))
}

auditVue('linkx-client/src', 'client')
auditVue('linkx-admin/src', 'admin')
auditTs('linkx-client/src', 'client')
auditTs('linkx-admin/src', 'admin')

function countVueI18n(root, label) {
  const files = walk(root).filter(f => f.endsWith('.vue'))
  const withI18n = []
  const without = []
  for (const f of files) {
    const rel = path.relative(root, f).replace(/\\/g, '/')
    const src = fs.readFileSync(f, 'utf8')
    if (/useI18n|from ['"].*i18n/.test(src)) withI18n.push(rel)
    else without.push(rel)
  }
  console.log(`\n=== ${label} Vue i18n 接入 ===`)
  console.log(`已接入: ${withI18n.length}/${files.length} (${Math.round((withI18n.length / files.length) * 100)}%)`)
  console.log(`未接入: ${without.join(', ') || '(无)'}`)
}

countVueI18n('linkx-client/src', 'client')
countVueI18n('linkx-admin/src', 'admin')
