export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface UserInfo {
  id: string
  username: string
  nickname: string
  avatar?: string
  signature?: string
  gender?: string
  birthday?: string | number | null
  country?: string
  province?: string
  region?: string
  email?: string | null
  emailBound?: boolean
  phone?: string | null
  phoneBound?: boolean
}

export interface TokenData {
  accessToken: string
  refreshToken: string
  expireTime?: string | number
  user: UserInfo
}

export interface CaptchaData {
  type?: 'image' | 'slider'
  captchaId: string
  imageBase64: string
  puzzleImageBase64?: string
  puzzleY?: number
  expireSeconds: string | number
}

/** GET /auth/config */
export interface AuthConfig {
  captchaEnabled: boolean
  captchaType?: 'image' | 'slider'
  registerEnabled?: boolean
  forgotPasswordEmailEnabled?: boolean
  passwordPolicy?: PasswordPolicy
}

export interface PasswordPolicy {
  minLength?: number
  maxLength?: number
  requireUpperLower?: boolean
  requireDigit?: boolean
  requireSpecial?: boolean
}

export interface LoginRequest {
  username: string
  password: string
  captchaId?: string
  captchaCode?: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname: string
  email: string
  emailCode: string
  captchaId?: string
  captchaCode?: string
}

export interface SendRegisterCodeRequest {
  email: string
  username?: string
}

export interface ResetPasswordRequest {
  captchaId?: string
  captchaCode?: string
  newPassword: string
}

export interface SendResetCodeRequest {
  username: string
}

export interface ResetPasswordByEmailRequest {
  username: string
  code: string
  newPassword: string
}
