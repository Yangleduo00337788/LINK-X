/**
 * 作者：yangleduo
 */
/**
 * Windows 打包后写入圆角应用图标（避免 signAndEditExecutable 触发 winCodeSign 解压失败）
 */
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default async function afterPack(context) {
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
