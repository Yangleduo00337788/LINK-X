/**
 * 作者：yangleduo
 */
import { t } from '../i18n'

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
  if (!value) return t('validation.usernameRequired')
  if (value.length < 4 || value.length > 32) return t('validation.usernameLength')
  if (!USERNAME_PATTERN.test(value)) return t('validation.usernamePattern')
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
  if (!value) return t('validation.passwordRequired')

  const isSet =
    forSetPassword === true || (typeof forSetPassword === 'object' && forSetPassword !== null)
  if (!isSet) {
    // 登录：不按策略卡长度，避免历史短密码无法登录
    if (value.length > 128) return t('validation.passwordTooLong')
    return null
  }

  const policy = normalizePasswordPolicy(
    typeof forSetPassword === 'object' ? forSetPassword : undefined,
  )
  if (value.length < policy.minLength || value.length > policy.maxLength) {
    return t('validation.passwordLength', { min: policy.minLength, max: policy.maxLength })
  }
  if (policy.requireUpperLower) {
    if (!/[A-Z]/.test(value) || !/[a-z]/.test(value)) {
      return t('validation.passwordUpperLower')
    }
  }
  if (policy.requireDigit && !/\d/.test(value)) {
    return t('validation.passwordDigit')
  }
  if (policy.requireSpecial && /^[A-Za-z0-9]+$/.test(value)) {
    return t('validation.passwordSpecial')
  }
  return null
}

export function validateNickname(nickname: string): string | null {
  const value = nickname.trim()
  if (!value) return t('validation.nicknameRequired')
  if (value.length > 64) return t('validation.nicknameLength')
  return null
}

export function validateLockPin(pin: string): string | null {
  if (!/^\d{4,6}$/.test(pin)) return t('validation.lockPinFormat')
  return null
}
