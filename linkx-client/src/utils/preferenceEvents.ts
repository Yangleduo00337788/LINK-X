/**
 * 偏好设置保存事件总线
 * <p>
 * store 在 flush 完成后通过 onPreferenceChange.emit 广播结果；
 * 组件订阅后弹 toast 或播放试听音。
 * </p>
 */

import type { UserPreferencePatch } from '../api/preference'

export type PreferenceChangeEvent =
  | {
      kind: 'success'
      /** 本次成功保存的字段→值映射 */
      fields: UserPreferencePatch
      /** 字段名 → 中文标签，供 toast 文案复用 */
      fieldLabel: Record<string, string>
    }
  | {
      kind: 'error'
      fields: UserPreferencePatch
      message: string
    }

/** 简单事件订阅器（mitt 风格的极简实现，零依赖） */
class Emitter<TEvents extends { kind: string }> {
  private handlers: Array<(e: TEvents) => void> = []

  emit(event: TEvents) {
    for (const h of this.handlers) {
      try {
        h(event)
      } catch (err) {
        console.error('[preferenceEvents] 订阅器回调异常:', err)
      }
    }
  }

  on(handler: (e: TEvents) => void): () => void {
    this.handlers.push(handler)
    return () => {
      this.handlers = this.handlers.filter(h => h !== handler)
    }
  }
}

export const onPreferenceChange = new Emitter<PreferenceChangeEvent>()