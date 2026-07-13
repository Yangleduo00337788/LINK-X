<script setup lang="ts">
/**
 * 编辑资料弹窗（QQ 风格）。
 * 支持昵称、性别、生日、地区编辑；头像可点击更换。
 */
import { ref, watch } from 'vue'
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

const genderOptions = [
  { label: '男', value: '男' },
  { label: '女', value: '女' }
]

const countryOptions = [{ label: '中国', value: '中国' }]

const provinceOptions = [
  '北京', '上海', '广东', '浙江', '江苏', '四川', '湖北', '湖南', '福建', '山东'
].map(p => ({ label: p, value: p }))

const regionOptions = [
  '请选择', '城区', '郊区', '高新区', '开发区'
].map(r => ({ label: r, value: r }))

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
    message.warning('请输入昵称')
    return
  }
  if (nickname.length > 36) {
    message.warning('昵称不能超过 36 个字符')
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
    message.success('资料已保存')
    closeEditProfile()
  } catch (error) {
    message.error('保存失败: ' + (error as Error).message)
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
    message.error('请选择图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.error('图片大小不能超过 10MB')
    return
  }

  uploading.value = true
  try {
    await appStore.updateAvatar(file)
    message.success('头像已更新')
  } catch (error) {
    message.error('上传失败: ' + (error as Error).message)
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
        <span class="modal-title">编辑资料</span>
        <button type="button" class="close-btn" aria-label="关闭" @click="closeEditProfile">
          <n-icon :component="CloseOutline" :size="20" />
        </button>
      </div>

      <div class="avatar-section">
        <button
          type="button"
          class="avatar-btn"
          :class="{ uploading }"
          title="点击更换头像"
          @click="triggerAvatarUpload"
        >
          <img
            :src="userProfile.avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user'"
            alt="头像"
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
          <label class="form-label">昵称</label>
          <n-input
            v-model:value="profileNick"
            placeholder="输入昵称"
            maxlength="36"
            show-count
            class="form-control"
          />
        </div>

        <div class="form-row">
          <label class="form-label">性别</label>
          <n-select
            v-model:value="profileGender"
            :options="genderOptions"
            to="body"
            class="form-control"
          />
        </div>

        <div class="form-row">
          <label class="form-label">生日</label>
          <n-date-picker
            v-model:value="profileBirthday"
            type="date"
            clearable
            to="body"
            placement="top-start"
            class="form-control"
            placeholder="选择生日"
          />
        </div>

        <div class="form-row">
          <label class="form-label">国家</label>
          <n-select
            v-model:value="profileCountry"
            :options="countryOptions"
            to="body"
            class="form-control"
          />
        </div>

        <div class="form-row form-row-split">
          <label class="form-label">省份</label>
          <div class="split-controls">
            <n-select
              v-model:value="profileProvince"
              :options="provinceOptions"
              placeholder="请选择"
              clearable
              to="body"
              class="split-item"
            />
            <n-select
              v-model:value="profileRegion"
              :options="regionOptions"
              placeholder="请选择"
              clearable
              to="body"
              class="split-item"
            />
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        <n-button @click="closeEditProfile">取消</n-button>
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
