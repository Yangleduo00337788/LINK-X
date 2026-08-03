import { get } from './request'
import type { PageResult } from '@/types/api'

export interface SnailJobTaskItem {
  jobId?: number
  executorName: string
  jobName: string
  description?: string
  triggerType?: string
  triggerInterval?: string
  executorTimeoutSeconds?: number
  jobStatus?: number
  nextTriggerAt?: string
  lastBatchId?: number
  lastBatchStatus?: string
  lastExecutionAt?: string
  lastDurationMs?: number
  registered?: boolean
}

export interface SnailJobOverview {
  adminConsoleUrl: string
  clientGroup: string
  tasks: SnailJobTaskItem[]
  monitorAvailable?: boolean
  refreshedAt?: string
  totalTasks?: number
  registeredTasks?: number
  enabledTasks?: number
  failedTasks?: number
}

export interface SnailJobBatchItem {
  id: number
  jobId: number
  jobName: string
  batchStatus: string
  executionAt?: string
  createDt?: string
  operationReason?: number
  durationMs?: number
}

export interface SnailJobLogItem {
  id: number
  taskBatchId: number
  taskId: number
  message: string
  logNum?: number
  createDt?: string
}

export function fetchSnailJobOverview() {
  return get<SnailJobOverview>('/admin/scheduled-tasks')
}

export function fetchSnailJobBatches(jobId: number, page = 1, size = 20) {
  return get<PageResult<SnailJobBatchItem>>(`/admin/scheduled-tasks/${jobId}/batches`, {
    page,
    size,
  })
}

export function fetchSnailJobLogs(batchId: number, page = 1, size = 50) {
  return get<PageResult<SnailJobLogItem>>(`/admin/scheduled-tasks/batches/${batchId}/logs`, {
    page,
    size,
  })
}
