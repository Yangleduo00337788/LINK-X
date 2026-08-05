<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDivider,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSlider,
  NSpace,
  NSpin,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  useDialog,
  useMessage,
} from 'naive-ui'
import { PlayCircleOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import {
  fetchSettings,
  testForgotPasswordEmail,
  updateClientSideSettings,
  updateLoginSettings,
  updateMailSettings,
  updateMailTemplates,
  updatePasswordSettings,
  updateRegisterSettings,
  updateSecuritySettings,
  type AdminSetting,
  type MailTemplateSetting,
} from '@/api/settings'
import { fetchAuthConfig } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { usePreferencesStore } from '@/stores/preferences'
import { ensureVoices, listVoicesForLang, previewSpeech, unlockSpeech } from '@/utils/voiceNotify'
import { useSecurityStore } from '@/stores/security'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const securityStore = useSecurityStore()
const prefs = usePreferencesStore()
const { watermarkEnabled, watermarkFullscreen, watermarkLines, watermarkOpacity, voiceNotifyEnabled, speechVoiceUri } =
  storeToRefs(prefs)

const loading = ref(false)
const savingRegister = ref(false)
const savingLogin = ref(false)
const savingPassword = ref(false)
const savingClient = ref(false)
const savingMail = ref(false)
const savingMailTemplates = ref(false)
const savingSecurity = ref(false)
const testingEmail = ref(false)
const showTestEmailModal = ref(false)
const testEmail = ref('')
const tabNames = new Set([
  'register',
  'login',
  'password',
  'client',
  'mail',
  'mail-templates',
  'security',
  'watermark',
  'sound',
])
const activeTab = ref(
  tabNames.has(String(route.query.tab || '')) ? String(route.query.tab) : 'register'
)

watch(activeTab, (tab) => {
  const nextQuery = { ...route.query }
  if (tab === 'register') {
    delete nextQuery.tab
  } else {
    nextQuery.tab = tab
  }
  router.replace({ query: nextQuery })
})

watch(
  () => route.query.tab,
  (tab) => {
    const name = String(tab || 'register')
    if (tabNames.has(name) && activeTab.value !== name) {
      activeTab.value = name
    }
  }
)

const canEdit = computed(() => auth.hasPermission('admin:setting:edit'))

const speechVoices = ref<SpeechSynthesisVoice[]>([])

const voiceOptions = computed(() => {
  const items = listVoicesForLang(locale.value, speechVoices.value).map((voice) => ({
    uri: voice.voiceURI,
    name: voice.name,
    lang: voice.lang,
    local: voice.localService,
  }))
  return items
})

async function refreshSpeechVoices() {
  speechVoices.value = await ensureVoices()
}

watch(
  activeTab,
  (tab) => {
    if (tab === 'sound') void refreshSpeechVoices()
  },
  { immediate: true }
)

watch(locale, () => {
  if (activeTab.value === 'sound') void refreshSpeechVoices()
})

function onVoiceToggle(next: boolean) {
  unlockSpeech(locale.value)
  prefs.setVoiceNotifyEnabled(next)
  if (next) {
    void previewSpeech(t('voiceNotify.pendingTask'), locale.value).then((ok) => {
      if (!ok) message.warning(t('setting.voiceUnsupported'))
    })
  }
}

async function previewVoice() {
  const ok = await previewSpeech(t('voiceNotify.pendingTask'), locale.value)
  if (!ok) message.warning(t('setting.voiceUnsupported'))
}

function pickSpeechVoice(uri: string) {
  unlockSpeech(locale.value)
  prefs.setSpeechVoiceUri(uri)
  void previewSpeech(t('voiceNotify.pendingTask'), locale.value).then((ok) => {
    if (!ok) message.warning(t('setting.voiceUnsupported'))
  })
}

function isVoiceSelected(uri: string) {
  return (speechVoiceUri.value || '') === uri
}

const registerForm = reactive({
  registerEnabled: true,
  forgotPasswordEmailEnabled: true,
})

const loginForm = reactive({
  client: {
    captchaEnabled: true,
    captchaType: 'image' as 'image' | 'slider',
    maxAttempts: 5,
    lockDurationMinutes: 10,
  },
  admin: {
    captchaEnabled: true,
    captchaType: 'image' as 'image' | 'slider',
    maxAttempts: 5,
    lockDurationMinutes: 10,
    totpRequired: false,
  },
})

const captchaTypeOptions = computed(() => [
  { label: t('setting.captchaTypeImage'), value: 'image' },
  { label: t('setting.captchaTypeSlider'), value: 'slider' },
])

const passwordForm = reactive({
  minLength: 8,
  maxLength: 64,
  requireUpperLower: false,
  requireDigit: true,
  requireSpecial: false,
})

const clientForm = reactive({
  captchaEnabled: true,
  appVersion: '',
  appChannel: 'stable',
  downloadUrl: '',
  maxUploadMb: 100,
  releaseNotes: '',
  forceUpdate: false,
  minSupportedVersion: '',
  sensitiveFilterEnabled: true,
  supportEmail: '',
  supportPhone: '',
  feedbackSlaHours: 24,
  feedbackEscalationEnabled: false,
  feedbackEscalationAutoReassign: true,
  feedbackEscalationIntervalHours: 24,
  reviewSlaHours: 24,
  reviewEscalationEnabled: false,
  reviewEscalationIntervalHours: 24,
})

const mailForm = reactive({
  host: '',
  port: 587 as number | null,
  username: '',
  password: '',
  passwordConfigured: false,
  from: '',
  fromName: '',
  startTls: true,
  ssl: false,
  codeExpireMinutes: 10 as number | null,
})

type MailTemplateFormItem = {
  subject: string
  html: string
  usingDefault: boolean
}

const mailTemplatesForm = reactive<{
  register: MailTemplateFormItem
  reset: MailTemplateFormItem
  welcome: MailTemplateFormItem
}>({
  register: { subject: '', html: '', usingDefault: true },
  reset: { subject: '', html: '', usingDefault: true },
  welcome: { subject: '', html: '', usingDefault: true },
})

const securityForm = reactive({
  apiSignEnabled: true,
  apiEncryptEnabled: false,
  disableFrontendDebug: false,
})

watch(
  () => securityForm.apiEncryptEnabled,
  (enabled) => {
    if (enabled) securityForm.apiSignEnabled = true
  }
)

const channelOptions = [
  { label: 'stable', value: 'stable' },
  { label: 'beta', value: 'beta' },
  { label: 'dev', value: 'dev' },
]

const linesText = ref(watermarkLines.value.join('\n'))

watch(watermarkLines, (lines) => {
  linesText.value = lines.join('\n')
})

const lineCount = computed(
  () =>
    linesText.value
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean).length
)

