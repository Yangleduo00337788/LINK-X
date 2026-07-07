import { createApp } from 'vue'
import naive from 'naive-ui'
import AppRoot from './AppRoot.vue'
import 'uno.css'
import './assets/styles.css'

createApp(AppRoot).use(naive).mount('#app')