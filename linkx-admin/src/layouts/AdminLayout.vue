<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NLayout,
  NLayoutSider,
  NLayoutHeader,
  NLayoutContent,
  NLayoutFooter,
  NMenu,
  NButton,
  NAvatar,
  NDropdown,
  NIcon,
  NSpace,
  NWatermark,
  useMessage,
  type MenuOption,
} from 'naive-ui'
import { LogOutOutline, PersonCircleOutline, PersonOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { usePreferencesStore } from '@/stores/preferences'
import { resolveMenuIcon } from '@/utils/icons'
import { resolveAvatarSrc } from '@/utils/mediaUrl'
import { resolveMenuLabel } from '@/utils/menuI18n'
import { onAdminRealtimeEvent, startAdminRealtime, stopAdminRealtime } from '@/api/realtime'
import { notifyAdminEvent } from '@/utils/adminNotify'
import { unlockSpeech } from '@/utils/voiceNotify'
import { usePendingVoiceWatch } from '@/composables/usePendingVoiceWatch'
import AdminHeaderToolbar from '@/components/AdminHeaderToolbar.vue'
import PageHeaderBar from '@/components/PageHeaderBar.vue'
import AdminFloatActions from '@/components/AdminFloatActions.vue'
import AdminNoticeBell from '@/components/AdminNoticeBell.vue'
import AdminBrandLogo from '@/components/AdminBrandLogo.vue'
import type { AdminMenuTree } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const prefs = usePreferencesStore()
const message = useMessage()
const {
  watermarkEnabled,
  watermarkFullscreen,
  watermarkLines,
  watermarkFontColor,
  headerThemeColor,
  headerThemeFull,
  moduleDockEnabled,
  footerEnabled,
  footerText,
  menuAccordion,
  siderCollapsedDefault,
  layoutMode,
  navTheme,
  appearancePreset,
} = storeToRefs(prefs)
const { t, locale } = useI18n()
const collapsed = ref(prefs.siderCollapsedDefault)
const menuWrapRef = ref<HTMLElement | null>(null)
const menuHover = reactive({
  visible: false,
  top: 0,
  height: 0,
})

function syncMenuHover(target: EventTarget | null) {
  const wrap = menuWrapRef.value
  if (!wrap || !(target instanceof Element)) {
    menuHover.visible = false
    return
  }
  const item = target.closest('.n-menu-item-content')
  if (!item || !wrap.contains(item)) {
    menuHover.visible = false
    return
  }
  const wrapRect = wrap.getBoundingClientRect()
  const itemRect = item.getBoundingClientRect()
  menuHover.top = itemRect.top - wrapRect.top + wrap.scrollTop
  menuHover.height = itemRect.height
  menuHover.visible = true
}

function onMenuWrapMouseOver(e: MouseEvent) {
  syncMenuHover(e.target)
}

function onMenuWrapMouseLeave() {
  menuHover.visible = false
}

function onMenuWrapScroll() {
  menuHover.visible = false
}

let offRealtime: (() => void) | null = null

function maybeNotify(evt: Parameters<typeof notifyAdminEvent>[0]) {
  notifyAdminEvent(evt, t, locale.value)
}

usePendingVoiceWatch()

onMounted(() => {
  startAdminRealtime()
  const unlockOnInteract = () => {
    unlockSpeech(locale.value)
    window.removeEventListener('pointerdown', unlockOnInteract)
  }
  window.addEventListener('pointerdown', unlockOnInteract, { passive: true })
  offRealtime = onAdminRealtimeEvent((evt) => {
    if (evt?.type === 'admin_notice_published') {
      const title = typeof evt.title === 'string' ? evt.title : ''
      const content = typeof evt.content === 'string' ? evt.content : ''
      message.info(
        title
          ? t('notice.adminBulletin', { title, content: content || title })
          : t('notice.adminBulletinGeneric'),
        { duration: 6000 }
      )
      maybeNotify(evt)
    } else if (evt?.type === 'admin_notice_unpublished') {
      const title = typeof evt.title === 'string' ? evt.title : ''
      message.warning(
        title
          ? t('notice.adminBulletinRecalled', { title })
          : t('notice.adminBulletinRecalledGeneric'),
        { duration: 5000 }
      )
      maybeNotify(evt)
    } else if (evt?.type === 'export_ready') {
      message.success(t('common.exportReady'), { duration: 4000 })
      maybeNotify(evt)
    } else if (evt?.type === 'export_failed') {
      const err = typeof evt.error === 'string' ? evt.error : ''
      message.error(err || t('common.exportFailed'), { duration: 5000 })
      maybeNotify(evt)
    } else if (evt?.type === 'feedback_escalated') {
      message.warning(t('feedback.escalatedRealtime'), { duration: 5000 })
      maybeNotify(evt)
    } else if (evt?.type === 'feedback_created') {
      message.info(t('feedback.createdRealtime'), { duration: 5000 })
      maybeNotify(evt)
    } else if (evt?.type === 'review_escalated') {
      message.warning(t('review.escalatedRealtime'), { duration: 5000 })
      maybeNotify(evt)
    } else if (evt?.type === 'review_created') {
      message.info(t('review.newArrived', { n: 1 }), { duration: 5000 })
      maybeNotify(evt)
    } else if (evt?.type === 'risk_created') {
      message.info(t('risk.newArrived', { n: 1 }), { duration: 5000 })
      maybeNotify(evt)
    }
  })
  void auth.fetchMenusAndPermissions()
})

onUnmounted(() => {
  offRealtime?.()
  offRealtime = null
  stopAdminRealtime()
})

const headerAvatarSrc = computed(() => resolveAvatarSrc(auth.user?.avatar, auth.user?.id, true))
const headerAvatarBroken = ref(false)
watch(headerAvatarSrc, () => {
  headerAvatarBroken.value = false
})
function onHeaderAvatarError() {
  headerAvatarBroken.value = true
}

function findMenuByPath(menus: AdminMenuTree[], path: string): AdminMenuTree | undefined {
  for (const m of menus) {
    if (m.path === path) return m
    if (m.children?.length) {
      const found = findMenuByPath(m.children, path)
      if (found) return found
    }
  }
  return undefined
}

function collectAncestorKeys(
  menus: AdminMenuTree[],
  path: string,
  ancestors: string[] = []
): string[] | null {
  for (const m of menus) {
    const key = m.path || String(m.id)
    if (m.path === path) return ancestors
    if (m.children?.length) {
      const found = collectAncestorKeys(m.children, path, [...ancestors, key])
      if (found) return found
    }
  }
  return null
}

function menuContainsPath(menu: AdminMenuTree, path: string): boolean {
  if (menu.path === path) return true
  if (menu.path && path.startsWith(`${menu.path}/`)) return true
  return !!menu.children?.some((c) => menuContainsPath(c, path))
}

function findTopMenuForPath(menus: AdminMenuTree[], path: string): AdminMenuTree | undefined {
  const matched = menus.find((m) => menuContainsPath(m, path))
  return matched || menus[0]
}

function menuKey(menu: AdminMenuTree) {
  return menu.path || String(menu.id)
}

function isGroupMenu(menu: AdminMenuTree) {
  return menu.type === 'dir' || !!menu.children?.length
}

const expandedKeys = ref<string[]>([])

function syncExpandedForRoute(path: string) {
  const ancestors = collectAncestorKeys(auth.menus, path)
  if (!ancestors?.length) return
  const merged = new Set([...expandedKeys.value, ...ancestors])
  expandedKeys.value = [...merged]
}

watch(
  () => route.path,
  (path) => syncExpandedForRoute(path),
  { immediate: true }
)

watch(
  () => auth.menus,
  () => syncExpandedForRoute(route.path),
  { deep: true }
)

watch(siderCollapsedDefault, (v) => {
  collapsed.value = v
})

const menuOptions = computed(() => {
  void locale.value
  if (!auth.menus.length) return [] as MenuOption[]
  return toMenuOptions(auth.menus)
})

function toMenuOptions(menus: AdminMenuTree[], flat = false): MenuOption[] {
  return menus
    .filter((m) => m.visible !== false && m.type !== 'button')
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map((m) => {
      const children =
        !flat && m.children?.length ? toMenuOptions(m.children, flat) : undefined
      const hasChildren = children && children.length > 0
      return {
        label: resolveMenuLabel(t, m),
        key: m.path || String(m.id),
        icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon(m.icon)) }),
        children: hasChildren ? children : undefined,
      }
    })
}