const opacityPercent = computed({
  get: () => Math.round(watermarkOpacity.value * 100),
  set: (v: number) => prefs.setWatermarkOpacity(v / 100),
})

function applySettings(data: AdminSetting) {
  registerForm.registerEnabled = data.register?.registerEnabled !== false
  registerForm.forgotPasswordEmailEnabled = data.register?.forgotPasswordEmailEnabled !== false

  loginForm.client.captchaEnabled =
    data.login?.client?.captchaEnabled ?? data.client?.captchaEnabled !== false
  loginForm.client.captchaType =
    data.login?.client?.captchaType === 'slider' ? 'slider' : 'image'
  loginForm.client.maxAttempts = data.login?.client?.maxAttempts ?? 5
  loginForm.client.lockDurationMinutes = data.login?.client?.lockDurationMinutes ?? 10
  loginForm.admin.captchaEnabled =
    data.login?.admin?.captchaEnabled ?? data.admin?.captchaEnabled !== false
  loginForm.admin.captchaType =
    data.login?.admin?.captchaType === 'slider' ? 'slider' : 'image'
  loginForm.admin.maxAttempts = data.login?.admin?.maxAttempts ?? 5
  loginForm.admin.lockDurationMinutes = data.login?.admin?.lockDurationMinutes ?? 10
  loginForm.admin.totpRequired = data.login?.admin?.totpRequired === true

  passwordForm.minLength = data.password?.minLength ?? 8
  passwordForm.maxLength = data.password?.maxLength ?? 64
  passwordForm.requireUpperLower = data.password?.requireUpperLower === true
  passwordForm.requireDigit = data.password?.requireDigit !== false
  passwordForm.requireSpecial = data.password?.requireSpecial === true

  clientForm.captchaEnabled = loginForm.client.captchaEnabled
  clientForm.appVersion = data.client?.appVersion || ''
  clientForm.appChannel = data.client?.appChannel || 'stable'
  clientForm.downloadUrl = data.client?.downloadUrl || ''
  clientForm.releaseNotes = data.client?.releaseNotes || ''
  clientForm.forceUpdate = data.client?.forceUpdate === true
  clientForm.minSupportedVersion = data.client?.minSupportedVersion || ''
  clientForm.maxUploadMb = data.client?.maxUploadBytes
    ? Math.round((data.client.maxUploadBytes / (1024 * 1024)) * 10) / 10
    : 100
  clientForm.sensitiveFilterEnabled = data.client?.sensitiveFilterEnabled !== false
  clientForm.supportEmail = data.client?.supportEmail || ''
  clientForm.supportPhone = data.client?.supportPhone || ''
  clientForm.feedbackSlaHours = data.client?.feedbackSlaHours ?? 24
  clientForm.feedbackEscalationEnabled = data.client?.feedbackEscalationEnabled === true
  clientForm.feedbackEscalationAutoReassign = data.client?.feedbackEscalationAutoReassign !== false
  clientForm.feedbackEscalationIntervalHours = data.client?.feedbackEscalationIntervalHours ?? 24
  clientForm.reviewSlaHours = data.client?.reviewSlaHours ?? 24
  clientForm.reviewEscalationEnabled = data.client?.reviewEscalationEnabled === true
  clientForm.reviewEscalationIntervalHours = data.client?.reviewEscalationIntervalHours ?? 24

  mailForm.host = data.mail?.host || ''
  mailForm.port = data.mail?.port ?? 587
  mailForm.username = data.mail?.username || ''
  mailForm.password = ''
  mailForm.passwordConfigured = data.mail?.passwordConfigured === true
  mailForm.from = data.mail?.from || ''
  mailForm.fromName = data.mail?.fromName || ''
  mailForm.startTls = data.mail?.startTls !== false
  mailForm.ssl = data.mail?.ssl === true
  mailForm.codeExpireMinutes = data.mail?.codeExpireMinutes ?? 10
  syncMailPortByEncryption()

  applyMailTemplate('register', data.mailTemplates?.register)
  applyMailTemplate('reset', data.mailTemplates?.reset)
  applyMailTemplate('welcome', data.mailTemplates?.welcome)

  securityForm.apiSignEnabled = data.security?.apiSignEnabled !== false
  securityForm.apiEncryptEnabled = data.security?.apiEncryptEnabled === true
  securityForm.disableFrontendDebug = data.security?.disableFrontendDebug === true
  securityStore.applyFromSettings(data.security)
}

function applyMailTemplate(key: 'register' | 'reset' | 'welcome', tpl?: MailTemplateSetting) {
  const target = mailTemplatesForm[key]
  target.subject = tpl?.subject || ''
  target.html = tpl?.html || ''
  target.usingDefault = tpl?.usingDefault !== false
}

async function load() {
  loading.value = true
  try {
    applySettings(await fetchSettings())
  } finally {
    loading.value = false
  }
}

/** SSL → 465，STARTTLS → 587，保证端口与加密方式一致 */
function syncMailPortByEncryption() {
  if (mailForm.ssl) {
    mailForm.port = 465
  } else if (mailForm.startTls) {
    mailForm.port = 587
  }
}

function onMailStartTlsChange(enabled: boolean) {
  mailForm.startTls = enabled
  if (enabled) {
    mailForm.ssl = false
  }
  syncMailPortByEncryption()
}

function onMailSslChange(enabled: boolean) {
  mailForm.ssl = enabled
  if (enabled) {
    mailForm.startTls = false
  }
  syncMailPortByEncryption()
}

async function saveRegister() {
  if (!canEdit.value) return
  confirmSave(t('setting.saveRegister'), async () => {
    savingRegister.value = true
    try {
      applySettings(
        await updateRegisterSettings({
          registerEnabled: registerForm.registerEnabled,
          forgotPasswordEmailEnabled: registerForm.forgotPasswordEmailEnabled,
        })
      )
      message.success(t('setting.registerSaved'))
    } finally {
      savingRegister.value = false
    }
  })
}

