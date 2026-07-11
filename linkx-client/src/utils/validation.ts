const USERNAME_PATTERN = /^[a-zA-Z0-9_]+$/

export function validateUsername(username: string): string | null {
  const value = username.trim()
  if (!value) return '请输入用户名'
  if (value.length < 4 || value.length > 32) return '用户名长度为 4-32 个字符'
  if (!USERNAME_PATTERN.test(value)) return '用户名只能包含字母、数字和下划线'
  return null
}

export function validatePassword(password: string, forRegister = false): string | null {
  const value = password.trim()
  if (!value) return '请输入密码'
  if (value.length < 8 || value.length > 64) return '密码长度为 8-64 个字符'
  if (forRegister) {
    if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
      return '密码须同时包含字母和数字'
    }
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
