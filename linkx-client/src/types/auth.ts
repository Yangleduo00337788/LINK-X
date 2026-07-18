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
}

export interface TokenData {
  accessToken: string
  refreshToken: string
  expireTime?: string | number
  user: UserInfo
}

export interface CaptchaData {
  captchaId: string
  imageBase64: string
  expireSeconds: string | number
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
  captchaId?: string
  captchaCode?: string
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
  emailCode: string
  newPassword: string
}