async function saveLogin() {
  if (!canEdit.value) return
  if (!loginForm.client.maxAttempts || !loginForm.admin.maxAttempts) {
    message.warning(t('setting.maxAttemptsRequired'))
    return
  }
  if (!loginForm.client.lockDurationMinutes || !loginForm.admin.lockDurationMinutes) {
    message.warning(t('setting.lockDurationRequired'))
    return
  }
  confirmSave(t('setting.saveLogin'), async () => {
    savingLogin.value = true
    try {
      applySettings(
        await updateLoginSettings({
          client: {
            captchaEnabled: loginForm.client.captchaEnabled,
            captchaType: loginForm.client.captchaType,
            maxAttempts: loginForm.client.maxAttempts,
            lockDurationMinutes: loginForm.client.lockDurationMinutes,
          },
          admin: {
            captchaEnabled: loginForm.admin.captchaEnabled,
            captchaType: loginForm.admin.captchaType,
            maxAttempts: loginForm.admin.maxAttempts,
            lockDurationMinutes: loginForm.admin.lockDurationMinutes,
            totpRequired: loginForm.admin.totpRequired,
          },
        })
      )
      message.success(t('setting.loginSaved'))
    } finally {
      savingLogin.value = false
    }
  })
}

async function savePassword() {
  if (!canEdit.value) return
  if (!passwordForm.minLength || !passwordForm.maxLength) {
    message.warning(t('setting.passwordLengthRequired'))
    return
  }
  if (passwordForm.minLength > passwordForm.maxLength) {
    message.warning(t('setting.passwordLengthOrder'))
    return
  }
  confirmSave(t('setting.savePassword'), async () => {
    savingPassword.value = true
    try {
      applySettings(
        await updatePasswordSettings({
          minLength: passwordForm.minLength,
          maxLength: passwordForm.maxLength,
          requireUpperLower: passwordForm.requireUpperLower,
          requireDigit: passwordForm.requireDigit,
          requireSpecial: passwordForm.requireSpecial,
        })
      )
      message.success(t('setting.passwordSaved'))
    } finally {
      savingPassword.value = false
    }
  })
}

async function saveClient() {
  if (!canEdit.value) return
  if (!clientForm.appVersion.trim()) {
    message.warning(t('setting.versionRequired'))
    return
  }
  if (!clientForm.appChannel.trim()) {
    message.warning(t('setting.channelRequired'))
    return
  }
  if (!clientForm.maxUploadMb || clientForm.maxUploadMb <= 0) {
    message.warning(t('setting.maxUploadRequired'))
    return
  }
  confirmSave(t('setting.saveClient'), async () => {
    savingClient.value = true
    try {
      applySettings(
        await updateClientSideSettings({
          captchaEnabled: loginForm.client.captchaEnabled,
          appVersion: clientForm.appVersion.trim(),
          appChannel: clientForm.appChannel.trim(),
          downloadUrl: clientForm.downloadUrl.trim() || undefined,
          releaseNotes: clientForm.releaseNotes.trim() || undefined,
          forceUpdate: clientForm.forceUpdate,
          minSupportedVersion: clientForm.minSupportedVersion.trim() || undefined,
          maxUploadBytes: Math.round(clientForm.maxUploadMb * 1024 * 1024),
          sensitiveFilterEnabled: clientForm.sensitiveFilterEnabled,
          supportEmail: clientForm.supportEmail.trim() || undefined,
          supportPhone: clientForm.supportPhone.trim() || undefined,
          feedbackSlaHours: clientForm.feedbackSlaHours || 24,
          feedbackEscalationEnabled: clientForm.feedbackEscalationEnabled,
          feedbackEscalationAutoReassign: clientForm.feedbackEscalationAutoReassign,
          feedbackEscalationIntervalHours: clientForm.feedbackEscalationIntervalHours || 24,
          reviewSlaHours: clientForm.reviewSlaHours || 24,
          reviewEscalationEnabled: clientForm.reviewEscalationEnabled,
          reviewEscalationIntervalHours: clientForm.reviewEscalationIntervalHours || 24,
        })
      )
      message.success(t('setting.clientSaved'))
    } finally {
      savingClient.value = false
    }
  })
}

async function saveMail() {
  if (!canEdit.value) return
  if (!mailForm.host.trim()) {
    message.warning(t('setting.mailHostRequired'))
    return
  }
  if (!mailForm.from.trim()) {
    message.warning(t('setting.mailFromRequired'))
    return
  }
  syncMailPortByEncryption()
  const mailPort = mailForm.port
  if (!mailPort) {
    message.warning(t('setting.mailPort'))
    return
  }
  if (mailForm.startTls && mailForm.ssl) {
    message.warning(t('setting.mailTlsConflict'))
    return
  }
  confirmSave(t('setting.saveMail'), async () => {
    savingMail.value = true
    try {
      applySettings(
        await updateMailSettings({
          host: mailForm.host.trim(),
          port: mailPort,
          username: mailForm.username.trim(),
          password: mailForm.password.trim() || undefined,
          from: mailForm.from.trim(),
          fromName: mailForm.fromName.trim(),
          startTls: mailForm.startTls,
          ssl: mailForm.ssl,
          codeExpireMinutes: mailForm.codeExpireMinutes || 10,
        })
      )
      message.success(t('setting.mailSaved'))
    } finally {
      savingMail.value = false
    }
  })
}

function resetMailTemplate(key: 'register' | 'reset' | 'welcome') {
  mailTemplatesForm[key].subject = ''
  mailTemplatesForm[key].html = ''
  mailTemplatesForm[key].usingDefault = true
}

function onMailTemplateEdit(key: 'register' | 'reset' | 'welcome') {
  mailTemplatesForm[key].usingDefault = false
}

function buildMailTemplatePayload(key: 'register' | 'reset' | 'welcome') {
  const item = mailTemplatesForm[key]
  if (item.usingDefault) {
    return { subject: '', html: '' }
  }
  return {
    subject: item.subject.trim(),
    html: item.html.trim(),
  }
}

