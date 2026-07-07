<script setup lang="ts">
import { useChatModals } from '../../composables/useChatModals'
import { useAppState } from '../../composables/useAppState'

const { groupEssenceOpen, closeGroupEssence } = useChatModals()
const { currentSession } = useAppState()

const items = [
  {
    user: '有BB机的小豆包',
    date: '05-29',
    type: 'link',
    content: 'https://millionweekend.feishu.cn/wiki/JS47wPzs6iDHLZkLMHAc8rhun0e'
  },
  {
    user: '一杆大铁枪',
    date: '05-08',
    type: 'video',
    content: '新手教程.mp4'
  },
  {
    user: '33',
    date: '04-21',
    type: 'link',
    content: 'https://mcp.sukeyun.com/'
  }
]

function close() {
  closeGroupEssence()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="groupEssenceOpen" class="modal-root" @click.self="close">
      <div class="essence-window" @click.stop>
        <header class="win-head">
          <h2>群精华 - {{ currentSession?.name || '群聊' }}</h2>
          <button type="button" class="close-x" @click="close">×</button>
        </header>
        <div class="list">
          <article v-for="(item, i) in items" :key="i" class="essence-card">
            <div class="card-head">
              <span class="user">{{ item.user }}</span>
              <span class="date">{{ item.date }}</span>
            </div>
            <div v-if="item.type === 'video'" class="video-thumb">
              <span class="play">▶</span>
              <span class="vname">{{ item.content }}</span>
            </div>
            <a v-else class="link-text" href="#" @click.prevent>{{ item.content }}</a>
          </article>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: 2200;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.essence-window {
  width: min(520px, 94vw);
  max-height: min(520px, 85vh);
  background: #fff;
  border-radius: var(--lx-radius);
  display: flex;
  flex-direction: column;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
}

.win-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #eee;
}

.win-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.close-x {
  border: none;
  background: none;
  font-size: 22px;
  color: #999;
  cursor: pointer;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 18px 18px;
}

.essence-card {
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}

.card-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
}

.user {
  color: #333;
  font-weight: 500;
}

.date {
  color: #999;
}

.link-text {
  font-size: 13px;
  color: #12b7f5;
  word-break: break-all;
  text-decoration: none;
}

.video-thumb {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #f5f5f5;
  border-radius: var(--lx-radius);
}

.play {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #12b7f5;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.vname {
  font-size: 13px;
  color: #333;
}
</style>