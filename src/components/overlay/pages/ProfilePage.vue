<script setup lang="ts">
import { ref, watch } from 'vue'
import { NButton, NIcon, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { PersonOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../../stores/app'
import { useOverlayStore } from '../../../stores/overlay'

const message = useMessage()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const { userProfile } = storeToRefs(appStore)
const { close } = overlayStore

const profileNick = ref(userProfile.value.nickname)
const profileSig = ref(userProfile.value.signature)

watch(() => userProfile.value, () => {
  profileNick.value = userProfile.value.nickname
  profileSig.value = userProfile.value.signature
}, { deep: true })

function saveProfile() {
  appStore.updateNickname(profileNick.value.trim() || '晚香玉')
  appStore.updateSignature(profileSig.value.trim() || '编辑个性签名')
  message.success('资料已保存')
  close()
}
</script>

<template>
  <div class="page-wrap profile-page">
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