async function saveMailTemplates() {
  if (!canEdit.value) return
  confirmSave(t('setting.saveMailTemplates'), async () => {
    savingMailTemplates.value = true
    try {
      applySettings(
        await updateMailTemplates({
          register: buildMailTemplatePayload('register'),
          reset: buildMailTemplatePayload('reset'),
          welcome: buildMailTemplatePayload('welcome'),
        })
      )
      message.success(t('setting.mailTemplatesSaved'))
    } finally {
      savingMailTemplates.value = false
    }
  })
}

async function saveSecurity() {
  if (!canEdit.value) return
  const enablingDebugBlock = securityForm.disableFrontendDebug
  confirmSave(t('setting.saveSecurity'), async () => {
    savingSecurity.value = true
    try {
      // 仅关闭加密时提前同步 store；开启加密须等服务端保存成功后再同步，否则请求体被加密而服务端尚未解密
      const preSync: {
        apiSignEnabled: boolean
        disableFrontendDebug: boolean
        apiEncryptEnabled?: boolean
      } = {
        apiSignEnabled: securityForm.apiSignEnabled,
        disableFrontendDebug: securityForm.disableFrontendDebug,
      }
      if (!securityForm.apiEncryptEnabled || securityStore.apiEncryptEnabled) {
        preSync.apiEncryptEnabled = securityForm.apiEncryptEnabled
      }
      securityStore.applyFromSettings(preSync)
      applySettings(
        await updateSecuritySettings({
          apiSignEnabled: securityForm.apiSignEnabled,
          apiEncryptEnabled: securityForm.apiEncryptEnabled,
          disableFrontendDebug: securityForm.disableFrontendDebug,
        })
      )
      const cfg = await fetchAuthConfig()
      securityStore.applyFromAuthConfig(cfg)
      message.success(t('setting.securitySaved'))
      if (enablingDebugBlock) {
        window.location.reload()
      }
    } finally {
      savingSecurity.value = false
    }
  })
}

