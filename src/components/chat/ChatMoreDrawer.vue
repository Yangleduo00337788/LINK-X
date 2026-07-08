<script setup lang="ts">
import { ref } from 'vue'
import { NSwitch, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'

const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const message = useMessage()
const { moreDrawerOpen } = storeToRefs(chatModalsStore)
const { closeMore } = chatModalsStore
const { currentSession } = storeToRefs(appStore)

const pinTop = ref(false)
const mute = ref(false)
const block = ref(false)

function onBackdrop() {
  closeMore()
}

function demo(action: string) {
  message.info(`${action}（演示）`)
  if (action.includes('删除聊天记录')) closeMore()
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="moreDrawerOpen" class="drawer-root" @click.self="onBackdrop">
        <Transition name="drawer-slide">
          <aside v-if="moreDrawerOpen" class="drawer-panel" @click.stop>
            <div class="drawer-inner">
              <div class="row switch-row">
                <span>设为置顶</span>
                <n-switch v-model:value="pinTop" size="small" />
              </div>
              <div class="row switch-row">
                <span>消息免打扰</span>
                <n-switch v-model:value="mute" size="small" />
              </div>
              <div class="row switch-row">
                <span>屏蔽此人</span>
                <n-switch v-model:value="block" size="small" />
              </div>
              <button type="button" class="row link-row" @click="demo('文件传输列表')">
                文件传输列表
              </button>
              <button type="button" class="row danger-text" @click="demo('删除聊天记录')">
                删除聊天记录
              </button>
              <button type="button" class="row danger-text" @click="demo('删除好友')">
                删除好友
              </button>
              <p class="report">
                <a href="#" @click.prevent="demo('举报该用户')">被骚扰了？举报该用户</a>
              </p>
              <p v-if="currentSession" class="hint-name">{{ currentSession.name }}</p>
            </div>
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-root {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.25);
}

.drawer-panel {
  position: absolute;
  top: 0;
  right: 0;
  width: min(280px, 42vw);
  height: 100%;
  background: #fff;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
}

.drawer-inner {
  padding: 20px 18px 24px;
}

.row {
  width: 100%;
  text-align: left;
  border: none;
  background: none;
  font-size: 14px;
  color: #333;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.link-row {
  cursor: pointer;
  color: #333;
}

.link-row:hover {
  color: #12b7f5;
}

.danger-text {
  cursor: pointer;
  color: #e34d59;
  font-size: 14px;
}

.report {
  margin: 20px 0 0;
  text-align: center;
  font-size: 12px;
}

.report a {
  color: #12b7f5;
  text-decoration: none;
}

.hint-name {
  display: none;
}

.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 0.25s ease;
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}

.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.drawer-slide-enter-from,
.drawer-slide-leave-to {
  transform: translateX(100%);
}
</style>