/**
 * 作者：yangleduo
 */
/**
 * Windows 打包后写入圆角应用图标，并剥离 sourcemap 以减小安装体积。
 */
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function stripSourceMaps(rootDir) {
  if (!fs.existsSync(rootDir)) return
  let removed = 0
  const stack = [rootDir]
  while (stack.length) {
    const dir = stack.pop()
    if (!dir) continue
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        stack.push(full)
        continue
      }
      if (!entry.name.endsWith('.map')) continue
      fs.unlinkSync(full)
      removed += 1
    }
  }
  if (removed > 0) {
    console.log(`[afterPack] 已移除 ${removed} 个 sourcemap 文件`)
  }
}

export default async function afterPack(context) {
  stripSourceMaps(context.appOutDir)

  if (context.electronPlatformName !== 'win32') return

  const projectDir = context.packager.projectDir
  const exeName = `${context.packager.appInfo.productFilename}.exe`
  const exePath = path.join(context.appOutDir, exeName)
  const iconPath = path.join(projectDir, 'build', 'icon.ico')
  const rceditExe = path.join(projectDir, 'node_modules', 'rcedit', 'bin', 'rcedit.exe')

  if (!fs.existsSync(exePath)) {
    throw new Error(`[afterPack] 未找到可执行文件: ${exePath}`)
  }
  if (!fs.existsSync(iconPath)) {
    throw new Error(`[afterPack] 未找到图标文件: ${iconPath}`)
  }
  if (!fs.existsSync(rceditExe)) {
    throw new Error(`[afterPack] 未找到 rcedit，请先执行 npm install`)
  }

  execFileSync(rceditExe, [exePath, '--set-icon', iconPath], { stdio: 'inherit' })
  console.log(`[afterPack] 已写入应用图标: ${exeName}`)
}
