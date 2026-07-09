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

// 频道名称
const channelName = ref('')
// 频道简介
const channelDesc = ref('')

// 创建频道并跳转到友链页
function submitCreateChannel() {
  const name = channelName.value.trim()
  if (!name) {
    message.warning('请输入频道名称')
    return
  }
  contactsStore.addByName(name)
  appStore.setNav('moments')
  message.success(`频道「${name}」已创建`)
  channelName.value = ''
  channelDesc.value = ''
  close()
}
</script>

<template>
  <!-- 创建频道页面 -->
  <div class="page-wrap create-channel-page">
    <!-- 频道名称与简介表单 -->
    <section class="group-card">
      <n-form label-placement="top">
        <n-form-item label="频道名称">
          <n-input v-model:value="channelName" placeholder="频道名" />
        </n-form-item>
        <n-form-item label="简介">
          <n-input v-model:value="channelDesc" type="textarea" placeholder="介绍频道" />
        </n-form-item>
      </n-form>
      <n-button type="primary" @click="submitCreateChannel">创建频道</n-button>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
