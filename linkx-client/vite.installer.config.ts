/**
 * 作者：yangleduo
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import electron from 'vite-plugin-electron'
import renderer from 'vite-plugin-electron-renderer'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(__dirname)
const pkg = JSON.parse(fs.readFileSync(path.join(rootDir, 'package.json'), 'utf8'))

function copyInstallerPreload(targetRelativeDir: string) {
  const src = path.resolve(rootDir, 'installer/electron/preload.cjs')
  const dir = path.resolve(rootDir, targetRelativeDir)
  const dest = path.join(dir, 'preload.cjs')
  if (!fs.existsSync(src)) return
  fs.mkdirSync(dir, { recursive: true })
  fs.copyFileSync(src, dest)
}

function copyAllInstallerPreloads() {
  copyInstallerPreload('dist-installer-electron/preload')
  copyInstallerPreload('dist-uninstaller-electron/preload')
}

export default defineConfig({
  root: path.resolve(rootDir, 'installer'),
  base: './',
  envDir: rootDir,
  define: {
    'process.env.LINKX_VERSION': JSON.stringify(pkg.version),
    'process.env.VITE_LEGAL_PAGE_BASE_URL': JSON.stringify(
      process.env.VITE_LEGAL_PAGE_BASE_URL || 'https://mars-studio.asia'
    )
  },
  server: {
    port: 5174,
    strictPort: true
  },
  build: {
    outDir: path.resolve(rootDir, 'dist-installer'),
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: path.resolve(rootDir, 'installer/index.html'),
        uninstall: path.resolve(rootDir, 'installer/uninstall.html')
      }
    }
  },
  plugins: [
    vue(),
    {
      name: 'copy-installer-preload',
      buildStart() {
        copyAllInstallerPreloads()
      }
    },
    electron([
      {
        entry: path.resolve(rootDir, 'installer/electron/main.ts'),
        onstart(options) {
          copyAllInstallerPreloads()
          options.startup()
        },
        vite: {
          define: {
            'process.env.LINKX_VERSION': JSON.stringify(pkg.version),
            'process.env.VITE_LEGAL_PAGE_BASE_URL': JSON.stringify(
              process.env.VITE_LEGAL_PAGE_BASE_URL || 'https://mars-studio.asia'
            )
          },
          build: {
            outDir: path.resolve(rootDir, 'dist-installer-electron/main'),
            rollupOptions: {
              external: ['electron']
            }
          }
        }
      },
      {
        entry: path.resolve(rootDir, 'installer/electron/uninstall-main.ts'),
        vite: {
          define: {
            'process.env.LINKX_VERSION': JSON.stringify(pkg.version)
          },
          build: {
            outDir: path.resolve(rootDir, 'dist-uninstaller-electron/main'),
            rollupOptions: {
              external: ['electron']
            }
          }
        }
      }
    ]),
    renderer()
  ]
})
