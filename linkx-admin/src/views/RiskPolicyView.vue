<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
  NButton,
  NCard,
  NForm,
  NFormItem,
  NGrid,
  NGridItem,
  NInput,
  NInputNumber,
  NProgress,
  NSpace,
  NSpin,
  NTag,
  useMessage,
} from 'naive-ui'
import {
  fetchRiskPolicies,
  simulateRiskPolicy,
  updateRiskPolicies,
  type RiskPolicyOverview,
  type RiskPolicySimulateResult,
} from '@/api/riskPolicies'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const message = useMessage()
const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const simulating = ref(false)
const overview = ref<RiskPolicyOverview | null>(null)
const simulateText = ref('')
const simulateUserId = ref('')
const simulateResult = ref<RiskPolicySimulateResult | null>(null)

const form = reactive({
  messageStormUserThreshold: 30,
  messageStormUserWindowSeconds: 10,
  messageStormGroupMinMembers: 500,
  messageStormGroupLargeMembers: 1000,
  messageStormGroupMidPerMinute: 10,
  messageStormGroupLargePerMinute: 5,
  scoreMediumMin: 40,
  scoreHighMin: 65,
  scoreCriticalMin: 85,
  rateLimitLoginPerMinute: 10,
  rateLimitRegisterPerMinute: 5,
  rateLimitSearchPerMinute: 30,
  rateLimitListPerMinute: 60,
  rateLimitWritePerMinute: 30,
  rateLimitUploadPerMinute: 20,
})

const canEdit = computed(() => auth.hasPermission('admin:risk-policy:edit'))

function applyOverview(data: RiskPolicyOverview) {
  overview.value = data
  const storm = data.messageStorm
  const scores = data.scoreThresholds
  const limits = data.rateLimits
  form.messageStormUserThreshold = storm.userThreshold
  form.messageStormUserWindowSeconds = storm.userWindowSeconds
  form.messageStormGroupMinMembers = storm.groupMinMembers
  form.messageStormGroupLargeMembers = storm.groupLargeMembers
  form.messageStormGroupMidPerMinute = storm.groupMidPerMinute
  form.messageStormGroupLargePerMinute = storm.groupLargePerMinute
  form.scoreMediumMin = scores.mediumMin
  form.scoreHighMin = scores.highMin
  form.scoreCriticalMin = scores.criticalMin
  form.rateLimitLoginPerMinute = limits.loginPerMinute
  form.rateLimitRegisterPerMinute = limits.registerPerMinute
  form.rateLimitSearchPerMinute = limits.searchPerMinute
  form.rateLimitListPerMinute = limits.listPerMinute
  form.rateLimitWritePerMinute = limits.writePerMinute
  form.rateLimitUploadPerMinute = limits.uploadPerMinute
}

function riskLevelTagType(level?: string) {
  const map: Record<string, 'error' | 'warning' | 'info' | 'default'> = {
    critical: 'error',
    high: 'error',
    medium: 'warning',
    low: 'info',
  }
  return map[level || ''] || 'default'
}

function riskLevelLabel(level?: string) {
  const map: Record<string, string> = {
    critical: t('review.riskCritical'),
    high: t('review.riskHigh'),
    medium: t('review.riskMedium'),
    low: t('review.riskLow'),
  }
  return map[level || ''] || level || '-'
}

function actionLabel(action?: string) {
  const map: Record<string, string> = {
    block: t('riskPolicy.actionBlock'),
    filter: t('riskPolicy.actionFilter'),
    alert: t('riskPolicy.actionAlert'),
  }
  return map[action || ''] || action || '-'
}

async function load() {
  loading.value = true
  try {
    applyOverview(await fetchRiskPolicies())
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!canEdit.value) return
  saving.value = true
  try {
    applyOverview(
      await updateRiskPolicies({
        messageStormUserThreshold: form.messageStormUserThreshold,
        messageStormUserWindowSeconds: form.messageStormUserWindowSeconds,
        messageStormGroupMinMembers: form.messageStormGroupMinMembers,
        messageStormGroupLargeMembers: form.messageStormGroupLargeMembers,
        messageStormGroupMidPerMinute: form.messageStormGroupMidPerMinute,
        messageStormGroupLargePerMinute: form.messageStormGroupLargePerMinute,
        scoreMediumMin: form.scoreMediumMin,
        scoreHighMin: form.scoreHighMin,
        scoreCriticalMin: form.scoreCriticalMin,
        rateLimitLoginPerMinute: form.rateLimitLoginPerMinute,
        rateLimitRegisterPerMinute: form.rateLimitRegisterPerMinute,
        rateLimitSearchPerMinute: form.rateLimitSearchPerMinute,
        rateLimitListPerMinute: form.rateLimitListPerMinute,
        rateLimitWritePerMinute: form.rateLimitWritePerMinute,
        rateLimitUploadPerMinute: form.rateLimitUploadPerMinute,
      })
    )
    message.success(t('riskPolicy.saveSuccess'))
  } finally {
    saving.value = false
  }
}