/** 顶部布局：一级菜单平铺，有子菜单时保留 children 供下拉 */
const topMenuOptions = computed(() => {
  void locale.value
  if (!auth.menus.length) return [] as MenuOption[]
  return toMenuOptions(auth.menus)
})

/** 混合布局：顶栏仅一级 */
const mixTopOptions = computed(() => {
  void locale.value
  return auth.menus
    .filter((m) => m.visible !== false && m.type !== 'button')
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map((m) => ({
      label: resolveMenuLabel(t, m),
      key: menuKey(m),
      icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon(m.icon)) }),
    }))
})

const activeTopMenu = computed(() => findTopMenuForPath(auth.menus, route.path))

const activeTopKey = computed(() => {
  const top = activeTopMenu.value
  return top ? menuKey(top) : undefined
})

/** 混合侧栏：有子菜单显示子级；叶子一级菜单则显示自身 */
const mixSideMenus = computed(() => {
  void locale.value
  const top = activeTopMenu.value
  if (!top) return [] as MenuOption[]
  if (top.children?.length) return toMenuOptions(top.children)
  return [
    {
      label: resolveMenuLabel(t, top),
      key: menuKey(top),
      icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon(top.icon)) }),
    },
  ]
})

function onExpandedKeysUpdate(keys: string[]) {
  if (!menuAccordion.value) {
    expandedKeys.value = keys
    return
  }
  const topKeys = new Set(menuOptions.value.map((o) => String(o.key)))
  const added = keys.filter((k) => !expandedKeys.value.includes(k))
  if (!added.length) {
    expandedKeys.value = keys
    return
  }
  const latest = added[added.length - 1]
  if (topKeys.has(latest)) {
    expandedKeys.value = keys.filter((k) => !topKeys.has(k) || k === latest)
    return
  }
  expandedKeys.value = keys
}

