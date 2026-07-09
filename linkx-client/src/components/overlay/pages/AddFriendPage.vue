<script setup lang="ts">
// Vue 响应式 API
import { ref } from 'vue'
// Naive UI 按钮、表单与消息提示
import { NButton, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
// 应用全局状态 Store
import { useAppStore } from '../../../stores/app'
// 联系人 Store
import { useContactsStore } from '../../../stores/contacts'
// 全屏覆盖层 Store
import { useOverlayStore } from '../../../stores/overlay'

// 消息提示实例
const message = useMessage()
// 应用 Store 实例
const appStore = useAppStore()
// 联系人 Store 实例
const contactsStore = useContactsStore()
// 覆盖层 Store 实例
const overlayStore = useOverlayStore()
// 关闭覆盖层的方法
const { close } = overlayStore

// 要添加的好友账号
const addFriendAccount = ref('')
// 好友申请验证信息
const addFriendMsg = ref('我是…')

// 提交好友申请（本地模拟）
function submitAddFriend() {
  const account = addFriendAccount.value.trim()
  if (!account) {
    message.warning('请输入账号')
    return
  }
  appStore.addFriendSession(account)
  contactsStore.addByName(account)
  message.success(`已向「${account}」发送好友申请（本地模拟）`)
  addFriendAccount.value = ''
  close()
}
</script>

<template>
  <!-- 添加好友页面 -->
  <div class="page-wrap add-friend-page">
    <!-- 账号与验证信息表单 -->
    <section class="group-card">
      <n-form label-placement="top">
        <n-form-item label="LinkX ID / 手机号">
          <n-input v-model:value="addFriendAccount" placeholder="输入账号" />
        </n-form-item>
        <n-form-item label="验证信息">
          <n-input v-model:value="addFriendMsg" placeholder="我是…" />
        </n-form-item>
      </n-form>
      <n-button type="primary" @click="submitAddFriend">发送申请</n-button>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
