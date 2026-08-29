/**
 * 作者：yangleduo
 */
import { get, post, put } from './request'

export interface RegisterSideSetting {
  registerEnabled?: boolean
  forgotPasswordEmailEnabled?: boolean
}

export interface LoginEntrySetting {
  captchaEnabled?: boolean
  captchaType?: 'image' | 'slider'
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
  feedbackEscalationEnabled?: boolean
  feedbackEscalationAutoReassign?: boolean
  feedbackEscalationIntervalHours?: number
  reviewSlaHours?: number
  reviewEscalationEnabled?: boolean
  reviewEscalationIntervalHours?: number
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

export interface MailTemplateSetting {
  subject?: string
  html?: string
  usingDefault?: boolean
}

export interface MailTemplatesSideSetting {
  register?: MailTemplateSetting
  reset?: MailTemplateSetting
  welcome?: MailTemplateSetting
}

export interface SecuritySideSetting {
  apiSignEnabled?: boolean
  apiEncryptEnabled?: boolean
  disableFrontendDebug?: boolean
}

export interface StorageSideSetting {
  provider?: 'minio' | 'oss' | 'cos' | 'r2'
  minioEndpoint?: string
  minioBucketName?: string
  minioAccessKey?: string
  minioSecretConfigured?: boolean
  ossEndpoint?: string
  ossBucketName?: string
  ossAccessKeyId?: string
  ossAccessKeySecretConfigured?: boolean
  ossCnameDomain?: string
  cosRegion?: string
  cosBucketName?: string
  cosSecretId?: string
  cosSecretKeyConfigured?: boolean
  cosCnameDomain?: string
  r2Endpoint?: string
  r2BucketName?: string
  r2AccessKeyId?: string
  r2SecretAccessKeyConfigured?: boolean
  r2CnameDomain?: string
  maxUploadBytes?: number
  presignAvatarSeconds?: number
  presignFileSeconds?: number
  presignShareSeconds?: number
}

export interface LinkMateSideSetting {
  enabled?: boolean
  baseUrl?: string
  model?: string
  maxTokens?: number
  temperature?: number
  dailyTokenLimit?: number
  apiKeyConfigured?: boolean
  systemPrompt?: string
  reasoningSupported?: boolean
  agentEnabled?: boolean
  sttBaseUrl?: string
  sttModel?: string
  sttApiKeyConfigured?: boolean
  realtimeBaseUrl?: string
  realtimeModel?: string
  realtimeVoice?: string
  realtimeApiKeyConfigured?: boolean
  groupAiDefaults?: GroupAiDefaultsSetting
  groupAiOverview?: GroupAiOverviewSetting
}

export interface GroupAiDefaultsSetting {
  linkmateEnabled?: boolean
  proactiveEnabled?: boolean
  smartSummaryEnabled?: boolean
  interestTopics?: string
  summaryInstruction?: string
}

export interface GroupAiOverviewSetting {
  totalGroups?: number
  linkmateEnabledGroups?: number
  proactiveEnabledGroups?: number
  smartSummaryEnabledGroups?: number
}

export interface AdminSetting {
  register?: RegisterSideSetting
  login?: LoginSideSetting
  password?: PasswordSideSetting
  admin?: AdminSideSetting
  client?: ClientSideSetting
  mail?: MailSideSetting
  mailTemplates?: MailTemplatesSideSetting
  security?: SecuritySideSetting
  storage?: StorageSideSetting
  linkmate?: LinkMateSideSetting
}

export type RegisterUpdatePayload = Required<
  Pick<RegisterSideSetting, 'registerEnabled' | 'forgotPasswordEmailEnabled'>
>

export type LoginUpdatePayload = {
  client: Required<
    Pick<LoginEntrySetting, 'captchaEnabled' | 'captchaType' | 'maxAttempts' | 'lockDurationMinutes'>
  >
  admin: Required<
    Pick<LoginEntrySetting, 'captchaEnabled' | 'captchaType' | 'maxAttempts' | 'lockDurationMinutes'>
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
    | 'feedbackEscalationEnabled'
    | 'feedbackEscalationAutoReassign'
    | 'feedbackEscalationIntervalHours'
    | 'reviewSlaHours'
    | 'reviewEscalationEnabled'
    | 'reviewEscalationIntervalHours'
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

export type MailTemplateUpdatePayload = {
  subject?: string
  html?: string
}

export type MailTemplatesUpdatePayload = {
  register: MailTemplateUpdatePayload
  reset: MailTemplateUpdatePayload
  welcome: MailTemplateUpdatePayload
}

export type SecurityUpdatePayload = Required<SecuritySideSetting>

export type StorageUpdatePayload = Required<
  Pick<
    StorageSideSetting,
  | 'provider'
  | 'maxUploadBytes'
  >
> &
  Pick<
    StorageSideSetting,
    | 'minioEndpoint'
    | 'minioBucketName'
    | 'minioAccessKey'
    | 'ossEndpoint'
    | 'ossBucketName'
    | 'ossAccessKeyId'
    | 'ossCnameDomain'
    | 'cosRegion'
    | 'cosBucketName'
    | 'cosSecretId'
    | 'cosCnameDomain'
    | 'r2Endpoint'
    | 'r2BucketName'
    | 'r2AccessKeyId'
    | 'r2CnameDomain'
  > & {
    minioSecretKey?: string
    ossAccessKeySecret?: string
    cosSecretKey?: string
    r2SecretAccessKey?: string
  }

export type TestStorageConnectionPayload = StorageUpdatePayload

export type LinkMateUpdatePayload = Required<
  Pick<
    LinkMateSideSetting,
    | 'enabled'
    | 'baseUrl'
    | 'model'
    | 'maxTokens'
    | 'temperature'
    | 'dailyTokenLimit'
    | 'agentEnabled'
  >
> &
  Pick<
    LinkMateSideSetting,
    | 'systemPrompt'
    | 'sttBaseUrl'
    | 'sttModel'
    | 'realtimeBaseUrl'
    | 'realtimeModel'
    | 'realtimeVoice'
  > & {
    apiKey?: string
    sttApiKey?: string
    realtimeApiKey?: string
    groupLinkmateDefaultEnabled: boolean
    groupAiProactiveDefaultEnabled: boolean
    groupAiSmartSummaryDefaultEnabled: boolean
    groupAiDefaultInterestTopics?: string
    groupAiDefaultSummaryInstruction?: string
  }

export type TestLinkMateConnectionPayload = LinkMateUpdatePayload

export type AdminSettingUpdatePayload = {
  register?: RegisterUpdatePayload
  login?: LoginUpdatePayload
  password?: PasswordUpdatePayload
  admin?: AdminSideSetting
  client?: ClientSideUpdatePayload
  mail?: MailUpdatePayload
  security?: SecurityUpdatePayload
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

export function updateSecuritySettings(payload: SecurityUpdatePayload) {
  return put<AdminSetting>('/admin/settings/security', payload)
}

export function updateMailTemplates(payload: MailTemplatesUpdatePayload) {
  return put<AdminSetting>('/admin/settings/mail-templates', payload)
}

export function testForgotPasswordEmail(email: string) {
  return post<string>('/admin/settings/test-forgot-password-email', { email })
}

export function updateStorageSettings(payload: StorageUpdatePayload) {
  return put<AdminSetting>('/admin/settings/storage', payload)
}

export function testStorageConnection(payload: TestStorageConnectionPayload) {
  return post<string>('/admin/settings/test-storage-connection', payload)
}

export function updateLinkMateSettings(payload: LinkMateUpdatePayload) {
  return put<AdminSetting>('/admin/settings/linkmate', payload)
}

export function testLinkMateConnection(payload: TestLinkMateConnectionPayload) {
  return post<string>('/admin/settings/test-linkmate-connection', payload)
}
