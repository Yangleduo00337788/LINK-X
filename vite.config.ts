import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import electron from 'vite-plugin-electron'
import renderer from 'vite-plugin-electron-renderer'
import fs from 'node:fs'
import path from 'node:path'

function copyPreloadCjs() {
  const src = path.resolve('electron/preload.cjs')
  const dir = path.resolve('dist-electron/preload')
  const dest = path.join(dir, 'preload.cjs')
  if (!fs.existsSync(src)) return
  fs.mkdirSync(dir, { recursive: true })
  fs.copyFileSync(src, dest)
}

export default defineConfig(({ mode }) => {
  const isElectron = mode === 'electron'

  return {
    base: './',
    plugins: [
      vue(),
      UnoCSS(),
      isElectron
        ? {
            name: 'copy-electron-preload-cjs',
            buildStart() {
              copyPreloadCjs()
            }
          }
        : null,
      isElectron
        ? electron([
            {
              entry: 'electron/main.ts',
              onstart: options => {
                copyPreloadCjs()
                options.startup()
              },
              vite: {
                build: {
                  sourcemap: true,
                  minify: mode === 'electron',
                  outDir: 'dist-electron/main',
                  rollupOptions: {
                    external: ['electron']
                  }
                }
              }
            }
          ])
        : null,
      isElectron ? renderer() : null
    ].filter(Boolean)
  }
})
