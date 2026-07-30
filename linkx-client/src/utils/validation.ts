const USERNAME_PATTERN = /^[a-zA-Z0-9_]+$/

export interface PasswordPolicy {
  minLength?: number
  maxLength?: number
  requireUpperLower?: boolean
  requireDigit?: boolean
  requireSpecial?: boolean
}

const DEFAULT_POLICY: Required<PasswordPolicy> = {
  minLength: 8,
  maxLength: 64,
  requireUpperLower: false,
  requireDigit: true,
  requireSpecial: false,
}

export function normalizePasswordPolicy(policy?: PasswordPolicy | null): Required<PasswordPolicy> {
  return {
    minLength: policy?.minLength ?? DEFAULT_POLICY.minLength,
    maxLength: policy?.maxLength ?? DEFAULT_POLICY.maxLength,
    requireUpperLower: policy?.requireUpperLower === true,
    requireDigit: policy?.requireDigit !== false,
    requireSpecial: policy?.requireSpecial === true,
  }
}

export function validateUsername(username: string): string | null {
  const value = username.trim()
  if (!value) return '请输入用户名'
  if (value.length < 4 || value.length > 32) return '用户名长度为 4-32 个字符'
  if (!USERNAME_PATTERN.test(value)) return '用户名只能包含字母、数字和下划线'
  return null
}

/**
 * @param forSetPassword 为 true（或传入策略）时按密码策略校验；登录场景只要求非空
 */
export function validatePassword(
  password: string,
  forSetPassword: boolean | PasswordPolicy = false,
): string | null {
  const value = password.trim()
  if (!value) return '请输入密码'

  const isSet =
    forSetPassword === true || (typeof forSetPassword === 'object' && forSetPassword !== null)
  if (!isSet) {
    // 登录：不按策略卡长度，避免历史短密码无法登录
    if (value.length > 128) return '密码过长'
    return null
  }

  const policy = normalizePasswordPolicy(
    typeof forSetPassword === 'object' ? forSetPassword : undefined,
  )
  if (value.length < policy.minLength || value.length > policy.maxLength) {
    return `密码长度为 ${policy.minLength}-${policy.maxLength} 个字符`
  }
  if (policy.requireUpperLower) {
    if (!/[A-Z]/.test(value) || !/[a-z]/.test(value)) {
      return '密码须同时包含大写和小写字母'
    }
  }
  if (policy.requireDigit && !/\d/.test(value)) {
    return '密码须包含数字'
  }
  if (policy.requireSpecial && /^[A-Za-z0-9]+$/.test(value)) {
    return '密码须包含特殊字符'
  }
  return null
}

export function validateNickname(nickname: string): string | null {
  const value = nickname.trim()
  if (!value) return '请输入昵称'
  if (value.length > 64) return '昵称长度为 1-64 个字符'
  return null
}

export function validateLockPin(pin: string): string | null {
  if (!/^\d{4,6}$/.test(pin)) return '锁屏密码须为 4-6 位数字'
  return null
}
