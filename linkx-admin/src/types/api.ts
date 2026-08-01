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
  totpEnabled?: boolean
}

export interface AdminLoginResult {
  accessToken?: string
  refreshToken?: string
  expiresIn?: number
  user?: AdminUserProfile
  requiresTotp?: boolean
  requiresTotpSetup?: boolean
  challengeToken?: string
  challengeExpiresIn?: number
  /** 本次登录 IP */
  loginIp?: string
  /** 相对近期成功登录是否为新 IP */
  newLoginIp?: boolean
}

export interface AdminTotpSetup {
  secret: string
  otpauthUri: string
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
  status?: number
  children?: AdminMenuTree[]
}

export interface AdminMenuPayload {
  parentId: number
  name: string
  title: string
  path: string
  component?: string
  redirect?: string
  icon?: string
  menuType: string
  permissionCode?: string
  sortOrder?: number
  hidden?: number
  status?: number
  remark?: string
}

export interface AdminMenuReorderItem {
  id: number
  parentId?: number
  sortOrder: number
}

export interface CaptchaVO {
  captchaId: string
  imageBase64: string
}

export interface AuthConfigVO {
  captchaEnabled: boolean
  registerEnabled?: boolean
  forgotPasswordEmailEnabled?: boolean
  totpRequired?: boolean
  passwordPolicy?: PasswordPolicyVO
}

export interface PasswordPolicyVO {
  minLength?: number
  maxLength?: number
  requireUpperLower?: boolean
  requireDigit?: boolean
  requireSpecial?: boolean
}
