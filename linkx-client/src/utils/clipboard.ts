/**
 * 复制文本到剪贴板：优先 Electron 原生 API，其次 Clipboard API，最后 textarea 兜底。
 * 不因权限失败抛错，返回是否复制成功。
 */
export async function copyText(text: string): Promise<boolean> {
  const value = String(text ?? '')
  if (!value) return false

  const electronWrite = window.electronAPI?.clipboardWriteText
  if (typeof electronWrite === 'function') {
    try {
      const ok = await electronWrite(value)
      if (ok) return true
    } catch {
      /* fall through */
    }
  }

  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
      return true
    }
  } catch {
    /* fall through */
  }

  try {
    const ta = document.createElement('textarea')
    ta.value = value
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.left = '-9999px'
    ta.style.top = '0'
    document.body.appendChild(ta)
    ta.focus()
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}
