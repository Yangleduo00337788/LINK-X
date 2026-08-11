<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NInput, NCheckbox, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import Avatar from '../Avatar.vue'
import PinIcon from '../icons/PinIcon.vue'
import { LxButton, LxIconButton } from '../ui'
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

watch(groupAnnouncementOpen, async open => {
  if (open && currentSessionId.value) {
    void groupMetaStore.fetchMembers(currentSessionId.value)
    await groupMetaStore.fetchAnnouncements(currentSessionId.value, true)
    // 无公告且有权限时直接进入发布，方便 CRUD
    if (canEdit.value && !(groupMetaStore.announcements[currentSessionId.value]?.length)) {
      composing.value = true
      editingId.value = null
      draft.value = ''
      draftPinned.value = true
    }
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
          <LxIconButton variant="close" :title="t('common.close')" @click="close">×</LxIconButton>
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
              <LxButton variant="sm" :disabled="saving" @click="cancelCompose">{{ t('common.cancel') }}</LxButton>
              <LxButton variant="sm-primary" :disabled="saving" @click="save">{{ t('common.save') }}</LxButton>
            </div>
          </div>

          <div v-else class="toolbar">
            <LxButton v-if="canEdit" variant="sm-primary" @click="startCreate">
              {{ t('extra.publishAnnouncement') }}
            </LxButton>
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
                      <PinIcon :size="11" filled />
                      {{ t('extra.pinned') }}
                    </span>
                  </div>
                  <span v-if="item.time" class="time">{{ item.time }}</span>
                </div>
              </div>
              <pre class="content">{{ item.content }}</pre>
              <div v-if="canEdit" class="card-actions">
                <LxButton
                  variant="link"
                  :disabled="busyId === item.id"
                  @click="togglePin(item.id, item.pinned)"
                >
                  {{ item.pinned ? t('extra.unpinAnnouncement') : t('extra.pinAnnouncement') }}
                </LxButton>
                <LxButton
                  variant="link"
                  :disabled="busyId === item.id"
                  @click="startEdit(item.id, item.content)"
                >
                  {{ t('common.edit') }}
                </LxButton>
                <LxButton
                  variant="link-danger"
                  :disabled="busyId === item.id"
                  @click="removeItem(item.id)"
                >
                  {{ t('common.delete') }}
                </LxButton>
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
  z-index: var(--lx-z-dialog-top);
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-3xl);
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
  padding: var(--lx-space-xl) var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border-light);
}

.win-head h2 {
  margin: 0;
  font-size: var(--lx-font-lg);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: var(--lx-space-lg);
  color: var(--lx-text-body);
}

.body {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-lg) var(--lx-space-2xl) var(--lx-space-2xl);
}

.hint {
  margin: 0 0 var(--lx-space-lg);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--lx-space-lg);
}

.view-only {
  margin: 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.compose {
  margin-bottom: var(--lx-space-xl);
  padding: var(--lx-space-lg);
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
}

.compose-pin {
  margin-top: var(--lx-space);
}

.compose-actions {
  margin-top: var(--lx-space-md);
  display: flex;
  gap: var(--lx-space);
  justify-content: flex-end;
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-lg);
}

.card {
  padding: var(--lx-space-lg);
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
}

.card.pinned {
  border-color: var(--lx-accent);
  background: var(--lx-accent-bg-soft);
}

.card-head {
  display: flex;
  gap: var(--lx-space-md);
  margin-bottom: var(--lx-space);
}

.meta {
  flex: 1;
  min-width: 0;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lx-space-sm);
}

.author {
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-body);
}

.role {
  font-size: var(--lx-font-xs);
  color: var(--lx-group-announce);
  background: var(--lx-warning-bg);
  padding: var(--lx-space-hair) var(--lx-space-sm);
  border-radius: var(--lx-radius);
}

.pin-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-2xs);
  font-size: var(--lx-font-xs);
  color: var(--lx-accent);
  background: var(--lx-bg-card);
  padding: var(--lx-space-hair) var(--lx-space-sm);
  border-radius: var(--lx-radius);
}

.time {
  display: block;
  margin-top: var(--lx-space-2xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.content {
  margin: 0;
  font-family: inherit;
  font-size: var(--lx-font);
  line-height: var(--lx-leading-relaxed);
  color: var(--lx-text-body);
  white-space: pre-wrap;
  word-break: break-word;
}

.card-actions {
  margin-top: var(--lx-space-md);
  display: flex;
  gap: var(--lx-space-lg);
  justify-content: flex-end;
}

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: var(--lx-space-5xl-minus) var(--lx-space-lg);
  margin: 0;
}
</style>
