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

export default defineConfig(({ mode, command }) => {
  const isElectron = mode === 'electron'

  return {
    base: './',
    // [P2-5] 生产构建移除 console / debugger，减少包体积并避免泄露调试信息
    // esbuild drop 仅在 minify 生效时实际剥离，dev serve 不受影响
    esbuild: command === 'build' ? { drop: ['console', 'debugger'] } : undefined,
    build: {
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          manualChunks: {
            'naive-ui': ['naive-ui'],
            'vue-vendor': ['vue', 'vue-router', 'pinia']
          }
        }
      }
    },
    plugins: [
      vue({
        template: {
          compilerOptions: {
            isCustomElement: tag => tag === 'webview'
          }
        }
      }),
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
