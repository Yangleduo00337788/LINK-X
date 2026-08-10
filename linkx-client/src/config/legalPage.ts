/**
 * 作者：yangleduo
 */
export type { LegalDocKind } from '../../shared/legalPage'
export {
  DEFAULT_LEGAL_PAGE_BASE_URL,
  buildLegalPageUrl,
  resolveLegalPageBaseUrl
} from '../../shared/legalPage'

import { resolveLegalPageBaseUrl } from '../../shared/legalPage'

export const LEGAL_PAGE_BASE_URL = resolveLegalPageBaseUrl(
  import.meta.env.VITE_LEGAL_PAGE_BASE_URL
)
