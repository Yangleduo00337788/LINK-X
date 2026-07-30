import { get } from './request'

export interface AdminSetting {
  captchaEnabled?: boolean
  appVersion?: string
  appChannel?: string
  releaseNotes?: string
  downloadUrl?: string
  maxUploadBytes?: number
}

export function fetchSettings() {
  return get<AdminSetting>('/admin/settings')
}
