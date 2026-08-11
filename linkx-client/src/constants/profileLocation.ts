/** 与后端 API 一致的国家/地区存储值（展示文案走 i18n） */
export const PROFILE_COUNTRY_CN = '中国'

export const PROFILE_PROVINCES = [
  { key: 'beijing', value: '北京' },
  { key: 'shanghai', value: '上海' },
  { key: 'guangdong', value: '广东' },
  { key: 'zhejiang', value: '浙江' },
  { key: 'jiangsu', value: '江苏' },
  { key: 'sichuan', value: '四川' },
  { key: 'hubei', value: '湖北' },
  { key: 'hunan', value: '湖南' },
  { key: 'fujian', value: '福建' },
  { key: 'shandong', value: '山东' }
] as const

export const PROFILE_REGIONS = [
  { key: 'urban', value: '城区' },
  { key: 'suburb', value: '郊区' },
  { key: 'hightech', value: '高新区' },
  { key: 'devzone', value: '开发区' }
] as const

export type ProfileProvinceKey = (typeof PROFILE_PROVINCES)[number]['key']
export type ProfileRegionKey = (typeof PROFILE_REGIONS)[number]['key']
