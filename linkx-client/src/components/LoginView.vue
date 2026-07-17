<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { NInput, NButton, NIcon, NCheckbox, NModal, useMessage } from 'naive-ui'
import { LockClosedOutline, PersonOutline, RefreshOutline, MailOutline } from '@vicons/ionicons5'
import WindowControls from './WindowControls.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import * as authApi from '../api/auth'
import { sendResetCode, verifyResetCode, resetPasswordByEmail } from '../api/account'
import { validateUsername, validatePassword, validateNickname } from '../utils/validation'

const message = useMessage()
const appStore = useAppStore()
const { savedLogin, isLoading } = storeToRefs(appStore)
const { login } = appStore

const isElectron = !!window.electronAPI?.isElectron

const username = ref('')
const password = ref('')
const rememberMe = ref(true)
const autoLogin = ref(false)

const captchaId = ref('')
const captchaImage = ref('')
const captchaCode = ref('')

const showRegister = ref(false)
const regUser = ref('')
const regPass = ref('')
const regNickname = ref('')
const regEmail = ref('')
const regCaptchaCode = ref('')
const regCaptchaId = ref('')
const regCaptchaImage = ref('')

const showForgot = ref(false)
const forgotStep = ref<'input' | 'verify' | 'reset'>('input')  // 'input': 输入用户名, 'verify': 输入邮箱验证码, 'reset': 输入新密码
const forgotUser = ref('')
const forgotCode = ref('')
const forgotNewPassword = ref('')
const forgotConfirmPassword = ref('')
const forgotLoading = ref(false)
const forgotSendLoading = ref(false)
const forgotCountdown = ref(0)  // 倒计时（秒）
let forgotCountdownTimer: ReturnType<typeof setInterval> | null = null

const compact = computed(() => isElectron)

async function loadCaptcha(target: 'login' | 'register' = 'login') {
  try {
    const res = await authApi.fetchCaptcha()
    if (res.code === 200 && res.data) {
      if (target === 'login') {
        captchaId.value = res.data.captchaId
        captchaImage.value = res.data.imageBase64
        captchaCode.value = ''
      } else {
        regCaptchaId.value = res.data.captchaId
        regCaptchaImage.value = res.data.imageBase64
        regCaptchaCode.value = ''
      }
    }
  } catch {
    message.error('验证码加载失败')
  }
}

onMounted(() => {
  username.value = savedLogin.value.username || ''
  rememberMe.value = savedLogin.value.rememberMe ?? true
  autoLogin.value = savedLogin.value.autoLogin ?? false
  // 首屏先绘制登录表单，验证码异步加载避免阻塞交互
  requestAnimationFrame(() => {
    void loadCaptcha('login')
  })
})

watch(rememberMe, val => {
  if (!val) autoLogin.value = false
})

watch(autoLogin, val => {
  if (val) rememberMe.value = true
})

async function handleLogin() {
  const user = username.value.trim()
  const pass = password.value

  const userErr = validateUsername(user)
  if (userErr) {
    message.warning(userErr)
    return
  }
  const passErr = validatePassword(pass)
  if (passErr) {
    message.warning(passErr)
    return
  }
  if (!captchaCode.value.trim()) {
    message.warning('请输入验证码')
    return
  }

  try {
    await login(user, pass, {
      rememberMe: rememberMe.value,
      autoLogin: autoLogin.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value.trim()
    })
    message.success(`欢迎回来，${user}`)
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '登录失败，请检查账号密码')
    await loadCaptcha('login')
  }
}

async function openRegister() {
  showRegister.value = true
  regNickname.value = regUser.value.trim()
  await loadCaptcha('register')
}

async function handleRegister() {
  const user = regUser.value.trim()
  const pass = regPass.value
  const nickname = regNickname.value.trim() || user
  const email = regEmail.value.trim()

  const userErr = validateUsername(user)
  if (userErr) {
    message.warning(userErr)
    return
  }
  const passErr = validatePassword(pass, true)
  if (passErr) {
    message.warning(passErr)
    return
  }
  const nickErr = validateNickname(nickname)
  if (nickErr) {
    message.warning(nickErr)
    return
  }
  if (!email) {
    message.warning('请输入邮箱')
    return
  }
  // 简单邮箱格式校验
  if (!/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(email)) {
    message.warning('请输入有效的邮箱地址')
    return
  }
  if (!regCaptchaCode.value.trim()) {
    message.warning('请输入验证码')
    return
  }

  try {
    const res = await authApi.register({
      username: user,
      password: pass,
      nickname,
      email,
      captchaId: regCaptchaId.value,
      captchaCode: regCaptchaCode.value.trim()
    })
    if (res.code === 200) {
      message.success('注册成功，请登录')
      username.value = user
      password.value = ''
      showRegister.value = false
      await loadCaptcha('login')
    } else {
      message.error(res.message || '注册失败，请检查信息后重试')
      await loadCaptcha('register')
    }
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '注册请求失败')
    await loadCaptcha('register')
  }
}

