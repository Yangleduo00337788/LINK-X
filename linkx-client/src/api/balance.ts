import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface BalanceInfo {
  userId: string
  balance: number
  frozen: number
  available: number
  totalRecharge: number
  totalWithdraw: number
}

export interface BalanceLog {
  id: string
  type: string
  amount: number
  balanceBefore: number
  balanceAfter: number
  remark: string
  time: string
}

/**
 * 获取当前用户余额
 */
export function getBalance() {
  return apiClient.get<never, ApiResult<BalanceInfo>>('/balance')
}
