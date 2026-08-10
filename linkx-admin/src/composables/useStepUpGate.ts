/**
 * 作者：yangleduo
 */
import { ref } from 'vue'
import type { StepUpChallenge } from '@/api/stepUp'

type Resolver = (token: string | null) => void

const visible = ref(false)
const challenge = ref<StepUpChallenge | null>(null)
let resolver: Resolver | null = null

/** 弹出二次验证，完成后 resolve stepUpToken；取消则为 null */
export function promptStepUp(payload: StepUpChallenge): Promise<string | null> {
  if (visible.value && resolver) {
    // 已有弹窗时取消上一次等待，避免悬挂
    resolver(null)
    resolver = null
  }
  challenge.value = payload
  visible.value = true
  return new Promise<string | null>((resolve) => {
    resolver = resolve
  })
}

export function resolveStepUp(token: string | null) {
  visible.value = false
  const r = resolver
  resolver = null
  challenge.value = null
  r?.(token)
}

export function useStepUpGate() {
  return { visible, challenge, resolveStepUp }
}
