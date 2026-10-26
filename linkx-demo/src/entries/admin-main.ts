/**
 * 作者：yangleduo
 */
import { installHttpMocks } from '@demo/mocks/install'
import { installAdminFetchMocks } from '@demo/mocks/admin/fetch'

installHttpMocks('admin')
installAdminFetchMocks()

void import('../../../linkx-admin/src/main.ts')
