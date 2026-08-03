import { get, post, put } from './request'

export interface RegisterSideSetting {
  registerEnabled?: boolean
  forgotPasswordEmailEnabled?: boolean
}

export interface LoginEntrySetting {
  captchaEnabled?: boolean
  maxAttempts?: number
  lockDurationMinutes?: number
  totpRequired?: boolean
}

export interface LoginSideSetting {
  client?: LoginEntrySetting
  admin?: LoginEntrySetting
}

export interface PasswordSideSetting {
  minLength?: number
  maxLength?: number
  requireUpperLower?: boolean
  requireDigit?: boolean
  requireSpecial?: boolean
}

export interface AdminSideSetting {
  captchaEnabled?: boolean
}

export interface ClientSideSetting {
  captchaEnabled?: boolean
  appVersion?: string
  appChannel?: string
  releaseNotes?: string
  downloadUrl?: string
  forceUpdate?: boolean
  minSupportedVersion?: string
  maxUploadBytes?: number
  sensitiveFilterEnabled?: boolean
  supportEmail?: string
  supportPhone?: string
  feedbackSlaHours?: number
}

export interface MailSideSetting {
  host?: string
  port?: number
  username?: string
  passwordConfigured?: boolean
  from?: string
  fromName?: string
  startTls?: boolean
  ssl?: boolean
  codeExpireMinutes?: number
}

export interface AdminSetting {
  register?: RegisterSideSetting
  login?: LoginSideSetting
  password?: PasswordSideSetting
  admin?: AdminSideSetting
  client?: ClientSideSetting
  mail?: MailSideSetting
}

export type RegisterUpdatePayload = Required<
  Pick<RegisterSideSetting, 'registerEnabled' | 'forgotPasswordEmailEnabled'>
>

export type LoginUpdatePayload = {
  client: Required<
    Pick<LoginEntrySetting, 'captchaEnabled' | 'maxAttempts' | 'lockDurationMinutes'>
  >
  admin: Required<
    Pick<LoginEntrySetting, 'captchaEnabled' | 'maxAttempts' | 'lockDurationMinutes'>
  > & {
    totpRequired: boolean
  }
}

export type PasswordUpdatePayload = Required<PasswordSideSetting>

export type ClientSideUpdatePayload = Required<
  Pick<
    ClientSideSetting,
    | 'appVersion'
    | 'appChannel'
    | 'maxUploadBytes'
    | 'forceUpdate'
    | 'sensitiveFilterEnabled'
    | 'feedbackSlaHours'
  >
> &
  Pick<
    ClientSideSetting,
    | 'releaseNotes'
    | 'downloadUrl'
    | 'captchaEnabled'
    | 'minSupportedVersion'
    | 'supportEmail'
    | 'supportPhone'
  >

export type MailUpdatePayload = {
  host: string
  port: number
  username?: string
  password?: string
  from: string
  fromName?: string
  startTls: boolean
  ssl: boolean
  codeExpireMinutes: number
}

export type AdminSettingUpdatePayload = {
  register?: RegisterUpdatePayload
  login?: LoginUpdatePayload
  password?: PasswordUpdatePayload
  admin?: AdminSideSetting
  client?: ClientSideUpdatePayload
  mail?: MailUpdatePayload
}

export function fetchSettings() {
  return get<AdminSetting>('/admin/settings')
}

export function updateSettings(payload: AdminSettingUpdatePayload) {
  return put<AdminSetting>('/admin/settings', payload)
}

export function updateRegisterSettings(payload: RegisterUpdatePayload) {
  return updateSettings({ register: payload })
}

export function updateLoginSettings(payload: LoginUpdatePayload) {
  return updateSettings({ login: payload })
}

export function updatePasswordSettings(payload: PasswordUpdatePayload) {
  return updateSettings({ password: payload })
}

export function updateClientSideSettings(payload: ClientSideUpdatePayload) {
  return updateSettings({ client: payload })
}

export function updateMailSettings(payload: MailUpdatePayload) {
  return updateSettings({ mail: payload })
}

export function testForgotPasswordEmail(email: string) {
  return post<string>('/admin/settings/test-forgot-password-email', { email })
}
