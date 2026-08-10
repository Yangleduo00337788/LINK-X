/**
 * 作者：yangleduo
 */
/** 用户资料性别：与后端 API 枚举值一致（展示层请用 i18n）。 */
export const PROFILE_GENDER_MALE = '男' as const
export const PROFILE_GENDER_FEMALE = '女' as const

export type ProfileGender = typeof PROFILE_GENDER_MALE | typeof PROFILE_GENDER_FEMALE

export function normalizeProfileGender(value?: string | null): ProfileGender {
  return value === PROFILE_GENDER_FEMALE ? PROFILE_GENDER_FEMALE : PROFILE_GENDER_MALE
}
