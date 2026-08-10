/**
 * 作者：yangleduo
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.join(__dirname, '..')

const AUTHOR_MARK = '作者：yangleduo'
const AUTHOR_ALT = '@author yangleduo'

const SCAN_ROOTS = [
  'linkx-client/src',
  'linkx-client/electron',
  'linkx-client/installer',
  'linkx-client/scripts',
  'linkx-client/shared',
  'linkx-client/public/legal',
  'linkx-server/src',
  'linkx-server/docker',
  'linkx-admin/src'
]

const SINGLE_FILES = [
  'README.md',
  'CHANGELOG.md',
  'CONTRIBUTING.md',
  'linkx-client/vite.config.ts',
  'linkx-client/vite.installer.config.ts',
  'linkx-client/electron-builder.installer.yml',
  'linkx-server/Dockerfile',
  'linkx-server/docker-compose.yml',
  'linkx-server/init.sql',
  'linkx-server/pom.xml',
  'linkx-admin/vite.config.ts',
  'linkx-admin/README.md'
]

const EXT_HANDLERS = {
  '.java': stampJava,
  '.ts': stampJsBlock,
  '.tsx': stampJsBlock,
  '.js': stampJsBlock,
  '.mjs': stampJsBlock,
  '.cjs': stampJsBlock,
  '.vue': stampVue,
  '.css': stampCss,
  '.sql': stampSql,
  '.yml': stampYaml,
  '.yaml': stampYaml,
  '.properties': stampProperties,
  '.html': stampHtml,
  '.ps1': stampPs1,
  '.md': stampMarkdown
}

const SKIP_DIRS = new Set([
  'node_modules',
  'dist',
  'dist-electron',
  'dist-installer',
  'dist-installer-electron',
  'target',
  'release',
  '.installer-payload',
  '.git',
  '.idea'
])

const SKIP_EXT = new Set([
  '.png',
  '.jpg',
  '.jpeg',
  '.gif',
  '.webp',
  '.ico',
  '.bmp',
  '.woff',
  '.woff2',
  '.ttf',
  '.eot',
  '.svg',
  '.map',
  '.json',
  '.imports',
  '.lock'
])

let updated = 0
let skipped = 0
let examined = 0

function hasAuthor(text) {
  return text.includes(AUTHOR_MARK) || text.includes(AUTHOR_ALT)
}

function stampJsBlock(content) {
  return `/**\n * ${AUTHOR_MARK}\n */\n${content}`
}

function stampCss(content) {
  return `/**\n * ${AUTHOR_MARK}\n */\n${content}`
}

function stampSql(content) {
  return `-- ${AUTHOR_MARK}\n${content}`
}

function stampYaml(content) {
  return `# ${AUTHOR_MARK}\n${content}`
}

function stampProperties(content) {
  return `# ${AUTHOR_MARK}\n${content}`
}

function stampMarkdown(content) {
  return `<!-- ${AUTHOR_MARK} -->\n${content}`
}

function stampHtml(content) {
  return `<!-- ${AUTHOR_MARK} -->\n${content}`
}

function stampVue(content) {
  return `<!-- ${AUTHOR_MARK} -->\n${content}`
}

function stampPs1(content) {
  if (content.startsWith('#!')) {
    const idx = content.indexOf('\n')
    return `${content.slice(0, idx + 1)}\n# ${AUTHOR_MARK}\n${content.slice(idx + 1)}`
  }
  return `# ${AUTHOR_MARK}\n${content}`
}

function stampJava(content) {
  const packageMatch = content.match(/^package\s+[\w.]+;\s*/m)
  if (packageMatch) {
    const insertAt = packageMatch.index + packageMatch[0].length
    const before = content.slice(0, insertAt)
    const after = content.slice(insertAt).replace(/^\s*/, '\n')
    return `${before}\n/**\n * ${AUTHOR_MARK}\n */${after}`
  }
  return `/**\n * ${AUTHOR_MARK}\n */\n${content}`
}

function stampDockerfile(content) {
  return `# ${AUTHOR_MARK}\n${content}`
}

function stampXmlDeveloper(content) {
  if (hasAuthor(content)) return content
  if (content.includes('<developers>')) return content
  const marker = '</description>'
  if (!content.includes(marker)) return content
  return content.replace(
    marker,
    `${marker}\n\n    <developers>\n        <developer>\n            <id>yangleduo</id>\n            <name>yangleduo</name>\n        </developer>\n    </developers>`
  )
}

function stampPackageJson(filePath, content) {
  const data = JSON.parse(content)
  if (data.author === 'yangleduo') return content
  data.author = 'yangleduo'
  fs.writeFileSync(filePath, `${JSON.stringify(data, null, 2)}\n`, 'utf8')
  return null
}

function walk(dir, files = []) {
  if (!fs.existsSync(dir)) return files
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full, files)
    } else {
      files.push(full)
    }
  }
  return files
}

function stampFile(filePath) {
  examined++
  const ext = path.extname(filePath).toLowerCase()

  if (filePath.endsWith('package.json')) {
    const content = fs.readFileSync(filePath, 'utf8')
    if (content.includes('"author": "yangleduo"')) {
      skipped++
      return
    }
    stampPackageJson(filePath, content)
    updated++
    return
  }

  if (SKIP_EXT.has(ext)) {
    skipped++
    return
  }

  if (filePath.endsWith('pom.xml')) {
    const content = fs.readFileSync(filePath, 'utf8')
    if (hasAuthor(content)) {
      skipped++
      return
    }
    const next = stampXmlDeveloper(content)
    if (next !== content) {
      fs.writeFileSync(filePath, next, 'utf8')
      updated++
    } else {
      skipped++
    }
    return
  }

  if (filePath.endsWith('Dockerfile')) {
    const content = fs.readFileSync(filePath, 'utf8')
    if (hasAuthor(content)) {
      skipped++
      return
    }
    fs.writeFileSync(filePath, stampDockerfile(content), 'utf8')
    updated++
    return
  }

  const handler = EXT_HANDLERS[ext]
  if (!handler) {
    skipped++
    return
  }

  const content = fs.readFileSync(filePath, 'utf8')
  if (hasAuthor(content)) {
    skipped++
    return
  }

  fs.writeFileSync(filePath, handler(content), 'utf8')
  updated++
}

const files = []
for (const rel of SCAN_ROOTS) {
  walk(path.join(rootDir, rel), files)
}
for (const rel of SINGLE_FILES) {
  const full = path.join(rootDir, rel)
  if (fs.existsSync(full)) files.push(full)
}
for (const rel of ['linkx-client/package.json', 'linkx-admin/package.json']) {
  const full = path.join(rootDir, rel)
  if (fs.existsSync(full)) files.push(full)
}

for (const file of [...new Set(files)].sort()) {
  stampFile(file)
}

console.log(`[stamp-author] examined=${examined} updated=${updated} skipped=${skipped}`)
