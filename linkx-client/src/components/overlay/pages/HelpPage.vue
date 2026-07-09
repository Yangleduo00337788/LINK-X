<script setup lang="ts">
// Vue 响应式 API
import { ref } from 'vue'
// Naive UI 按钮、图标、输入框与消息提示
import { NButton, NIcon, NInput, useMessage } from 'naive-ui'
// Ionicons5 帮助与反馈相关图标
import { HelpCircleOutline, CloudOutline, MoonOutline, ApertureOutline, MailOutline } from '@vicons/ionicons5'
// 全屏覆盖层 Store
import { useOverlayStore } from '../../../stores/overlay'

// 消息提示实例
const message = useMessage()
// 覆盖层 Store 实例
const overlayStore = useOverlayStore()
// 关闭覆盖层的方法
const { close } = overlayStore

// 用户反馈文本
const feedbackText = ref('')
// 当前展开的 FAQ 索引，null 表示全部折叠
const expandedFaq = ref<number | null>(0)

// 常见问题列表数据
const faqItems = [
  {
    icon: CloudOutline,
    q: '如何同步消息？',
    a: '对接后端 WebSocket 后即可实时同步，当前为本地 Mock 演示。'
  },
  {
    icon: MoonOutline,
    q: '如何切换深色模式？',
    a: '点击侧栏调色盘图标，或进入设置 → 外观与显示。'
  },
  {
    icon: ApertureOutline,
    q: '友链独立窗口如何使用？',
    a: '在 Electron 客户端点击侧栏友链图标，将打开独立浏览窗口。'
  }
]

// 提交用户反馈并关闭页面
function submitFeedback() {
  const text = feedbackText.value.trim()
  if (!text) {
    message.warning('请先描述您遇到的问题')
    return
  }
  console.info('[LinkX] 用户反馈', text)
  message.success('反馈已提交，感谢您的建议')
  feedbackText.value = ''
  close()
}
</script>

<template>
  <!-- 帮助与反馈页面 -->
  <div class="page-wrap help-page">
    <!-- 常见问题折叠面板 -->
    <section class="panel-card">
      <div class="panel-head">
        <div class="panel-head-icon">
          <n-icon :component="HelpCircleOutline" :size="20" />
        </div>
        <div>
          <h2 class="panel-title">常见问题</h2>
          <p class="panel-sub">快速了解 LinkX 常用功能</p>
        </div>
      </div>
      <div class="faq-list">
        <button
          v-for="(item, index) in faqItems"
          :key="item.q"
          type="button"
          class="faq-row"
          :class="{ open: expandedFaq === index }"
          @click="expandedFaq = expandedFaq === index ? null : index"
        >
          <div class="faq-row-head">
            <n-icon :component="item.icon" :size="18" class="faq-ico" />
            <span class="faq-q">{{ item.q }}</span>
            <span class="faq-chevron">{{ expandedFaq === index ? '−' : '+' }}</span>
          </div>
          <p v-if="expandedFaq === index" class="faq-a">{{ item.a }}</p>
        </button>
      </div>
    </section>

    <!-- 问题反馈表单 -->
    <section class="panel-card feedback-card">
      <div class="panel-head compact">
        <div class="panel-head-icon soft">
          <n-icon :component="MailOutline" :size="20" />
        </div>
        <div>
          <h2 class="panel-title">问题反馈</h2>
          <p class="panel-sub">你的建议会帮助我们改进产品</p>
        </div>
      </div>
      <n-input
        v-model:value="feedbackText"
        type="textarea"
        placeholder="描述你遇到的问题或建议…"
        :rows="5"
        class="feedback-input"
      />
      <div class="feedback-actions">
        <n-button type="primary" @click="submitFeedback">提交反馈</n-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
