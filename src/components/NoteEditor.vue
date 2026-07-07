<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import {
  RemoveOutline,
  CloseOutline,
  SquareOutline,
  CopyOutline,
  ImageOutline,
  FolderOpenOutline,
  MicOutline,
  LocationOutline,
  ListOutline,
  CheckboxOutline,
  TextOutline
} from '@vicons/ionicons5'

const message = useMessage()

const title = ref('')
const content = ref('')

const isMaximized = ref(false)

function minimizeWindow() {
  if (window.electronAPI) {
    window.electronAPI.minimize()
  }
}

function toggleMaximize() {
  if (window.electronAPI) {
    window.electronAPI.maximize()
    window.electronAPI.isMaximized().then(res => {
      isMaximized.value = res
    })
  }
}

function closeWindow() {
  if (window.electronAPI) {
    window.electronAPI.close()
  } else {
    window.close()
  }
}

function handleToolClick(toolName: string) {
  message.info(`点击了：${toolName}`)
}

let cleanupMaximizedListener: (() => void) | undefined

onMounted(() => {
  if (window.electronAPI?.onMaximizedChange) {
    cleanupMaximizedListener = window.electronAPI.onMaximizedChange((maximized) => {
      isMaximized.value = maximized
    })
  }
})

onUnmounted(() => {
  if (cleanupMaximizedListener) {
    cleanupMaximizedListener()
  }
})
</script>

<template>
  <div class="note-editor standalone-window">
    <div class="header drag-area">
      <div class="header-left">
        <span class="title">笔记</span>
      </div>
      <div class="header-right no-drag">
        <div class="action-btn" title="最小化" @click="minimizeWindow">
          <n-icon :component="RemoveOutline" size="18" />
        </div>
        <div class="action-btn" title="最大化" @click="toggleMaximize">
          <n-icon :component="isMaximized ? CopyOutline : SquareOutline" size="14" />
        </div>
        <div class="action-btn close-btn" title="关闭" @click="closeWindow">
          <n-icon :component="CloseOutline" size="18" />
        </div>
      </div>
    </div>
    
    <div class="editor-container no-drag">
      <div class="toolbar">
        <div class="toolbar-group">
          <div class="tool-btn" title="图片" @click="handleToolClick('图片')">
            <n-icon :component="ImageOutline" size="20" />
          </div>
          <div class="tool-btn" title="文件" @click="handleToolClick('文件')">
            <n-icon :component="FolderOpenOutline" size="20" />
          </div>
          <div class="tool-btn" title="语音" @click="handleToolClick('语音')">
            <n-icon :component="MicOutline" size="20" />
          </div>
          <div class="tool-btn" title="位置" @click="handleToolClick('位置')">
            <n-icon :component="LocationOutline" size="20" />
          </div>
        </div>
        <div class="toolbar-divider"></div>
        <div class="toolbar-group">
          <div class="tool-btn" title="文本格式" @click="handleToolClick('文本格式')">
            <n-icon :component="TextOutline" size="20" />
          </div>
          <div class="tool-btn" title="列表" @click="handleToolClick('列表')">
            <n-icon :component="ListOutline" size="20" />
          </div>
          <div class="tool-btn" title="待办" @click="handleToolClick('待办')">
            <n-icon :component="CheckboxOutline" size="20" />
          </div>
        </div>
      </div>

      <div class="editor-body">
        <input
          v-model="title"
          type="text"
          placeholder="请填写标题"
          class="title-input"
        />
        <div class="divider"></div>
        <textarea
          v-model="content"
          placeholder="请填写正文"
          class="content-input"
        ></textarea>
      </div>
    </div>
  </div>
</template>

<style scoped>
.standalone-window {
  width: 100vw !important;
  height: 100vh !important;
  border-radius: 0 !important;
  margin: 0 !important;
}

.note-editor {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
  height: 100vh;
}

.header {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  background: #f5f5f5;
  border-bottom: 1px solid #ebebeb;
  flex-shrink: 0;
}

.drag-area {
  -webkit-app-region: drag;
}

.no-drag {
  -webkit-app-region: no-drag;
}

.header-left .title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.action-btn {
  width: 28px;
  height: 28px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s;
  color: #333;
}

.action-btn:hover {
  background: rgba(0, 0, 0, 0.1);
}

.close-btn:hover {
  background: #fa5151;
  color: #ffffff;
}

.editor-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.toolbar {
  height: 48px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: #ffffff;
  border-bottom: 1px solid #ebebeb;
  flex-shrink: 0;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.tool-btn:hover {
  background: #f0f0f0;
  color: #333;
}

.toolbar-divider {
  width: 1px;
  height: 16px;
  background: #ebebeb;
  margin: 0 12px;
}

.editor-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 24px 40px;
  overflow-y: auto;
}

.title-input {
  font-size: 22px;
  font-weight: 600;
  color: #333;
  border: none;
  outline: none;
  background: transparent;
  padding: 0 0 16px 0;
}

.title-input::placeholder {
  color: #c0c0c0;
  font-weight: 500;
}

.divider {
  height: 1px;
  background: #ebebeb;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.content-input {
  flex: 1;
  font-size: 15px;
  line-height: 1.8;
  color: #333;
  border: none;
  outline: none;
  background: transparent;
  resize: none;
  padding: 0;
}

.content-input::placeholder {
  color: #c0c0c0;
}
</style>
