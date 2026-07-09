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

const channelName = ref('')
const channelDesc = ref('')

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
  <div class="page-wrap create-channel-page">
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
