<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NAutoComplete,
  NAvatar,
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NSpace,
  NSpin,
  NTag,
  useMessage,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { CameraOutline, PersonCircleOutline } from '@vicons/ionicons5'
import { changePassword, updateProfile, uploadAvatar } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { resolveAvatarSrc } from '@/utils/mediaUrl'

const router = useRouter()
const message = useMessage()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const profileSaving = ref(false)
const pwdSaving = ref(false)
const avatarUploading = ref(false)
const profileFormRef = ref<FormInst | null>(null)
const pwdFormRef = ref<FormInst | null>(null)
const avatarInputRef = ref<HTMLInputElement | null>(null)

const profileForm = reactive({
  nickname: '',
  email: '',
  avatar: '',
})

const nicknameOptions = computed(() => {
  const pool = [auth.user?.nickname, auth.user?.username].filter(
    (x): x is string => !!x && x.trim().length > 0,
  )
  const q = profileForm.nickname.trim().toLowerCase()
  return [...new Set(pool)]
    .filter((x) => !q || x.toLowerCase().includes(q))
    .map((value) => ({ label: value, value }))
})

const emailOptions = computed(() => {
  const pool = [auth.user?.email].filter((x): x is string => !!x && x.trim().length > 0)
  const q = profileForm.email.trim().toLowerCase()
  return [...new Set(pool)]
    .filter((x) => !q || x.toLowerCase().includes(q))
    .map((value) => ({ label: value, value }))
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rolesText = computed(() => {
  void locale.value
  const roles = auth.user?.roles
  return roles?.length ? roles.join(', ') : t('common.none')
})

const permissionCount = computed(() => auth.permissions?.length || auth.user?.permissions?.length || 0)

const profileRules = computed<FormRules>(() => {
  void locale.value
  return {
    nickname: [
      { required: true, message: t('profile.nicknameRequired'), trigger: ['blur', 'input'] },
      { max: 64, message: t('profile.nicknameMax'), trigger: ['blur', 'input'] },
    ],
    email: [
      {
        validator: (_rule, value: string) => {
          if (!value) return true
          if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
            return new Error(t('profile.emailInvalid'))
          }
          return true
        },
        trigger: ['blur', 'input'],
      },
    ],
    avatar: [],
  }
})

const pwdRules = computed<FormRules>(() => {
  void locale.value
  return {
    oldPassword: [{ required: true, message: t('profile.oldPasswordRequired'), trigger: ['blur', 'input'] }],
    newPassword: [
      { required: true, message: t('profile.newPasswordRequired'), trigger: ['blur', 'input'] },
      {
        validator: (_rule, value: string) => {
          if (!value || value.length < 8 || value.length > 64) {
            return new Error(t('profile.newPasswordLength'))
          }
          if (!/(?=.*[A-Za-z])(?=.*\d)/.test(value)) {
            return new Error(t('profile.newPasswordPattern'))
          }
          return true
        },
        trigger: ['blur', 'input'],
      },
    ],
    confirmPassword: [
      {
        required: true,
        validator: (_rule, value: string) => {
          if (!value) return new Error(t('profile.confirmPasswordRequired'))
          if (value !== pwdForm.newPassword) return new Error(t('profile.confirmPasswordMismatch'))
          return true
        },
        trigger: ['blur', 'input'],
      },
    ],
  }
})

function syncProfileForm() {
  profileForm.nickname = auth.user?.nickname || ''
  profileForm.email = auth.user?.email || ''
  profileForm.avatar = auth.user?.avatar || ''
}

async function refreshProfile() {
  loading.value = true
  try {
    await auth.fetchProfile()
    syncProfileForm()
  } finally {
    loading.value = false
  }
}

async function submitProfile() {
  await profileFormRef.value?.validate()
  profileSaving.value = true
  try {
    const updated = await updateProfile({
      nickname: profileForm.nickname.trim(),
      email: profileForm.email.trim() || null,
    })
    auth.user = {
      ...auth.user,
      ...updated,
    }
    syncProfileForm()
    message.success(t('profile.profileSaved'))
  } finally {
    profileSaving.value = false
  }
}

function triggerAvatarUpload() {
  if (avatarUploading.value) return
  avatarInputRef.value?.click()
}

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.error(t('profile.selectImage'))
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.error(t('profile.imageTooLarge'))
    return
  }
  const preview = URL.createObjectURL(file)
  profileForm.avatar = preview
  avatarUploading.value = true
  try {
    await uploadAvatar(file)
    await auth.fetchProfile()
    syncProfileForm()
    message.success(t('profile.avatarUpdated'))
  } catch {
    syncProfileForm()
  } finally {
    URL.revokeObjectURL(preview)
    avatarUploading.value = false
  }
}

const avatarDisplaySrc = computed(() =>
  resolveAvatarSrc(profileForm.avatar || auth.user?.avatar, auth.user?.id, true),
)
const avatarBroken = ref(false)
watch(avatarDisplaySrc, () => {
  avatarBroken.value = false
})
function onAvatarError() {
  avatarBroken.value = true
}

async function submitPassword() {
  await pwdFormRef.value?.validate()
  pwdSaving.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    message.success(t('profile.passwordChanged'))
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    await auth.logout()
    router.push('/login')
  } finally {
    pwdSaving.value = false
  }
}

watch(
  () => auth.user,
  () => syncProfileForm(),
  { immediate: true },
)

onMounted(refreshProfile)
</script>

