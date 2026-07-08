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
  likedBy: string[]
  comments: MomentComment[]
}

const initialPosts: MomentPost[] = [
  {
    id: 'p1',
    user: '晚香玉',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=me',
    content: 'LinkX 前端本地持久化已上线，刷新不丢消息 🎉',
    time: '2小时前',
    likes: 2,
    liked: false,
    likedBy: ['吱唔猪', '小明'],
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
    likes: 1,
    liked: true,
    likedBy: ['晚香玉'],
    comments: []
  },
  {
    id: 'p3',
    user: '王五',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=王五',
    content: '分享一首好听的歌 🎵',
    time: '昨天',
    likes: 0,
    liked: false,
    likedBy: [],
    comments: [
      { id: 'c3', user: '养乐多', content: '确实好听！' }
    ]
  }
]

export const useMomentsStore = defineStore('moments', {
  state: () => ({
    posts: [...initialPosts] as MomentPost[],
    uiShowActions: {} as Record<string, boolean>
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
        likedBy: [],
        comments: []
      })
    },

    toggleLike(postId: string, userName: string) {
      const post = this.posts.find(p => p.id === postId)
      if (!post) return
      if (post.liked) {
        post.liked = false
        post.likedBy = post.likedBy.filter(n => n !== userName)
      } else {
        post.liked = true
        if (!post.likedBy.includes(userName)) {
          post.likedBy.push(userName)
        }
      }
      post.likes = post.likedBy.length
      this.uiShowActions[postId] = false
    },

    addComment(postId: string, user: string, content: string) {
      const post = this.posts.find(p => p.id === postId)
      if (!post || !content.trim()) return
      post.comments.push({
        id: `c-${Date.now()}`,
        user,
        content: content.trim()
      })
      this.uiShowActions[postId] = false
    },

    toggleActions(postId: string) {
      this.uiShowActions[postId] = !this.uiShowActions[postId]
    },

    isActionsOpen(postId: string) {
      return !!this.uiShowActions[postId]
    }
  },

  persist: {
    key: 'linkx-moments',
    paths: ['posts']
  }
})
