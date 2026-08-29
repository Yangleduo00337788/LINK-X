/**
 * 作者：yangleduo
 */
import './loadLocalEnv'
import { test } from '@playwright/test'

/** 避免 Windows 上 localhost 解析到 ::1 导致连接失败 */
function normalizeOrigin(url: string): string {
  return url.replace(/\/$/, '').replace(/\/\/localhost\b/i, '//127.0.0.1')
}

export const e2eEnv = {
  apiBase: normalizeOrigin(process.env.E2E_API_BASE_URL || 'http://127.0.0.1:8080'),
  adminBase: normalizeOrigin(process.env.E2E_ADMIN_BASE_URL || 'http://127.0.0.1:5174'),
  clientBase: normalizeOrigin(process.env.E2E_CLIENT_BASE_URL || 'http://127.0.0.1:5173'),
  adminUser: process.env.E2E_ADMIN_USER || '',
  adminPassword: process.env.E2E_ADMIN_PASSWORD || '',
  clientUser: process.env.E2E_CLIENT_USER || '',
  clientPassword: process.env.E2E_CLIENT_PASSWORD || ''
}

export function requireAdminCredentials() {
  if (!e2eEnv.adminUser || !e2eEnv.adminPassword) {
    test.skip(true, '需要设置 E2E_ADMIN_USER / E2E_ADMIN_PASSWORD')
  }
}

export function requireClientCredentials() {
  if (!e2eEnv.clientUser || !e2eEnv.clientPassword) {
    test.skip(true, '需要设置 E2E_CLIENT_USER / E2E_CLIENT_PASSWORD')
  }
}
