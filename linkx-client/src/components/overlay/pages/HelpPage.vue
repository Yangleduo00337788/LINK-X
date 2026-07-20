<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NIcon, NInput, NSelect, useMessage } from 'naive-ui'
import { HelpCircleOutline, CloudOutline, MoonOutline, ApertureOutline, MailOutline } from '@vicons/ionicons5'
import { useOverlayStore } from '../../../stores/overlay'
import * as feedbackApi from '../../../api/feedback'
import { useI18n } from '../../../i18n'

const message = useMessage()
const overlayStore = useOverlayStore()
const { close } = overlayStore
const { t } = useI18n()

const feedbackText = ref('')
const feedbackType = ref<'bug' | 'suggestion' | 'other'>('suggestion')
const feedbackContact = ref('')
const submitting = ref(false)
const expandedFaq = ref<number | null>(0)

const faqItems = computed(() => [
  {
    icon: CloudOutline,
    q: t('overlay.faqSyncQ'),
    a: t('overlay.faqSyncA')
  },
  {
    icon: MoonOutline,
    q: t('overlay.faqDarkQ'),
    a: t('overlay.faqDarkA')
  },
  {
    icon: ApertureOutline,
    q: t('overlay.faqMomentsQ'),
    a: t('overlay.faqMomentsA')
  }
])

const feedbackTypeOptions = computed(() => [
  { label: t('overlay.typeSuggestion'), value: 'suggestion' },
  { label: t('overlay.typeBug'), value: 'bug' },
  { label: t('overlay.typeOther'), value: 'other' }
])

async function submitFeedback() {
  const text = feedbackText.value.trim()
  if (!text) {
    message.warning(t('overlay.feedbackNeedContent'))
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
      message.success(t('overlay.feedbackOk'))
      feedbackText.value = ''
      feedbackContact.value = ''
      close()
    } else {
      message.error(res.message || t('overlay.submitFail'))
    }
  } catch (e) {
    console.error('提交反馈失败:', e)
    message.error(t('overlay.submitFailNetwork'))
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
          <h2 class="panel-title">{{ t('overlay.faq') }}</h2>
          <p class="panel-sub">{{ t('overlay.faqSub') }}</p>
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
          <h2 class="panel-title">{{ t('overlay.feedback') }}</h2>
          <p class="panel-sub">{{ t('overlay.feedbackSub') }}</p>
        </div>
      </div>
      <div class="feedback-form">
        <div class="form-item">
          <label>{{ t('overlay.feedbackType') }}</label>
          <n-select
            v-model:value="feedbackType"
            :options="feedbackTypeOptions"
            :placeholder="t('overlay.feedbackTypePh')"
          />
        </div>
        <n-input
          v-model:value="feedbackText"
          type="textarea"
          :placeholder="t('overlay.feedbackContentPh')"
          :rows="5"
          class="feedback-input"
        />
        <div class="form-item">
          <label>{{ t('overlay.feedbackContact') }}</label>
          <n-input
            v-model:value="feedbackContact"
            :placeholder="t('overlay.feedbackContactPh')"
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
          {{ t('overlay.submitFeedback') }}
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
