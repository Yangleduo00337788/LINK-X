import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { clearTokens, getAccessToken, getRefreshToken, setTokens, ensureApiSignKey } from '@/api/request'
import type { AdminLoginResult, AdminMenuTree, AdminUserProfile } from '@/types/api'
import { tGlobal } from '@/i18n'
import { useSecurityStore } from '@/stores/security'

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
      () => user.value?.nickname || user.value?.username || tGlobal('common.admin')
    )

    function hasPermission(code?: string | string[]) {
      if (!code) return true
      const list = permissions.value
      if (list.includes('*')) return true
      const codes = Array.isArray(code) ? code : [code]
      return codes.some((c) => list.includes(c))
    }

    async function applyLoginResult(data: AdminLoginResult) {
      if (!data.accessToken || !data.refreshToken || !data.user) {
        throw new Error('incomplete login result')
      }
      setTokens(data.accessToken, data.refreshToken)
      accessToken.value = data.accessToken
      refreshToken.value = data.refreshToken
      user.value = data.user
      permissions.value = [...(data.user.permissions || [])]
      const security = useSecurityStore()
      security.setApiSignKey(data.apiSignKey)
      await fetchMenusAndPermissions()
    }

    /** 密码登录；若返回 challenge 则不写 token，由调用方进入 2FA 步骤 */
    async function login(payload: authApi.LoginPayload): Promise<AdminLoginResult> {
      const data = await authApi.login(payload)
      if (data.requiresTotp || data.requiresTotpSetup) {
        return data
      }
      if (data.accessToken && data.refreshToken) {
        await applyLoginResult(data)
      }
      return data
    }

    async function completeTotpLogin(challengeToken: string, code: string) {
      const data = await authApi.verifyTotpLogin(challengeToken, code)
      await applyLoginResult(data)
      return data
    }

    async function completeTotpSetup(challengeToken: string, code: string) {
      const data = await authApi.confirmTotpChallenge(challengeToken, code)
      await applyLoginResult(data)
      return data
    }

    async function fetchProfile() {
      if (!getAccessToken()) return
      await ensureApiSignKey()
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
      const security = useSecurityStore()
      try {
        await authApi.logout(refreshToken.value || undefined)
      } catch {
        clearTokens()
      }
      security.resetSession()
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
      completeTotpLogin,
      completeTotpSetup,
      logout,
      fetchProfile,
      fetchMenusAndPermissions,
      syncTokensFromStorage,
    }
  },
  {
    persist: {
      // 权限不落地 localStorage，避免迁移后仍用旧 permissions 渲染按钮/路由
      key: 'linkx-admin-auth-v2',
      paths: ['user'],
    },
  }
)
