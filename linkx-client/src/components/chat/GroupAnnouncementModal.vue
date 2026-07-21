<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NInput, NButton, NCheckbox, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import Avatar from '../Avatar.vue'
import PinIcon from '../icons/PinIcon.vue'
import { useI18n } from '../../i18n'

const message = useMessage()
const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const { groupAnnouncementOpen } = storeToRefs(chatModalsStore)
const { closeGroupAnnouncement } = chatModalsStore
const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)

const composing = ref(false)
const draft = ref('')
const draftPinned = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const busyId = ref<string | null>(null)

const items = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.announcementsFor(id)
})

const canEdit = computed(() => {
  const id = currentSessionId.value
  const me = userProfile.value.userId
  if (!id || !me) return false
  const members = groupMetaStore.membersFor(id)
  return members.some(m => m.id === me && (m.role === 'owner' || m.role === 'admin'))
})

watch(groupAnnouncementOpen, open => {
  if (open && currentSessionId.value) {
    void groupMetaStore.fetchMembers(currentSessionId.value)
    void groupMetaStore.fetchAnnouncements(currentSessionId.value, true)
  }
  if (!open) {
    composing.value = false
    editingId.value = null
    draft.value = ''
    draftPinned.value = false
  }
})

function close() {
  closeGroupAnnouncement()
}

function startCreate() {
  if (!canEdit.value) return
  composing.value = true
  editingId.value = null
  draft.value = ''
  draftPinned.value = false
}

function startEdit(id: string, content: string) {
  if (!canEdit.value) return
  composing.value = true
  editingId.value = id
  draft.value = content
  draftPinned.value = false
}

function cancelCompose() {
  composing.value = false
  editingId.value = null
  draft.value = ''
  draftPinned.value = false
}

async function save() {
  const sid = currentSessionId.value
  if (!sid || !canEdit.value || !draft.value.trim()) return
  saving.value = true
  try {
    let ok = false
    if (editingId.value) {
      ok = await groupMetaStore.updateAnnouncementContent(sid, editingId.value, draft.value.trim())
    } else {
      ok = await groupMetaStore.createAnnouncement(sid, draft.value.trim(), draftPinned.value)
    }
    if (ok) {
      message.success(t('extra.announcementUpdated'))
      cancelCompose()
    } else {
      message.error(t('extra.announcementUpdateFail'))
    }
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('extra.announcementUpdateFail'))
  } finally {
    saving.value = false
  }
}

async function togglePin(id: string, pinned: boolean) {
  const sid = currentSessionId.value
  if (!sid || !canEdit.value) return
  busyId.value = id
  try {
    const ok = await groupMetaStore.setAnnouncementPinned(sid, id, !pinned)
    if (ok) {
      message.success(!pinned ? t('extra.announcementPinned') : t('extra.announcementUnpinned'))
    } else {
      message.error(t('extra.announcementUpdateFail'))
    }
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('extra.announcementUpdateFail'))
  } finally {
    busyId.value = null
  }
}

async function removeItem(id: string) {
  const sid = currentSessionId.value
  if (!sid || !canEdit.value) return
  busyId.value = id
  try {
    const ok = await groupMetaStore.removeAnnouncement(sid, id)
    if (ok) message.success(t('extra.announcementDeleted'))
    else message.error(t('extra.announcementDeleteFail'))
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('extra.announcementDeleteFail'))
  } finally {
    busyId.value = null
  }
}

function roleLabel(role: string) {
  return role || ''
}
</script>

