/**
 * 朋友圈（Moments）Store
 * 管理动态帖子、点赞、评论及操作栏 UI 状态
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'

/** 单条评论 */
export interface MomentComment {
  id: string     // 评论 id
  user: string   // 评论者昵称
  content: string // 评论正文
}

/** 单条朋友圈动态 */
export interface MomentPost {
  id: string              // 动态 id
  user: string            // 发布者昵称
  avatar: string          // 头像 URL
  content: string         // 文字内容
  images?: string[]       // 可选图片 URL 列表
  time: string            // 相对时间标签
  likes: number           // 点赞数（与 likedBy 长度同步）
  liked: boolean          // 当前用户是否已赞
  likedBy: string[]       // 点赞用户昵称列表
  comments: MomentComment[] // 评论列表
}

// mock 初始动态数据
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

// 定义并导出 moments Store
export const useMomentsStore = defineStore('moments', {
  // 初始状态
  state: () => ({
    posts: [...initialPosts] as MomentPost[],              // 动态列表
    uiShowActions: {} as Record<string, boolean>           // 每条动态是否展开点赞/评论操作栏
  }),

  actions: {
    /**
     * 发布新动态（插入列表头部）
     * @param content 文字内容
     * @param user 发布者昵称
     * @param avatar 头像 URL
     * @param images 可选图片列表
     */
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

    /**
     * 切换当前用户对某动态的点赞状态
     * @param postId 动态 id
     * @param userName 当前用户昵称（用于 likedBy）
     */
    toggleLike(postId: string, userName: string) {
      const post = this.posts.find(p => p.id === postId)
      if (!post) return
      if (post.liked) {
        // 取消赞：从 likedBy 移除并标记未赞
        post.liked = false
        post.likedBy = post.likedBy.filter(n => n !== userName)
      } else {
        // 点赞：加入 likedBy（去重）
        post.liked = true
        if (!post.likedBy.includes(userName)) {
          post.likedBy.push(userName)
        }
      }
      post.likes = post.likedBy.length // 同步点赞计数
      this.uiShowActions[postId] = false // 操作后收起操作栏
    },

    /**
     * 添加评论
     * @param postId 动态 id
     * @param user 评论者
     * @param content 评论内容
     */
    addComment(postId: string, user: string, content: string) {
      const post = this.posts.find(p => p.id === postId)
      if (!post || !content.trim()) return // 空评论忽略
      post.comments.push({
        id: `c-${Date.now()}`,
        user,
        content: content.trim()
      })
      this.uiShowActions[postId] = false
    },

    /**
     * 切换某动态的操作栏（赞/评）显示
     * @param postId 动态 id
     */
    toggleActions(postId: string) {
      this.uiShowActions[postId] = !this.uiShowActions[postId]
    },

    /**
     * 查询某动态操作栏是否打开
     * @param postId 动态 id
     */
    isActionsOpen(postId: string) {
      return !!this.uiShowActions[postId]
    }
  },

  // 持久化帖子数据，UI 展开状态不持久化
  persist: {
    key: 'linkx-moments',
    paths: ['posts']
  }
})