const headerClass = computed(() => ({
  'header--primary': headerThemeColor.value,
  'header--primary-full': headerThemeFull.value,
  'header--dark-nav': navTheme.value === 'dark' && layoutMode.value !== 'side',
}))

const siderClass = computed(() => ({
  'admin-sider': true,
  'admin-sider--dark': navTheme.value === 'dark',
  'admin-sider--light': navTheme.value === 'light',
}))

const menuInverted = computed(() => navTheme.value === 'dark')

const activeKey = computed(() => {
  const path = route.path
  if (path.startsWith('/admin/users/')) return '/admin/users'
  return path
})

const showPageHeader = computed(() => route.meta.hidePageHeader !== true)

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

const resolvedFooterText = computed(() => {
  const custom = footerText.value?.trim()
  return custom || t('themeConfig.footerText')
})

const contentMinHeight = computed(() => {
  const headerH = layoutMode.value === 'top' || layoutMode.value === 'mix' ? 56 : 48
  const footerH = footerEnabled.value ? 40 : 0
  return `calc(100vh - ${headerH}px - ${footerH}px)`
})

function firstLeafPath(menu: AdminMenuTree): string | null {
  if (isGroupMenu(menu) && menu.children?.length) {
    for (const c of menu.children) {
      const p = firstLeafPath(c)
      if (p) return p
    }
    return null
  }
  return menu.path || null
}

