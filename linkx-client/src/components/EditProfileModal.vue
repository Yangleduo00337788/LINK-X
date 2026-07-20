<script setup lang="ts">
/**
 * 编辑资料弹窗。
 * 支持昵称、性别、生日、地区编辑；头像可点击更换。
 */
import { ref, watch, computed } from 'vue'
import {
  NModal,
  NButton,
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
import { generateDefaultAvatar } from '../utils/defaultAvatar'
import { normalizeMediaUrl } from '../utils/mediaUrl'
import { useI18n } from '../i18n'

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
const profileCountry = ref('中国')
const profileProvince = ref<string | null>(null)
const profileRegion = ref<string | null>(null)
const saving = ref(false)
const uploading = ref(false)
const avatarInputRef = ref<HTMLInputElement | null>(null)

const defaultAvatarUrl = computed(() => generateDefaultAvatar(profileNick.value || t('common.me')))
const avatarSrc = computed(
  () => normalizeMediaUrl(userProfile.value.avatar) || defaultAvatarUrl.value
)

const genderOptions = computed(() => [
  { label: t('modals.male'), value: '男' },
  { label: t('modals.female'), value: '女' }
])

const countryOptions = computed(() => [{ label: t('modals.china'), value: '中国' }])

const provinceOptions = [
  '北京', '上海', '广东', '浙江', '江苏', '四川', '湖北', '湖南', '福建', '山东'
].map(p => ({ label: p, value: p }))

const regionOptions = computed(() => [
  { label: t('modals.pleaseSelect'), value: '请选择' },
  ...['城区', '郊区', '高新区', '开发区'].map(r => ({ label: r, value: r }))
])

function syncFromStore() {
  profileNick.value = userProfile.value.nickname
  profileGender.value = userProfile.value.gender
  profileBirthday.value = userProfile.value.birthday
  profileCountry.value = userProfile.value.country || '中国'
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
      region: profileRegion.value === '请选择' ? '' : (profileRegion.value || '')
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
  <n-modal
    v-model:show="editProfileOpen"
    class="edit-profile-modal"
    preset="card"
    to="body"
    :bordered="false"
    :show-icon="false"
    :closable="false"
    :mask-closable="true"
    :z-index="10002"
    style="width: 480px; max-width: 94vw; border-radius: 12px; padding: 0;"
    @after-leave="closeEditProfile"
  >
    <div class="edit-profile-shell">
      <div class="modal-header">
        <span class="modal-title">{{ t('modals.editProfile') }}</span>
        <button type="button" class="close-btn" :aria-label="t('modals.close')" @click="closeEditProfile">
          <n-icon :component="CloseOutline" :size="20" />
        </button>
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
            :src="avatarSrc"
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
        <n-button type="primary" :loading="saving" @click="handleSave">{{ t('common.save') }}</n-button>
        <n-button @click="closeEditProfile">{{ t('common.cancel') }}</n-button>
      </div>
    </div>
  </n-modal>
</template>

<style scoped>
.edit-profile-shell {
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
  border-radius: 12px;
  overflow: hidden;
}

.modal-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px 48px 12px;
  border-bottom: 1px solid var(--lx-border-light);
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
}

.close-btn {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border-radius: 6px;
}

.close-btn:hover {
  background: var(--lx-bg-panel);
  color: var(--lx-text-body);
}

.avatar-section {
  display: flex;
  justify-content: center;
  padding: 24px 0 8px;
}

.avatar-btn {
  position: relative;
  width: 88px;
  height: 88px;
  border: none;
  padding: 0;
  border-radius: 50%;
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
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-btn:hover .avatar-mask {
  opacity: 1;
}

.form-body {
  padding: 12px 28px 8px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.form-row-split {
  align-items: flex-start;
}

.form-label {
  width: 48px;
  flex-shrink: 0;
  font-size: 14px;
  color: var(--lx-text-body);
  line-height: 34px;
}

.form-control {
  flex: 1;
  min-width: 0;
}

.split-controls {
  flex: 1;
  display: flex;
  gap: 12px;
  min-width: 0;
}

.split-item {
  flex: 1;
  min-width: 0;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 28px 24px;
}
</style>