async function openForgot() {
  showForgot.value = true
  // 重置所有状态
  forgotStep.value = 'input'
  forgotUser.value = ''
  forgotCode.value = ''
  forgotNewPassword.value = ''
  forgotConfirmPassword.value = ''
  forgotCountdown.value = 0
  if (forgotCountdownTimer) {
    clearInterval(forgotCountdownTimer)
    forgotCountdownTimer = null
  }
}
async function handleSendResetCode() {
  const user = forgotUser.value.trim()
  if (!user) {
    message.warning('请输入 LinkX ID')
    return
  }

  const userErr = validateUsername(user)
  if (userErr) {
    message.warning(userErr)
    return
  }

  forgotSendLoading.value = true
  try {
    await sendResetCode({ username: user })
    message.success('验证码已发送到您的注册邮箱，请查收')
    forgotStep.value = 'verify'
    startCountdown()
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '发送验证码失败')
  } finally {
    forgotSendLoading.value = false
  }
}

// 验证邮箱验证码
const verifyLoading = ref(false)
async function handleVerifyCode() {
  const code = forgotCode.value.trim()
  if (!code) {
    message.warning('请输入验证码')
    return
  }
  if (code.length !== 6) {
    message.warning('验证码为6位数字')
    return
  }

  verifyLoading.value = true
  try {
    // 真正调用后端校验，不通过不能进入下一步
    await verifyResetCode({ username: forgotUser.value.trim(), code })
    forgotStep.value = 'reset'
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '验证码错误')
    // 不切换步骤，让用户重新输入
  } finally {
    verifyLoading.value = false
  }
}

// 重发验证码
async function handleResendCode() {
  forgotSendLoading.value = true
  try {
    await sendResetCode({ username: forgotUser.value.trim() })
    message.success('验证码已重新发送')
    startCountdown()
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '发送验证码失败')
  } finally {
    forgotSendLoading.value = false
  }
}

// 开始倒计时
function startCountdown() {
  forgotCountdown.value = 60
  if (forgotCountdownTimer) clearInterval(forgotCountdownTimer)
  forgotCountdownTimer = setInterval(() => {
    forgotCountdown.value--
    if (forgotCountdown.value <= 0) {
      if (forgotCountdownTimer) clearInterval(forgotCountdownTimer)
      forgotCountdownTimer = null
    }
  }, 1000)
}

async function handleForgot() {
  const user = forgotUser.value.trim()
  const newPass = forgotNewPassword.value
  const confirmPass = forgotConfirmPassword.value

  const passErr = validatePassword(newPass)
  if (passErr) {
    message.warning('新' + passErr)
    return
  }
  if (newPass !== confirmPass) {
    message.warning('两次输入的密码不一致')
    return
  }

  forgotLoading.value = true
  try {
    await resetPasswordByEmail({
      username: user,
      code: forgotCode.value.trim(),
      newPassword: newPass
    })
    message.success('密码重置成功，请使用新密码登录')
    showForgot.value = false
    username.value = user
    password.value = ''
    await loadCaptcha('login')
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '重置密码失败')
  } finally {
    forgotLoading.value = false
  }
}
</script>

