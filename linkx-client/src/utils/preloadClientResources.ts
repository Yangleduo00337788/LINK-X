/**
 * 登录页停留期间后台预拉主界面资源，减轻登录成功后的首屏卡顿。
 */
let preloadPromise: Promise<void> | null = null

export function preloadAppShellComponent(): Promise<typeof import('../components/AppShell.vue')> {
  return import('../components/AppShell.vue')
}

/** 预加载主壳与常用 store / 子模块（幂等，可多次调用） */
export function preloadClientResources(): Promise<void> {
  if (!preloadPromise) {
    preloadPromise = Promise.all([
      preloadAppShellComponent(),
      import('../stores/contacts'),
      import('../stores/groupMeta'),
      import('../stores/appSettings'),
      import('../stores/linkmate')
    ]).then(() => undefined)
  }
  return preloadPromise
}
