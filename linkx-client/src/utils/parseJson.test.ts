import { describe, it, expect } from 'vitest'
import { parseJsonPreservingIds } from './parseJson'

describe('parseJson', () => {
  it('should parse valid JSON with numeric IDs', () => {
    const input = '{"id": 1234567890123456789, "name": "test"}'
    const result = parseJsonPreservingIds(input)
    expect(result.id).toBe('1234567890123456789')
    expect(result.name).toBe('test')
  })

  it('should parse nested objects with large IDs', () => {
    const input = '{"user": {"id": 9876543210987654321}, "items": [{"id": 111222333}]}'
    const result = parseJsonPreservingIds(input) as { user: { id: string }; items: { id: string }[] }
    expect(result.user.id).toBe('9876543210987654321')
    expect(result.items[0].id).toBe('111222333')
  })

  it('should return original string for invalid JSON', () => {
    const input = 'not a json string'
    const result = parseJsonPreservingIds(input)
    expect(result).toBe(input)
  })

  it('should preserve string IDs', () => {
    const input = '{"id": "string-id-123", "name": "test"}'
    const result = parseJsonPreservingIds(input) as { id: string }
    expect(result.id).toBe('string-id-123')
  })

  it('should handle arrays with large numbers', () => {
    const input = '[1234567890123456789, 9876543210987654321]'
    const result = parseJsonPreservingIds(input) as string[]
    expect(result[0]).toBe('1234567890123456789')
    expect(result[1]).toBe('9876543210987654321')
  })
})
