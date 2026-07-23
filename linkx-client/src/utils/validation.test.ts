import { describe, it, expect } from 'vitest'
import {
  validateUsername,
  validatePassword,
  validateNickname,
  validateLockPin
} from './validation'

describe('validation', () => {
  it('validateUsername', () => {
    expect(validateUsername('')).toBeTruthy()
    expect(validateUsername('ab')).toBeTruthy()
    expect(validateUsername('ab!@')).toBeTruthy()
    expect(validateUsername('user_01')).toBeNull()
    expect(validateUsername('a'.repeat(33))).toBeTruthy()
  })

  it('validatePassword', () => {
    expect(validatePassword('')).toBeTruthy()
    expect(validatePassword('short')).toBeTruthy()
    expect(validatePassword('password')).toBeNull()
    expect(validatePassword('password', true)).toBeTruthy()
    expect(validatePassword('pass1234', true)).toBeNull()
  })

  it('validateNickname', () => {
    expect(validateNickname('')).toBeTruthy()
    expect(validateNickname('昵称')).toBeNull()
    expect(validateNickname('x'.repeat(65))).toBeTruthy()
  })

  it('validateLockPin', () => {
    expect(validateLockPin('12')).toBeTruthy()
    expect(validateLockPin('1234')).toBeNull()
    expect(validateLockPin('123456')).toBeNull()
    expect(validateLockPin('12ab')).toBeTruthy()
  })
})
