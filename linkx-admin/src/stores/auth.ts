import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { clearTokens, getAccessToken, getRefreshToken } from '@/api/request'
import type { AdminMenuTree, AdminUserProfile } from '@/types/api'
import { tGlobal } from '@/i18n'

export const useAuthStore = defineStore(
  'auth',
  () => {
    const accessToken = ref(getAccessToken() || '')
    const refreshToken = ref(getRefreshToken() || '')
    const user = ref<AdminUserProfile | null>(null)
    const menus = ref<AdminMenuTree[]>([])
    const permissions = ref<string[]>([])

    const isLoggedIn = computed(() => !!accessToken.value)
    const displayName = computed(
      () => user.value?.nickname || user.value?.username || tGlobal('common.admin'),
    )

    function hasPermission(code?: string | string[]) {
      if (!code) return true
      const list = permissions.value
      if (list.includes('*')) return true
      const codes = Array.isArray(code) ? code : [code]
      return codes.some((c) => list.includes(c))
    }

    async function login(payload: authApi.LoginPayload) {
      const data = await authApi.login(payload)
      accessToken.value = data.accessToken
      refreshToken.value = data.refreshToken
      user.value = data.user
      permissions.value = [...(data.user.permissions || [])]
      await fetchMenusAndPermissions()
    }

    async function fetchProfile() {
      if (!getAccessToken()) return
      const me = await authApi.fetchMe()
      user.value = me
      permissions.value = [...(me.permissions || [])]
      accessToken.value = getAccessToken() || ''
      refreshToken.value = getRefreshToken() || ''
      await fetchMenusAndPermissions()
    }

    async function fetchMenusAndPermissions() {
      const [menuTree, perms] = await Promise.all([
        authApi.fetchMenus(),
        authApi.fetchPermissions(),
      ])
      menus.value = menuTree || []
      if (perms?.length) permissions.value = [...perms]
    }

    async function logout() {
      try {
        await authApi.logout(refreshToken.value || undefined)
      } catch {
        clearTokens()
      }
      accessToken.value = ''
      refreshToken.value = ''
      user.value = null
      menus.value = []
      permissions.value = []
    }

    function syncTokensFromStorage() {
      accessToken.value = getAccessToken() || ''
      refreshToken.value = getRefreshToken() || ''
    }

    return {
      accessToken,
      refreshToken,
      user,
      menus,
      permissions,
      isLoggedIn,
      displayName,
      hasPermission,
      login,
      logout,
      fetchProfile,
      fetchMenusAndPermissions,
      syncTokensFromStorage,
    }
  },
  {
    persist: {
      key: 'linkx-admin-auth',
      paths: ['user', 'menus', 'permissions'],
    },
  },
)
