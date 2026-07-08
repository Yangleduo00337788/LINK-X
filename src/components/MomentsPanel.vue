<script setup lang="ts">
import { ref, computed } from 'vue'
import { NInput, NButton, NIcon, useMessage } from 'naive-ui'
import { HeartOutline, Heart, ChatbubbleOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import Avatar from './Avatar.vue'
import { storeToRefs } from 'pinia'
import { useMomentsStore } from '../stores/moments'
import { useAppStore } from '../stores/app'

const message = useMessage()
const momentsStore = useMomentsStore()
const appStore = useAppStore()
const { posts } = storeToRefs(momentsStore)
const { userProfile } = storeToRefs(appStore)
const { addPost, toggleLike, addComment } = momentsStore

const search = ref('')
const newPost = ref('')
const commentDrafts = ref<Record<string, string>>({})
const showCommentFor = ref<string | null>(null)

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return posts.value
  return posts.value.filter(
    p => p.user.toLowerCase().includes(q) || p.content.toLowerCase().includes(q)
  )
})

function publish() {
  const text = newPost.value.trim()
  if (!text) {
    message.warning('请输入动态内容')
    return
  }
  addPost(text, userProfile.value.nickname, 'https://api.dicebear.com/7.x/avataaars/svg?seed=me')
  newPost.value = ''
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
    <PanelSearchBar v-model="search" placeholder="搜索朋友圈" />
    <div class="composer">
      <n-input
        v-model:value="newPost"
        type="textarea"
        placeholder="分享新鲜事…"
        :rows="2"
      />
      <n-button type="primary" size="small" class="publish-btn" @click="publish">发布</n-button>
    </div>
    <div class="list">
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
          <button type="button" class="foot-btn" @click="toggleLike(m.id)">
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
