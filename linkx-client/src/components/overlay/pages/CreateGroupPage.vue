<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { useAppStore } from '../../../stores/app'
import { useOverlayStore } from '../../../stores/overlay'
import * as groupApi from '../../../api/group'
import * as friendApi from '../../../api/friend'
import type { UserSearchResult } from '../../../types/friend'

const message = useMessage()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const { close } = overlayStore

const createGroupName = ref('')
const createGroupMembers = ref('')
const submitting = ref(false)

async function submitCreateGroup() {
  const name = createGroupName.value.trim() || '新建群聊'
  const memberNames = createGroupMembers.value
    .split(/[,，、\s]+/)
    .map(s => s.trim())
    .filter(Boolean)

  if (!memberNames.length) {
    message.warning('请至少邀请一个成员')
    return
  }

  submitting.value = true
  try {
    const userIds: string[] = []
    for (const memberName of memberNames) {
      const res = await friendApi.searchUsers(memberName)
      if (res.code === 200 && res.data && res.data.length > 0) {
        const user = res.data.find((u: UserSearchResult) =>
          u.nickname === memberName || u.username === memberName
        ) || res.data[0]
        userIds.push(String(user.userId))
      }
    }

    if (userIds.length === 0) {
      message.warning('未找到任何成员，请检查用户名是否正确')
      return
    }

    const createRes = await groupApi.createGroup({
      name,
      memberIds: userIds
    })

    if (createRes.code !== 200 || !createRes.data) {
      message.error(createRes.message || '创建群聊失败')
      return
    }

    await appStore.loadChatSessions()

    const newGroup = appStore.sessions.find(s => s.id === String(createRes.data.id))
    if (newGroup) {
      appStore.selectSession(newGroup)
    }
    appStore.setNav('chat')

    message.success('群聊已创建')
    createGroupName.value = ''
    createGroupMembers.value = ''
    close()
  } catch (e) {
    console.error('创建群聊失败:', e)
    message.error('创建群聊失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-wrap create-group-page">
    <section class="group-card">
      <n-form label-placement="top">
        <n-form-item label="群名称">
          <n-input v-model:value="createGroupName" placeholder="起个群名" />
        </n-form-item>
        <n-form-item label="邀请成员">
          <n-input
            v-model:value="createGroupMembers"
            placeholder="多个成员用逗号分隔"
            :disabled="submitting"
          />
        </n-form-item>
      </n-form>
      <n-button
        type="primary"
        :loading="submitting"
        :disabled="submitting"
        @click="submitCreateGroup"
      >
        创建并进入
      </n-button>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
