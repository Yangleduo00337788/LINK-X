/**
 * 作者：yangleduo
 */
import type { ShortVideoComment } from '../api/shortVideo'

export interface ShortVideoCommentNode extends ShortVideoComment {
  replies: ShortVideoCommentNode[]
}

function sortByTime(a: ShortVideoCommentNode, b: ShortVideoCommentNode) {
  const ta = Date.parse(a.time || '') || 0
  const tb = Date.parse(b.time || '') || 0
  return ta - tb
}

function resolveRootId(
  commentId: string,
  parentById: Map<string, string | undefined>
): string {
  let current = commentId
  const visited = new Set<string>()
  while (true) {
    const parentId = parentById.get(current)
    if (!parentId || visited.has(parentId)) {
      return current
    }
    visited.add(current)
    current = parentId
  }
}

/** 抖音式两层：一级评论 + 其下所有回复（扁平，不继续嵌套折叠） */
export function buildShortVideoCommentTree(comments: ShortVideoComment[]): ShortVideoCommentNode[] {
  if (!Array.isArray(comments) || comments.length === 0) return []

  const parentById = new Map<string, string | undefined>()
  for (const comment of comments) {
    const parentId = comment.parentId?.trim()
    parentById.set(comment.id, parentId || undefined)
  }

  const nodes = new Map<string, ShortVideoCommentNode>()
  for (const comment of comments) {
    nodes.set(comment.id, { ...comment, replies: [] })
  }

  const roots: ShortVideoCommentNode[] = []
  const rootIndex = new Map<string, ShortVideoCommentNode>()

  for (const comment of comments) {
    const node = nodes.get(comment.id)
    if (!node) continue
    const parentId = comment.parentId?.trim()
    if (!parentId) {
      roots.push(node)
      rootIndex.set(comment.id, node)
      continue
    }
    const rootId = resolveRootId(comment.id, parentById)
    const rootNode = nodes.get(rootId)
    if (!rootNode || rootId === comment.id) {
      roots.push(node)
      rootIndex.set(comment.id, node)
      continue
    }
    if (!rootIndex.has(rootId)) {
      roots.push(rootNode)
      rootIndex.set(rootId, rootNode)
    }
    if (!rootNode.replies.some(r => r.id === node.id)) {
      rootNode.replies.push(node)
    }
  }

  const uniqueRoots = Array.from(new Map(roots.map(r => [r.id, r])).values())
  uniqueRoots.sort(sortByTime)
  for (const root of uniqueRoots) {
    root.replies.sort(sortByTime)
  }
  return uniqueRoots
}
