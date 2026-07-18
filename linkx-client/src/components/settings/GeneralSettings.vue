<script setup lang="ts">
// Vue 组合式 API
// Naive UI 开关、按钮、单选、图标与消息提示
import { NSwitch, NIcon, NRadioGroup, NRadio, NButton, useMessage } from 'naive-ui'
// Ionicons5 桌面、通知、音乐相关图标
import {
  DesktopOutline,
  NotificationsOutline,
  PlayCircleOutline,
  MusicalNotesOutline
} from '@vicons/ionicons5'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 应用设置 Store
import { useAppSettingsStore } from '../../stores/appSettings'
// 提示音播放与解锁工具
import { listTones, playTone, unlockAudio, type ToneId } from '../../utils/notifyTone'

// 应用设置 Store 实例
const appSettingsStore = useAppSettingsStore()
// 自启动设置失败需要立即给用户提示（与 scheduleSave 的去抖 toast 是两个独立失败路径）
const message = useMessage()
// 解构通用与通知相关设置项
const {
  autoStart,
  soundNotify,
  messageDetail,
  notifyAtMe,
  notifySound,
  notifyTone
} = storeToRefs(appSettingsStore)
// 设置 Store action 解构
const { scheduleSave, setNotifyTone } = appSettingsStore

// 可选提示音列表（Web Audio 实时合成，零外部资源依赖）
const tones = listTones()

/**
 * 同步开机自启设置到 Electron 与后端。
 * 区分 Electron 调用结果：失败时给用户具体错误提示；
 * 成功时由事件总线统一 toast（避免重复弹两次）。
 */
async function applyAutoStart() {
  // 浏览器安全策略：必须在用户交互后 resume audio context
  unlockAudio()
  try {
    const ok = await window.electronAPI?.setAutoStart?.(autoStart.value)
    if (ok === false) {
      message.error('设置开机自启失败，请检查系统权限')
    }
  } catch (e) {
    message.error('设置开机自启失败：' + (e instanceof Error ? e.message : '未知错误'))
  }
  // 不论 Electron 调用结果如何，scheduleSave 都会触发；toast 由事件总线统一处理
  scheduleSave('autoStart')
}

/**
 * 切换开关的统一处理：仅 scheduleSave；toast 由事件总线统一处理。
 * audio context 必须先 unlock 才会在后续提示音播放时正常发声
 */
function toggleSwitch(key: Parameters<typeof scheduleSave>[0]) {
  unlockAudio()
  scheduleSave(key)
}

/** 选择提示音并立即试听 */
function pickTone(id: ToneId) {
  setNotifyTone(id)
  playTone(id)
  scheduleSave('notifyTone')
}
</script>

<template>
  <!-- 通用设置页 -->
  <div class="settings-scroll">
    <!-- 系统设置分组 -->
    <section class="group-card">
      <div class="group-head">
        <n-icon :component="DesktopOutline" :size="18" class="group-ico" />
        <span>系统</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">开机自动启动</span>
          <span class="setting-desc">登录 Windows 后自动打开 LinkX</span>
        </div>
        <n-switch
          v-model:value="autoStart"
          size="small"
          @update:value="applyAutoStart"
        />
      </div>
    </section>

    <!-- 消息与通知设置分组 -->
    <section class="group-card">
      <div class="group-head">
        <n-icon :component="NotificationsOutline" :size="18" class="group-ico" />
        <span>消息与通知</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">新消息声音提示</span>
        </div>
        <n-switch
          v-model:value="soundNotify"
          size="small"
          @update:value="toggleSwitch('soundNotify')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">通知显示消息详情</span>
        </div>
        <n-switch
          v-model:value="messageDetail"
          size="small"
          @update:value="toggleSwitch('messageDetail')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">群聊 @ 我</span>
        </div>
        <n-switch
          v-model:value="notifyAtMe"
          size="small"
          @update:value="toggleSwitch('notifyAtMe')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">通知声音</span>
        </div>
        <n-switch
          v-model:value="notifySound"
          size="small"
          @update:value="toggleSwitch('notifySound')"
        />
      </div>

      <!-- 提示音选择：仅在"通知声音"开启时才生效但选择始终可见 -->
      <div class="setting-row tone-row">
        <div class="setting-text">
          <span class="setting-name">
            <n-icon :component="MusicalNotesOutline" :size="14" class="tone-ico" />
            提示音
          </span>
          <span class="setting-desc">点击试听；选中后会保存到云端</span>
        </div>
        <n-radio-group
          v-model:value="notifyTone"
          size="small"
          @update:value="(v: string) => pickTone(v as ToneId)"
        >
          <n-radio
            v-for="t in tones"
            :key="t.id"
            :value="t.id"
          >
            {{ t.label }}
          </n-radio>
        </n-radio-group>
      </div>

      <div class="tone-preview-row">
        <n-button
          size="tiny"
          tertiary
          @click="playTone(notifyTone)"
        >
          <template #icon>
            <n-icon :component="PlayCircleOutline" />
          </template>
          试听当前音色（{{ tones.find(x => x.id === notifyTone)?.label }}）
        </n-button>
        <span class="tone-desc">{{ tones.find(x => x.id === notifyTone)?.description }}</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';

/* ---- 提示音选择行 ---- */
.tone-row {
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.tone-row .setting-text {
  width: 100%;
}

.tone-ico {
  vertical-align: -2px;
  margin-right: 4px;
  color: var(--lx-accent);
}

.tone-preview-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px 16px;
}

.tone-desc {
  font-size: 12px;
  color: var(--lx-text-muted);
}
</style>