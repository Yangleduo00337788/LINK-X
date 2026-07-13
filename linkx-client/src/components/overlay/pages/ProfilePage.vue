<script setup lang="ts">
// Vue 响应式 API 与侦听器
import { computed, ref, watch } from 'vue'
// Naive UI 按钮、图标、表单与消息提示
import { NButton, NIcon, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
// Ionicons5 个人资料图标
import { PersonOutline, CameraOutline } from '@vicons/ionicons5'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../../../stores/app'
// 全屏覆盖层 Store
import { useOverlayStore } from '../../../stores/overlay'

// 消息提示实例
const message = useMessage()
// 应用 Store 实例
const appStore = useAppStore()
// 覆盖层 Store 实例
const overlayStore = useOverlayStore()
// 用户资料与登录账号
const { userProfile, savedLogin } = storeToRefs(appStore)
// 关闭覆盖层的方法
const { close } = overlayStore

const displayUsername = computed(
  () => savedLogin.value.username || userProfile.value.username || '—'
)

const defaultAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user'

// 编辑中的昵称
const profileNick = ref(userProfile.value.nickname)
// 编辑中的个性签名
const profileSig = ref(userProfile.value.signature)

// 同步 Store 中的资料到本地编辑状态
watch(() => userProfile.value, () => {
  profileNick.value = userProfile.value.nickname
  profileSig.value = userProfile.value.signature
}, { deep: true })

// 保存资料并关闭页面
async function saveProfile() {
  try {
    await appStore.updateNickname(profileNick.value.trim() || '晚香玉')
    await appStore.updateSignature(profileSig.value.trim() || '编辑个性签名')
    message.success('资料已保存')
    close()
  } catch (error) {
    message.error('保存失败: ' + (error as Error).message)
  }
}
// 上传中状态
const uploading = ref(false)
const avatarInputRef = ref<HTMLInputElement | null>(null)

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
    message.error('图片大小不能超过10MB')
    return
  }

  uploading.value = true
  try {
    await appStore.updateAvatar(file)
    message.success('头像上传成功')
  } catch (error) {
    message.error('上传失败: ' + (error as Error).message)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <!-- 个人资料编辑页面 -->
  <div class="page-wrap profile-page">
    <!-- 头像与基本信息展示 -->
    <section class="profile-card">
      <div class="avatar-wrapper" :class="{ uploading }">
        <img
          :src="userProfile.avatar || defaultAvatar"
          alt="头像"
          class="big-avatar"
        />
        <div class="avatar-overlay" @click="triggerAvatarUpload">
          <n-icon :component="CameraOutline" :size="24" />
          <span>更换头像</span>
        </div>
        <input
          ref="avatarInputRef"
          type="file"
          accept="image/*"
          style="display: none"
          @change="handleAvatarChange"
        />
      </div>
      <div class="profile-meta">
        <div class="profile-name">{{ profileNick || userProfile.nickname }}</div>
        <div class="profile-id">LinkX ID · {{ displayUsername }}</div>
      </div>
    </section>

    <!-- 昵称与签名编辑表单 -->
    <section class="group-card">
      <div class="group-head">
        <n-icon :component="PersonOutline" :size="18" class="group-ico" />
        <span>基本信息</span>
      </div>
      <n-form label-placement="top">
        <n-form-item label="昵称">
          <n-input v-model:value="profileNick" placeholder="输入昵称" />
        </n-form-item>
        <n-form-item label="个性签名">
          <n-input v-model:value="profileSig" placeholder="编辑个性签名" />
        </n-form-item>
        <n-form-item label="LinkX ID">
          <n-input :value="displayUsername" disabled />
        </n-form-item>
      </n-form>
      <n-button type="primary" @click="saveProfile">保存资料</n-button>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';

.avatar-wrapper {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
}

.avatar-wrapper.uploading {
  opacity: 0.7;
  pointer-events: none;
}

.big-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}
</style>