function onMenuUpdate(key: string) {
  const menu = findMenuByPath(auth.menus, key)
  if (!menu) {
    if (key && key !== route.path) router.push(key)
    return
  }
  if (isGroupMenu(menu)) {
    const leaf = firstLeafPath(menu)
    if (leaf && leaf !== route.path) router.push(leaf)
    return
  }
  if (key && key !== route.path) router.push(key)
}

function onMixTopUpdate(key: string) {
  const top = auth.menus.find((m) => menuKey(m) === key)
  if (!top) return
  const leaf = firstLeafPath(top)
  if (leaf && leaf !== route.path) {
    router.push(leaf)
    return
  }
  // 一级叶子菜单（如工作台）直接跳转自身 path
  if (top.path && top.path !== route.path) router.push(top.path)
}

/** 切到混合布局时，若当前是无子菜单的一级页，跳到第一个有子菜单的分组，便于立刻看到侧栏 */
watch(layoutMode, (mode) => {
  if (mode !== 'mix' || !auth.menus.length) return
  const top = findTopMenuForPath(auth.menus, route.path)
  if (top?.children?.length) return
  const grouped = auth.menus.find(
    (m) => m.visible !== false && m.type !== 'button' && !!m.children?.length
  )
  if (!grouped) return
  const leaf = firstLeafPath(grouped)
  if (leaf && leaf !== route.path) router.push(leaf)
})

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
    stopAdminRealtime()
    await auth.logout()
    router.push('/login')
  }
}
</script>

