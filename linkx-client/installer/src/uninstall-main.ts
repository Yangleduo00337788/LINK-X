/**
 * 作者：yangleduo
 */
import { createApp } from 'vue'
import UninstallerApp from './UninstallerApp.vue'
import './installer.css'

document.documentElement.classList.add('lx-electron')

createApp(UninstallerApp).mount('#app')
