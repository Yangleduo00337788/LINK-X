<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NLayout,
  NLayoutSider,
  NLayoutHeader,
  NLayoutContent,
  NMenu,
  NButton,
  NAvatar,
  NDropdown,
  NIcon,
  NSpace,
  NText,
  NWatermark,
  type MenuOption,
} from 'naive-ui'
import { LogOutOutline, PersonCircleOutline, PersonOutline, MenuOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { usePreferencesStore } from '@/stores/preferences'
import { resolveMenuIcon } from '@/utils/icons'
import { resolveAvatarSrc } from '@/utils/mediaUrl'
import { resolveMenuLabel } from '@/utils/menuI18n'
import PrefSwitcher from '@/components/PrefSwitcher.vue'
import PageHeaderBar from '@/components/PageHeaderBar.vue'
import AdminFloatActions from '@/components/AdminFloatActions.vue'
import type { AdminMenuTree } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const prefs = usePreferencesStore()
const { watermarkEnabled, watermarkFullscreen, watermarkLines, watermarkFontColor } =
  storeToRefs(prefs)
const { t, locale } = useI18n()
const collapsed = ref(false)

const headerAvatarSrc = computed(() =>
  resolveAvatarSrc(auth.user?.avatar, auth.user?.id, true),
)
const headerAvatarBroken = ref(false)
watch(headerAvatarSrc, () => {
  headerAvatarBroken.value = false
})
function onHeaderAvatarError() {
  headerAvatarBroken.value = true
}

function toMenuOptions(menus: AdminMenuTree[]): MenuOption[] {
  return menus
    .filter(
      (m) =>
        m.visible !== false &&
        m.type !== 'button' &&
        m.name !== 'versions' &&
        m.path !== '/admin/versions',
    )
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map((m) => {
      const children = m.children?.length ? toMenuOptions(m.children) : undefined
      const hasChildren = children && children.length > 0
      return {
        label: resolveMenuLabel(t, m),
        key: m.path || String(m.id),
        icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon(m.icon)) }),
        children: hasChildren ? children : undefined,
      }
    })
}

