<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 编辑资料弹窗。
 * 支持昵称、性别、生日、地区编辑；头像可点击更换。
 */
import { ref, watch, computed } from 'vue'
import {
  NInput,
  NSelect,
  NDatePicker,
  NIcon,
  useMessage
} from 'naive-ui'
import { CloseOutline, CameraOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
import { generateDefaultAvatar, resolveUserAvatarUrl } from '../utils/defaultAvatar'
import { useI18n } from '../i18n'
import { LxButton, LxIconButton, LxModal } from './ui'
import {
  PROFILE_COUNTRY_CN,
  PROFILE_PROVINCES,
  PROFILE_REGIONS
} from '../constants/profileLocation'

const { t } = useI18n()
const message = useMessage()
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const { editProfileOpen } = storeToRefs(chatModalsStore)
const { closeEditProfile } = chatModalsStore
const { userProfile } = storeToRefs(appStore)

const profileNick = ref('')
const profileGender = ref<'男' | '女'>('男')
const profileBirthday = ref<number | null>(null)
const profileCountry = ref(PROFILE_COUNTRY_CN)
const profileProvince = ref<string | null>(null)
const profileRegion = ref<string | null>(null)
const saving = ref(false)
const uploading = ref(false)
const avatarInputRef = ref<HTMLInputElement | null>(null)

const defaultAvatarUrl = computed(() => generateDefaultAvatar(profileNick.value || t('common.me')))
const realAvatarUrl = computed(() =>
  resolveUserAvatarUrl(userProfile.value.avatar, userProfile.value.userId)
)

const genderOptions = computed(() => [
  { label: t('modals.male'), value: '男' },
  { label: t('modals.female'), value: '女' }
])

const countryOptions = computed(() => [{ label: t('modals.china'), value: PROFILE_COUNTRY_CN }])

const provinceOptions = computed(() =>
  PROFILE_PROVINCES.map(p => ({
    label: t(`profile.provinces.${p.key}`),
    value: p.value
  }))
)

const regionOptions = computed(() => [
  { label: t('modals.pleaseSelect'), value: '' },
  ...PROFILE_REGIONS.map(r => ({
    label: t(`profile.regions.${r.key}`),
    value: r.value
  }))
])

function syncFromStore() {
  profileNick.value = userProfile.value.nickname
  profileGender.value = userProfile.value.gender
  profileBirthday.value = userProfile.value.birthday
  profileCountry.value = userProfile.value.country || PROFILE_COUNTRY_CN
  profileProvince.value = userProfile.value.province || null
  profileRegion.value = userProfile.value.region || null
}

watch(editProfileOpen, open => {
  if (open) syncFromStore()
})

async function handleSave() {
  const nickname = profileNick.value.trim()
  if (!nickname) {
    message.warning(t('modals.enterNickname'))
    return
  }
  if (nickname.length > 36) {
    message.warning(t('modals.nicknameTooLong'))
    return
  }

  saving.value = true
  try {
    await appStore.updateProfile({
      nickname,
      gender: profileGender.value,
      birthday: profileBirthday.value,
      country: profileCountry.value,
      province: profileProvince.value || '',
      region: profileRegion.value || ''
    })
    message.success(t('modals.profileSaved'))
    closeEditProfile()
  } catch (error) {
    message.error(t('modals.saveFail', { message: (error as Error).message }))
  } finally {
    saving.value = false
  }
}

function triggerAvatarUpload() {
  avatarInputRef.value?.click()
}

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!file.type.startsWith('image/')) {
    message.error(t('modals.pickImage'))
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.error(t('modals.imageTooLarge'))
    return
  }

  uploading.value = true
  try {
    await appStore.updateAvatar(file)
    message.success(t('modals.avatarUpdated'))
  } catch (error) {
    message.error(t('modals.uploadFail', { message: (error as Error).message }))
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <LxModal
    v-model:show="editProfileOpen"
    class="edit-profile-modal"
    preset="card"
    to="body"
    :bordered="false"
    :show-icon="false"
    :closable="false"
    :mask-closable="true"
    :z-index="10002"
    style="width: 480px; max-width: 94vw; border-radius: var(--lx-radius-lg); padding: 0;"
    @after-leave="closeEditProfile"
  >
    <div class="edit-profile-shell">
      <div class="modal-header">
        <span class="modal-title">{{ t('modals.editProfile') }}</span>
        <LxIconButton
          variant="close"
          class="close-btn"
          :title="t('modals.close')"
          @click="closeEditProfile"
        >
          <n-icon :component="CloseOutline" :size="20" />
        </LxIconButton>
      </div>

      <div class="avatar-section">
        <button
          type="button"
          class="avatar-btn"
          :class="{ uploading }"
          :title="t('modals.changeAvatar')"
          @click="triggerAvatarUpload"
        >
          <img
            v-if="realAvatarUrl"
            :src="realAvatarUrl"
            :alt="t('modals.avatar')"
            class="avatar-img"
          />
          <img
            v-else
            :src="defaultAvatarUrl"
            :alt="t('modals.avatar')"
            class="avatar-img"
          />
          <span class="avatar-mask">
            <n-icon :component="CameraOutline" :size="22" />
          </span>
        </button>
        <input
          ref="avatarInputRef"
          type="file"
          accept="image/*"
          hidden
          @change="handleAvatarChange"
        />
      </div>

      <div class="form-body">
        <div class="form-row">
          <label class="form-label">{{ t('modals.nickname') }}</label>
          <n-input
            v-model:value="profileNick"
            :placeholder="t('modals.nicknamePh')"
            maxlength="36"
            show-count
            class="form-control"
          />
        </div>

        <div class="form-row">
          <label class="form-label">{{ t('modals.gender') }}</label>
          <n-select
            v-model:value="profileGender"
            :options="genderOptions"
            to="body"
            class="form-control"
          />
        </div>

        <div class="form-row">
          <label class="form-label">{{ t('modals.birthday') }}</label>
          <n-date-picker
            v-model:value="profileBirthday"
            type="date"
            clearable
            to="body"
            placement="top-start"
            class="form-control"
            :placeholder="t('modals.birthdayPh')"
          />
        </div>

        <div class="form-row">
          <label class="form-label">{{ t('modals.country') }}</label>
          <n-select
            v-model:value="profileCountry"
            :options="countryOptions"
            to="body"
            class="form-control"
          />
        </div>

        <div class="form-row form-row-split">
          <label class="form-label">{{ t('modals.province') }}</label>
          <div class="split-controls">
            <n-select
              v-model:value="profileProvince"
              :options="provinceOptions"
              :placeholder="t('modals.pleaseSelect')"
              clearable
              to="body"
              class="split-item"
            />
            <n-select
              v-model:value="profileRegion"
              :options="regionOptions"
              :placeholder="t('modals.pleaseSelect')"
              clearable
              to="body"
              class="split-item"
            />
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <LxButton variant="modal-primary" :disabled="saving" @click="handleSave">{{ t('common.save') }}</LxButton>
        <LxButton variant="modal" @click="closeEditProfile">{{ t('common.cancel') }}</LxButton>
      </div>
    </div>
  </LxModal>
</template>

<style scoped>
.edit-profile-shell {
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
  border-radius: var(--lx-radius-lg);
  overflow: hidden;
}

.modal-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-2xl) var(--lx-space-6xl) var(--lx-space-lg);
  border-bottom: 1px solid var(--lx-border-light);
}

