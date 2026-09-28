/**
 * 项目 Logo 资源：默认彩色圆角标用仓库根 assets/logo.png；
 * 系统栏 / 登录页左上角用 LinkX 字标；未选会话主区水印仍用 logo-chrome。
 */
import projectLogoUrl from '@assets/logo.png'
import chromeBrandLogoUrl from '../assets/logo-chrome-transparent.png'
import wordmarkLogoUrl from '../assets/logo-wordmark.png'

/** 全站默认项目 Logo（头像占位、关于页等） */
export const PROJECT_LOGO_URL = projectLogoUrl

/** 主区空状态水印（灰色 LX 标） */
export const CHROME_BRAND_LOGO_URL = chromeBrandLogoUrl

/** 系统栏左上角与登录页品牌字标 */
export const WORDMARK_LOGO_URL = wordmarkLogoUrl