const menuOptions = computed(() => {
  void locale.value
  if (auth.menus.length) return toMenuOptions(auth.menus)
  return [
    { label: t('route.dashboard'), key: '/admin/dashboard', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Dashboard')) }) },
    { label: t('route.users'), key: '/admin/users', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Users')) }) },
    { label: t('route.roles'), key: '/admin/roles', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Badge')) }) },
    { label: t('route.permissions'), key: '/admin/permissions', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Key')) }) },
    { label: t('route.menus'), key: '/admin/menus', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Menu')) }) },
    { label: t('route.auditLogs'), key: '/admin/audit-logs', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('History')) }) },
    { label: t('route.loginLogs'), key: '/admin/login-logs', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('LogIn')) }) },
    { label: t('route.feedback'), key: '/admin/feedback', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Message')) }) },
    { label: t('route.reviews'), key: '/admin/reviews', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Document')) }) },
    { label: t('route.sensitiveWords'), key: '/admin/sensitive-words', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Key')) }) },
    { label: t('route.settings'), key: '/admin/settings', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Settings')) }) },
  ] as MenuOption[]
})

const activeKey = computed(() => {
  const path = route.path
  if (path.startsWith('/admin/users/')) return '/admin/users'
  return path
})

const watermarkContent = computed(() => {
  void locale.value
  if (watermarkLines.value.length) {
    return watermarkLines.value.join('\n')
  }
  const name = auth.displayName || auth.user?.username || t('common.admin')
  return [t('app.brandAdmin'), name, new Date().toLocaleDateString()].join('\n')
})

const showFullscreenWm = computed(() => watermarkEnabled.value && watermarkFullscreen.value)
const showContentWm = computed(() => watermarkEnabled.value && !watermarkFullscreen.value)

function onMenuUpdate(key: string) {
  if (key && key !== route.path) router.push(key)
}

const userOptions = computed(() => {
  void locale.value
  return [
    {
      label: t('layout.profile'),
      key: 'profile',
      icon: () => h(NIcon, null, { default: () => h(PersonOutline) }),
    },
    {
      label: t('layout.logout'),
      key: 'logout',
      icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }),
    },
  ]
})

async function onUserSelect(key: string) {
  if (key === 'profile') {
    router.push('/admin/profile')
    return
  }
  if (key === 'logout') {
    await auth.logout()
    router.push('/login')
  }
}

onMounted(() => {
  // 刷新侧边栏菜单，避免本地缓存仍显示已下线的「版本管理」等项
  void auth.fetchMenusAndPermissions()
})
</script>

<template>
  <div class="wm-host">
    <NLayout has-sider style="height: 100vh">
      <NLayoutSider
        bordered
        collapse-mode="width"
        :collapsed-width="64"
        :width="232"
        :collapsed="collapsed"
        show-trigger
        @collapse="collapsed = true"
        @expand="collapsed = false"
      >
        <div class="brand">
          <NIcon size="22" :component="MenuOutline" />
          <span v-show="!collapsed" class="brand-text">{{ t('app.brandAdmin') }}</span>
        </div>
        <NMenu
          :collapsed="collapsed"
          :collapsed-width="64"
          :collapsed-icon-size="20"
          :options="menuOptions"
          :value="activeKey"
          @update:value="onMenuUpdate"
        />
      </NLayoutSider>
      <NLayout>
        <NLayoutHeader bordered class="header">
          <NText depth="3">{{ t('app.brandAdmin') }}</NText>
          <NSpace align="center" :size="12">
            <PrefSwitcher compact />
            <NDropdown :options="userOptions" @select="onUserSelect">
              <NButton quaternary class="lx-float-btn">
                <NSpace align="center" :size="8">
                  <span class="header-avatar">
                    <img
                      v-if="headerAvatarSrc && !headerAvatarBroken"
                      :key="headerAvatarSrc"
                      class="header-avatar-img"
                      :src="headerAvatarSrc"
                      alt=""
                      referrerpolicy="no-referrer"
                      @error="onHeaderAvatarError"
                    />
                    <NAvatar v-else round size="small">
                      <NIcon :component="PersonCircleOutline" />
                    </NAvatar>
                  </span>
                  <span>{{ auth.displayName }}</span>
                </NSpace>
              </NButton>
            </NDropdown>
          </NSpace>
        </NLayoutHeader>
        <NLayoutContent
          class="main-content"
          content-style="padding: 20px; min-height: calc(100vh - 56px);"
          native-scrollbar
        >
          <div class="content-inner">
            <PageHeaderBar />
            <router-view />
            <!-- 内容区水印：覆盖层，避免切换时重挂载页面 -->
            <NWatermark
              v-if="showContentWm"
              class="wm-content-overlay"
              :content="watermarkContent"
              cross
              selectable
              :font-size="14"
              :line-height="18"
              :width="220"
              :height="140"
              :x-offset="12"
              :y-offset="60"
              :rotate="-18"
              :font-color="watermarkFontColor"
            >
              <div class="wm-fill" />
            </NWatermark>
          </div>
        </NLayoutContent>
      </NLayout>
    </NLayout>
    <!-- 全屏水印：覆盖层，避免切换时重挂载整页 -->
    <NWatermark
      v-if="showFullscreenWm"
      class="wm-fullscreen-overlay"
      :content="watermarkContent"
      cross
      selectable
      :font-size="14"
      :line-height="18"
      :width="220"
      :height="140"
      :x-offset="16"
      :y-offset="80"
      :rotate="-18"
      :z-index="30"
      :font-color="watermarkFontColor"
    >
      <div class="wm-fill" />
    </NWatermark>
    <AdminFloatActions />
  </div>
</template>

<style scoped>
.wm-host {
  height: 100vh;
  position: relative;
}
.wm-fullscreen-overlay {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 30;
}
.wm-content-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 5;
}
.wm-fill {
  width: 100%;
  height: 100%;
  min-height: 100%;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid var(--lx-border);
  color: var(--lx-text);
  font-weight: 600;
  letter-spacing: 0.04em;
}
.brand-text {
  white-space: nowrap;
}
.header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-avatar {
  display: inline-flex;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
}
.header-avatar-img {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}
.main-content {
  background: var(--lx-body) !important;
}
.content-inner {
  position: relative;
  min-height: 100%;
  /* keep table action column clear of collapsed FAB */
  padding-right: 8px;
  padding-bottom: 56px;
}
</style>
