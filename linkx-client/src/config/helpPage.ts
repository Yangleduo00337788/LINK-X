/**
 * 作者：yangleduo
 */
export {
  DEFAULT_HELP_PAGE_BASE_URL,
  buildHelpPageUrl,
  resolveHelpPageBaseUrl
} from '../../shared/helpPage'

import { resolveHelpPageBaseUrl } from '../../shared/helpPage'

export const HELP_PAGE_BASE_URL = resolveHelpPageBaseUrl(
  import.meta.env.VITE_HELP_PAGE_BASE_URL
)