<template>
  <div class="page">
    <NSpace justify="end" style="margin-bottom: 4px">
      <NButton quaternary :loading="loading" @click="refreshProfile">{{ t('common.refresh') }}</NButton>
    </NSpace>

    <NSpin :show="loading">
      <div class="profile-grid">
        <div class="page-card profile-card">
          <div class="profile-hero">
            <button
              type="button"
              class="avatar-upload"
              :class="{ uploading: avatarUploading }"
              :title="t('profile.uploadAvatar')"
              :disabled="avatarUploading"
              @click="triggerAvatarUpload"
            >
              <span class="avatar-visual">
                <img
                  v-if="avatarDisplaySrc && !avatarBroken"
                  :key="avatarDisplaySrc"
                  class="avatar-img"
                  :src="avatarDisplaySrc"
                  alt=""
                  referrerpolicy="no-referrer"
                  @error="onAvatarError"
                />
                <NAvatar v-else round :size="72">
                  <NIcon :size="36" :component="PersonCircleOutline" />
                </NAvatar>
              </span>
              <span class="avatar-mask">
                <NIcon :size="22" :component="CameraOutline" />
                <span>{{ avatarUploading ? t('profile.uploading') : t('profile.change') }}</span>
              </span>
            </button>
            <input
              ref="avatarInputRef"
              type="file"
              accept="image/*"
              hidden
              @change="handleAvatarChange"
            />
            <div>
              <div class="profile-name">{{ profileForm.nickname || auth.displayName }}</div>
              <div class="profile-sub">@{{ auth.user?.username || '-' }}</div>
            </div>
          </div>

          <h2 class="section-title">{{ t('profile.basicInfo') }}</h2>
          <p class="section-hint">{{ t('profile.basicHint') }}</p>
          <NForm
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-placement="left"
            label-width="88"
            require-mark-placement="right-hanging"
          >
            <NFormItem :label="t('profile.userId')">
              <NInput :value="String(auth.user?.id ?? '')" disabled />
            </NFormItem>
            <NFormItem :label="t('user.username')">
              <NInput :value="auth.user?.username || ''" disabled />
            </NFormItem>
            <NFormItem :label="t('profile.nickname')" path="nickname">
              <NAutoComplete
                v-model:value="profileForm.nickname"
                :options="nicknameOptions"
                :placeholder="t('profile.nicknamePlaceholder')"
              />
            </NFormItem>
            <NFormItem :label="t('profile.email')" path="email">
              <NAutoComplete
                v-model:value="profileForm.email"
                :options="emailOptions"
                :placeholder="t('profile.emailPlaceholder')"
              />
            </NFormItem>
            <NFormItem :label="t('profile.roles')">
              <NSpace size="small">
                <NTag
                  v-for="role in auth.user?.roles || []"
                  :key="role"
                  size="small"
                  type="info"
                >
                  {{ role }}
                </NTag>
                <span v-if="!auth.user?.roles?.length">{{ rolesText }}</span>
              </NSpace>
            </NFormItem>
            <NFormItem :label="t('profile.permissionCount')">
              <span class="readonly-text">{{ permissionCount }}</span>
            </NFormItem>
            <NFormItem>
              <NButton type="primary" :loading="profileSaving" @click="submitProfile">
                {{ t('profile.saveProfile') }}
              </NButton>
            </NFormItem>
          </NForm>
        </div>

        <div class="page-card">
          <h2 class="section-title">{{ t('profile.changePassword') }}</h2>
          <p class="section-hint">{{ t('profile.passwordHint') }}</p>
          <NForm
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-placement="left"
            label-width="110"
            require-mark-placement="right-hanging"
            style="max-width: 420px"
          >
            <NFormItem :label="t('profile.oldPassword')" path="oldPassword">
              <NInput
                v-model:value="pwdForm.oldPassword"
                type="password"
                show-password-on="click"
                :placeholder="t('profile.oldPassword')"
                autocomplete="current-password"
              />
            </NFormItem>
            <NFormItem :label="t('profile.newPassword')" path="newPassword">
              <NInput
                v-model:value="pwdForm.newPassword"
                type="password"
                show-password-on="click"
                :placeholder="t('profile.newPasswordPlaceholder')"
                autocomplete="new-password"
              />
            </NFormItem>
            <NFormItem :label="t('profile.confirmPassword')" path="confirmPassword">
              <NInput
                v-model:value="pwdForm.confirmPassword"
                type="password"
                show-password-on="click"
                :placeholder="t('profile.confirmPasswordPlaceholder')"
                autocomplete="new-password"
              />
            </NFormItem>
            <NFormItem>
              <NButton type="primary" :loading="pwdSaving" @click="submitPassword">
                {{ t('profile.savePassword') }}
              </NButton>
            </NFormItem>
          </NForm>
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: minmax(280px, 460px) minmax(320px, 1fr);
  gap: 16px;
  align-items: start;
}

.profile-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.avatar-upload {
  position: relative;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 50%;
  flex-shrink: 0;
}

.avatar-visual {
  display: block;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
}

.avatar-img {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
  background: var(--lx-avatar-bg);
}

.avatar-upload:disabled {
  cursor: wait;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border-radius: 50%;
  background: var(--lx-avatar-mask);
  color: #fff;
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.avatar-upload:hover .avatar-mask,
.avatar-upload.uploading .avatar-mask,
.avatar-upload:focus-visible .avatar-mask {
  opacity: 1;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
}

.profile-sub {
  margin-top: 4px;
  color: var(--lx-text-3);
  font-size: 13px;
}

.section-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
}

.section-hint {
  margin: 0 0 16px;
  color: var(--lx-text-3);
  font-size: 13px;
}

.readonly-text {
  color: var(--lx-text);
  line-height: 34px;
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