<template>
  <div class="wm-host" :data-layout="layoutMode" :data-appearance="appearancePreset">
    <!-- 侧边布局 -->
    <NLayout v-if="layoutMode === 'side'" has-sider style="height: 100vh">
      <NLayoutSider
        bordered
        collapse-mode="width"
        :collapsed-width="64"
        :width="220"
        :collapsed="collapsed"
        show-trigger
        :class="siderClass"
        @collapse="collapsed = true"
        @expand="collapsed = false"
      >
        <div class="brand">
          <AdminBrandLogo size="sider" :collapsed="collapsed" />
        </div>
        <div
          ref="menuWrapRef"
          class="sider-menu-wrap"
          @mouseover="onMenuWrapMouseOver"
          @mouseleave="onMenuWrapMouseLeave"
          @scroll="onMenuWrapScroll"
        >
          <div
            class="sider-menu-hover"
            :class="{ visible: menuHover.visible && !collapsed }"
            :style="{ top: `${menuHover.top}px`, height: `${menuHover.height}px` }"
          />
          <NMenu
            :collapsed="collapsed"
            :collapsed-width="64"
            :collapsed-icon-size="18"
            :options="menuOptions"
            :value="activeKey"
            :expanded-keys="expandedKeys"
            :inverted="menuInverted"
            @update:expanded-keys="onExpandedKeysUpdate"
            @update:value="onMenuUpdate"
          />
        </div>
      </NLayoutSider>
      <NLayout>
        <NLayoutHeader bordered class="header" :class="headerClass">
          <div class="header-left">
            <span class="header-title">{{ t('app.brandAdmin') }}</span>
          </div>
          <div class="header-actions">
            <AdminNoticeBell />
            <AdminHeaderToolbar />
            <NDropdown :options="userOptions" @select="onUserSelect">
              <NButton quaternary class="lx-float-btn header-user-btn">
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
          </div>
        </NLayoutHeader>
        <NLayoutContent
          class="main-content"
          :content-style="`padding: 12px 16px 20px; min-height: ${contentMinHeight};`"
          native-scrollbar
        >
          <div class="content-inner">
            <PageHeaderBar v-if="showPageHeader" />
            <router-view />
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
        <NLayoutFooter v-if="footerEnabled" bordered class="admin-footer">
          {{ resolvedFooterText }}
        </NLayoutFooter>
      </NLayout>
    </NLayout>

    <!-- 顶部布局 -->
    <NLayout v-else-if="layoutMode === 'top'" style="height: 100vh">
      <NLayoutHeader bordered class="header header--top" :class="headerClass">
        <div class="header-brand-inline">
          <AdminBrandLogo size="sider" :collapsed="true" />
          <span class="header-title">{{ t('app.brandAdmin') }}</span>
        </div>
        <div class="header-menu-wrap">
          <NMenu
            mode="horizontal"
            :options="topMenuOptions"
            :value="activeKey"
            :inverted="menuInverted || headerThemeColor"
            responsive
            @update:value="onMenuUpdate"
          />
        </div>
        <div class="header-actions">
          <AdminNoticeBell />
          <AdminHeaderToolbar />
          <NDropdown :options="userOptions" @select="onUserSelect">
            <NButton quaternary class="lx-float-btn header-user-btn">
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
        </div>
      </NLayoutHeader>
      <NLayoutContent
        class="main-content"
        :content-style="`padding: 12px 16px 20px; min-height: ${contentMinHeight};`"
        native-scrollbar
      >
        <div class="content-inner">
          <PageHeaderBar v-if="showPageHeader" />
          <router-view />
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
      <NLayoutFooter v-if="footerEnabled" bordered class="admin-footer">
        {{ resolvedFooterText }}
      </NLayoutFooter>
    </NLayout>

    <!-- 混合布局：顶栏一级 + 左侧二级 -->
    <NLayout v-else-if="layoutMode === 'mix'" class="layout-mix-root" style="height: 100vh">
      <NLayoutHeader bordered class="header header--top" :class="headerClass">
        <div class="header-brand-inline">
          <AdminBrandLogo size="sider" :collapsed="true" />
          <span class="header-title">{{ t('app.brandAdmin') }}</span>
        </div>
        <div class="header-menu-wrap">
          <NMenu
            mode="horizontal"
            :options="mixTopOptions"
            :value="activeTopKey"
            :inverted="menuInverted || headerThemeColor"
            responsive
            @update:value="onMixTopUpdate"
          />
        </div>
        <div class="header-actions">
          <AdminNoticeBell />
          <AdminHeaderToolbar />
          <NDropdown :options="userOptions" @select="onUserSelect">
            <NButton quaternary class="lx-float-btn header-user-btn">
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
        </div>
      </NLayoutHeader>
      <NLayout has-sider class="layout-mix-body" position="absolute" style="top: 56px; bottom: 0">
        <NLayoutSider
          bordered
          collapse-mode="width"
          :collapsed-width="64"
          :width="200"
          :collapsed="collapsed"
          show-trigger
          :class="siderClass"
          @collapse="collapsed = true"
          @expand="collapsed = false"
        >
          <div
            ref="menuWrapRef"
            class="sider-menu-wrap sider-menu-wrap--mix"
            @mouseover="onMenuWrapMouseOver"
            @mouseleave="onMenuWrapMouseLeave"
            @scroll="onMenuWrapScroll"
          >
            <div
              class="sider-menu-hover"
              :class="{ visible: menuHover.visible && !collapsed }"
              :style="{ top: `${menuHover.top}px`, height: `${menuHover.height}px` }"
            />
            <NMenu
              :collapsed="collapsed"
              :collapsed-width="64"
              :collapsed-icon-size="18"
              :options="mixSideMenus"
              :value="activeKey"
              :expanded-keys="expandedKeys"
              :inverted="menuInverted"
              @update:expanded-keys="onExpandedKeysUpdate"
              @update:value="onMenuUpdate"
            />
          </div>
        </NLayoutSider>
        <NLayout>
          <NLayoutContent
            class="main-content"
            :content-style="`padding: 12px 16px 20px; min-height: ${contentMinHeight};`"
            native-scrollbar
          >
            <div class="content-inner">
              <PageHeaderBar v-if="showPageHeader" />
              <router-view />
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
          <NLayoutFooter v-if="footerEnabled" bordered class="admin-footer">
            {{ resolvedFooterText }}
          </NLayoutFooter>
        </NLayout>
      </NLayout>
    </NLayout>

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
    <AdminFloatActions v-if="moduleDockEnabled" />
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
  justify-content: center;
  height: 64px;
  padding: 0 14px;
  border-bottom: 1px solid var(--lx-border);
  background: var(--lx-sider-bg, var(--lx-brand-bg));
  overflow: hidden;
}

