<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NIcon, NInput, NSelect, useMessage } from 'naive-ui'
import {
  HelpCircleOutline,
  CloudOutline,
  MoonOutline,
  ApertureOutline,
  MailOutline,
  CallOutline,
  ChevronDownOutline,
  SendOutline,
  ChatbubblesOutline
} from '@vicons/ionicons5'
import { useOverlayStore } from '../../../stores/overlay'
import * as feedbackApi from '../../../api/feedback'
import * as versionApi from '../../../api/version'
import { APP_CLIENT_CHANNEL, APP_CLIENT_VERSION } from '../../../utils/appVersion'
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
const supportEmail = ref('')
const supportPhone = ref('')

const hasSupportContact = computed(
  () => !!(supportEmail.value.trim() || supportPhone.value.trim())
)

async function loadSupportContact() {
  try {
    const res = await versionApi.checkUpdate(APP_CLIENT_VERSION, APP_CLIENT_CHANNEL)
    if (res.code === 200 && res.data) {
      supportEmail.value = (res.data.supportEmail || '').trim()
      supportPhone.value = (res.data.supportPhone || '').trim()
    }
  } catch (e) {
    console.warn('[HelpPage] 加载客服联系方式失败:', e)
  }
}

onMounted(() => {
  void loadSupportContact()
})

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
      // 立即刷新官方会话，不等待推送
      void import('../../../stores/notifications').then(({ useNotificationsStore }) => {
        void useNotificationsStore().fetchMessageNotifications()
      })
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
    <!-- 顶部欢迎区（hero） -->
    <section class="help-hero">
      <div class="help-hero-icon">
        <n-icon :component="ChatbubblesOutline" :size="26" />
      </div>
      <div class="help-hero-text">
        <h1 class="help-hero-title">{{ t('overlay.helpTitle') }}</h1>
        <p class="help-hero-sub">{{ t('overlay.helpSub') }}</p>
      </div>
    </section>

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
        <div
          v-for="(item, index) in faqItems"
          :key="item.q"
          class="faq-row"
          :class="{ open: expandedFaq === index }"
        >
          <button
            type="button"
            class="faq-row-trigger"
            :aria-expanded="expandedFaq === index"
            @click="expandedFaq = expandedFaq === index ? null : index"
          >
            <span class="faq-ico">
              <n-icon :component="item.icon" :size="18" />
            </span>
            <span class="faq-q">{{ item.q }}</span>
            <span class="faq-chevron">
              <n-icon :component="ChevronDownOutline" :size="16" />
            </span>
          </button>
          <div v-if="expandedFaq === index" class="faq-a">{{ item.a }}</div>
        </div>
      </div>
    </section>

    <!-- 客服联系方式 -->
    <section v-if="hasSupportContact" class="panel-card support-card">
      <div class="panel-head compact">
        <div class="panel-head-icon soft">
          <n-icon :component="CallOutline" :size="20" />
        </div>
        <div>
          <h2 class="panel-title">{{ t('overlay.supportContact') }}</h2>
        </div>
      </div>
      <div class="support-list">
        <p v-if="supportEmail" class="support-line">
          <span class="support-label">{{ t('overlay.supportEmail') }}</span>
          <a class="support-link" :href="`mailto:${supportEmail}`">{{ supportEmail }}</a>
        </p>
        <p v-if="supportPhone" class="support-line">
          <span class="support-label">{{ t('overlay.supportPhone') }}</span>
          <a class="support-link" :href="`tel:${supportPhone}`">{{ supportPhone }}</a>
        </p>
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
        <div class="form-row">
          <label class="form-label">{{ t('overlay.feedbackType') }}</label>
          <n-select
            v-model:value="feedbackType"
            :options="feedbackTypeOptions"
            :placeholder="t('overlay.feedbackTypePh')"
            class="form-control"
          />
        </div>
        <div class="form-row">
          <label class="form-label">{{ t('overlay.feedbackContent') }}</label>
          <n-input
            v-model:value="feedbackText"
            type="textarea"
            :placeholder="t('overlay.feedbackContentPh')"
            :rows="5"
            class="form-control feedback-input"
          />
        </div>
        <div class="form-row">
          <label class="form-label">{{ t('overlay.feedbackContact') }}</label>
          <n-input
            v-model:value="feedbackContact"
            :placeholder="t('overlay.feedbackContactPh')"
            class="form-control"
          />
        </div>
      </div>
      <div class="feedback-actions">
        <n-button
          type="primary"
          size="medium"
          :loading="submitting"
          :disabled="submitting"
          class="submit-btn"
          @click="submitFeedback"
        >
          <template #icon>
            <n-icon :component="SendOutline" />
          </template>
          {{ t('overlay.submitFeedback') }}
        </n-button>
      </div>
    </section>

    <!-- 底部版权 -->
    <p class="help-footer">{{ t('overlay.helpFooter') }}</p>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';

.help-page {
  padding: 18px 4px 4px;
}

/* 顶部 hero —— 微信欢迎语风 */
.help-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: linear-gradient(
    135deg,
    var(--lx-accent-soft),
    color-mix(in srgb, var(--lx-accent-soft) 60%, transparent)
  );
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--lx-accent) 18%, transparent);
}

.help-hero-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: var(--lx-bg-card);
  color: var(--lx-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--lx-accent) 18%, transparent);
  flex-shrink: 0;
}

.help-hero-text {
  flex: 1;
  min-width: 0;
}

.help-hero-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--lx-text-body);
  letter-spacing: 0.2px;
}

.help-hero-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--lx-text-secondary);
  line-height: 1.5;
}

/* FAQ 行 —— 头尾留白一致，展开时柔顺过渡 */
.faq-row {
  border: 1px solid var(--lx-border-light);
  border-radius: 10px;
  background: var(--lx-bg-panel);
  overflow: hidden;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.faq-row + .faq-row {
  margin-top: 8px;
}

.faq-row:hover {
  border-color: color-mix(in srgb, var(--lx-accent) 35%, transparent);
}

.faq-row.open {
  border-color: color-mix(in srgb, var(--lx-accent) 35%, transparent);
  background: var(--lx-bg-card);
}

.faq-row-trigger {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: inherit;
  font: inherit;
}

.faq-ico {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.faq-q {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
  line-height: 1.4;
}

.faq-chevron {
  color: var(--lx-text-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease, color 0.18s ease;
}

.faq-row.open .faq-chevron {
  transform: rotate(180deg);
  color: var(--lx-accent);
}

.faq-a {
  padding: 0 14px 14px 54px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--lx-text-secondary);
  animation: faq-slide 0.2s ease;
}

@keyframes faq-slide {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.support-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 2px;
}

.support-line {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--lx-text-secondary);
}

.support-label {
  margin-right: 8px;
  color: var(--lx-text-muted);
}

.support-link {
  color: var(--lx-accent);
  text-decoration: none;
  word-break: break-all;
}

.support-link:hover {
  text-decoration: underline;
}

/* 反馈表单 —— 标签 + 控件，控件之间留白一致 */
.feedback-card {
  display: flex;
  flex-direction: column;
}

.feedback-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--lx-text-secondary);
}

.form-control :deep(.n-base-selection),
.form-control :deep(.n-input) {
  border-radius: 10px;
}

.feedback-input :deep(textarea) {
  border-radius: 10px;
  padding: 10px 12px;
  line-height: 1.6;
}

.feedback-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed var(--lx-border-light);
}

.submit-btn {
  min-width: 132px;
}

/* 底部细字版权 */
.help-footer {
  margin: 4px 0 8px;
  text-align: center;
  font-size: 12px;
  color: var(--lx-text-muted);
}
</style>