<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDivider,
  NForm,
  NFormItem,
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
  useMessage,
} from 'naive-ui'
import { storeToRefs } from 'pinia'
import {
  fetchSettings,
  testForgotPasswordEmail,
  updateClientSideSettings,
  updateLoginSettings,
  updateRegisterSettings,
  type AdminSetting,
} from '@/api/settings'
import { useAuthStore } from '@/stores/auth'
import { usePreferencesStore } from '@/stores/preferences'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()
const prefs = usePreferencesStore()
const { watermarkEnabled, watermarkFullscreen, watermarkLines, watermarkOpacity } =
  storeToRefs(prefs)

const loading = ref(false)
const savingRegister = ref(false)
const savingLogin = ref(false)
const savingClient = ref(false)
const testingEmail = ref(false)
const showTestEmailModal = ref(false)
const testEmail = ref('')
const tabNames = new Set(['register', 'login', 'client', 'watermark'])
const activeTab = ref(
  tabNames.has(String(route.query.tab || '')) ? String(route.query.tab) : 'register',
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
  },
)

const canEdit = computed(() => auth.hasPermission('admin:setting:edit'))

const registerForm = reactive({
  registerEnabled: true,
  forgotPasswordEmailEnabled: true,
})

const loginForm = reactive({
  client: {
    captchaEnabled: true,
    maxAttempts: 5,
    lockDurationMinutes: 10,
  },
  admin: {
    captchaEnabled: true,
    maxAttempts: 5,
    lockDurationMinutes: 10,
  },
})

const clientForm = reactive({
  captchaEnabled: true,
  appVersion: '',
  appChannel: 'stable',
  downloadUrl: '',
  maxUploadMb: 100,
  releaseNotes: '',
})

const channelOptions = [
  { label: 'stable', value: 'stable' },
  { label: 'beta', value: 'beta' },
  { label: 'dev', value: 'dev' },
]

const linesText = ref(watermarkLines.value.join('\n'))

watch(watermarkLines, (lines) => {
  linesText.value = lines.join('\n')
})

const lineCount = computed(() =>
  linesText.value
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean).length,
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
  loginForm.client.maxAttempts = data.login?.client?.maxAttempts ?? 5
  loginForm.client.lockDurationMinutes = data.login?.client?.lockDurationMinutes ?? 10
  loginForm.admin.captchaEnabled =
    data.login?.admin?.captchaEnabled ?? data.admin?.captchaEnabled !== false
  loginForm.admin.maxAttempts = data.login?.admin?.maxAttempts ?? 5
  loginForm.admin.lockDurationMinutes = data.login?.admin?.lockDurationMinutes ?? 10

  clientForm.captchaEnabled = loginForm.client.captchaEnabled
  clientForm.appVersion = data.client?.appVersion || ''
  clientForm.appChannel = data.client?.appChannel || 'stable'
  clientForm.downloadUrl = data.client?.downloadUrl || ''
  clientForm.releaseNotes = data.client?.releaseNotes || ''
  clientForm.maxUploadMb = data.client?.maxUploadBytes
    ? Math.round((data.client.maxUploadBytes / (1024 * 1024)) * 10) / 10
    : 100
}

async function load() {
  loading.value = true
  try {
    applySettings(await fetchSettings())
  } finally {
    loading.value = false
  }
}

async function saveRegister() {
  if (!canEdit.value) return
  savingRegister.value = true
  try {
    applySettings(
      await updateRegisterSettings({
        registerEnabled: registerForm.registerEnabled,
        forgotPasswordEmailEnabled: registerForm.forgotPasswordEmailEnabled,
      }),
    )
    message.success(t('setting.registerSaved'))
  } finally {
    savingRegister.value = false
  }
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
  savingLogin.value = true
  try {
    applySettings(
      await updateLoginSettings({
        client: {
          captchaEnabled: loginForm.client.captchaEnabled,
          maxAttempts: loginForm.client.maxAttempts,
          lockDurationMinutes: loginForm.client.lockDurationMinutes,
        },
        admin: {
          captchaEnabled: loginForm.admin.captchaEnabled,
          maxAttempts: loginForm.admin.maxAttempts,
          lockDurationMinutes: loginForm.admin.lockDurationMinutes,
        },
      }),
    )
    message.success(t('setting.loginSaved'))
  } finally {
    savingLogin.value = false
  }
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
  savingClient.value = true
  try {
    applySettings(
      await updateClientSideSettings({
        captchaEnabled: loginForm.client.captchaEnabled,
        appVersion: clientForm.appVersion.trim(),
        appChannel: clientForm.appChannel.trim(),
        downloadUrl: clientForm.downloadUrl.trim() || undefined,
        releaseNotes: clientForm.releaseNotes.trim() || undefined,
        maxUploadBytes: Math.round(clientForm.maxUploadMb * 1024 * 1024),
      }),
    )
    message.success(t('setting.clientSaved'))
  } finally {
    savingClient.value = false
  }
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
              <NFormItem :label="t('setting.downloadUrl')">
                <NInput v-model:value="clientForm.downloadUrl" :placeholder="t('setting.downloadUrlPh')" />
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
                  <NButton type="primary" class="lx-float-btn" :loading="savingClient" @click="saveClient">
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

          <NTabPane name="watermark" :tab="t('setting.watermarkTitle')">
            <p class="section-hint">{{ t('setting.watermarkHint') }}</p>
            <NForm label-placement="left" label-width="120">
              <NFormItem :label="t('setting.watermarkEnabled')">
                <NSwitch
                  :value="watermarkEnabled"
                  @update:value="prefs.setWatermarkEnabled"
                />
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
                  <NButton type="primary" class="lx-float-btn" :disabled="!watermarkEnabled" @click="saveWatermark">
                    {{ t('common.save') }}
                  </NButton>
                  <NButton class="lx-float-btn" :disabled="!watermarkEnabled" @click="resetWatermark">
                    {{ t('setting.watermarkResetBtn') }}
                  </NButton>
                </NSpace>
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
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-1);
}
.opacity-row,
.upload-row,
.number-row {
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
.readonly-hint {
  margin: 0;
  color: var(--lx-text-3);
  font-size: 13px;
}
</style>
