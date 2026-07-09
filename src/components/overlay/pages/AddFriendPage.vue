<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { useAppStore } from '../../../stores/app'
import { useContactsStore } from '../../../stores/contacts'
import { useOverlayStore } from '../../../stores/overlay'

const message = useMessage()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const overlayStore = useOverlayStore()
const { close } = overlayStore

const addFriendAccount = ref('')
const addFriendMsg = ref('我是…')

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
  <div class="page-wrap add-friend-page">
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