function confirmSave(label: string, run: () => Promise<void>) {
  dialog.warning({
    title: t('common.confirmAction', { action: label }),
    content: t('setting.saveConfirm', { action: label }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => run(),
  })
}

function openTestEmailModal() {
  if (!canEdit.value) return
  testEmail.value = auth.user?.email || ''
  showTestEmailModal.value = true
}

async function runTestEmail() {
  const email = testEmail.value.trim()
  if (!email) {
    message.warning(t('setting.testEmailRequired'))
    return false
  }
  testingEmail.value = true
  try {
    const tip = await testForgotPasswordEmail(email)
    message.success(tip || t('setting.testEmailOk'))
    showTestEmailModal.value = false
    return true
  } catch {
    return false
  } finally {
    testingEmail.value = false
  }
}

function saveWatermark() {
  const lines = linesText.value
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
  prefs.setWatermarkLines(lines)
  message.success(t('setting.watermarkSaved'))
}

function resetWatermark() {
  linesText.value = ''
  prefs.setWatermarkLines([])
  prefs.setWatermarkOpacity(0.12)
  message.success(t('setting.watermarkReset'))
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpin :show="loading">
        <NTabs v-model:value="activeTab" type="line" animated>
          <NTabPane name="register" :tab="t('setting.registerTitle')">
            <p class="section-hint">{{ t('setting.registerHint') }}</p>
            <NForm label-placement="left" label-width="160" :disabled="!canEdit">
              <NFormItem :label="t('setting.openRegister')">
                <NSwitch v-model:value="registerForm.registerEnabled" />
                <span class="field-hint">{{
                  registerForm.registerEnabled ? t('common.on') : t('common.off')
                }}</span>
              </NFormItem>
              <NFormItem :label="t('setting.forgotPasswordEmail')">
                <div class="switch-with-action">
                  <NSwitch v-model:value="registerForm.forgotPasswordEmailEnabled" />
                  <span class="field-hint">{{
                    registerForm.forgotPasswordEmailEnabled ? t('common.on') : t('common.off')
                  }}</span>
                  <NButton
                    v-if="canEdit"
                    size="small"
                    class="lx-float-btn test-btn"
                    :loading="testingEmail"
                    @click="openTestEmailModal"
                  >
                    {{ t('setting.testEmail') }}
                  </NButton>
                </div>
              </NFormItem>
              <NFormItem v-if="canEdit">
                <NSpace>
                  <NButton
                    type="primary"
                    class="lx-float-btn"
                    :loading="savingRegister"
                    @click="saveRegister"
                  >
                    {{ t('setting.saveRegister') }}
                  </NButton>
                  <NButton class="lx-float-btn" :disabled="savingRegister" @click="load">
                    {{ t('common.refresh') }}
                  </NButton>
                </NSpace>
              </NFormItem>
              <p v-else class="readonly-hint">{{ t('setting.readonlyHint') }}</p>
            </NForm>
          </NTabPane>

          <NTabPane name="login" :tab="t('setting.loginTitle')">
            <p class="section-hint">{{ t('setting.loginHint') }}</p>
            <NForm label-placement="left" label-width="160" :disabled="!canEdit">
              <h3 class="block-title">{{ t('setting.loginClientBlock') }}</h3>
              <NFormItem :label="t('setting.captcha')">
                <NSwitch v-model:value="loginForm.client.captchaEnabled" />
                <span class="field-hint">{{
                  loginForm.client.captchaEnabled ? t('common.on') : t('common.off')
                }}</span>
              </NFormItem>
              <NFormItem v-if="loginForm.client.captchaEnabled" :label="t('setting.captchaType')">
                <NSelect
                  v-model:value="loginForm.client.captchaType"
                  :options="captchaTypeOptions"
                  style="width: 220px"
                />
              </NFormItem>
              <NFormItem :label="t('setting.maxAttempts')" required>
                <div class="number-row">
                  <NInputNumber
                    v-model:value="loginForm.client.maxAttempts"
                    :min="1"
                    :max="100"
                    :step="1"
                    style="width: 160px"
                  />
                  <span class="field-hint">{{ t('setting.maxAttemptsHint') }}</span>
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.lockDuration')" required>
                <div class="number-row">
                  <NInputNumber
                    v-model:value="loginForm.client.lockDurationMinutes"
                    :min="1"
                    :max="1440"
                    :step="1"
                    style="width: 160px"
                  />
                  <span class="field-hint">{{ t('setting.lockDurationHint') }}</span>
                </div>
              </NFormItem>

              <NDivider />

              <h3 class="block-title">{{ t('setting.loginAdminBlock') }}</h3>
              <NFormItem :label="t('setting.captcha')">
                <NSwitch v-model:value="loginForm.admin.captchaEnabled" />
                <span class="field-hint">{{
                  loginForm.admin.captchaEnabled ? t('common.on') : t('common.off')
                }}</span>
              </NFormItem>
              <NFormItem v-if="loginForm.admin.captchaEnabled" :label="t('setting.captchaType')">
                <NSelect
                  v-model:value="loginForm.admin.captchaType"
                  :options="captchaTypeOptions"
                  style="width: 220px"
                />
              </NFormItem>
              <NFormItem :label="t('setting.maxAttempts')" required>
                <div class="number-row">
                  <NInputNumber
                    v-model:value="loginForm.admin.maxAttempts"
                    :min="1"
                    :max="100"
                    :step="1"
                    style="width: 160px"
                  />
                  <span class="field-hint">{{ t('setting.maxAttemptsHint') }}</span>
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.lockDuration')" required>
                <div class="number-row">
                  <NInputNumber
                    v-model:value="loginForm.admin.lockDurationMinutes"
                    :min="1"
                    :max="1440"
                    :step="1"
                    style="width: 160px"
                  />
                  <span class="field-hint">{{ t('setting.lockDurationHint') }}</span>
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.totpRequired')">
                <NSwitch v-model:value="loginForm.admin.totpRequired" />
                <span class="field-hint">{{ t('setting.totpRequiredHint') }}</span>
              </NFormItem>

              <NFormItem v-if="canEdit">
                <NSpace>
                  <NButton
                    type="primary"
                    class="lx-float-btn"
                    :loading="savingLogin"
                    @click="saveLogin"
                  >
                    {{ t('setting.saveLogin') }}
                  </NButton>
                  <NButton class="lx-float-btn" :disabled="savingLogin" @click="load">
                    {{ t('common.refresh') }}
                  </NButton>
                </NSpace>
              </NFormItem>
              <p v-else class="readonly-hint">{{ t('setting.readonlyHint') }}</p>
            </NForm>
          </NTabPane>

          <NTabPane name="password" :tab="t('setting.passwordTitle')">
            <p class="section-hint">{{ t('setting.passwordHint') }}</p>
            <NForm label-placement="left" label-width="180" :disabled="!canEdit">
              <NFormItem :label="t('setting.passwordMinLength')" required>
                <div class="number-row">
                  <NInputNumber
                    v-model:value="passwordForm.minLength"
                    :min="4"
                    :max="128"
                    :step="1"
                    style="width: 160px"
                  />
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.passwordMaxLength')" required>
                <div class="number-row">
                  <NInputNumber
                    v-model:value="passwordForm.maxLength"
                    :min="4"
                    :max="128"
                    :step="1"
                    style="width: 160px"
                  />
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.passwordRequireUpperLower')">
                <NSwitch v-model:value="passwordForm.requireUpperLower" />
                <span class="field-hint">{{
                  passwordForm.requireUpperLower ? t('common.on') : t('common.off')
                }}</span>
              </NFormItem>
              <NFormItem :label="t('setting.passwordRequireDigit')">
                <NSwitch v-model:value="passwordForm.requireDigit" />
                <span class="field-hint">{{
                  passwordForm.requireDigit ? t('common.on') : t('common.off')
                }}</span>
              </NFormItem>
              <NFormItem :label="t('setting.passwordRequireSpecial')">
                <NSwitch v-model:value="passwordForm.requireSpecial" />
                <span class="field-hint">{{
                  passwordForm.requireSpecial ? t('common.on') : t('common.off')
                }}</span>
              </NFormItem>
              <NFormItem v-if="canEdit">
                <NSpace>
                  <NButton
                    type="primary"
                    class="lx-float-btn"
                    :loading="savingPassword"
                    @click="savePassword"
                  >
                    {{ t('setting.savePassword') }}
                  </NButton>
                  <NButton class="lx-float-btn" :disabled="savingPassword" @click="load">
                    {{ t('common.refresh') }}
                  </NButton>
                </NSpace>
              </NFormItem>
              <p v-else class="readonly-hint">{{ t('setting.readonlyHint') }}</p>
            </NForm>
          </NTabPane>

          <NTabPane name="client" :tab="t('setting.clientTitle')">
            <p class="section-hint">{{ t('setting.clientHint') }}</p>
            <NForm label-placement="left" label-width="120" :disabled="!canEdit">
              <NFormItem :label="t('setting.appVersion')" required>
                <NInput
                  v-model:value="clientForm.appVersion"
                  :placeholder="t('setting.appVersionPh')"
                  style="max-width: 280px"
                />
              </NFormItem>
              <NFormItem :label="t('setting.channel')" required>
                <NSelect
                  v-model:value="clientForm.appChannel"
                  :options="channelOptions"
                  filterable
                  tag
                  :placeholder="t('setting.channelPh')"
                  style="max-width: 280px"
                />
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.channelHint') }}</p>
              <NFormItem :label="t('setting.forceUpdate')">
                <NSpace align="center">
                  <NSwitch v-model:value="clientForm.forceUpdate" />
                  <span class="field-hint">
                    {{ clientForm.forceUpdate ? t('common.on') : t('common.off') }}
                  </span>
                </NSpace>
              </NFormItem>
              <NFormItem :label="t('setting.minSupportedVersion')">
                <NInput
                  v-model:value="clientForm.minSupportedVersion"
                  :placeholder="t('setting.minSupportedVersionPh')"
                  style="max-width: 280px"
                />
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.minSupportedVersionHint') }}</p>
              <NFormItem :label="t('setting.downloadUrl')">
                <NInput
                  v-model:value="clientForm.downloadUrl"
                  :placeholder="t('setting.downloadUrlPh')"
                />
              </NFormItem>
              <NFormItem :label="t('setting.maxUpload')" required>
                <div class="upload-row">
                  <NInputNumber
                    v-model:value="clientForm.maxUploadMb"
                    :min="1"
                    :max="2048"
                    :step="1"
                    :precision="1"
                    style="width: 160px"
                  />
                  <span class="field-hint">MB</span>
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.sensitiveFilterEnabled')">
                <NSpace align="center">
                  <NSwitch v-model:value="clientForm.sensitiveFilterEnabled" />
                  <span class="field-hint">
                    {{ clientForm.sensitiveFilterEnabled ? t('common.on') : t('common.off') }}
                  </span>
                </NSpace>
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.sensitiveFilterHint') }}</p>
              <NFormItem :label="t('setting.supportEmail')">
                <NInput v-model:value="clientForm.supportEmail" style="max-width: 360px" />
              </NFormItem>
              <NFormItem :label="t('setting.supportPhone')">
                <NInput v-model:value="clientForm.supportPhone" style="max-width: 280px" />
              </NFormItem>
              <NFormItem :label="t('setting.feedbackSlaHours')">
                <div class="upload-row">
                  <NInputNumber
                    v-model:value="clientForm.feedbackSlaHours"
                    :min="1"
                    :max="720"
                    :step="1"
                    style="width: 160px"
                  />
                  <span class="field-hint">{{ t('setting.feedbackSlaHoursUnit') }}</span>
                </div>
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.feedbackSlaHint') }}</p>
              <NFormItem :label="t('setting.feedbackEscalationEnabled')">
                <NSpace align="center">
                  <NSwitch v-model:value="clientForm.feedbackEscalationEnabled" />
                  <span class="field-hint">
                    {{
                      clientForm.feedbackEscalationEnabled ? t('common.on') : t('common.off')
                    }}
                  </span>
                </NSpace>
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.feedbackEscalationHint') }}</p>
              <NFormItem
                v-if="clientForm.feedbackEscalationEnabled"
                :label="t('setting.feedbackEscalationAutoReassign')"
              >
                <NSpace align="center">
                  <NSwitch v-model:value="clientForm.feedbackEscalationAutoReassign" />
                  <span class="field-hint">
                    {{
                      clientForm.feedbackEscalationAutoReassign ? t('common.on') : t('common.off')
                    }}
                  </span>
                </NSpace>
              </NFormItem>
              <NFormItem
                v-if="clientForm.feedbackEscalationEnabled"
                :label="t('setting.feedbackEscalationIntervalHours')"
              >
                <div class="upload-row">
                  <NInputNumber
                    v-model:value="clientForm.feedbackEscalationIntervalHours"
                    :min="1"
                    :max="720"
                    :step="1"
                    style="width: 160px"
                  />
                  <span class="field-hint">{{ t('setting.feedbackSlaHoursUnit') }}</span>
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.reviewSlaHours')">
                <div class="upload-row">
                  <NInputNumber
                    v-model:value="clientForm.reviewSlaHours"
                    :min="1"
                    :max="720"
                    :step="1"
                    style="width: 160px"
                  />
                  <span class="field-hint">{{ t('setting.feedbackSlaHoursUnit') }}</span>
                </div>
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.reviewSlaHint') }}</p>
              <NFormItem :label="t('setting.reviewEscalationEnabled')">
                <NSpace align="center">
                  <NSwitch v-model:value="clientForm.reviewEscalationEnabled" />
                  <span class="field-hint">
                    {{
                      clientForm.reviewEscalationEnabled ? t('common.on') : t('common.off')
                    }}
                  </span>
                </NSpace>
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.reviewEscalationHint') }}</p>
              <NFormItem
                v-if="clientForm.reviewEscalationEnabled"
                :label="t('setting.reviewEscalationIntervalHours')"
              >
                <div class="upload-row">
                  <NInputNumber
                    v-model:value="clientForm.reviewEscalationIntervalHours"
                    :min="1"
                    :max="720"
                    :step="1"
                    style="width: 160px"
                  />
                  <span class="field-hint">{{ t('setting.feedbackSlaHoursUnit') }}</span>
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.releaseNotes')">
                <NInput
                  v-model:value="clientForm.releaseNotes"
                  type="textarea"
                  :rows="3"
                  :placeholder="t('setting.releaseNotesPh')"
                />
              </NFormItem>
              <NFormItem v-if="canEdit">
                <NSpace>
                  <NButton
                    type="primary"
                    class="lx-float-btn"
                    :loading="savingClient"
                    @click="saveClient"
                  >
                    {{ t('setting.saveClient') }}
                  </NButton>
                  <NButton class="lx-float-btn" :disabled="savingClient" @click="load">
                    {{ t('common.refresh') }}
                  </NButton>
                </NSpace>
              </NFormItem>
              <p v-else class="readonly-hint">{{ t('setting.readonlyHint') }}</p>
            </NForm>
          </NTabPane>

          <NTabPane name="mail" :tab="t('setting.mailTitle')">
            <p class="section-hint">{{ t('setting.mailHint') }}</p>
            <NForm label-placement="left" label-width="140" :disabled="!canEdit">
              <NFormItem :label="t('setting.mailHost')" required>
                <NInput
                  v-model:value="mailForm.host"
                  placeholder="smtp.qq.com"
                  style="max-width: 360px"
                />
              </NFormItem>
              <NFormItem :label="t('setting.mailPort')" required>
                <NInputNumber
                  v-model:value="mailForm.port"
                  :min="1"
                  :max="65535"
                  style="width: 160px"
                />
              </NFormItem>
              <NFormItem :label="t('setting.mailUsername')">
                <NInput v-model:value="mailForm.username" style="max-width: 360px" />
              </NFormItem>
              <NFormItem :label="t('setting.mailPassword')">
                <div class="password-row">
                  <NInput
                    v-model:value="mailForm.password"
                    type="password"
                    show-password-on="click"
                    :placeholder="t('setting.mailPasswordPh')"
                    style="max-width: 360px"
                  />
                  <span class="field-hint">{{
                    mailForm.passwordConfigured
                      ? t('setting.mailPasswordConfigured')
                      : t('setting.mailPasswordMissing')
                  }}</span>
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.mailFrom')" required>
                <NInput v-model:value="mailForm.from" style="max-width: 360px" />
              </NFormItem>
              <NFormItem :label="t('setting.mailFromName')">
                <NInput v-model:value="mailForm.fromName" style="max-width: 360px" />
              </NFormItem>
              <NFormItem :label="t('setting.mailStartTls')">
                <NSwitch :value="mailForm.startTls" @update:value="onMailStartTlsChange" />
                <span class="field-hint"
                  >{{ mailForm.startTls ? t('common.on') : t('common.off') }} ·
                  {{ t('setting.mailPortAuto587') }}</span
                >
              </NFormItem>
              <NFormItem :label="t('setting.mailSsl')">
                <NSwitch :value="mailForm.ssl" @update:value="onMailSslChange" />
                <span class="field-hint"
                  >{{ mailForm.ssl ? t('common.on') : t('common.off') }} ·
                  {{ t('setting.mailPortAuto465') }}</span
                >
              </NFormItem>
              <NFormItem :label="t('setting.mailCodeExpire')" required>
                <div class="number-row">
                  <NInputNumber
                    v-model:value="mailForm.codeExpireMinutes"
                    :min="1"
                    :max="1440"
                    style="width: 160px"
                  />
                  <span class="field-hint">{{ t('setting.mailCodeExpireHint') }}</span>
                </div>
              </NFormItem>
              <NFormItem v-if="canEdit">
                <NSpace>
                  <NButton
                    type="primary"
                    class="lx-float-btn"
                    :loading="savingMail"
                    @click="saveMail"
                  >
                    {{ t('setting.saveMail') }}
                  </NButton>
                  <NButton class="lx-float-btn" :disabled="savingMail" @click="load">
                    {{ t('common.refresh') }}
                  </NButton>
                  <NButton class="lx-float-btn" :loading="testingEmail" @click="openTestEmailModal">
                    {{ t('setting.testEmail') }}
                  </NButton>
                </NSpace>
              </NFormItem>
              <p v-else class="readonly-hint">{{ t('setting.readonlyHint') }}</p>
            </NForm>
          </NTabPane>

          <NTabPane name="mail-templates" :tab="t('setting.mailTemplatesTitle')">
            <p class="section-hint">{{ t('setting.mailTemplatesHint') }}</p>
            <NForm label-placement="top" :disabled="!canEdit">
              <template v-for="key in ['register', 'reset', 'welcome'] as const" :key="key">
                <NDivider v-if="key !== 'register'" />
                <div class="template-block">
                  <div class="template-head">
                    <h3 class="block-title">
                      {{
                        key === 'register'
                          ? t('setting.mailTemplateRegister')
                          : key === 'reset'
                            ? t('setting.mailTemplateReset')
                            : t('setting.mailTemplateWelcome')
                      }}
                    </h3>
                    <NTag
                      size="small"
                      :type="mailTemplatesForm[key].usingDefault ? 'default' : 'success'"
                      :bordered="false"
                    >
                      {{
                        mailTemplatesForm[key].usingDefault
                          ? t('setting.mailTemplateUsingDefault')
                          : t('setting.mailTemplateCustom')
                      }}
                    </NTag>
                    <NButton
                      v-if="canEdit"
                      size="small"
                      tertiary
                      class="template-reset-btn"
                      @click="resetMailTemplate(key)"
                    >
                      {{ t('setting.mailTemplateResetDefault') }}
                    </NButton>
                  </div>
                  <NFormItem :label="t('setting.mailTemplateSubject')">
                    <NInput
                      v-model:value="mailTemplatesForm[key].subject"
                      @update:value="onMailTemplateEdit(key)"
                    />
                  </NFormItem>
                  <NFormItem :label="t('setting.mailTemplateHtml')">
                    <NInput
                      v-model:value="mailTemplatesForm[key].html"
                      type="textarea"
                      :rows="10"
                      class="template-html"
                      @update:value="onMailTemplateEdit(key)"
                    />
                  </NFormItem>
                </div>
              </template>
              <NFormItem v-if="canEdit">
                <NSpace>
                  <NButton
                    type="primary"
                    class="lx-float-btn"
                    :loading="savingMailTemplates"
                    @click="saveMailTemplates"
                  >
                    {{ t('setting.saveMailTemplates') }}
                  </NButton>
                  <NButton
                    class="lx-float-btn"
                    :disabled="savingMailTemplates"
                    @click="load"
                  >
                    {{ t('common.refresh') }}
                  </NButton>
                </NSpace>
              </NFormItem>
              <p v-else class="readonly-hint">{{ t('setting.readonlyHint') }}</p>
            </NForm>
          </NTabPane>

          <NTabPane name="security" :tab="t('setting.securityTitle')">
            <p class="section-hint">{{ t('setting.securityHint') }}</p>
            <NForm label-placement="left" label-width="180" :disabled="!canEdit">
              <NFormItem :label="t('setting.apiSignEnabled')">
                <NSpace align="center">
                  <NSwitch v-model:value="securityForm.apiSignEnabled" />
                  <span class="field-hint">
                    {{ securityForm.apiSignEnabled ? t('common.on') : t('common.off') }}
                  </span>
                </NSpace>
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.apiSignHint') }}</p>
              <NFormItem :label="t('setting.apiEncryptEnabled')">
                <NSpace align="center">
                  <NSwitch v-model:value="securityForm.apiEncryptEnabled" />
                  <span class="field-hint">
                    {{ securityForm.apiEncryptEnabled ? t('common.on') : t('common.off') }}
                  </span>
                </NSpace>
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.apiEncryptHint') }}</p>
              <NFormItem :label="t('setting.disableFrontendDebug')">
                <NSpace align="center">
                  <NSwitch v-model:value="securityForm.disableFrontendDebug" />
                  <span class="field-hint">
                    {{ securityForm.disableFrontendDebug ? t('common.on') : t('common.off') }}
                  </span>
                </NSpace>
              </NFormItem>
              <p class="field-hint channel-hint">{{ t('setting.disableFrontendDebugHint') }}</p>
              <NFormItem v-if="canEdit">
                <NSpace>
                  <NButton
                    type="primary"
                    class="lx-float-btn"
                    :loading="savingSecurity"
                    @click="saveSecurity"
                  >
                    {{ t('setting.saveSecurity') }}
                  </NButton>
                  <NButton class="lx-float-btn" :disabled="savingSecurity" @click="load">
                    {{ t('common.refresh') }}
                  </NButton>
                </NSpace>
              </NFormItem>
              <p v-else class="readonly-hint">{{ t('setting.readonlyHint') }}</p>
            </NForm>
          </NTabPane>

          <NTabPane name="watermark" :tab="t('setting.watermarkTitle')">
            <p class="section-hint">{{ t('setting.watermarkHint') }}</p>
            <NForm label-placement="left" label-width="120">
              <NFormItem :label="t('setting.watermarkEnabled')">
                <NSwitch :value="watermarkEnabled" @update:value="prefs.setWatermarkEnabled" />
              </NFormItem>
              <NFormItem :label="t('setting.watermarkFullscreen')">
                <NSwitch
                  :value="watermarkFullscreen"
                  :disabled="!watermarkEnabled"
                  @update:value="prefs.setWatermarkFullscreen"
                />
              </NFormItem>
              <NFormItem :label="t('setting.watermarkOpacity')">
                <div class="opacity-row">
                  <NSlider
                    v-model:value="opacityPercent"
                    :min="2"
                    :max="50"
                    :step="1"
                    :disabled="!watermarkEnabled"
                    style="flex: 1"
                  />
                  <span class="opacity-value">{{ opacityPercent }}%</span>
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.watermarkLines')">
                <NInput
                  v-model:value="linesText"
                  type="textarea"
                  :rows="4"
                  :placeholder="t('setting.watermarkLinesPlaceholder')"
                  :disabled="!watermarkEnabled"
                />
              </NFormItem>
              <NFormItem :label="t('setting.watermarkLineCount')">
                <span>{{ lineCount || t('setting.watermarkDefault') }}</span>
              </NFormItem>
              <NFormItem>
                <NSpace>
                  <NButton
                    type="primary"
                    class="lx-float-btn"
                    :disabled="!watermarkEnabled"
                    @click="saveWatermark"
                  >
                    {{ t('common.save') }}
                  </NButton>
                  <NButton
                    class="lx-float-btn"
                    :disabled="!watermarkEnabled"
                    @click="resetWatermark"
                  >
                    {{ t('setting.watermarkResetBtn') }}
                  </NButton>
                </NSpace>
              </NFormItem>
            </NForm>
          </NTabPane>

          <NTabPane name="sound" :tab="t('setting.soundTitle')">
            <p class="section-hint">{{ t('setting.soundHint') }}</p>
            <NForm label-placement="left" label-width="120">
              <NFormItem :label="t('setting.voiceNotifyEnabled')">
                <div class="switch-with-action">
                  <NSwitch :value="voiceNotifyEnabled" @update:value="onVoiceToggle" />
                  <NButton
                    size="small"
                    quaternary
                    class="test-btn"
                    :disabled="!voiceNotifyEnabled"
                    @click="previewVoice"
                  >
                    {{ t('setting.voicePreview') }}
                  </NButton>
                </div>
              </NFormItem>
              <NFormItem :label="t('setting.speechVoice')">
                <div class="tone-grid">
                  <div
                    class="tone-card"
                    :class="{ 'tone-card--active': !speechVoiceUri }"
                  >
                    <button type="button" class="tone-card__select" @click="pickSpeechVoice('')">
                      <span class="tone-card__label">{{ t('setting.voiceAuto') }}</span>
                      <span class="tone-card__desc">{{ t('setting.voiceAutoDesc') }}</span>
                    </button>
                    <NButton
                      size="small"
                      quaternary
                      class="tone-card__play"
                      @click="pickSpeechVoice('')"
                    >
                      <template #icon>
                        <NIcon :component="PlayCircleOutline" />
                      </template>
                      {{ t('setting.voicePreview') }}
                    </NButton>
                  </div>
                  <div
                    v-for="voice in voiceOptions"
                    :key="voice.uri"
                    class="tone-card"
                    :class="{ 'tone-card--active': isVoiceSelected(voice.uri) }"
                  >
                    <button
                      type="button"
                      class="tone-card__select"
                      @click="pickSpeechVoice(voice.uri)"
                    >
                      <span class="tone-card__label">{{ voice.name }}</span>
                      <span class="tone-card__desc">
                        {{ voice.lang }}{{ voice.local ? ` · ${t('setting.voiceLocal')}` : '' }}
                      </span>
                    </button>
                    <NButton
                      size="small"
                      quaternary
                      class="tone-card__play"
                      @click="pickSpeechVoice(voice.uri)"
                    >
                      <template #icon>
                        <NIcon :component="PlayCircleOutline" />
                      </template>
                      {{ t('setting.voicePreview') }}
                    </NButton>
                  </div>
                </div>
                <p v-if="!voiceOptions.length" class="section-hint voice-empty">
                  {{ t('setting.voiceEmpty') }}
                </p>
              </NFormItem>
            </NForm>
          </NTabPane>
        </NTabs>
      </NSpin>
    </div>

    <NModal
      v-model:show="showTestEmailModal"
      preset="dialog"
      :title="t('setting.testEmailTitle')"
      :positive-text="t('setting.testEmailSend')"
      :negative-text="t('common.cancel')"
      :loading="testingEmail"
      @positive-click="runTestEmail"
    >
      <p class="section-hint">{{ t('setting.testEmailHint') }}</p>
      <NInput
        v-model:value="testEmail"
        type="text"
        :placeholder="t('setting.testEmailPh')"
        @keydown.enter.prevent="runTestEmail"
      />
    </NModal>
  </div>
</template>

<style scoped>
.section-hint {
  margin: 4px 0 16px;
  color: var(--lx-text-3);
  font-size: 13px;
}
.block-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text);
}
.opacity-row,
.upload-row,
.number-row,
.password-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 520px;
}
.switch-with-action {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.test-btn {
  margin-left: 12px;
}
.opacity-value {
  width: 42px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--lx-text-2);
}
.field-hint {
  margin-left: 10px;
  color: var(--lx-text-3);
  font-size: 13px;
}
.channel-hint {
  margin: -4px 0 12px 120px;
}
.readonly-hint {
  margin: 0;
  color: var(--lx-text-3);
  font-size: 13px;
}
.tone-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  width: 100%;
  max-width: 720px;
}
.tone-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--n-border-color);
  border-radius: var(--lx-radius, 8px);
  background: var(--n-color-embedded);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.tone-card--active {
  border-color: var(--n-primary-color);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--n-primary-color) 35%, transparent);
}
.tone-card__select {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: inherit;
  padding: 0;
}
.tone-card__label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}
.tone-card__desc {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--lx-text-3);
  line-height: 1.4;
}
.tone-card__play {
  flex-shrink: 0;
}
.voice-empty {
  margin: 8px 0 0;
}
.template-block {
  margin-bottom: 8px;
}
.template-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.template-head .block-title {
  margin: 0;
}
.template-reset-btn {
  margin-left: auto;
}
.template-html :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}
</style>
