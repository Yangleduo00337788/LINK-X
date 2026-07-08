import { defineStore } from 'pinia'

export interface MomentComment {
  id: string
  user: string
  content: string
}

export interface MomentPost {
  id: string
  user: string
  avatar: string
  content: string
  images?: string[]
  time: string
  likes: number
  liked: boolean
  comments: MomentComment[]
}

const initialPosts: MomentPost[] = [
  {
    id: 'p1',
    user: '晚香玉',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=me',
    content: 'LinkX 前端本地持久化已上线，刷新不丢消息 🎉',
    time: '2小时前',
    likes: 12,
    liked: false,
    comments: [
      { id: 'c1', user: '吱唔猪', content: '厉害！' }
    ]
  },
  {
    id: 'p2',
    user: '吱唔猪',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhiwuzhu',
    content: '周末愉快～',
    images: ['https://picsum.photos/seed/linkx1/400/300'],
    time: '昨天',
    likes: 8,
    liked: true,
    comments: []
  }
]

export const useMomentsStore = defineStore('moments', {
  state: () => ({
    posts: [...initialPosts] as MomentPost[]
  }),

  actions: {
    addPost(content: string, user: string, avatar: string, images?: string[]) {
      this.posts.unshift({
        id: `p-${Date.now()}`,
        user,
        avatar,
        content,
        images,
        time: '刚刚',
        likes: 0,
        liked: false,
        comments: []
      })
    },

    toggleLike(postId: string) {
      const post = this.posts.find(p => p.id === postId)
      if (!post) return
      post.liked = !post.liked
      post.likes += post.liked ? 1 : -1
    },

    addComment(postId: string, user: string, content: string) {
      const post = this.posts.find(p => p.id === postId)
      if (!post || !content.trim()) return
      post.comments.push({
        id: `c-${Date.now()}`,
        user,
        content: content.trim()
      })
    }
  },

  persist: {
    key: 'linkx-moments',
    paths: ['posts']
  }
})
