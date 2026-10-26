/**
 * 作者：yangleduo
 */
/** 与真实客户端一致的同源媒体代理路径（由 demo 中间件回源静态图） */
export function avatarMediaPath(userId: string | number): string {
  return `/media/avatars/${userId}`
}

export function chatImageMediaPath(file: string): string {
  return `/media/chat/${file}`
}

export function momentsBgMediaPath(userId: string | number): string {
  return `/media/moments-background/${userId}`
}

export function pickAvatarFile(index: number): string {
  return `avatar-${String((index % 12) + 1).padStart(2, '0')}.jpg`
}