async function runSimulate() {
  if (!canEdit.value) return
  simulating.value = true
  try {
    simulateResult.value = await simulateRiskPolicy({
      text: simulateText.value,
      subjectUserId: simulateUserId.value.trim() || undefined,
    })
  } finally {
    simulating.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <NSpin :show="loading">
      <div class="page-shell">
        <NAlert type="info" :bordered="false" class="hint">
          {{ t('riskPolicy.hint') }}
        </NAlert>

        <NGrid :cols="2" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
          <NGridItem span="2 m:1">
            <NCard :title="t('riskPolicy.messageStorm')" size="small">
              <NForm label-placement="left" label-width="140">
                <NFormItem :label="t('riskPolicy.userThreshold')">
                  <NInputNumber v-model:value="form.messageStormUserThreshold" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.userWindow')">
                  <NInputNumber v-model:value="form.messageStormUserWindowSeconds" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.groupMinMembers')">
                  <NInputNumber v-model:value="form.messageStormGroupMinMembers" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.groupLargeMembers')">
                  <NInputNumber v-model:value="form.messageStormGroupLargeMembers" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.groupMidPerMinute')">
                  <NInputNumber v-model:value="form.messageStormGroupMidPerMinute" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.groupLargePerMinute')">
                  <NInputNumber v-model:value="form.messageStormGroupLargePerMinute" :min="1" :disabled="!canEdit" />
                </NFormItem>
              </NForm>
            </NCard>
          </NGridItem>

          <NGridItem span="2 m:1">
            <NCard :title="t('riskPolicy.scoreThresholds')" size="small">
              <NForm label-placement="left" label-width="140">
                <NFormItem :label="t('riskPolicy.scoreMedium')">
                  <NInputNumber v-model:value="form.scoreMediumMin" :min="0" :max="100" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.scoreHigh')">
                  <NInputNumber v-model:value="form.scoreHighMin" :min="0" :max="100" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.scoreCritical')">
                  <NInputNumber v-model:value="form.scoreCriticalMin" :min="0" :max="100" :disabled="!canEdit" />
                </NFormItem>
              </NForm>
              <p class="muted">{{ t('riskPolicy.scoreHint') }}</p>
            </NCard>
          </NGridItem>

          <NGridItem span="2 m:1">
            <NCard :title="t('riskPolicy.rateLimits')" size="small">
              <NForm label-placement="left" label-width="140">
                <NFormItem :label="t('riskPolicy.limitLogin')">
                  <NInputNumber v-model:value="form.rateLimitLoginPerMinute" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.limitRegister')">
                  <NInputNumber v-model:value="form.rateLimitRegisterPerMinute" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.limitSearch')">
                  <NInputNumber v-model:value="form.rateLimitSearchPerMinute" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.limitList')">
                  <NInputNumber v-model:value="form.rateLimitListPerMinute" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.limitWrite')">
                  <NInputNumber v-model:value="form.rateLimitWritePerMinute" :min="1" :disabled="!canEdit" />
                </NFormItem>
                <NFormItem :label="t('riskPolicy.limitUpload')">
                  <NInputNumber v-model:value="form.rateLimitUploadPerMinute" :min="1" :disabled="!canEdit" />
                </NFormItem>
              </NForm>
            </NCard>
          </NGridItem>

          <NGridItem span="2 m:1">
            <NCard :title="t('riskPolicy.loginLock')" size="small">
              <template v-if="overview?.loginLock">
                <p>{{ t('riskPolicy.clientLock', { n: overview.loginLock.clientMaxAttempts, m: overview.loginLock.clientLockMinutes }) }}</p>
                <p>{{ t('riskPolicy.adminLock', { n: overview.loginLock.adminMaxAttempts, m: overview.loginLock.adminLockMinutes }) }}</p>
                <p class="muted">{{ t('riskPolicy.loginLockHint') }}</p>
              </template>
              <p v-if="overview">
                {{ t('riskPolicy.sensitiveFilter') }}：
                <NTag :type="overview.sensitiveFilterEnabled ? 'success' : 'default'" size="small">
                  {{ overview.sensitiveFilterEnabled ? t('common.enabled') : t('common.disabled') }}
                </NTag>
              </p>
            </NCard>
          </NGridItem>
        </NGrid>

        <NSpace class="toolbar" justify="end">
          <NButton v-if="canEdit" type="primary" :loading="saving" @click="save">
            {{ t('common.save') }}
          </NButton>
        </NSpace>

        <NCard :title="t('riskPolicy.simulateTitle')" size="small" class="simulate-card">
          <NForm label-placement="top">
            <NFormItem :label="t('riskPolicy.simulateText')">
              <NInput
                v-model:value="simulateText"
                type="textarea"
                :rows="4"
                :placeholder="t('riskPolicy.simulatePlaceholder')"
                :disabled="!canEdit"
              />
            </NFormItem>
            <NFormItem :label="t('riskPolicy.simulateUserId')">
              <NInput v-model:value="simulateUserId" :placeholder="t('riskPolicy.simulateUserIdPlaceholder')" :disabled="!canEdit" />
            </NFormItem>
          </NForm>
          <NSpace>
            <NButton v-if="canEdit" type="primary" :loading="simulating" @click="runSimulate">
              {{ t('riskPolicy.simulateRun') }}
            </NButton>
          </NSpace>

          <div v-if="simulateResult" class="simulate-result">
            <NSpace align="center" :size="12">
              <span>{{ t('review.riskLevel') }}:</span>
              <NTag :type="riskLevelTagType(simulateResult.riskLevel)" size="small">
                {{ riskLevelLabel(simulateResult.riskLevel) }}
              </NTag>
              <span>{{ t('riskPolicy.riskScore') }}: {{ simulateResult.riskScore ?? 0 }}</span>
            </NSpace>
            <NProgress
              type="line"
              :percentage="simulateResult.riskScore ?? 0"
              :height="8"
              :show-indicator="false"
              class="score-bar"
            />
            <p v-if="simulateResult.filteredText && simulateResult.filtered" class="muted">
              {{ t('riskPolicy.filteredText') }}: {{ simulateResult.filteredText }}
            </p>
            <div v-if="simulateResult.matchedDetails?.length" class="matched">
              <div class="matched-title">{{ t('riskPolicy.matchedWords') }}</div>
              <NSpace>
                <NTag v-for="item in simulateResult.matchedDetails" :key="item.word" size="small">
                  {{ item.word }} · {{ actionLabel(item.action) }}
                </NTag>
              </NSpace>
            </div>
            <ul v-if="simulateResult.riskFactors?.length" class="factors">
              <li v-for="(factor, idx) in simulateResult.riskFactors" :key="idx">{{ factor }}</li>
            </ul>
            <div v-if="simulateResult.matchedRules?.length" class="matched">
              <div class="matched-title">{{ t('riskPolicy.matchedRules') }}</div>
              <NSpace>
                <NTag v-for="rule in simulateResult.matchedRules" :key="rule.ruleId" size="small">
                  {{ rule.ruleName }} +{{ rule.scoreDelta }} · {{ rule.actionType }}
                </NTag>
              </NSpace>
              <p v-if="simulateResult.ruleScoreDelta" class="muted">
                {{ t('riskPolicy.ruleScoreDelta') }}: {{ simulateResult.ruleScoreDelta }}
              </p>
            </div>
          </div>
        </NCard>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.hint {
  margin-bottom: 16px;
}
.toolbar {
  margin: 16px 0;
}
.muted {
  color: var(--lx-text-3, #999);
  font-size: 13px;
  line-height: 1.5;
}
.simulate-card {
  margin-top: 8px;
}
.simulate-result {
  margin-top: 16px;
  line-height: 1.6;
}
.score-bar {
  margin: 8px 0 12px;
}
.matched {
  margin-top: 8px;
}
.matched-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.factors {
  margin: 12px 0 0;
  padding-left: 18px;
  color: var(--lx-text-2);
}
</style>
