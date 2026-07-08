<script setup lang="ts">
import { computed } from 'vue'
import { NDrawer, NDrawerContent, NList, NListItem, NThing, NSwitch, NIcon } from 'naive-ui'
import {
  SettingsOutline,
  NotificationsOutline,
  ShieldCheckmarkOutline,
  HelpCircleOutline,
  InformationCircleOutline,
  PersonOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useSecondaryViewStore } from '../stores/secondaryView'
import { useAppStore } from '../stores/app'
import { useOverlayStore } from '../stores/overlay'
import type { OverlayPage } from '../types'

const secondaryViewStore = useSecondaryViewStore()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const { menuOpen } = storeToRefs(secondaryViewStore)
const { theme } = storeToRefs(appStore)
const { toggleTheme } = appStore
const { open: openOverlay } = overlayStore

const darkMode = computed({
  get: () => theme.value === 'dark',
  set: () => toggleTheme()
})

function go(page: OverlayPage) {
  menuOpen.value = false
  openOverlay(page)
}
</script>

<template>
  <n-drawer v-model:show="menuOpen" :width="280" placement="left">
    <n-drawer-content title="菜单" closable>
      <n-list hoverable clickable>
        <n-list-item @click="go('profile')">
          <n-thing>
            <template #avatar>
              <n-icon :component="PersonOutline" :size="22" />
            </template>
            <template #header>个人资料</template>
            <template #description>昵称、签名</template>
          </n-thing>
        </n-list-item>
        <n-list-item @click="go('settings')">
          <n-thing>
            <template #avatar>
              <n-icon :component="SettingsOutline" :size="22" />
            </template>
            <template #header>设置</template>
            <template #description>账号、通用、快捷键</template>
          </n-thing>
        </n-list-item>
        <n-list-item @click="go('notifications')">
          <n-thing>
            <template #avatar>
              <n-icon :component="NotificationsOutline" :size="22" />
            </template>
            <template #header>消息通知</template>
          </n-thing>
        </n-list-item>
        <n-list-item @click="go('privacy')">
          <n-thing>
            <template #avatar>
              <n-icon :component="ShieldCheckmarkOutline" :size="22" />
            </template>
            <template #header>隐私与安全</template>
          </n-thing>
        </n-list-item>
        <n-list-item>
          <n-thing>
            <template #header>深色模式</template>
            <template #description>切换界面主题</template>
            <template #footer>
              <n-switch v-model:value="darkMode" />
            </template>
          </n-thing>
        </n-list-item>
        <n-list-item @click="go('help')">
          <n-thing>
            <template #avatar>
              <n-icon :component="HelpCircleOutline" :size="22" />
            </template>
            <template #header>帮助与反馈</template>
          </n-thing>
        </n-list-item>
        <n-list-item @click="go('about')">
          <n-thing>
            <template #avatar>
              <n-icon :component="InformationCircleOutline" :size="22" />
            </template>
            <template #header>关于</template>
            <template #description>LinkX v1.0.0</template>
          </n-thing>
        </n-list-item>
      </n-list>
    </n-drawer-content>
  </n-drawer>
</template>