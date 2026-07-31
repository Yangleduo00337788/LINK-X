import { get, post, put } from './request'

export interface RegisterSideSetting {
  registerEnabled?: boolean
  forgotPasswordEmailEnabled?: boolean
}

export interface LoginEntrySetting {
  captchaEnabled?: boolean
  maxAttempts?: number
  lockDurationMinutes?: number
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
  maxUploadBytes?: number
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
  client: Required<LoginEntrySetting>
  admin: Required<LoginEntrySetting>
}

export type PasswordUpdatePayload = Required<PasswordSideSetting>

export type ClientSideUpdatePayload = Required<
  Pick<ClientSideSetting, 'appVersion' | 'appChannel' | 'maxUploadBytes'>
> &
  Pick<ClientSideSetting, 'releaseNotes' | 'downloadUrl' | 'captchaEnabled'>

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

export function fetchSettings() {
  return get<AdminSetting>('/admin/settings')
}

export function updateRegisterSettings(payload: RegisterUpdatePayload) {
  return put<AdminSetting>('/admin/settings/register', payload)
}

export function updateLoginSettings(payload: LoginUpdatePayload) {
  return put<AdminSetting>('/admin/settings/login', payload)
}

export function updatePasswordSettings(payload: PasswordUpdatePayload) {
  return put<AdminSetting>('/admin/settings/password', payload)
}

export function updateClientSideSettings(payload: ClientSideUpdatePayload) {
  return put<AdminSetting>('/admin/settings/client', payload)
}

export function updateMailSettings(payload: MailUpdatePayload) {
  return put<AdminSetting>('/admin/settings/mail', payload)
}

export function testForgotPasswordEmail(email: string) {
  return post<string>('/admin/settings/test-forgot-password-email', { email })
}