<template>
  <div class="login-page" :class="{ 'login-page--compact': compact }">
    <div v-if="isElectron" class="login-win-bar">
      <div class="drag-area" />
      <WindowControls />
    </div>

    <div class="login-card">
      <div class="brand">
        <img src="../assets/logo-linkx.svg" alt="" class="brand-logo" width="56" height="56" />
        <h1 class="brand-name">LinkX</h1>
        <p class="brand-desc">企业级即时通讯与协同平台</p>
      </div>

      <div class="form">
        <n-input
          v-model:value="username"
          size="large"
          placeholder="用户名（4-32位字母数字下划线）"
          class="field"
          :bordered="true"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <n-icon :component="PersonOutline" :size="18" class="field-ico" />
          </template>
        </n-input>

        <n-input
          v-model:value="password"
          type="password"
          show-password-on="click"
          size="large"
          placeholder="密码（8-64位）"
          class="field"
          :bordered="true"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <n-icon :component="LockClosedOutline" :size="18" class="field-ico" />
          </template>
        </n-input>

        <div class="captcha-row">
          <img
            v-if="captchaImage"
            :src="captchaImage"
            alt="验证码"
            class="captcha-img"
            title="点击刷新"
            @click="loadCaptcha('login')"
          />
          <n-input
            v-model:value="captchaCode"
            size="large"
            placeholder="验证码"
            class="captcha-input"
            maxlength="6"
            @keyup.enter="handleLogin"
          />
          <n-button quaternary circle @click="loadCaptcha('login')">
            <template #icon>
              <n-icon :component="RefreshOutline" />
            </template>
          </n-button>
        </div>

        <div class="options">
          <n-checkbox v-model:checked="rememberMe" size="small">记住账号</n-checkbox>
          <n-checkbox v-model:checked="autoLogin" size="small">自动登录</n-checkbox>
        </div>

        <n-button
          type="primary"
          size="large"
          block
          :loading="isLoading"
          class="submit-btn"
          @click="handleLogin"
        >
          登 录
        </n-button>
      </div>

      <div class="footer">
        <a href="#" class="footer-link" @click.prevent="openRegister">注册账号</a>
        <span class="footer-sep">|</span>
        <a href="#" class="footer-link" @click.prevent="openForgot">找回密码</a>
      </div>
    </div>

    <n-modal v-model:show="showRegister" preset="dialog" title="注册 LinkX 账号">
      <n-input v-model:value="regUser" placeholder="用户名" />
      <n-input
        v-model:value="regPass"
        type="password"
        placeholder="密码（8位以上，含字母和数字）"
        style="margin-top: 12px"
      />
      <n-input
        v-model:value="regNickname"
        placeholder="昵称"
        style="margin-top: 12px"
      />
      <n-input
        v-model:value="regEmail"
        placeholder="邮箱（用于找回密码）"
        style="margin-top: 12px"
      />
      <div class="captcha-row" style="margin-top: 12px">
        <img
          v-if="regCaptchaImage"
          :src="regCaptchaImage"
          alt="验证码"
          class="captcha-img"
          @click="loadCaptcha('register')"
        />
        <n-input v-model:value="regCaptchaCode" placeholder="验证码" maxlength="6" />
        <n-button quaternary circle @click="loadCaptcha('register')">
          <template #icon>
            <n-icon :component="RefreshOutline" />
          </template>
        </n-button>
      </div>
      <template #action>
        <n-button @click="showRegister = false">取消</n-button>
        <n-button type="primary" @click="handleRegister">注册</n-button>
      </template>
    </n-modal>

    <n-modal v-model:show="showForgot" preset="dialog" title="找回密码" style="max-width: 400px">
      <div class="forgot-form">
        <!-- 步骤 1: 输入用户名 -->
        <template v-if="forgotStep === 'input'">
          <div class="forgot-tip">
            <n-icon :component="MailOutline" :size="20" class="forgot-tip-icon" />
            <p>请输入您的 LinkX ID，系统将向您的注册邮箱发送验证码</p>
          </div>
          <div class="form-item">
            <label>LinkX ID</label>
            <n-input
              v-model:value="forgotUser"
              placeholder="请输入用户名"
              @keyup.enter="handleSendResetCode"
            />
          </div>
        </template>

        <!-- 步骤 2: 输入邮箱验证码 -->
        <template v-else-if="forgotStep === 'verify'">
          <div class="forgot-tip">
            <p>验证码已发送至您注册账号时填写的邮箱，请查收并输入</p>
          </div>
          <div class="form-item">
            <label>验证码</label>
            <n-input
              v-model:value="forgotCode"
              placeholder="请输入6位验证码"
              maxlength="6"
              @keyup.enter="handleVerifyCode"
            />
          </div>
          <div class="resend-row">
            <span v-if="forgotCountdown > 0" class="resend-tips">
              {{ forgotCountdown }} 秒后可重新发送
            </span>
            <a
              v-else
              href="#"
              class="resend-link"
              :class="{ disabled: forgotSendLoading }"
              @click.prevent="handleResendCode"
            >
              重新发送验证码
            </a>
            <span class="resend-sep">|</span>
            <a
              href="#"
              class="resend-link"
              @click.prevent="forgotStep = 'input'"
            >
              返回上一步
            </a>
          </div>
        </template>

        <!-- 步骤 3: 设置新密码 -->
        <template v-else>
          <div class="forgot-tip">
            <p>验证成功，请设置您的新密码</p>
          </div>
          <div class="form-item">
            <label>新密码</label>
            <n-input
              v-model:value="forgotNewPassword"
              type="password"
              show-password-on="click"
              placeholder="请输入新密码（8位以上含字母数字）"
            />
          </div>
          <div class="form-item">
            <label>确认密码</label>
            <n-input
              v-model:value="forgotConfirmPassword"
              type="password"
              show-password-on="click"
              placeholder="请再次输入新密码"
              @keyup.enter="handleForgot"
            />
          </div>
        </template>
      </div>
      <template #action>
        <n-button @click="showForgot = false">取消</n-button>
        <n-button
          v-if="forgotStep === 'input'"
          type="primary"
          :loading="forgotSendLoading"
          @click="handleSendResetCode"
        >
          发送验证码
        </n-button>
        <n-button
          v-else-if="forgotStep === 'verify'"
          type="primary"
          :loading="verifyLoading"
          @click="handleVerifyCode"
        >
          下一步
        </n-button>
        <n-button
          v-else
          type="primary"
          :loading="forgotLoading"
          @click="handleForgot"
        >
          重置密码
        </n-button>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.login-page {
  width: 100%;
  height: 100%;
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e8e8e8;
  box-sizing: border-box;
  padding: 16px;
}

