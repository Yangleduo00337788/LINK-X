import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'
import AppRoot from './AppRoot.vue'
import 'uno.css'
import './assets/styles.css'

const app = createApp(AppRoot)
app.use(createPinia())
app.use(naive)
app.mount('#app')