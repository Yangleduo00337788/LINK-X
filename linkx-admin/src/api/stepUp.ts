import { get, post } from './request'

export type StepUpMethod = 'totp' | 'email' | 'sms'

export interface StepUpChallenge {
  methods: StepUpMethod[]
  totpEnabled?: boolean
  emailBound?: boolean
  emailMasked?: string
  smsAvailable?: boolean
  action?: string
  method?: string
  expiresIn?: number
}

export interface StepUpToken {
  stepUpToken: string
  action: string
  method: string
  expiresIn: number
}

export function fetchStepUpOptions(action?: string) {
  return get<StepUpChallenge>('/admin/auth/step-up/options', action ? { action } : undefined)
}

export function requestStepUp(method: StepUpMethod, action: string) {
  return post<StepUpChallenge>('/admin/auth/step-up/request', { method, action })
}

export function verifyStepUp(method: StepUpMethod, code: string, action: string) {
  return post<StepUpToken>('/admin/auth/step-up/verify', { method, code, action })
}
