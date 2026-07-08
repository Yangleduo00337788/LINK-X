import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import naive from 'naive-ui'
import AppRoot from './AppRoot.vue'
import router from './router'
import { useAppStore } from './stores/app'
import { applyDocumentTheme, initCrossWindowThemeSync } from './utils/themeSync'
import 'uno.css'
import './assets/styles.css'

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

const app = createApp(AppRoot)
app.use(pinia)
app.use(router)
app.use(naive)

const appStore = useAppStore()
applyDocumentTheme(appStore.theme)

initCrossWindowThemeSync(theme => {
  if (appStore.theme !== theme) {
    appStore.$patch({ theme })
    applyDocumentTheme(theme)
  }
})

router.beforeEach(() => {
  applyDocumentTheme(useAppStore().theme)
})

app.mount('#app')