.modal-title {
  font-size: var(--lx-font-xl);
  font-weight: 600;
}

.close-btn {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
}

.avatar-section {
  display: flex;
  justify-content: center;
  padding: var(--lx-space-4xl) 0 var(--lx-space);
}

.avatar-btn {
  position: relative;
  width: 88px;
  height: 88px;
  border: none;
  padding: 0;
  border-radius: var(--lx-avatar-radius);
  overflow: hidden;
  cursor: pointer;
  background: var(--lx-bg-panel);
}

.avatar-btn.uploading {
  opacity: 0.7;
  pointer-events: none;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  color: var(--lx-text-on-accent);
  opacity: 0;
  transition: opacity var(--lx-duration-md);
}

.avatar-btn:hover .avatar-mask {
  opacity: 1;
}

.form-body {
  padding: var(--lx-space-lg) var(--lx-space-5xl-minus) var(--lx-space);
}

.form-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-2xl);
  margin-bottom: var(--lx-space-2xl);
}

.form-row-split {
  align-items: flex-start;
}

.form-label {
  width: 48px;
  flex-shrink: 0;
  font-size: var(--lx-font);
  color: var(--lx-text-body);
  line-height: var(--lx-font-8xl);
}

.form-control {
  flex: 1;
  min-width: 0;
}

.split-controls {
  flex: 1;
  display: flex;
  gap: var(--lx-space-lg);
  min-width: 0;
}

.split-item {
  flex: 1;
  min-width: 0;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-2xl) var(--lx-space-5xl-minus) var(--lx-space-4xl);
}
</style>
