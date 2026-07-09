<script setup lang="ts">
// Vue 响应式 API
import { ref } from 'vue'
// Naive UI 按钮、表单与消息提示
import { NButton, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
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

// 新建群名称
const createGroupName = ref('')
// 邀请成员名单（逗号分隔）
const createGroupMembers = ref('')

// 创建群聊并跳转到聊天页
function submitCreateGroup() {
  const name = createGroupName.value.trim() || '新建群聊'
  // 解析成员名称列表
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
  // 无成员时默认加入当前用户
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
  <!-- 发起群聊页面 -->
  <div class="page-wrap create-group-page">
    <!-- 群名称与成员表单 -->
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
