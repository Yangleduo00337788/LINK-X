/**
 * 作者：yangleduo
 */
import { installHttpMocks } from '@demo/mocks/install'
import { purgeDemoClientStorage } from '@demo/mocks/client/purge-demo-storage'
import { installClientElectronShim } from '@demo/mocks/client/electron-shim'
import { installClientWebSocketMock } from '@demo/mocks/client/websocket'
purgeDemoClientStorage()
installHttpMocks('client')
installClientElectronShim()
installClientWebSocketMock()

/** 勿用静态 import：会被提升，导致在 installHttpMocks 之前就创建 apiClient */
void import('../../../linkx-client/src/main.ts')
