<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useMessage } from 'naive-ui'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { groupFilesOpen } = storeToRefs(chatModalsStore)
const { closeGroupFiles } = chatModalsStore
const { currentSession } = storeToRefs(appStore)

const search = ref('')

const fileGroups = [
  {
    month: '2026年7月',
    files: [
      { name: 'sub2api.2026-07-04_08-04-03.json', size: '58.4 KB', dl: 12, user: '蓬蒿人', date: '07/04' },
      { name: 'sub2api.2026-07-04_06-43-09.json', size: '58.2 KB', dl: 8, user: '蓬蒿人', date: '07/04' },
      {
        name: 'hf_20260627_104656_5b141f4b-bf...9f73d3.mp4',
        size: '23.0 MB',
        dl: 45,
        user: 'Zander',
        date: '07/02',
        expire: '22天后过期'
      },
      { name: 'Cursor 账号3万+.txt', size: '15.7 MB', dl: 120, user: '打工人', date: '07/01' }
    ]
  },
  {
    month: '2026年6月',
    files: [
      {
        name: 'Pchat协同演示单机游戏开发.mp4',
        size: '202.1 MB',
        dl: 33,
        user: '33',
        date: '06/28'
      },
      {
        name: '卡密导出_20260624 (3).txt',
        size: '1.5 KB',
        dl: 5,
        user: '有BB机的小豆包',
        date: '06/24'
      }
    ]
  }
]

function close() {
  closeGroupFiles()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="groupFilesOpen" class="modal-root" @click.self="close">
      <div class="files-window" @click.stop>
        <header class="win-head">
          <h2>群文件 - {{ currentSession?.name || '群聊' }}</h2>
          <button type="button" class="close-x" @click="close">×</button>
        </header>
        <div class="search-row">
          <input v-model="search" type="text" class="search-field" placeholder="搜索" />
        </div>
        <div class="file-scroll">
          <section v-for="g in fileGroups" :key="g.month" class="month-block">
            <h3 class="month-title">{{ g.month }}</h3>
            <div v-for="(f, i) in g.files" :key="i" class="file-row">
              <div class="file-ico">��</div>
              <div class="file-main">
                <div class="file-name">{{ f.name }}</div>
                <div class="file-meta">
                  <span>{{ f.size }}</span>
                  <span>{{ f.dl }}次下载</span>
                  <span>{{ f.user }}</span>
                  <span>{{ f.date }}</span>
                  <span v-if="f.expire" class="expire">{{ f.expire }}</span>
                </div>
              </div>
            </div>
          </section>
        </div>
        <footer class="win-foot">
          <span>共 19 个文件，342.77 MB / 10 GB</span>
          <button type="button" class="upload-btn" @click="message.info('上传文件（演示）')">
            上传文件
          </button>
        </footer>
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

.files-window {
  width: min(720px, 96vw);
  height: min(560px, 88vh);
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

.search-row {
  padding: 12px 18px;
}

.search-field {
  width: 100%;
  height: 32px;
  border: 1px solid #e8e8e8;
  border-radius: var(--lx-radius);
  padding: 0 12px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.file-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 18px;
}

.month-title {
  margin: 12px 0 8px;
  font-size: 13px;
  color: #999;
  font-weight: 500;
}

.file-row {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.file-ico {
  font-size: 28px;
  flex-shrink: 0;
}

.file-name {
  font-size: 14px;
  color: #222;
  margin-bottom: 6px;
  word-break: break-all;
}

.file-meta {
  font-size: 12px;
  color: #999;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.expire {
  color: #fa8c16;
}

.win-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-top: 1px solid #eee;
  font-size: 12px;
  color: #666;
}

.upload-btn {
  height: 32px;
  padding: 0 16px;
  border: none;
  border-radius: var(--lx-radius);
  background: #12b7f5;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
</style>