.login-page--compact {
  background: #ffffff;
  padding: 0;
  flex-direction: column;
  align-items: stretch;
  min-height: 520px;
}

.login-win-bar {
  flex-shrink: 0;
  height: 32px;
  display: flex;
  align-items: stretch;
  -webkit-app-region: no-drag;
}

.drag-area {
  flex: 1;
  -webkit-app-region: drag;
}

.login-card {
  width: 360px;
  min-height: 520px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 40px 36px 28px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.login-page--compact .login-card {
  width: 100%;
  min-height: 0;
  flex: 1;
  border: none;
  border-radius: 0;
  box-shadow: none;
  padding: 36px 40px 24px;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 28px;
}

.brand-logo {
  display: block;
  margin-bottom: 10px;
}

.brand-name {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  color: #1a1a1a;
  letter-spacing: 0.5px;
}

.brand-desc {
  margin: 0;
  font-size: 12px;
  color: #999;
  line-height: 1.4;
}

.form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.captcha-img {
  width: 120px;
  height: 40px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.captcha-input {
  flex: 1;
}

.field :deep(.n-input-wrapper) {
  background: #f5f5f5;
  border-radius: 6px;
}

.field :deep(.n-input__input-el) {
  font-size: 14px;
}

.field-ico {
  color: #b0b0b0;
}

.options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 2px;
  margin-top: 2px;
}

.options :deep(.n-checkbox__label) {
  font-size: 13px;
  color: #666;
}

.submit-btn {
  height: 40px;
  margin-top: 6px;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 6px;
  text-indent: 6px;
}

.footer {
  margin-top: auto;
  padding-top: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 12px;
}

.footer-link {
  color: #888;
  text-decoration: none;
}

.footer-link:hover {
  color: var(--lx-accent, #12b7f5);
}

.footer-sep {
  color: #ddd;
  user-select: none;
}

/* 找回密码表单样式 */
.forgot-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.forgot-form .form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.forgot-form .form-item label {
  font-size: 14px;
  color: #666;
}

.forgot-form .captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.forgot-form .captcha-img {
  width: 120px;
  height: 40px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

/* 找回密码多步骤提示 */
.forgot-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f0f8ff;
  border-left: 3px solid #12b7f5;
  border-radius: 4px;
  margin-bottom: 8px;
}

.forgot-tip-icon {
  color: #12b7f5;
  flex-shrink: 0;
}

.forgot-tip p {
  margin: 0;
  color: #555;
  font-size: 13px;
  line-height: 1.5;
}

.resend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.resend-tips {
  color: #999;
}

.resend-link {
  color: #12b7f5;
  text-decoration: none;
  cursor: pointer;
}

.resend-link:hover {
  text-decoration: underline;
}

.resend-link.disabled {
  color: #ccc;
  cursor: not-allowed;
  pointer-events: none;
}

.resend-sep {
  color: #ddd;
}
</style>
