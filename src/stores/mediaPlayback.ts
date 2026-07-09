import { defineStore } from 'pinia'
import { apps } from '../data/mockData'
import type { AppItem } from '../types'

export interface NowPlayingInfo {
  track: string
  artist: string
  appId: string
}

function parseNeteaseTitle(title: string): { track: string; artist: string } | null {
  if (!title || /^网易云音乐$/i.test(title.trim())) return null
  const text = title.replace(/\s*[-–—|]\s*网易云音乐.*$/i, '').trim()
  if (!text || /^网易云音乐$/i.test(text)) return null

  const parts = text.split(/\s*-\s*/).map(s => s.trim()).filter(Boolean)
  if (parts.length >= 2) {
    return { track: parts[0], artist: parts.slice(1).join(' - ') }
  }
  if (parts.length === 1) {
    return { track: parts[0], artist: '' }
  }
  return null
}

export const useMediaPlaybackStore = defineStore('mediaPlayback', {
  state: () => ({
    nowPlaying: null as NowPlayingInfo | null
  }),

  getters: {
    isPlaying(state): boolean {
      return !!state.nowPlaying?.track
    },

    displayLabel(state): string {
      if (!state.nowPlaying) return ''
      const { track, artist } = state.nowPlaying
      return artist ? `${track} - ${artist}` : track
    },

    sourceApp(state): AppItem | null {
      if (!state.nowPlaying) return null
      return apps.find(a => a.id === state.nowPlaying!.appId) ?? null
    }
  },

  actions: {
    updateFromPageTitle(appKind: AppItem['appKind'], appId: string, title: string) {
      if (appKind !== 'netease') return
      const parsed = parseNeteaseTitle(title)
      if (!parsed) return
      this.nowPlaying = {
        track: parsed.track,
        artist: parsed.artist,
        appId
      }
    },

    clear() {
      this.nowPlaying = null
    }
  }
})
