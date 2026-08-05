import { defineStore } from 'pinia'
import { ref } from 'vue'
import { startAntiDebug, stopAntiDebug } from '@/utils/antiDebug'

const API_SIGN_KEY_STORAGE = 'linkx_admin_api_sign_key'

export const useSecurityStore = defineStore('security', () => {
  const apiSignEnabled = ref(true)
  const apiEncryptEnabled = ref(false)
  const disableFrontendDebug = ref(false)
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
    try {
      if (apiSignKey.value) {
        sessionStorage.setItem(API_SIGN_KEY_STORAGE, apiSignKey.value)
      } else {
        sessionStorage.removeItem(API_SIGN_KEY_STORAGE)
      }
    } catch {
      /* ignore */
    }
  }

  function clearApiSignKey() {
    apiSignKey.value = ''
    try {
      sessionStorage.removeItem(API_SIGN_KEY_STORAGE)
    } catch {
      /* ignore */
    }
  }

  try {
    const stored = sessionStorage.getItem(API_SIGN_KEY_STORAGE)
    if (stored?.trim()) {
      apiSignKey.value = stored.trim()
    }
  } catch {
    /* ignore */
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
