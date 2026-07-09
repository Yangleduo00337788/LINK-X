<script setup lang="ts">
// Vue 响应式 API 与侦听器
import { ref, watch } from 'vue'
// Naive UI 按钮、图标、表单与消息提示
import { NButton, NIcon, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
// Ionicons5 个人资料图标
import { PersonOutline } from '@vicons/ionicons5'
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
// 用户资料
const { userProfile } = storeToRefs(appStore)
// 关闭覆盖层的方法
const { close } = overlayStore

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
function saveProfile() {
  appStore.updateNickname(profileNick.value.trim() || '晚香玉')
  appStore.updateSignature(profileSig.value.trim() || '编辑个性签名')
  message.success('资料已保存')
  close()
}
</script>

<template>
  <!-- 个人资料编辑页面 -->
  <div class="page-wrap profile-page">
    <!-- 头像与基本信息展示 -->
    <section class="profile-card">
      <img
        src="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user"
        alt=""
        class="big-avatar"
      />
      <div class="profile-meta">
        <div class="profile-name">{{ profileNick || userProfile.nickname }}</div>
        <div class="profile-id">LinkX ID · linkx_888888</div>
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
          <n-input value="linkx_888888" disabled />
        </n-form-item>
      </n-form>
      <n-button type="primary" @click="saveProfile">保存资料</n-button>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
