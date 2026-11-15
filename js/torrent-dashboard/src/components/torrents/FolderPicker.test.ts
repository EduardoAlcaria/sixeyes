import { describe, expect, it } from 'vitest'
import { friendlyPath } from './FolderPicker'

describe('friendlyPath', () => {
  it('shows absolute paths as-is', () => {
    expect(friendlyPath('D:\\Games')).toBe('D:\\Games')
    expect(friendlyPath('C:/Downloads')).toBe('C:/Downloads')
    expect(friendlyPath('\\\\nas\\share')).toBe('\\\\nas\\share')
  })

  it('labels relative or missing paths as the default', () => {
    expect(friendlyPath('./downloads')).toBe('Default downloads')
    expect(friendlyPath(null, 'Not set')).toBe('Not set')
  })
})
