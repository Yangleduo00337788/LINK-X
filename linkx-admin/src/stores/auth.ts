import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { clearTokens, ensureApiSignKey, setSessionTokens } from '@/api/request'
import type { AdminLoginResult, AdminMenuTree, AdminUserProfile } from '@/types/api'
import { tGlobal } from '@/i18n'
import { useSecurityStore } from '@/stores/security'
import { isSessionActive, setSessionActive } from '@/utils/sessionState'

function rollbackSession(security = useSecurityStore()) {
  clearTokens()
  security.resetSession()
  setSessionActive(false)
}

export const useAuthStore = defineStore(
  'auth',
  () => {
    const user = ref<AdminUserProfile | null>(null)
    const menus = ref<AdminMenuTree[]>([])
    const permissions = ref<string[]>([])

    const isLoggedIn = computed(() => isSessionActive())

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
      if (!data.user) {
        throw new Error('incomplete login result')
      }
      const security = useSecurityStore()
      setSessionActive(true)
      setSessionTokens(data.accessToken, data.refreshToken)
      user.value = data.user
      permissions.value = [...(data.user.permissions || [])]
      security.setApiSignKey(data.apiSignKey)
      try {
        await fetchMenusAndPermissions()
      } catch (e) {
        rollbackSession(security)
        user.value = null
        menus.value = []
        permissions.value = []
        throw e
      }
    }

    /** 密码登录；若返回 challenge 则不写 token，由调用方进入 2FA 步骤 */
    async function login(payload: authApi.LoginPayload): Promise<AdminLoginResult> {
      const data = await authApi.login(payload)
      if (data.requiresTotp || data.requiresTotpSetup) {
        return data
      }
      if (data.user) {
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
      await ensureApiSignKey()
      const me = await authApi.fetchMe()
      user.value = me
      permissions.value = [...(me.permissions || [])]
      setSessionActive(true)
      await fetchMenusAndPermissions()
    }

    /** 页面刷新后凭 HttpOnly Cookie 恢复会话 */
    async function restoreSession(): Promise<boolean> {
      if (isSessionActive() && user.value && menus.value.length > 0) {
        return true
      }
      try {
        await fetchProfile()
        return true
      } catch {
        rollbackSession()
        user.value = null
        menus.value = []
        permissions.value = []
        return false
      }
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
        await authApi.logout()
      } catch {
        clearTokens()
      }
      security.resetSession()
      setSessionActive(false)
      user.value = null
      menus.value = []
      permissions.value = []
    }

    function resetLocalSession() {
      rollbackSession()
      user.value = null
      menus.value = []
      permissions.value = []
    }

    return {
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
      restoreSession,
      fetchMenusAndPermissions,
      resetLocalSession,
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
