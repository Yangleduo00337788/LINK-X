<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NForm, NFormItem, NInput, NModal, NRadioButton, NRadioGroup, NSpace, useMessage } from 'naive-ui'
import { requestStepUp, verifyStepUp, type StepUpMethod } from '@/api/stepUp'
import { useStepUpGate } from '@/composables/useStepUpGate'

const { t } = useI18n()
const message = useMessage()
const { visible, challenge, resolveStepUp } = useStepUpGate()

const method = ref<StepUpMethod>('totp')
const code = ref('')
const sending = ref(false)
const verifying = ref(false)
const emailed = ref(false)

const methods = computed(() => challenge.value?.methods || [])
const action = computed(() => challenge.value?.action || '')

watch(visible, (v) => {
  if (!v) return
  const list = methods.value
  method.value = (list.includes('totp') ? 'totp' : list[0]) || 'totp'
  code.value = ''
  emailed.value = false
})

watch(method, () => {
  code.value = ''
  emailed.value = false
})

async function sendEmailCode() {
  if (!action.value) return
  sending.value = true
  try {
    await requestStepUp('email', action.value)
    emailed.value = true
    message.success(t('stepUp.codeSent'))
  } finally {
    sending.value = false
  }
}

async function confirm() {
  if (!action.value || !/^\d{6}$/.test(code.value.trim())) {
    message.warning(t('stepUp.codeRequired'))
    return
  }
  verifying.value = true
  try {
    if (method.value === 'email' && !emailed.value) {
      await requestStepUp('email', action.value)
      emailed.value = true
    }
    if (method.value === 'totp') {
      await requestStepUp('totp', action.value)
    }
    const result = await verifyStepUp(method.value, code.value.trim(), action.value)
    resolveStepUp(result.stepUpToken)
  } finally {
    verifying.value = false
  }
}

function cancel() {
  resolveStepUp(null)
}
</script>

<template>
  <NModal
    :show="visible"
    preset="card"
    :title="t('stepUp.title')"
    style="width: 420px"
    :mask-closable="false"
    :close-on-esc="false"
    @update:show="(v) => { if (!v) cancel() }"
  >
    <p class="stepup-desc">{{ t('stepUp.desc') }}</p>
    <p v-if="action" class="stepup-action">{{ t('stepUp.action') }}: <code>{{ action }}</code></p>

    <NForm label-placement="left" label-width="72" class="stepup-form">
      <NFormItem v-if="methods.length > 1" :label="t('stepUp.method')">
        <NRadioGroup v-model:value="method" size="small">
          <NRadioButton v-if="methods.includes('totp')" value="totp">{{ t('stepUp.methodTotp') }}</NRadioButton>
          <NRadioButton v-if="methods.includes('email')" value="email">{{ t('stepUp.methodEmail') }}</NRadioButton>
        </NRadioGroup>
      </NFormItem>

      <NFormItem v-if="method === 'email'" :label="t('stepUp.email')">
        <div class="stepup-email-row">
          <span>{{ challenge?.emailMasked || '-' }}</span>
          <NButton size="tiny" :loading="sending" @click="sendEmailCode">
            {{ emailed ? t('stepUp.resend') : t('stepUp.sendCode') }}
          </NButton>
        </div>
      </NFormItem>

      <NFormItem :label="t('stepUp.code')">
        <NInput
          v-model:value="code"
          maxlength="6"
          :placeholder="t('stepUp.codePlaceholder')"
          @keyup.enter="confirm"
        />
      </NFormItem>
    </NForm>

    <template #footer>
      <NSpace justify="end">
        <NButton @click="cancel">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" :loading="verifying" @click="confirm">{{ t('stepUp.verify') }}</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.stepup-desc {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--lx-text-3);
}

.stepup-action {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--lx-text-3);
}

.stepup-action code {
  font-size: 11px;
  color: var(--lx-text);
}

.stepup-form {
  margin-top: 4px;
}

.stepup-email-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  font-size: 13px;
}
</style>
