export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar?: string
  signature?: string
}

export interface TokenData {
  accessToken: string
  refreshToken: string
  expireTime?: number
  user: UserInfo
}

export interface CaptchaData {
  captchaId: string
  imageBase64: string
  expireSeconds: number
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
  captchaId?: string
  captchaCode?: string
}