.sider-menu-wrap {
  position: relative;
  padding: 8px 0 12px;
  height: calc(100vh - 64px);
  overflow: auto;
  background: var(--lx-sider-bg, var(--lx-brand-bg));
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.sider-menu-wrap--mix {
  height: 100%;
  min-height: 100%;
}

.layout-mix-root {
  position: relative;
}

.layout-mix-body {
  left: 0;
  right: 0;
}

.sider-menu-wrap::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.sider-menu-hover {
  position: absolute;
  left: 8px;
  right: 8px;
  border-radius: var(--lx-radius);
  background: color-mix(in srgb, var(--lx-oa-blue) 10%, transparent);
  pointer-events: none;
  z-index: 0;
  opacity: 0;
  transition:
    top 0.22s cubic-bezier(0.22, 1, 0.36, 1),
    height 0.22s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.15s ease;
}

.sider-menu-hover.visible {
  opacity: 1;
}

.admin-sider :deep(.n-menu) {
  position: relative;
  z-index: 1;
}

.admin-sider :deep(.n-menu-item-content:hover) {
  background-color: transparent !important;
}

.admin-sider:not(.admin-sider--dark) :deep(.n-menu-item-content--selected),
.admin-sider:not(.admin-sider--dark) :deep(.n-menu-item-content--child-active) {
  background-color: color-mix(in srgb, var(--lx-oa-blue) 8%, transparent) !important;
}

.admin-sider {
  box-shadow: 1px 0 0 var(--lx-border);
  --lx-sider-bg: var(--lx-brand-bg);
}

.admin-sider :deep(.n-layout-sider-scroll-container) {
  background: var(--lx-sider-bg) !important;
  overflow: hidden;
}

.admin-sider--dark {
  --lx-sider-bg: #001529;
  --lx-border: rgba(255, 255, 255, 0.08);
}

.admin-sider--dark .brand,
.admin-sider--dark .sider-menu-wrap {
  background: #001529;
  border-color: rgba(255, 255, 255, 0.08);
}

.admin-sider--light {
  --lx-sider-bg: #ffffff;
}

.header {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--lx-card);
  border-bottom: 1px solid var(--lx-border);
  box-shadow: none;
  gap: 12px;
}

.header--top {
  height: 56px;
  padding: 0 16px;
}

.header-brand-inline {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.header-menu-wrap {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.header-menu-wrap :deep(.n-menu) {
  background: transparent;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-2);
  white-space: nowrap;
}

.header.header--primary {
  background: var(--lx-oa-blue) !important;
  border-bottom-color: color-mix(in srgb, var(--lx-oa-blue) 80%, #000) !important;
}

.header.header--dark-nav:not(.header--primary) {
  background: #001529 !important;
  border-bottom-color: rgba(255, 255, 255, 0.08) !important;
}

.header.header--dark-nav:not(.header--primary) .header-title,
.header.header--dark-nav:not(.header--primary) :deep(.n-button),
.header.header--primary .header-title,
.header.header--primary :deep(.n-button),
.header.header--primary :deep(.n-button .n-button__content) {
  color: rgba(255, 255, 255, 0.92);
}

.header.header--primary :deep(.n-button:hover),
.header.header--dark-nav:not(.header--primary) :deep(.n-button:hover) {
  color: #fff;
  background: rgba(255, 255, 255, 0.12) !important;
}

.header.header--primary-full {
  box-shadow: inset 0 -1px 0 rgba(0, 0, 0, 0.08);
}

.admin-footer {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  font-size: 12px;
  color: var(--lx-text-3);
  background: var(--lx-card);
}

.header :deep(.n-button) {
  height: 32px;
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
  padding-right: 8px;
  padding-bottom: 56px;
}
</style>
