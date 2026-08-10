/**
 * 作者：yangleduo
 */
import { createApp } from 'vue'
import InstallerApp from './InstallerApp.vue'
import './installer.css'

document.documentElement.classList.add('lx-electron')

createApp(InstallerApp).mount('#app')
