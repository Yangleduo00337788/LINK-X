/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { startAntiDebug, stopAntiDebug } from '@/utils/antiDebug'

export const useSecurityStore = defineStore('security', () => {
  const apiSignEnabled = ref(true)
  const apiEncryptEnabled = ref(false)
  const disableFrontendDebug = ref(false)
  /** 仅内存持有，页面刷新后通过 refresh 接口补发 */
  const apiSignKey = ref('')

  function applyFromAuthConfig(config: {
    apiSignEnabled?: boolean
    apiEncryptEnabled?: boolean
    disableFrontendDebug?: boolean
  }) {
    apiSignEnabled.value = config.apiSignEnabled !== false
    apiEncryptEnabled.value = config.apiEncryptEnabled === true
    disableFrontendDebug.value = config.disableFrontendDebug === true

    if (disableFrontendDebug.value) {
      startAntiDebug()
    } else {
      stopAntiDebug()
    }
  }

  function applyFromSettings(config?: {
    apiSignEnabled?: boolean
    apiEncryptEnabled?: boolean
    disableFrontendDebug?: boolean
  }) {
    if (!config) return
    const prevDebug = disableFrontendDebug.value
    if (config.apiSignEnabled != null) {
      apiSignEnabled.value = config.apiSignEnabled === true
    }
    if (config.apiEncryptEnabled != null) {
      apiEncryptEnabled.value = config.apiEncryptEnabled === true
      if (apiEncryptEnabled.value) {
        apiSignEnabled.value = true
      }
    }
    disableFrontendDebug.value = config.disableFrontendDebug === true

    if (disableFrontendDebug.value && !prevDebug) {
      startAntiDebug()
    } else if (!disableFrontendDebug.value && prevDebug) {
      stopAntiDebug()
    }
  }

  function setApiSignKey(key?: string) {
    apiSignKey.value = key?.trim() || ''
  }

  function clearApiSignKey() {
    apiSignKey.value = ''
  }

  function resetSession() {
    clearApiSignKey()
  }

  return {
    apiSignEnabled,
    apiEncryptEnabled,
    disableFrontendDebug,
    apiSignKey,
    applyFromAuthConfig,
    applyFromSettings,
    resetSession,
    setApiSignKey,
    clearApiSignKey,
  }
})
