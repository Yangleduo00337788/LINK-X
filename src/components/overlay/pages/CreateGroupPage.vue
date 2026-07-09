<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../../stores/app'
import { useOverlayStore } from '../../../stores/overlay'

const message = useMessage()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const { userProfile } = storeToRefs(appStore)
const { close } = overlayStore

const createGroupName = ref('')
const createGroupMembers = ref('')

function submitCreateGroup() {
  const name = createGroupName.value.trim() || '新建群聊'
  const memberNames = createGroupMembers.value
    .split(/[,，、\s]+/)
    .map(s => s.trim())
    .filter(Boolean)
  const members = memberNames.map((n, i) => ({
    id: `invite-${i}-${Date.now()}`,
    name: n,
    avatarText: n.charAt(0) || '?',
    avatarColor: '#12b7f5'
  }))
  if (!members.length) {
    members.push({
      id: `invite-self-${Date.now()}`,
      name: userProfile.value.nickname,
      avatarText: userProfile.value.nickname.charAt(0) || '我',
      avatarColor: '#12b7f5'
    })
  }
  appStore.createGroup(members, name)
  appStore.setNav('chat')
  message.success('群聊已创建')
  createGroupName.value = ''
  createGroupMembers.value = ''
  close()
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
          <n-input v-model:value="createGroupMembers" placeholder="多个成员用逗号分隔" />
        </n-form-item>
      </n-form>
      <n-button type="primary" @click="submitCreateGroup">创建并进入</n-button>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
