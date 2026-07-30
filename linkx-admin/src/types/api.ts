export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface PageQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number | string
  feedbackStatus?: string
  startTime?: number
  endTime?: number
  sortBy?: string
  sortOrder?: string
}

export interface AdminUserProfile {
  id: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
  roles?: string[]
  permissions?: string[]
}

export interface AdminLoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: AdminUserProfile
}

export interface AdminMenuTree {
  id: number
  parentId: number
  name: string
  title: string
  path: string
  component?: string
  redirect?: string
  icon?: string
  type?: string
  permission?: string
  sort?: number
  visible?: boolean
  children?: AdminMenuTree[]
}

export interface CaptchaVO {
  captchaId: string
  imageBase64: string
}

export interface AuthConfigVO {
  captchaEnabled: boolean
}
