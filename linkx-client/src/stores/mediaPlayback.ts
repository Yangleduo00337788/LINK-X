/**
 * 媒体播放状态 Store
 * 从网易云音乐 WebView 页面标题解析当前播放曲目并展示在状态栏
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'
// 导入 mock 应用列表（用于查找来源应用）
import { apps } from '../data/mockData'
// 导入应用项类型
import type { AppItem } from '../types'

/** 当前正在播放的曲目信息 */
export interface NowPlayingInfo {
  track: string  // 歌曲名
  artist: string // 艺术家（可为空）
  appId: string  // 来源应用 id（如 netease-music）
}

/**
 * 从浏览器/WebView 页面标题解析网易云音乐播放信息
 * 常见格式：「歌名 - 歌手 - 网易云音乐」
 * @param title document.title 或 iframe title
 * @returns 解析结果，无法识别时返回 null
 */
function parseNeteaseTitle(title: string): { track: string; artist: string } | null {
  // 空标题或纯「网易云音乐」视为无播放信息
  if (!title || /^网易云音乐$/i.test(title.trim())) return null
  // 去掉末尾「- 网易云音乐」后缀
  const text = title.replace(/\s*[-–—|]\s*网易云音乐.*$/i, '').trim()
  if (!text || /^网易云音乐$/i.test(text)) return null

  // 按「 - 」分割歌名与歌手
  const parts = text.split(/\s*-\s*/).map(s => s.trim()).filter(Boolean)
  if (parts.length >= 2) {
    return { track: parts[0], artist: parts.slice(1).join(' - ') }
  }
  if (parts.length === 1) {
    return { track: parts[0], artist: '' }
  }
  return null
}

// 定义并导出 mediaPlayback Store
export const useMediaPlaybackStore = defineStore('mediaPlayback', {
  // 初始状态：无播放中曲目
  state: () => ({
    nowPlaying: null as NowPlayingInfo | null
  }),

  getters: {
    /** 是否正在播放（有有效 track 即视为播放中） */
    isPlaying(state): boolean {
      return !!state.nowPlaying?.track
    },

    /** 状态栏展示文案：「歌名 - 歌手」或仅歌名 */
    displayLabel(state): string {
      if (!state.nowPlaying) return ''
      const { track, artist } = state.nowPlaying
      return artist ? `${track} - ${artist}` : track
    },

    /** 播放来源对应的 AppItem（从 apps 列表查找） */
    sourceApp(state): AppItem | null {
      if (!state.nowPlaying) return null
      return apps.find(a => a.id === state.nowPlaying!.appId) ?? null
    }
  },

  actions: {
    /**
     * 根据内嵌页面 title 更新播放状态（仅处理 netease 类型应用）
     * @param appKind 应用种类
     * @param appId 应用 id
     * @param title 页面标题
     */
    updateFromPageTitle(appKind: AppItem['appKind'], appId: string, title: string) {
      if (appKind !== 'netease') return // 非网易云应用忽略
      const parsed = parseNeteaseTitle(title)
      if (!parsed) return
      this.nowPlaying = {
        track: parsed.track,
        artist: parsed.artist,
        appId
      }
    },

    /** 清除当前播放信息 */
    clear() {
      this.nowPlaying = null
    }
  }
})
