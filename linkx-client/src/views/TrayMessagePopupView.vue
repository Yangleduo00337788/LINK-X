<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 托盘悬停消息气泡（QQ 风格）：头像 + 昵称 + 消息预览。
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'
import Avatar from '../components/Avatar.vue'
import { useI18n } from '../i18n'

const { t } = useI18n()
const nickname = ref('LinkX')
const body = ref('')
const avatarUrl = ref<string | undefined>()
const unreadCount = ref(0)

let unsubData: (() => void) | null = null

function applyPayload(data: {
  title?: string
  body?: string
  avatarUrl?: string
  unreadCount?: number
}) {
  nickname.value = (data.title || 'LinkX').trim() || 'LinkX'
  body.value = (data.body || '').trim()
  avatarUrl.value = (data.avatarUrl || '').trim() || undefined
  unreadCount.value = Math.max(0, Number(data.unreadCount) || 0)
}

function openMain() {
  void window.electronAPI?.openTrayMessage?.()
}

function ignoreAll() {
  void window.electronAPI?.ignoreTrayMessages?.()
}

function onPopupEnter() {
  void window.electronAPI?.setTrayPopupHover?.(true)
}

function onPopupLeave() {
  void window.electronAPI?.setTrayPopupHover?.(false)
}

function avatarText(name: string) {
  return name?.charAt(0) || 'L'
}

document.documentElement.classList.add('lx-tray-popup')

onMounted(() => {
  document.documentElement.classList.add('lx-tray-popup')
  unsubData =
    window.electronAPI?.onTrayMessageData?.(data => {
      applyPayload(data || {})
    }) ?? null
  void window.electronAPI?.getTrayMessagePayload?.().then(data => {
    if (data) applyPayload(data)
  })
})

onBeforeUnmount(() => {
  document.documentElement.classList.remove('lx-tray-popup')
  unsubData?.()
  unsubData = null
})
</script>

<template>
  <div
    class="tray-popup-shell"
    @mouseenter="onPopupEnter"
    @mouseleave="onPopupLeave"
  >
    <div class="tray-popup-card">
      <div class="tray-popup-card__row" @click="openMain">
        <Avatar
          :text="avatarText(nickname)"
          :image-url="avatarUrl"
          color="var(--lx-accent)"
          :size="36"
        />
        <div class="tray-popup-card__main">
          <div class="tray-popup-card__name-row">
            <span class="tray-popup-card__name">{{ nickname }}</span>
            <span v-if="unreadCount > 0" class="tray-popup-card__badge">{{
              unreadCount > 99 ? '99+' : unreadCount
            }}</span>
          </div>
          <p class="tray-popup-card__body">{{ body || t('notifications.newMessageGeneric') }}</p>
        </div>
      </div>
      <div class="tray-popup-card__foot">
        <button type="button" class="tray-popup-card__link" @click.stop="ignoreAll">
          {{ t('trayMessage.ignoreAll') }}
        </button>
        <button type="button" class="tray-popup-card__link" @click.stop="openMain">
          {{ t('trayMessage.viewAll') }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tray-popup-shell {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: 8px;
  background: transparent;
}

.tray-popup-card {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: 14px 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
  background: #f9f9f9;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16);
  font-family: 'Segoe UI Variable', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.tray-popup-card__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
  flex: 1;
  min-height: 0;
}

.tray-popup-card__main {
  flex: 1;
  min-width: 0;
}

.tray-popup-card__name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tray-popup-card__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tray-popup-card__badge {
  flex-shrink: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--lx-danger-hover);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}

.tray-popup-card__body {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--lx-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tray-popup-card__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  padding-top: 8px;
}

.tray-popup-card__link {
  border: none;
  background: transparent;
  color: var(--lx-accent-deep);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}
</style>

<style>
html.lx-tray-popup,
html.lx-tray-popup body,
html.lx-tray-popup.lx-electron-win32,
html.lx-tray-popup.lx-electron-win32 body,
html.lx-tray-popup.lx-electron-win32:not(.lx-native-frame),
html.lx-tray-popup.lx-electron-win32:not(.lx-native-frame) body {
  background: transparent !important;
}
</style>
