import axios from 'axios'
import type { ApiResult } from '@/types/api'
import type { AuthConfigVO } from '@/types/api'
import { getDeviceHeaders } from '@/utils/deviceId'
import { useSecurityStore } from '@/stores/security'

export async function bootstrapSecurity() {
  try {
    const { data } = await axios.get<ApiResult<AuthConfigVO>>(
      `${import.meta.env.VITE_API_BASE_URL || '/api'}/admin/auth/config`,
      { headers: getDeviceHeaders(), withCredentials: true }
    )
    if (data.code === 200 && data.data) {
      useSecurityStore().applyFromAuthConfig(data.data)
    }
  } catch {
    // 启动阶段拉取失败不阻塞应用挂载
  }
}
