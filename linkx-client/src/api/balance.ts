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

/**
 * 余额流水
 */
export function listBalanceLogs(params?: { limit?: number; beforeId?: string }) {
  return apiClient.get<never, ApiResult<BalanceLog[]>>('/balance/logs', { params })
}

/**
 * 演示充值（无真实支付）
 */
export function rechargeBalance(amount: number) {
  return apiClient.post<never, ApiResult<BalanceInfo>>('/balance/recharge', { amount })
}
