/**
 * 作者：yangleduo
 */
import { createApp } from 'vue'
import InstallerApp from './InstallerApp.vue'
import './installer.css'
import { installGlobalWheelScrollDamping } from '../../src/utils/wheelScrollDamping'

document.documentElement.classList.add('lx-electron')
installGlobalWheelScrollDamping()

createApp(InstallerApp).mount('#app')
