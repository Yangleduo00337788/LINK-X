/**
 * 与后端 LinkMateModelCapability.supportsDeepThinking 保持一致。
 */
const REASONING_MODEL_PATTERN = /reasoner|thinking|deep-think|deepthink|\br1\b|o1|o3/i

export function inferReasoningSupported(model: string | undefined | null): boolean {
  const normalized = (model ?? '').trim().toLowerCase()
  if (!normalized) return false
  if (REASONING_MODEL_PATTERN.test(normalized)) return true
  return normalized === 'deepseek-chat'
}
