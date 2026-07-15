<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NIcon, NInput, NSelect, useMessage } from 'naive-ui'
import { HelpCircleOutline, CloudOutline, MoonOutline, ApertureOutline, MailOutline } from '@vicons/ionicons5'
import { useOverlayStore } from '../../../stores/overlay'
import * as feedbackApi from '../../../api/feedback'

const message = useMessage()
const overlayStore = useOverlayStore()
const { close } = overlayStore

const feedbackText = ref('')
const feedbackType = ref<'bug' | 'suggestion' | 'other'>('suggestion')
const feedbackContact = ref('')
const submitting = ref(false)
const expandedFaq = ref<number | null>(0)

const faqItems = [
  {
    icon: CloudOutline,
    q: '如何同步消息？',
    a: '登录后消息会通过 WebSocket（ws://host:8081/ws）实时同步；离线时可在网络恢复后自动重连。'
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

const feedbackTypeOptions = [
  { label: '功能建议', value: 'suggestion' },
  { label: 'Bug 反馈', value: 'bug' },
  { label: '其他问题', value: 'other' }
]

async function submitFeedback() {
  const text = feedbackText.value.trim()
  if (!text) {
    message.warning('请先描述您遇到的问题')
    return
  }

  submitting.value = true
  try {
    const res = await feedbackApi.submitFeedback({
      type: feedbackType.value,
      content: text,
      contact: feedbackContact.value.trim() || undefined
    })

    if (res.code === 200) {
      message.success('反馈已提交，感谢您的建议')
      feedbackText.value = ''
      feedbackContact.value = ''
      close()
    } else {
      message.error(res.message || '提交失败，请重试')
    }
  } catch (e) {
    console.error('提交反馈失败:', e)
    message.error('提交反馈失败，请检查网络连接')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
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
      <div class="feedback-form">
        <div class="form-item">
          <label>反馈类型</label>
          <n-select
            v-model:value="feedbackType"
            :options="feedbackTypeOptions"
            placeholder="请选择反馈类型"
          />
        </div>
        <n-input
          v-model:value="feedbackText"
          type="textarea"
          placeholder="描述你遇到的问题或建议…"
          :rows="5"
          class="feedback-input"
        />
        <div class="form-item">
          <label>联系方式（选填）</label>
          <n-input
            v-model:value="feedbackContact"
            placeholder="手机号或邮箱，方便我们联系你"
          />
        </div>
      </div>
      <div class="feedback-actions">
        <n-button
          type="primary"
          :loading="submitting"
          :disabled="submitting"
          @click="submitFeedback"
        >
          提交反馈
        </n-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';

.feedback-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label {
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.feedback-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
