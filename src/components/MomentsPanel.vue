<script setup lang="ts">
import { ref, computed } from 'vue'
import { NInput, NButton, NIcon, useMessage } from 'naive-ui'
import { HeartOutline, Heart, ChatbubbleOutline, ImageOutline, CloseOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import Avatar from './Avatar.vue'
import EmptyState from './common/EmptyState.vue'
import { storeToRefs } from 'pinia'
import { useMomentsStore } from '../stores/moments'
import { useAppStore } from '../stores/app'
import { readFileAsDataUrl, MAX_IMAGE_BYTES } from '../utils/file'

const message = useMessage()
const momentsStore = useMomentsStore()
const appStore = useAppStore()
const { posts } = storeToRefs(momentsStore)
const { userProfile } = storeToRefs(appStore)
const { addPost, toggleLike, addComment } = momentsStore

const search = ref('')
const newPost = ref('')
const composeImages = ref<string[]>([])
const imageInputRef = ref<HTMLInputElement | null>(null)
const commentDrafts = ref<Record<string, string>>({})
const showCommentFor = ref<string | null>(null)

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return posts.value
  return posts.value.filter(
    p => p.user.toLowerCase().includes(q) || p.content.toLowerCase().includes(q)
  )
})

async function onPickImages(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    if (composeImages.value.length >= 9) {
      message.warning('最多添加 9 张图片')
      break
    }
    if (file.size > MAX_IMAGE_BYTES) {
      message.warning(`「${file.name}」超过 2MB，已跳过`)
      continue
    }
    try {
      composeImages.value.push(await readFileAsDataUrl(file))
    } catch {
      message.error(`「${file.name}」读取失败`)
    }
  }
}

function removeComposeImage(index: number) {
  composeImages.value.splice(index, 1)
}

function publish() {
  const text = newPost.value.trim()
  if (!text && !composeImages.value.length) {
    message.warning('请输入动态内容或添加图片')
    return
  }
  addPost(
    text || '分享图片',
    userProfile.value.nickname,
    'https://api.dicebear.com/7.x/avataaars/svg?seed=me',
    composeImages.value.length ? [...composeImages.value] : undefined
  )
  newPost.value = ''
  composeImages.value = []
  message.success('动态已发布')
}

function submitComment(postId: string) {
  const content = commentDrafts.value[postId]?.trim()
  if (!content) return
  addComment(postId, userProfile.value.nickname, content)
  commentDrafts.value[postId] = ''
  showCommentFor.value = null
  message.success('评论已发送')
}
</script>

<template>
  <div class="moments-panel">
    <PanelSearchBar v-model="search" placeholder="搜索友链" />
    <div class="composer">
      <n-input
        v-model:value="newPost"
        type="textarea"
        placeholder="分享新鲜事…"
        :rows="2"
      />
      <div v-if="composeImages.length" class="compose-images">
        <div v-for="(img, i) in composeImages" :key="i" class="compose-thumb-wrap">
          <img :src="img" alt="" class="compose-thumb" />
          <button type="button" class="compose-remove" @click="removeComposeImage(i)">
            <n-icon :component="CloseOutline" :size="12" />
          </button>
        </div>
      </div>
      <div class="composer-actions">
        <input ref="imageInputRef" type="file" accept="image/*" multiple hidden @change="onPickImages" />
        <button type="button" class="compose-tool" title="添加图片" @click="imageInputRef?.click()">
          <n-icon :component="ImageOutline" :size="18" />
        </button>
        <n-button type="primary" size="small" class="publish-btn" @click="publish">发布</n-button>
      </div>
    </div>
    <div class="list">
      <EmptyState
        v-if="!filtered.length"
        :title="search.trim() ? '未找到相关动态' : '暂无动态'"
        :description="search.trim() ? '换个关键词试试' : '发布第一条友链动态吧'"
      />
      <template v-else>
        <article v-for="m in filtered" :key="m.id" class="moment-card">
        <div class="moment-head">
          <Avatar :text="m.user.charAt(0)" color="var(--lx-accent)" :size="40" :image-url="m.avatar" />
          <div class="meta">
            <span class="name">{{ m.user }}</span>
            <span class="time">{{ m.time }}</span>
          </div>
        </div>
        <p class="text">{{ m.content }}</p>
        <div v-if="m.images?.length" class="images">
          <img v-for="(img, i) in m.images" :key="i" :src="img" alt="" class="moment-img" />
        </div>
        <div v-if="m.comments.length" class="comments">
          <div v-for="c in m.comments" :key="c.id" class="comment-row">
            <strong>{{ c.user }}</strong>：{{ c.content }}
          </div>
        </div>
        <div class="foot">
          <button type="button" class="foot-btn" @click="toggleLike(m.id, userProfile.nickname)">
            <n-icon :component="m.liked ? Heart : HeartOutline" :size="16" />
            {{ m.likes }}
          </button>
          <button type="button" class="foot-btn" @click="showCommentFor = showCommentFor === m.id ? null : m.id">
            <n-icon :component="ChatbubbleOutline" :size="16" />
            {{ m.comments.length }}
          </button>
        </div>
        <div v-if="showCommentFor === m.id" class="comment-box">
          <n-input
            v-model:value="commentDrafts[m.id]"
            size="small"
            placeholder="写评论…"
            @keyup.enter="submitComment(m.id)"
          />
          <n-button size="tiny" type="primary" @click="submitComment(m.id)">发送</n-button>
        </div>
      </article>
      </template>
    </div>
  </div>
</template>

<style scoped>
.moments-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
}

.composer {
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-bottom: 1px solid var(--lx-border-light);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.compose-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.compose-thumb-wrap {
  position: relative;
  width: 64px;
  height: 64px;
}

.compose-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: var(--lx-radius);
}

.compose-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 50%;
  background: var(--lx-danger);
  color: var(--lx-text-on-accent);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.compose-tool {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-input);
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.compose-tool:hover {
  color: var(--lx-accent);
  background: var(--lx-accent-soft);
}

.publish-btn {
  align-self: flex-end;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px 12px;
}

.moment-card {
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  padding: 12px;
  margin-bottom: 8px;
  box-shadow: var(--lx-shadow-soft, 0 1px 2px var(--lx-bg-hover));
}

.moment-head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.time {
  font-size: 11px;
  color: var(--lx-text-muted);
}

.text {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--lx-text-secondary);
}

.images {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.moment-img {
  width: 120px;
  height: 90px;
  object-fit: cover;
  border-radius: var(--lx-radius);
}

.comments {
  margin-top: 8px;
  padding: 8px;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  font-size: 12px;
}

.comment-row {
  margin-bottom: 4px;
  color: var(--lx-text-secondary);
}

.foot {
  margin-top: 10px;
  display: flex;
  gap: 16px;
}

.foot-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--lx-text-muted);
  cursor: pointer;
}

.foot-btn:hover {
  color: var(--lx-accent);
}

.comment-box {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
