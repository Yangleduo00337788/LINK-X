<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
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

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rolesText = computed(() => {
  const roles = auth.user?.roles
  return roles?.length ? roles.join(', ') : '暂无'
})

const permissionCount = computed(() => auth.permissions?.length || auth.user?.permissions?.length || 0)

const profileRules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: ['blur', 'input'] },
    { max: 64, message: '昵称最多 64 个字符', trigger: ['blur', 'input'] },
  ],
  email: [
    {
      validator: (_rule, value: string) => {
        if (!value) return true
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
          return new Error('邮箱格式不正确')
        }
        return true
      },
      trigger: ['blur', 'input'],
    },
  ],
  avatar: [],
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: ['blur', 'input'] }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: ['blur', 'input'] },
    {
      validator: (_rule, value: string) => {
        if (!value || value.length < 8 || value.length > 64) {
          return new Error('新密码长度为 8-64 位')
        }
        if (!/(?=.*[A-Za-z])(?=.*\d)/.test(value)) {
          return new Error('新密码须同时包含字母和数字')
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
        if (!value) return new Error('请再次输入新密码')
        if (value !== pwdForm.newPassword) return new Error('两次输入的新密码不一致')
        return true
      },
      trigger: ['blur', 'input'],
    },
  ],
}

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
    message.success('资料已保存')
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
    message.error('请选择图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.error('图片不能超过 10MB')
    return
  }
  const preview = URL.createObjectURL(file)
  profileForm.avatar = preview
  avatarUploading.value = true
  try {
    await uploadAvatar(file)
    await auth.fetchProfile()
    syncProfileForm()
    message.success('头像已更新')
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
    message.success('密码已修改，请重新登录')
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
    <div class="page-header">
      <h1 class="page-title">个人中心</h1>
      <NButton quaternary :loading="loading" @click="refreshProfile">刷新</NButton>
    </div>

    <NSpin :show="loading">
      <div class="profile-grid">
        <div class="page-card profile-card">
          <div class="profile-hero">
            <button
              type="button"
              class="avatar-upload"
              :class="{ uploading: avatarUploading }"
              title="点击上传头像"
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
                <span>{{ avatarUploading ? '上传中' : '更换' }}</span>
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

          <h2 class="section-title">基本资料</h2>
          <p class="section-hint">用户名与角色不可在此修改。点击头像可上传新头像。</p>
          <NForm
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-placement="left"
            label-width="72"
            require-mark-placement="right-hanging"
          >
            <NFormItem label="用户 ID">
              <NInput :value="String(auth.user?.id ?? '')" disabled />
            </NFormItem>
            <NFormItem label="用户名">
              <NInput :value="auth.user?.username || ''" disabled />
            </NFormItem>
            <NFormItem label="昵称" path="nickname">
              <NInput v-model:value="profileForm.nickname" maxlength="64" show-count placeholder="显示名称" />
            </NFormItem>
            <NFormItem label="邮箱" path="email">
              <NInput v-model:value="profileForm.email" placeholder="选填，如 admin@example.com" />
            </NFormItem>
            <NFormItem label="角色">
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
            <NFormItem label="权限数">
              <span class="readonly-text">{{ permissionCount }}</span>
            </NFormItem>
            <NFormItem>
              <NButton type="primary" :loading="profileSaving" @click="submitProfile">保存资料</NButton>
            </NFormItem>
          </NForm>
        </div>

        <div class="page-card">
          <h2 class="section-title">修改密码</h2>
          <p class="section-hint">修改成功后需要重新登录。</p>
          <NForm
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-placement="left"
            label-width="96"
            require-mark-placement="right-hanging"
            style="max-width: 420px"
          >
            <NFormItem label="当前密码" path="oldPassword">
              <NInput
                v-model:value="pwdForm.oldPassword"
                type="password"
                show-password-on="click"
                placeholder="当前密码"
                autocomplete="current-password"
              />
            </NFormItem>
            <NFormItem label="新密码" path="newPassword">
              <NInput
                v-model:value="pwdForm.newPassword"
                type="password"
                show-password-on="click"
                placeholder="8-64 位，含字母和数字"
                autocomplete="new-password"
              />
            </NFormItem>
            <NFormItem label="确认密码" path="confirmPassword">
              <NInput
                v-model:value="pwdForm.confirmPassword"
                type="password"
                show-password-on="click"
                placeholder="再次输入新密码"
                autocomplete="new-password"
              />
            </NFormItem>
            <NFormItem>
              <NButton type="primary" :loading="pwdSaving" @click="submitPassword">保存新密码</NButton>
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
  background: #2a2f3a;
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
  background: rgba(15, 18, 28, 0.55);
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
  color: #9aa3b2;
  font-size: 13px;
}

.section-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
}

.section-hint {
  margin: 0 0 16px;
  color: #9aa3b2;
  font-size: 13px;
}

.readonly-text {
  color: #e8eaed;
  line-height: 34px;
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
