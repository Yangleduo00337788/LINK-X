<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { NIcon, NInput, NTag, useMessage, useDialog } from 'naive-ui'
import { LxButton, LxIconButton, LxGroupCard, LxModal } from '../ui'
import {
  ChevronForwardOutline,
  CreateOutline,
  CopyOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../stores/app'
import { useChatModalsStore } from '../../stores/chatModals'
import * as accountApi from '../../api/account'
import * as authApi from '../../api/auth'
import * as feedbackApi from '../../api/feedback'
import * as complianceApi from '../../api/compliance'
import { validatePassword, validateUsername } from '../../utils/validation'
import { copyText } from '../../utils/clipboard'
import { useI18n } from '../../i18n'
import SliderCaptcha from '../SliderCaptcha.vue'
import Avatar from '../Avatar.vue'
import { resolveUserAvatarUrl } from '../../utils/defaultAvatar'

const message = useMessage()
const dialog = useDialog()
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const { t } = useI18n()

const { userProfile, savedLogin } = storeToRefs(appStore)

const displayUsername = computed(
  () => userProfile.value.username || savedLogin.value.username || '—'
)

const profileAvatarUrl = computed(() =>
  resolveUserAvatarUrl(userProfile.value.avatar, userProfile.value.userId) || undefined
)

const phoneDisplay = computed(() =>
  userProfile.value.phoneBound
    ? userProfile.value.phone || t('account.bound')
    : t('account.unbound')
)
const emailDisplay = computed(() =>
  userProfile.value.emailBound
    ? userProfile.value.email || t('account.bound')
    : t('account.unbound')
)

const genderDisplay = computed(() => {
  const g = userProfile.value.gender
  if (g === '女') return t('modals.female')
  if (g === '男') return t('modals.male')
  return t('modals.notFilled')
})

const birthdayDisplay = computed(() => {
  const ts = userProfile.value.birthday
  if (ts == null) return t('modals.notFilled')
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return t('modals.notFilled')
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
})

const locationDisplay = computed(() => {
  const text = [userProfile.value.country, userProfile.value.province, userProfile.value.region]
    .map(p => (p || '').trim())
    .filter(p => p && p !== '请选择')
    .join(' · ')
  return text || t('modals.notFilled')
})

async function copyLinkxId() {
  const text = String(displayUsername.value)
  if (!text || text === '—') {
    message.warning(t('account.copyEmpty'))
    return
  }
  const ok = await copyText(text)
  if (ok) message.success(t('account.linkxIdCopied'))
  else message.error(t('account.copyFail'))
}

function openEditProfile() {
  chatModalsStore.openEditProfile()
}

function applyBoundProfile(data: Parameters<typeof appStore.applyUserProfile>[0]) {
  appStore.applyUserProfile(data)
}

const showPhoneModal = ref(false)
const phoneForm = ref({ phone: '', password: '' })
const phoneLoading = ref(false)

const showLinkxIdModal = ref(false)
const linkxIdForm = ref({ username: '', password: '' })
const linkxIdLoading = ref(false)

function openLinkxIdModal() {
  linkxIdForm.value = {
    username: displayUsername.value === '—' ? '' : displayUsername.value,
    password: ''
  }
  showLinkxIdModal.value = true
}

async function submitChangeLinkxId() {
  const usernameErr = validateUsername(linkxIdForm.value.username)
  if (usernameErr) {
    message.warning(usernameErr)
    return
  }
  if (!linkxIdForm.value.password) {
    message.warning(t('account.passwordRequired'))
    return
  }
  linkxIdLoading.value = true
  try {
    const res = await accountApi.changeUsername({
      username: linkxIdForm.value.username.trim(),
      password: linkxIdForm.value.password
    })
    if (res.code === 200 && res.data) {
      applyBoundProfile(res.data)
      message.success(t('account.linkxIdChanged'))
      showLinkxIdModal.value = false
    } else {
      message.error(res.message || t('account.linkxIdChangeFail'))
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('account.linkxIdChangeFail'))
  } finally {
    linkxIdLoading.value = false
  }
}

function openPhoneModal() {
  phoneForm.value = { phone: '', password: '' }
  showPhoneModal.value = true
}

async function submitBindPhone() {
  if (!/^1[3-9]\d{9}$/.test(phoneForm.value.phone.trim())) {
    message.warning(t('account.phoneInvalid'))
    return
  }
  if (!phoneForm.value.password) {
    message.warning(t('account.passwordRequired'))
    return
  }
  phoneLoading.value = true
  try {
    const res = await accountApi.bindPhone({
      phone: phoneForm.value.phone.trim(),
      password: phoneForm.value.password
    })
    if (res.code === 200 && res.data) {
      applyBoundProfile(res.data)
      message.success(t('account.phoneBoundOk'))
      showPhoneModal.value = false
    } else {
      message.error(res.message || t('account.bindFail'))
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('account.bindFail'))
  } finally {
    phoneLoading.value = false
  }
}

const showEmailModal = ref(false)
const emailForm = ref({ email: '', code: '' })
const emailLoading = ref(false)
const emailCodeSending = ref(false)
const emailCodeCooldown = ref(0)
let emailCooldownTimer: ReturnType<typeof setInterval> | null = null

function openEmailModal() {
  emailForm.value = { email: '', code: '' }
  showEmailModal.value = true
}

async function sendEmailCode() {
  const email = emailForm.value.email.trim()
  if (!/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(email)) {
    message.warning(t('account.emailInvalid'))
    return
  }
  emailCodeSending.value = true
  try {
    const res = await accountApi.sendBindEmailCode(email)
    if (res.code === 200) {
      message.success(t('account.codeSent'))
      emailCodeCooldown.value = 60
      if (emailCooldownTimer) clearInterval(emailCooldownTimer)
      emailCooldownTimer = setInterval(() => {
        emailCodeCooldown.value -= 1
        if (emailCodeCooldown.value <= 0 && emailCooldownTimer) {
          clearInterval(emailCooldownTimer)
          emailCooldownTimer = null
        }
      }, 1000)
    } else {
      message.error(res.message || t('account.sendFail'))
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('account.sendFail'))
  } finally {
    emailCodeSending.value = false
  }
}

async function submitBindEmail() {
  if (!emailForm.value.email.trim() || !emailForm.value.code.trim()) {
    message.warning(t('account.emailCodeRequired'))
    return
  }
  emailLoading.value = true
  try {
    const res = await accountApi.bindEmail({
      email: emailForm.value.email.trim(),
      code: emailForm.value.code.trim()
    })
    if (res.code === 200 && res.data) {
      applyBoundProfile(res.data)
      message.success(t('account.emailBoundOk'))
      showEmailModal.value = false
    } else {
      message.error(res.message || t('account.bindFail'))
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('account.bindFail'))
  } finally {
    emailLoading.value = false
  }
}

const showDeleteModal = ref(false)
const deletePassword = ref('')
const deleteLoading = ref(false)

function openDeleteModal() {
  deletePassword.value = ''
  showDeleteModal.value = true
}

async function submitDeleteAccount() {
  if (!deletePassword.value) {
    message.warning(t('account.deletePasswordRequired'))
    return
  }
  deleteLoading.value = true
  try {
    const res = await accountApi.deleteAccount({ password: deletePassword.value })
    if (res.code === 200) {
      message.success(t('account.deletedOk'))
      showDeleteModal.value = false
      await appStore.logout()
    } else {
      message.error(res.message || t('account.deleteFail'))
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('account.deleteFail'))
  } finally {
    deleteLoading.value = false
  }
}

const showFeedbackHistory = ref(false)
const feedbackList = ref<feedbackApi.FeedbackVO[]>([])
const feedbackLoading = ref(false)
const expandedFeedbackId = ref<string | null>(null)
const feedbackDetails = ref<Record<string, feedbackApi.FeedbackVO>>({})
const followUpDraft = ref<Record<string, string>>({})
const followUpLoadingId = ref<string | null>(null)

async function openFeedbackHistory() {
  showFeedbackHistory.value = true
  feedbackLoading.value = true
  expandedFeedbackId.value = null
  feedbackDetails.value = {}
  followUpDraft.value = {}
  try {
    const res = await feedbackApi.listFeedback()
    if (res.code === 200 && res.data) {
      feedbackList.value = res.data
    }
  } catch {
    message.error(t('account.feedbackListFail'))
  } finally {
    feedbackLoading.value = false
  }
}

async function toggleFeedbackDetail(id: string) {
  if (expandedFeedbackId.value === id) {
    expandedFeedbackId.value = null
    return
  }
  expandedFeedbackId.value = id
  if (feedbackDetails.value[id]) return
  try {
    const res = await feedbackApi.getFeedbackDetail(id)
    if (res.code === 200 && res.data) {
      feedbackDetails.value = { ...feedbackDetails.value, [id]: res.data }
    }
  } catch {
    message.error(t('account.feedbackListFail'))
  }
}

async function submitFollowUp(id: string) {
  const content = (followUpDraft.value[id] || '').trim()
  if (!content) {
    message.warning(t('account.followUpRequired'))
    return
  }
  followUpLoadingId.value = id
  try {
    const res = await feedbackApi.followUpFeedback(id, content)
    if (res.code === 200) {
      message.success(t('account.followUpSuccess'))
      followUpDraft.value = { ...followUpDraft.value, [id]: '' }
      const detailRes = await feedbackApi.getFeedbackDetail(id)
      if (detailRes.code === 200 && detailRes.data) {
        feedbackDetails.value = { ...feedbackDetails.value, [id]: detailRes.data }
        feedbackList.value = feedbackList.value.map((item) =>
          item.id === id ? { ...item, status: detailRes.data.status } : item
        )
      }
    } else {
      message.error(res.message || t('account.followUpFail'))
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('account.followUpFail'))
  } finally {
    followUpLoadingId.value = null
  }
}

function getFeedbackStatusType(status: string) {
  if (status === 'resolved' || status === 'replied') return 'success'
  if (status === 'processing' || status === 'pending') return 'warning'
  if (status === 'closed') return 'default'
  return 'default'
}

function getFeedbackStatusText(status: string) {
  if (status === 'resolved') return t('account.statusResolved')
  if (status === 'replied') return t('account.statusReplied')
  if (status === 'closed') return t('account.statusClosed')
  if (status === 'processing') return t('account.statusProcessing')
  return t('account.statusPending')
}

function getFeedbackTypeText(type: string) {
  if (type === 'bug') return t('account.typeBug')
  if (type === 'suggestion') return t('account.typeSuggestion')
  return t('account.typeOther')
}

const showPasswordModal = ref(false)
const passwordForm = ref({
  newPassword: '',
  confirmPassword: '',
  captchaId: '',
  captchaCode: '',
  captchaImage: '',
  captchaType: 'image' as 'image' | 'slider',
  puzzleImage: '',
  puzzleY: 0,
})
const passwordLoading = ref(false)
const passwordCaptchaLoading = ref(false)
/** 与 CAPTCHA_ENABLED / GET /auth/config 对齐 */
const captchaEnabled = ref(true)
const passwordPolicy = ref({
  minLength: 8,
  maxLength: 64,
  requireUpperLower: false,
  requireDigit: true,
  requireSpecial: false,
})

async function loadAuthConfig() {
  try {
    const res = await authApi.fetchAuthConfig()
    if (res.code === 200 && res.data) {
      captchaEnabled.value = !!res.data.captchaEnabled
      const p = res.data.passwordPolicy
      if (p) {
        passwordPolicy.value = {
          minLength: p.minLength ?? 8,
          maxLength: p.maxLength ?? 64,
          requireUpperLower: p.requireUpperLower === true,
          requireDigit: p.requireDigit !== false,
          requireSpecial: p.requireSpecial === true,
        }
      }
    }
  } catch {
    captchaEnabled.value = true
  }
}

async function loadResetCaptcha() {
  if (!captchaEnabled.value) return
  passwordCaptchaLoading.value = true
  try {
    const res = await authApi.fetchResetPasswordCaptcha()
    if (res.code === 200 && res.data) {
      passwordForm.value.captchaId = res.data.captchaId
      passwordForm.value.captchaImage = res.data.imageBase64
      passwordForm.value.captchaType = res.data.type === 'slider' ? 'slider' : 'image'
      passwordForm.value.puzzleImage = res.data.puzzleImageBase64 || ''
      passwordForm.value.puzzleY = res.data.puzzleY ?? 0
      passwordForm.value.captchaCode = ''
    } else {
      message.error(res.message || t('account.captchaFail'))
    }
  } catch {
    message.error(t('account.captchaFail'))
  } finally {
    passwordCaptchaLoading.value = false
  }
}

function onPasswordSliderSuccess(offset: number) {
  passwordForm.value.captchaCode = String(offset)
}

async function handleChangePassword() {
  if (!passwordForm.value.newPassword) {
    message.warning(t('account.passwordRequiredNew'))
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    message.warning(t('account.passwordMismatch'))
    return
  }
  const passErr = validatePassword(passwordForm.value.newPassword, passwordPolicy.value)
  if (passErr) {
    message.warning(passErr)
    return
  }
  if (captchaEnabled.value && (!passwordForm.value.captchaId || !passwordForm.value.captchaCode)) {
    message.warning(t('account.captchaRequired'))
    return
  }

  passwordLoading.value = true
  try {
    const res = await authApi.resetPassword({
      newPassword: passwordForm.value.newPassword,
      ...(captchaEnabled.value
        ? {
            captchaId: passwordForm.value.captchaId,
            captchaCode: passwordForm.value.captchaCode
          }
        : {})
    })
    if (res.code === 200) {
      message.success(t('account.passwordChanged'))
      showPasswordModal.value = false
      passwordForm.value = {
        newPassword: '',
        confirmPassword: '',
        captchaId: '',
        captchaCode: '',
        captchaImage: '',
        captchaType: 'image',
        puzzleImage: '',
        puzzleY: 0,
      }
    } else {
      message.error(res.message || t('account.passwordChangeFail'))
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('account.passwordChangeFail'))
    void loadResetCaptcha()
  } finally {
    passwordLoading.value = false
  }
}

function openPasswordModal() {
  passwordForm.value = {
    newPassword: '',
    confirmPassword: '',
    captchaId: '',
    captchaCode: '',
    captchaImage: '',
    captchaType: 'image',
    puzzleImage: '',
    puzzleY: 0,
  }
  showPasswordModal.value = true
  void loadResetCaptcha()
}

const exporting = ref(false)

async function exportMyData() {
  if (exporting.value) return
  exporting.value = true
  try {
    const res = await complianceApi.exportUserData()
    if (res.code !== 200 || !res.data) throw new Error(res.message || 'export failed')
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `linkx-export-${res.data.username || 'user'}-${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
    message.success(t('account.exportOk'))
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('account.exportFail'))
  } finally {
    exporting.value = false
  }
}

const showPurgeModal = ref(false)
const purgePassword = ref('')
const purgeLoading = ref(false)

function openPurgeModal() {
  purgePassword.value = ''
  showPurgeModal.value = true
}

async function submitPurgeData() {
  if (!purgePassword.value) {
    message.warning(t('account.passwordRequired'))
    return
  }
  purgeLoading.value = true
  try {
    const res = await complianceApi.purgeUserData(purgePassword.value)
    if (res.code !== 200) throw new Error(res.message || 'purge failed')
    showPurgeModal.value = false
    message.success(t('account.purgeOk'))
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('account.purgeFail'))
  } finally {
    purgeLoading.value = false
  }
}

function confirmPurgeData() {
  openPurgeModal()
}

const showDeviceModal = ref(false)
const devices = ref<accountApi.DeviceInfo[]>([])
const deviceLoading = ref(false)

async function openDeviceModal() {
  showDeviceModal.value = true
  deviceLoading.value = true
  try {
    const res = await accountApi.listDevices()
    if (res.code === 200 && res.data) {
      devices.value = res.data
    }
  } catch {
    message.error(t('account.deviceListFail'))
  } finally {
    deviceLoading.value = false
  }
}

async function handleLogoutDevice(deviceId: string) {
  dialog.warning({
    title: t('account.logoutDeviceTitle'),
    content: t('account.logoutDeviceContent'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        const res = await accountApi.logoutDevice(deviceId)
        if (res.code === 200) {
          message.success(t('account.deviceOffline'))
          devices.value = devices.value.filter(d => d.id !== deviceId)
        }
      } catch {
        message.error(t('account.opFail'))
      }
    }
  })
}

onMounted(() => {
  void loadAuthConfig()
})
</script>

<template>
  <div class="settings-scroll">
    <LxGroupCard tag="section" variant="settings" class="account-card">
      <div class="account-card-head">
        <span class="group-title">{{ t('account.infoTitle') }}</span>
        <LxButton variant="sm" @click="openEditProfile">{{ t('account.editProfile') }}</LxButton>
      </div>

      <div class="profile-block">
        <Avatar
          :size="56"
          color="var(--lx-accent)"
          :text="userProfile.nickname || t('common.me')"
          :image-url="profileAvatarUrl"
          class="profile-avatar"
        />
        <div class="profile-meta">
          <div class="profile-name-row">
            <span class="profile-name">{{ userProfile.nickname || t('account.noNickname') }}</span>
          </div>
          <div class="profile-id-row">
            <span>{{ t('modals.linkxId', { id: displayUsername }) }}</span>
            <LxIconButton class="copy-btn" :title="t('account.copyLinkxId')" @click="copyLinkxId">
              <n-icon :component="CopyOutline" :size="14" />
            </LxIconButton>
            <LxIconButton class="copy-btn" :title="t('account.editLinkxId')" @click="openLinkxIdModal">
              <n-icon :component="CreateOutline" :size="14" />
            </LxIconButton>
          </div>
        </div>
      </div>

      <button type="button" class="link-row" @click="openLinkxIdModal">
        <span class="link-label">{{ t('account.linkxIdLabel') }}</span>
        <div class="link-text compact">
          <span class="link-value">{{ displayUsername }}</span>
          <span class="link-desc">{{ t('account.linkxIdDesc') }}</span>
        </div>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>

      <button type="button" class="link-row" @click="openEditProfile">
        <span class="link-label">{{ t('modals.gender') }}</span>
        <span class="link-value" :class="{ muted: !userProfile.gender }">{{ genderDisplay }}</span>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
      <button type="button" class="link-row" @click="openEditProfile">
        <span class="link-label">{{ t('modals.birthday') }}</span>
        <span class="link-value" :class="{ muted: userProfile.birthday == null }">{{ birthdayDisplay }}</span>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
      <button type="button" class="link-row" @click="openEditProfile">
        <span class="link-label">{{ t('modals.location') }}</span>
        <span
          class="link-value"
          :class="{ muted: !userProfile.province && !userProfile.region }"
        >{{ locationDisplay }}</span>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>

      <button type="button" class="link-row" @click="openPhoneModal">
        <span class="link-label">{{ t('account.phone') }}</span>
        <span class="link-value" :class="{ muted: !userProfile.phoneBound }">{{ phoneDisplay }}</span>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
      <button type="button" class="link-row" @click="openEmailModal">
        <span class="link-label">{{ t('account.email') }}</span>
        <span class="link-value" :class="{ muted: !userProfile.emailBound }">{{ emailDisplay }}</span>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
      <button type="button" class="link-row" @click="openPasswordModal">
        <span class="link-label">{{ t('account.password') }}</span>
        <span class="link-value">{{ t('account.passwordSet') }}</span>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
      <button type="button" class="link-row" @click="openDeviceModal">
        <span class="link-label">{{ t('account.security') }}</span>
        <div class="link-text compact">
          <span class="link-value success">{{ t('account.deviceManage') }}</span>
          <span class="link-desc">{{ t('account.securityDesc') }}</span>
        </div>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
      <button type="button" class="link-row" @click="openFeedbackHistory">
        <span class="link-label">{{ t('account.feedback') }}</span>
        <span class="link-value">{{ t('account.viewRecords') }}</span>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
      <button type="button" class="link-row" :disabled="exporting" @click="exportMyData">
        <span class="link-label">{{ t('account.exportData') }}</span>
        <div class="link-text compact">
          <span class="link-value">{{ exporting ? '...' : t('account.exportDataValue') }}</span>
          <span class="link-desc">{{ t('account.exportDataDesc') }}</span>
        </div>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
      <button type="button" class="link-row" @click="confirmPurgeData">
        <span class="link-label">{{ t('account.purgeData') }}</span>
        <div class="link-text compact">
          <span class="link-desc">{{ t('account.purgeDataDesc') }}</span>
        </div>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
      <button type="button" class="link-row danger-row" @click="openDeleteModal">
        <div class="link-text">
          <span class="link-label">{{ t('account.deleteAccount') }}</span>
          <span class="link-desc">{{ t('account.deleteWarnShort') }}</span>
        </div>
        <div class="link-text compact right">
          <span class="link-desc">{{ t('account.deleteRiskDesc') }}</span>
        </div>
        <n-icon :component="ChevronForwardOutline" :size="16" class="link-chevron" />
      </button>
    </LxGroupCard>

    <LxModal
      v-model:show="showPasswordModal"
      preset="card"
      :title="t('account.changePassword')"
      to="body"
      :z-index="11000"
      style="max-width: 400px"
    >
      <div class="password-form">
        <div class="form-item">
          <label>{{ t('account.newPassword') }}</label>
          <n-input
            v-model:value="passwordForm.newPassword"
            type="password"
            :placeholder="t('account.newPasswordPh')"
            show-password-on="click"
          />
        </div>
        <div class="form-item">
          <label>{{ t('account.confirmPassword') }}</label>
          <n-input
            v-model:value="passwordForm.confirmPassword"
            type="password"
            :placeholder="t('account.confirmPasswordPh')"
            show-password-on="click"
          />
        </div>
        <div v-if="captchaEnabled" class="form-item">
          <label>{{ t('account.captcha') }}</label>
          <SliderCaptcha
            v-if="passwordForm.captchaType === 'slider'"
            :background="passwordForm.captchaImage"
            :puzzle="passwordForm.puzzleImage"
            :puzzle-y="passwordForm.puzzleY"
            :disabled="passwordCaptchaLoading"
            @success="onPasswordSliderSuccess"
            @refresh="loadResetCaptcha"
          />
          <div v-else class="captcha-row">
            <n-input v-model:value="passwordForm.captchaCode" :placeholder="t('account.captchaPh')" />
            <div class="captcha-img-wrap" :class="{ loading: passwordCaptchaLoading }" @click="loadResetCaptcha">
              <img v-if="passwordForm.captchaImage" :src="passwordForm.captchaImage" alt="captcha" />
              <span v-else-if="passwordCaptchaLoading">{{ t('common.loading') }}</span>
              <span v-else>{{ t('common.clickToLoad') }}</span>
            </div>
          </div>
          <span class="captcha-tip">{{ t('account.captchaTip') }}</span>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <LxButton variant="modal" @click="showPasswordModal = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" :disabled="passwordLoading" @click="handleChangePassword">{{ t('account.confirmChange') }}</LxButton>
        </div>
      </template>
    </LxModal>

    <LxModal
      v-model:show="showDeviceModal"
      preset="card"
      :title="t('account.devicesTitle')"
      to="body"
      :z-index="11000"
      style="max-width: 500px"
    >
      <div v-if="deviceLoading" class="loading-state"><span>{{ t('common.loading') }}</span></div>
      <div v-else-if="devices.length === 0" class="empty-state"><span>{{ t('account.noDevices') }}</span></div>
      <div v-else class="device-list">
        <div v-for="device in devices" :key="device.id" class="device-item">
          <div class="device-info">
            <div class="device-name">{{ device.deviceName || t('account.unknownDevice') }}</div>
            <div class="device-meta">
              {{ device.deviceType }} · {{ device.lastActive }}
              <span v-if="device.current" class="current-badge">{{ t('account.currentDevice') }}</span>
            </div>
          </div>
          <LxButton
            v-if="!device.current"
            variant="link-danger"
            @click="handleLogoutDevice(device.id)"
          >
            {{ t('account.logoutDevice') }}
          </LxButton>
        </div>
      </div>
    </LxModal>

    <LxModal
      v-model:show="showFeedbackHistory"
      preset="card"
      :title="t('account.feedbackTitle')"
      to="body"
      :z-index="11000"
      style="max-width: 600px"
    >
      <div v-if="feedbackLoading" class="loading-state"><span>{{ t('common.loading') }}</span></div>
      <div v-else-if="feedbackList.length === 0" class="empty-state"><span>{{ t('account.noFeedback') }}</span></div>
      <div v-else class="feedback-list">
        <div v-for="item in feedbackList" :key="item.id" class="feedback-item">
          <div class="feedback-header">
            <n-tag :type="getFeedbackStatusType(item.status)" size="small">
              {{ getFeedbackStatusText(item.status) }}
            </n-tag>
            <n-tag size="small">{{ getFeedbackTypeText(item.type) }}</n-tag>
            <span class="feedback-time">{{ item.createTime }}</span>
          </div>
          <div class="feedback-content">{{ item.content }}</div>
          <div v-if="item.reply" class="feedback-reply">
            <span class="feedback-reply-label">{{ t('account.officialReply') }}</span>
            {{ item.reply }}
          </div>
          <div class="feedback-actions">
            <LxButton variant="link" @click="toggleFeedbackDetail(item.id)">
              {{
                expandedFeedbackId === item.id
                  ? t('account.hideConversation')
                  : t('account.viewConversation')
              }}
            </LxButton>
          </div>
          <div v-if="expandedFeedbackId === item.id" class="feedback-thread">
            <div
              v-for="reply in feedbackDetails[item.id]?.replies || []"
              :key="reply.id"
              class="feedback-thread-item"
              :class="reply.senderType === 'admin' ? 'is-admin' : 'is-user'"
            >
              <div class="feedback-thread-meta">
                <span>{{ reply.senderName || (reply.senderType === 'admin' ? t('account.officialReply') : t('account.myReply')) }}</span>
                <span>{{ reply.createTime }}</span>
              </div>
              <div>{{ reply.content }}</div>
            </div>
            <div v-if="item.status !== 'closed'" class="feedback-follow-up">
              <n-input
                v-model:value="followUpDraft[item.id]"
                type="textarea"
                :rows="2"
                :placeholder="t('account.followUpPlaceholder')"
              />
              <LxButton
                variant="sm-primary"
                class="feedback-follow-up-btn"
                :disabled="followUpLoadingId === item.id"
                @click="submitFollowUp(item.id)"
              >
                {{ t('account.followUp') }}
              </LxButton>
            </div>
          </div>
        </div>
      </div>
    </LxModal>

    <LxModal
      v-model:show="showLinkxIdModal"
      preset="card"
      :title="t('account.changeLinkxId')"
      to="body"
      :z-index="11000"
      style="max-width: 400px"
    >
      <div class="password-form">
        <div class="form-item">
          <label>{{ t('account.linkxIdLabel') }}</label>
          <n-input
            v-model:value="linkxIdForm.username"
            maxlength="32"
            :placeholder="t('account.linkxIdPh')"
          />
          <span class="field-hint">{{ t('account.linkxIdHint') }}</span>
        </div>
        <div class="form-item">
          <label>{{ t('account.loginPassword') }}</label>
          <n-input
            v-model:value="linkxIdForm.password"
            type="password"
            show-password-on="click"
            :placeholder="t('account.passwordVerifyPh')"
          />
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <LxButton variant="modal" @click="showLinkxIdModal = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" :disabled="linkxIdLoading" @click="submitChangeLinkxId">{{ t('account.confirmChange') }}</LxButton>
        </div>
      </template>
    </LxModal>

    <LxModal
      v-model:show="showPhoneModal"
      preset="card"
      :title="userProfile.phoneBound ? t('account.changePhone') : t('account.bindPhone')"
      to="body"
      :z-index="11000"
      style="max-width: 400px"
    >
      <div class="password-form">
        <div class="form-item">
          <label>{{ t('account.phone') }}</label>
          <n-input v-model:value="phoneForm.phone" maxlength="11" :placeholder="t('account.phonePh')" />
        </div>
        <div class="form-item">
          <label>{{ t('account.loginPassword') }}</label>
          <n-input
            v-model:value="phoneForm.password"
            type="password"
            show-password-on="click"
            :placeholder="t('account.passwordVerifyPh')"
          />
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <LxButton variant="modal" @click="showPhoneModal = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" :disabled="phoneLoading" @click="submitBindPhone">{{ t('account.confirmBind') }}</LxButton>
        </div>
      </template>
    </LxModal>

    <LxModal
      v-model:show="showEmailModal"
      preset="card"
      :title="userProfile.emailBound ? t('account.changeEmail') : t('account.bindEmail')"
      to="body"
      :z-index="11000"
      style="max-width: 420px"
    >
      <div class="password-form">
        <div class="form-item">
          <label>{{ t('account.email') }}</label>
          <n-input v-model:value="emailForm.email" :placeholder="t('account.emailPh')" />
        </div>
        <div class="form-item">
          <label>{{ t('account.code') }}</label>
          <div class="captcha-row">
            <n-input v-model:value="emailForm.code" :placeholder="t('account.codePh')" />
            <LxButton
              variant="sm"
              :disabled="emailCodeSending || emailCodeCooldown > 0"
              @click="sendEmailCode"
            >
              {{ emailCodeCooldown > 0 ? `${emailCodeCooldown}s` : t('account.sendCode') }}
            </LxButton>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <LxButton variant="modal" @click="showEmailModal = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" :disabled="emailLoading" @click="submitBindEmail">{{ t('account.confirmBind') }}</LxButton>
        </div>
      </template>
    </LxModal>

    <LxModal
      v-model:show="showPurgeModal"
      preset="card"
      :title="t('account.purgeData')"
      to="body"
      :z-index="11000"
      style="max-width: 420px"
    >
      <p class="delete-warn">{{ t('account.purgeConfirm') }}</p>
      <div class="form-item">
        <label>{{ t('account.loginPassword') }}</label>
        <n-input
          v-model:value="purgePassword"
          type="password"
          show-password-on="click"
          :placeholder="t('account.passwordPh')"
        />
      </div>
      <template #footer>
        <div class="modal-footer">
          <LxButton variant="modal" @click="showPurgeModal = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-danger" :disabled="purgeLoading" @click="submitPurgeData">{{ t('common.confirm') }}</LxButton>
        </div>
      </template>
    </LxModal>

    <LxModal
      v-model:show="showDeleteModal"
      preset="card"
      :title="t('account.deleteTitle')"
      to="body"
      :z-index="11000"
      style="max-width: 420px"
    >
      <p class="delete-warn">{{ t('account.deleteWarn') }}</p>
      <div class="form-item">
        <label>{{ t('account.loginPassword') }}</label>
        <n-input
          v-model:value="deletePassword"
          type="password"
          show-password-on="click"
          :placeholder="t('account.passwordPh')"
        />
      </div>
      <template #footer>
        <div class="modal-footer">
          <LxButton variant="modal" @click="showDeleteModal = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-danger" :disabled="deleteLoading" @click="submitDeleteAccount">{{ t('account.confirmDelete') }}</LxButton>
        </div>
      </template>
    </LxModal>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.account-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-2xl) var(--lx-space-2xl) var(--lx-space);
}

.group-title {
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-body);
}

.profile-block {
  display: flex;
  align-items: flex-start;
  gap: var(--lx-space-xl);
  padding: var(--lx-space) var(--lx-space-2xl) var(--lx-space-2xl);
}

.profile-avatar {
  flex-shrink: 0;
  box-shadow: 0 2px 8px var(--lx-shadow-color);
}

.profile-meta {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
}

.profile-name-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
}

.profile-name {
  font-size: var(--lx-font-2xl);
  font-weight: 600;
  color: var(--lx-text-body);
}

.profile-id-row {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-sm);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-secondary);
  margin-top: var(--lx-space-2xs);
}

.profile-id-row.muted {
  color: var(--lx-text-muted);
  font-size: var(--lx-font-xs);
}

.copy-btn {
  border: none;
  background: none;
  padding: var(--lx-space-2xs);
  color: var(--lx-text-muted);
  cursor: pointer;
  display: inline-flex;
  border-radius: var(--lx-radius-2xs);
}

.copy-btn:hover {
  color: var(--lx-accent);
  background: var(--lx-bg-hover);
}

.link-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-lg);
  width: 100%;
  padding: var(--lx-space-xl) var(--lx-space-2xl);
  border: none;
  border-top: 1px solid var(--lx-border-light);
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background var(--lx-duration);
}

.link-row:hover {
  background: var(--lx-bg-hover);
}

.link-label {
  font-size: var(--lx-font);
  color: var(--lx-text-body);
  font-weight: 500;
}

.link-value {
  margin-left: auto;
  font-size: var(--lx-font-md);
  color: var(--lx-text-secondary);
}

.link-value.muted {
  color: var(--lx-text-muted);
}

.link-value.success {
  color: var(--lx-success);
}

.link-chevron {
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.link-text {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
  min-width: 0;
  flex: 1;
}

.link-text.compact {
  align-items: flex-end;
  gap: var(--lx-space-2xs);
}

.link-text.compact.right {
  flex: 0 0 auto;
}

.link-desc {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.danger-row .link-label {
  color: var(--lx-danger);
}

.delete-warn {
  margin: 0 0 var(--lx-space-2xl);
  font-size: var(--lx-font-md);
  color: var(--lx-danger);
  line-height: var(--lx-leading-normal);
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-2xl);
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-sm);
}

.form-item label {
  font-size: var(--lx-font);
  color: var(--lx-text-secondary);
}

.field-hint {
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--lx-space-lg);
}

.captcha-row {
  display: flex;
  gap: var(--lx-space);
  align-items: stretch;
}

.captcha-row > :first-child {
  flex: 1;
}

.captcha-img-wrap {
  width: 120px;
  height: 36px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  background: var(--lx-bg-panel);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  overflow: hidden;
}

.captcha-img-wrap img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.captcha-tip {
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
  margin-top: var(--lx-space-xs);
}

.loading-state,
.empty-state {
  padding: var(--lx-space-4xl);
  text-align: center;
  color: var(--lx-text-muted);
}

.device-list {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-lg);
}

.device-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--lx-space-lg);
  border-radius: var(--lx-radius);
  background: var(--lx-bg-panel);
}

.device-name {
  font-size: var(--lx-font);
  font-weight: 500;
  color: var(--lx-text-body);
}

.device-meta {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  margin-top: var(--lx-space-xs);
}

.current-badge {
  margin-left: var(--lx-space);
  padding: var(--lx-space-2xs) var(--lx-space-sm);
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  border-radius: var(--lx-radius-2xs);
  font-size: var(--lx-font-xs);
}

.feedback-list {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-lg);
}

.feedback-item {
  padding: var(--lx-space-lg);
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
}

.feedback-header {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  margin-bottom: var(--lx-space);
}

.feedback-time {
  margin-left: auto;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.feedback-content {
  font-size: var(--lx-font);
  color: var(--lx-text-body);
  line-height: var(--lx-leading-normal);
  white-space: pre-wrap;
}
.feedback-reply {
  margin-top: var(--lx-space);
  padding: var(--lx-space) var(--lx-space-md);
  border-radius: var(--lx-radius-sm);
  background: rgba(24, 160, 88, 0.08);
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
}
.feedback-reply-label {
  display: block;
  margin-bottom: var(--lx-space-xs);
  color: var(--lx-text-muted);
  font-size: var(--lx-font-sm);
}
.feedback-actions {
  margin-top: var(--lx-space);
}
.feedback-thread {
  margin-top: var(--lx-space-md);
  display: flex;
  flex-direction: column;
  gap: var(--lx-space);
}
.feedback-thread-item {
  padding: var(--lx-space) var(--lx-space-md);
  border-radius: var(--lx-radius-sm);
  background: var(--lx-card-bg, rgba(0, 0, 0, 0.03));
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
}
.feedback-thread-item.is-admin {
  background: rgba(24, 160, 88, 0.08);
}
.feedback-thread-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--lx-space);
  margin-bottom: var(--lx-space-xs);
  color: var(--lx-text-muted);
  font-size: var(--lx-font-sm);
}
.feedback-follow-up {
  margin-top: var(--lx-space-xs);
}
.feedback-follow-up-btn {
  margin-top: var(--lx-space);
}
</style>
