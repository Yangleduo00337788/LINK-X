<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
  type MenuOption,
} from 'naive-ui'
import { LogOutOutline, PersonCircleOutline, PersonOutline, MenuOutline } from '@vicons/ionicons5'
import { useAuthStore } from '@/stores/auth'
import { resolveMenuIcon } from '@/utils/icons'
import { resolveAvatarSrc } from '@/utils/mediaUrl'
import type { AdminMenuTree } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
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
    .filter((m) => m.visible !== false && m.type !== 'button')
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map((m) => {
      const children = m.children?.length ? toMenuOptions(m.children) : undefined
      const hasChildren = children && children.length > 0
      return {
        label: m.title || m.name,
        key: m.path || String(m.id),
        icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon(m.icon)) }),
        children: hasChildren ? children : undefined,
      }
    })
}

const menuOptions = computed(() => {
  if (auth.menus.length) return toMenuOptions(auth.menus)
  return [
    { label: '仪表盘', key: '/admin/dashboard', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Dashboard')) }) },
    { label: '用户管理', key: '/admin/users', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Users')) }) },
    { label: '角色管理', key: '/admin/roles', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Badge')) }) },
    { label: '权限管理', key: '/admin/permissions', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Key')) }) },
    { label: '菜单管理', key: '/admin/menus', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Menu')) }) },
    { label: '操作日志', key: '/admin/audit-logs', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('History')) }) },
    { label: '登录日志', key: '/admin/login-logs', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('LogIn')) }) },
    { label: '反馈管理', key: '/admin/feedback', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Message')) }) },
    { label: '系统配置', key: '/admin/settings', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Settings')) }) },
    { label: '版本管理', key: '/admin/versions', icon: () => h(NIcon, null, { default: () => h(resolveMenuIcon('Cube')) }) },
  ] as MenuOption[]
})

const activeKey = computed(() => {
  const path = route.path
  if (path.startsWith('/admin/users/')) return '/admin/users'
  return path
})

function onMenuUpdate(key: string) {
  if (key && key !== route.path) router.push(key)
}

const userOptions = [
  {
    label: '个人中心',
    key: 'profile',
    icon: () => h(NIcon, null, { default: () => h(PersonOutline) }),
  },
  {
    label: '退出登录',
    key: 'logout',
    icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }),
  },
]

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
</script>

<template>
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
        <span v-show="!collapsed" class="brand-text">LinkX Admin</span>
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
        <NText depth="3">{{ (route.meta.title as string) || '管理后台' }}</NText>
        <NSpace align="center">
          <NDropdown :options="userOptions" @select="onUserSelect">
            <NButton quaternary>
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
      <NLayoutContent content-style="padding: 20px; min-height: calc(100vh - 56px);" native-scrollbar>
        <router-view />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid #2a2f3a;
  color: #e8eaed;
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
</style>
