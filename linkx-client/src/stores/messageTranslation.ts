/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as linkmateApi from '../api/linkmate'
import {
  resolveTranslateTargetLang,
  type TranslateLangCode
} from '../utils/translateLang'
import { resolveLinkMateErrorMessage } from '../utils/linkmateErrors'
import { t } from '../i18n'
import { useAppSettingsStore } from './appSettings'

export interface MessageTranslationEntry {
  text?: string
  targetLang?: string
  targetLangCode?: TranslateLangCode
  loading?: boolean
  error?: string
  visible?: boolean
}

export const useMessageTranslationStore = defineStore('messageTranslation', () => {
  const byMessageId = ref<Record<string, MessageTranslationEntry>>({})

  function getEntry(messageId: string): MessageTranslationEntry | undefined {
    return byMessageId.value[messageId]
  }

  function hide(messageId: string) {
    const entry = byMessageId.value[messageId]
    if (!entry) return
    byMessageId.value = {
      ...byMessageId.value,
      [messageId]: { ...entry, visible: false }
    }
  }

  function clearSession(sessionId: string, messageIds: string[]) {
    if (!sessionId || !messageIds.length) return
    const next = { ...byMessageId.value }
    for (const id of messageIds) {
      delete next[id]
    }
    byMessageId.value = next
  }

  function clearAll() {
    byMessageId.value = {}
  }

  async function translateMessage(
    messageId: string,
    text: string,
    targetLang?: TranslateLangCode
  ) {
    const trimmed = text.trim()
    if (!trimmed) return

    const settingsStore = useAppSettingsStore()
    const lang = targetLang ?? resolveTranslateTargetLang(settingsStore.translateTargetLang)

    const existing = byMessageId.value[messageId]
    if (existing?.loading) return
    if (existing?.text && existing.visible !== false && existing.targetLangCode === lang) {
      byMessageId.value = {
        ...byMessageId.value,
        [messageId]: { ...existing, visible: true }
      }
      return
    }

    byMessageId.value = {
      ...byMessageId.value,
      [messageId]: { loading: true, visible: true, error: undefined }
    }

    try {
      const res = await linkmateApi.translateText(trimmed, lang)
      if (res.code !== 200 || !res.data?.translatedText) {
        throw new Error(res.message || '翻译失败')
      }
      byMessageId.value = {
        ...byMessageId.value,
        [messageId]: {
          text: res.data.translatedText,
          targetLang: res.data.targetLang,
          targetLangCode: lang,
          loading: false,
          visible: true
        }
      }
    } catch (err) {
      const ax = err as { response?: { data?: { message?: string } }; message?: string }
      const raw = ax.response?.data?.message || ax.message || ''
      byMessageId.value = {
        ...byMessageId.value,
        [messageId]: {
          loading: false,
          visible: true,
          error: resolveLinkMateErrorMessage(raw, t)
        }
      }
    }
  }

  return {
    byMessageId,
    getEntry,
    hide,
    clearSession,
    clearAll,
    translateMessage
  }
})
