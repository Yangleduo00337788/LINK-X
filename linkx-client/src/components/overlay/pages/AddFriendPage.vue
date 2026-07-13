<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { useOverlayStore } from '../../../stores/overlay'
import { useNotificationsStore } from '../../../stores/notifications'
import * as friendApi from '../../../api/friend'

const message = useMessage()
const overlayStore = useOverlayStore()
const notificationsStore = useNotificationsStore()
const { close } = overlayStore

const addFriendAccount = ref('')
const addFriendMsg = ref('我是…')
const submitting = ref(false)

async function submitAddFriend() {
  const account = addFriendAccount.value.trim()
  if (!account) {
    message.warning('请输入账号')
    return
  }

  submitting.value = true
  try {
    const res = await friendApi.sendFriendRequest({
      username: account,
      message: addFriendMsg.value.trim() || undefined
    })
    if (res.code === 200) {
      message.success(`已向「${account}」发送好友申请`)
      addFriendAccount.value = ''
      await notificationsStore.fetchFriendRequests()
      close()
      return
    }
    message.error(res.message || '发送好友申请失败')
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '发送好友申请失败')
  } finally {
    submitting.value = false
  }
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
      <n-button type="primary" :loading="submitting" @click="submitAddFriend">发送申请</n-button>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
