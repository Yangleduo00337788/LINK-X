<script setup lang="ts">
import { ref } from 'vue'
import { NIcon, NSwitch } from 'naive-ui'
import { SearchOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useMessage } from 'naive-ui'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { groupInfoDrawerOpen } = storeToRefs(chatModalsStore)
const { closeGroupInfo, openGroupAnnouncement, openAddMembers } = chatModalsStore
const { currentSession, userProfile } = storeToRefs(appStore)

const pinTop = ref(false)
const mute = ref(true)
const groupRemark = ref('')

const memberPreview = [
  { text: '有', color: '#f56c6c' },
  { text: '颜', color: '#12b7f5' },
  { text: '重', color: '#722ed1' },
  { text: '嫉', color: '#fa541c' },
  { text: 'J', color: '#52c41a' },
  { text: '李', color: '#fa8c16' },
  { text: 'M', color: '#1890ff' },
  { text: 'Q', color: '#0099ff' },
  { text: '我', color: '#eb2f96' },
  { text: '小', color: '#13c2c2' },
  { text: '雪', color: '#2f54eb' },
  { text: '执', color: '#595959' },
  { text: '3', color: '#8c8c8c' },
  { text: 'a', color: '#bfbfbf' }
]

const groupId = '1007446249'
const announcement = '卡网：https://pay.ldxp.cn/shop/aozai cursor 白号 售后 GPT 成品号'

function close() {
  closeGroupInfo()
}

function demo(t: string) {
  message.info(`${t}（演示）`)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="groupInfoDrawerOpen" class="drawer-root" @click.self="close">
        <Transition name="drawer-slide">
          <aside v-if="groupInfoDrawerOpen" class="drawer-panel" @click.stop>
            <div class="drawer-scroll">
              <div class="group-hero">
                <Avatar
                  :text="currentSession?.avatarText || '群'"
                  :color="currentSession?.avatarColor || '#e74c3c'"
                  :size="56"
                />
                <h2 class="g-name">{{ currentSession?.name || '群聊' }}</h2>
                <p class="g-id">群号：{{ groupId }}</p>
                <button type="button" class="share-btn" @click="demo('分享')">分享</button>
              </div>

              <section class="block">
                <div class="block-head">
                  <span>群聊成员</span>
                  <n-icon :component="SearchOutline" :size="18" class="ico" />
                </div>
                <div class="avatar-grid">
                  <div v-for="(m, i) in memberPreview" :key="i" class="av">
                    <Avatar :text="m.text" :color="m.color" :size="40" />
                  </div>
                  <button
                    type="button"
                    class="av invite"
                    title="邀请"
                    @click="openAddMembers"
                  >
                    +
                  </button>
                </div>
                <button type="button" class="link-row" @click="demo('查看全部成员')">
                  查看全部成员
                </button>
              </section>

              <section class="block">
                <h3 class="block-title">群公告</h3>
                <button type="button" class="announce announce-btn" @click="openGroupAnnouncement">
                  {{ announcement }}
                </button>
              </section>

              <section class="block row-item">
                <span>我的本群昵称</span>
                <span class="muted">{{ userProfile.nickname }}</span>
              </section>

              <section class="block">
                <div class="row-item">
                  <span>群聊备注</span>
                </div>
                <input
                  v-model="groupRemark"
                  type="text"
                  class="remark-input"
                  placeholder="填写备注"
                />
              </section>

              <div class="switch-block">
                <div class="switch-row">
                  <span>设为置顶</span>
                  <n-switch v-model:value="pinTop" size="small" />
                </div>
                <div class="switch-row">
                  <span>消息免打扰</span>
                  <n-switch v-model:value="mute" size="small" />
                </div>
                <p class="hint">接收消息但不提醒</p>
              </div>

              <button type="button" class="action-btn" @click="demo('删除聊天记录')">
                删除聊天记录
              </button>
              <button type="button" class="action-btn danger" @click="demo('退出群聊')">
                退出群聊
              </button>
              <p class="report">
                <a href="#" @click.prevent="demo('举报该群')">被骚扰了？举报该群</a>
              </p>
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
  width: min(320px, 88vw);
  height: 100%;
  background: #fff;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
}

.drawer-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 20px 18px 28px;
}

.group-hero {
  text-align: center;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 12px;
}

.group-hero :deep(.avatar) {
  margin: 0 auto 10px;
}

.g-name {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: #222;
  line-height: 1.3;
}

.g-id {
  margin: 0 0 12px;
  font-size: 12px;
  color: #999;
}

.share-btn {
  min-width: 88px;
  height: 32px;
  border-radius: var(--lx-radius);
  border: 1px solid #ddd;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
  color: #333;
}

.block {
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.ico {
  color: #999;
  cursor: pointer;
}

.avatar-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 10px;
}

.av {
  display: flex;
  justify-content: center;
}

.invite {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px dashed #ccc;
  background: #fafafa;
  font-size: 22px;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}

.link-row {
  border: none;
  background: none;
  color: #12b7f5;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
}

.block-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.announce {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #666;
  word-break: break-all;
}

.announce-btn {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.announce-btn:hover {
  color: #12b7f5;
}

.row-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #333;
}

.muted {
  color: #999;
  font-size: 13px;
}

.remark-input {
  width: 100%;
  margin-top: 8px;
  height: 36px;
  border: none;
  border-bottom: 1px solid #eee;
  font-size: 13px;
  outline: none;
  color: #333;
}

.switch-block {
  padding: 8px 0 16px;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  font-size: 14px;
  color: #333;
}

.hint {
  margin: -4px 0 0;
  font-size: 12px;
  color: #999;
}

.action-btn {
  width: 100%;
  height: 40px;
  border: none;
  background: #f5f5f5;
  border-radius: var(--lx-radius);
  font-size: 14px;
  color: #333;
  cursor: pointer;
  margin-bottom: 10px;
}

.action-btn.danger {
  background: transparent;
  color: #e34d59;
}

.report {
  text-align: center;
  margin: 16px 0 0;
  font-size: 12px;
}

.report a {
  color: #12b7f5;
  text-decoration: none;
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