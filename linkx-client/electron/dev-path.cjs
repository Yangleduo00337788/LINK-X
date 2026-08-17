/**
 * 开发态 Electron 启动路径：Windows 使用带 LinkX 品牌的 LinkX-dev.exe，避免任务栏缓存 electron.exe 的 Electron 标识。
 */
const fs = require('node:fs')
const path = require('node:path')

const distDir = path.join(__dirname, '../node_modules/electron/dist')
const brandedExe = path.join(distDir, 'LinkX-dev.exe')
const stockExe = path.join(distDir, 'electron.exe')

module.exports = fs.existsSync(brandedExe) ? brandedExe : stockExe
