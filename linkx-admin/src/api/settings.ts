import { get, post, put } from './request'

export interface RegisterSideSetting {
  registerEnabled?: boolean
  forgotPasswordEmailEnabled?: boolean
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

export interface AdminSetting {
  register?: RegisterSideSetting
  admin?: AdminSideSetting
  client?: ClientSideSetting
}

export type RegisterUpdatePayload = Required<
  Pick<RegisterSideSetting, 'registerEnabled' | 'forgotPasswordEmailEnabled'>
>

export type AdminSideUpdatePayload = Required<Pick<AdminSideSetting, 'captchaEnabled'>>

export type ClientSideUpdatePayload = Required<
  Pick<ClientSideSetting, 'captchaEnabled' | 'appVersion' | 'appChannel' | 'maxUploadBytes'>
> &
  Pick<ClientSideSetting, 'releaseNotes' | 'downloadUrl'>

export function fetchSettings() {
  return get<AdminSetting>('/admin/settings')
}

export function updateRegisterSettings(payload: RegisterUpdatePayload) {
  return put<AdminSetting>('/admin/settings/register', payload)
}

export function updateAdminSideSettings(payload: AdminSideUpdatePayload) {
  return put<AdminSetting>('/admin/settings/admin', payload)
}

export function updateClientSideSettings(payload: ClientSideUpdatePayload) {
  return put<AdminSetting>('/admin/settings/client', payload)
}

export function testForgotPasswordEmail(email: string) {
  return post<string>('/admin/settings/test-forgot-password-email', { email })
}
