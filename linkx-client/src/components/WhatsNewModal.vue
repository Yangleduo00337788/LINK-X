<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 版本更新说明弹窗：展示管理端为当前版本配置的 releaseNotes。
 */
import { computed } from 'vue'
import { APP_CLIENT_VERSION } from '../utils/appVersion'
import { markWhatsNewSeen, parseReleaseNotes } from '../utils/whatsNew'
import { useI18n } from '../i18n'
import BrandMarkIcon from './BrandMarkIcon.vue'
import { LxButton, LxModal } from './ui'

const show = defineModel<boolean>('show', { default: false })
const notes = defineModel<string>('notes', { default: '' })

const { t } = useI18n()

const blocks = computed(() => parseReleaseNotes(notes.value))

function dismiss() {
  markWhatsNewSeen(APP_CLIENT_VERSION)
  show.value = false
}
</script>

<template>
  <LxModal
    v-model:show="show"
    preset="card"
    :title="t('whatsNew.title')"
    :mask-closable="false"
    :close-on-esc="true"
    :auto-focus="false"
    class="whats-new-modal"
    style="width: min(480px, calc(100vw - 32px))"
    @after-leave="markWhatsNewSeen(APP_CLIENT_VERSION)"
  >
    <div class="whats-new">
      <div class="whats-new__hero">
        <BrandMarkIcon :size="56" />
        <p class="whats-new__version">{{ t('whatsNew.versionLabel', { version: APP_CLIENT_VERSION }) }}</p>
      </div>

      <div class="whats-new__body">
        <template v-for="(block, idx) in blocks" :key="idx">
          <p v-if="block.kind === 'text'" class="whats-new__text">{{ block.content }}</p>
          <ul v-else class="whats-new__list">
            <li v-for="(item, itemIdx) in block.items" :key="itemIdx">{{ item }}</li>
          </ul>
        </template>
      </div>

      <div class="whats-new__actions">
        <LxButton variant="submit-block" @click="dismiss">
          {{ t('whatsNew.gotIt') }}
        </LxButton>
      </div>
    </div>
  </LxModal>
</template>

<style scoped>
.whats-new__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lx-space-md);
  margin-bottom: var(--lx-space-xl);
}

.whats-new__version {
  margin: 0;
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}

.whats-new__body {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-sm);
  max-height: min(50vh, 360px);
  overflow-y: auto;
}

.whats-new__text {
  margin: 0;
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-body);
}

.whats-new__list {
  margin: 0;
  padding-left: 1.15em;
}

.whats-new__list li {
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-body);
}

.whats-new__actions {
  margin-top: var(--lx-space-2xl);
}
</style>