<template>
  <Teleport to="body">
    <div v-if="groupAnnouncementOpen" class="modal-root" @click.self="close">
      <div class="announce-window" @click.stop>
        <header class="win-head">
          <h2>{{ t('extra.groupAnnouncementTitle', { name: currentSession?.name || t('extra.groupChat') }) }}</h2>
          <button type="button" class="close-x" @click="close">×</button>
        </header>

        <div class="body">
          <p class="hint">{{ t('extra.announcementDisplayHint') }}</p>

          <div v-if="composing" class="compose">
            <n-input
              v-model:value="draft"
              type="textarea"
              :rows="5"
              :disabled="saving"
              :placeholder="t('extra.announcementPh')"
            />
            <div v-if="!editingId" class="compose-pin">
              <n-checkbox v-model:checked="draftPinned" :disabled="saving">
                {{ t('extra.pinOnPublish') }}
              </n-checkbox>
            </div>
            <div class="compose-actions">
              <n-button size="small" :disabled="saving" @click="cancelCompose">{{ t('common.cancel') }}</n-button>
              <n-button size="small" type="primary" :loading="saving" @click="save">{{ t('common.save') }}</n-button>
            </div>
          </div>

          <div v-else class="toolbar">
            <n-button v-if="canEdit" size="small" type="primary" @click="startCreate">
              {{ t('extra.publishAnnouncement') }}
            </n-button>
            <p v-else class="view-only">{{ t('extra.announcementAdminOnly') }}</p>
          </div>

          <div class="list">
            <article v-for="item in items" :key="item.id" class="card" :class="{ pinned: item.pinned }">
              <div class="card-head">
                <Avatar :text="(item.author || '?').charAt(0)" color="var(--lx-accent)" :size="36" />
                <div class="meta">
                  <div class="meta-row">
                    <span class="author">{{ item.author }}</span>
                    <span v-if="item.role" class="role">{{ roleLabel(item.role) }}</span>
                    <span v-if="item.pinned" class="pin-tag">
                      <PinIcon :size="11" />
                      {{ t('extra.pinned') }}
                    </span>
                  </div>
                  <span v-if="item.time" class="time">{{ item.time }}</span>
                </div>
              </div>
              <pre class="content">{{ item.content }}</pre>
              <div v-if="canEdit" class="card-actions">
                <button
                  type="button"
                  class="link-btn"
                  :disabled="busyId === item.id"
                  @click="togglePin(item.id, item.pinned)"
                >
                  {{ item.pinned ? t('extra.unpinAnnouncement') : t('extra.pinAnnouncement') }}
                </button>
                <button
                  type="button"
                  class="link-btn"
                  :disabled="busyId === item.id"
                  @click="startEdit(item.id, item.content)"
                >
                  {{ t('common.edit') }}
                </button>
                <button
                  type="button"
                  class="link-btn danger"
                  :disabled="busyId === item.id"
                  @click="removeItem(item.id)"
                >
                  {{ t('common.delete') }}
                </button>
              </div>
            </article>
            <p v-if="!items.length" class="empty">{{ t('extra.noAnnouncement') }}</p>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: 2250;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.announce-window {
  width: min(520px, 94vw);
  max-height: min(560px, 88vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--lx-shadow-modal);
}

.win-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--lx-border-light);
}

.win-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 12px;
  color: var(--lx-text-body);
}

.close-x {
  border: none;
  background: none;
  font-size: 22px;
  color: var(--lx-text-muted);
  cursor: pointer;
  flex-shrink: 0;
}

.body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 18px 18px;
}

.hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.view-only {
  margin: 0;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.compose {
  margin-bottom: 14px;
  padding: 12px;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
}

.compose-pin {
  margin-top: 8px;
}

.compose-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card {
  padding: 12px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
}

.card.pinned {
  border-color: var(--lx-accent);
  background: var(--lx-accent-bg-soft);
}

.card-head {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}

.meta {
  flex: 1;
  min-width: 0;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.author {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.role {
  font-size: 11px;
  color: #fa8c16;
  background: var(--lx-warning-bg);
  padding: 1px 6px;
  border-radius: var(--lx-radius);
}

.pin-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--lx-accent);
  background: var(--lx-bg-card);
  padding: 1px 6px;
  border-radius: var(--lx-radius);
}

.time {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.content {
  margin: 0;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  color: var(--lx-text-body);
  white-space: pre-wrap;
  word-break: break-word;
}

.card-actions {
  margin-top: 10px;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.link-btn {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: var(--lx-accent);
  cursor: pointer;
}

.link-btn.danger {
  color: var(--lx-danger, #e74c3c);
}

.link-btn:disabled {
  opacity: 0.5;
  cursor: default;
}

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: 28px 12px;
  margin: 0;
}
</style>
