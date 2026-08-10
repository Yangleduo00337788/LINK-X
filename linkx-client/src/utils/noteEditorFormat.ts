/**
 * 作者：yangleduo
 */
export type NoteFormatAction =
  | 'bold'
  | 'italic'
  | 'underline'
  | 'heading'
  | 'unordered'
  | 'ordered'
  | 'todo'
  | 'divider'

export type NoteFormatState = {
  bold: boolean
  italic: boolean
  underline: boolean
  heading: boolean
  unordered: boolean
  ordered: boolean
  todo: boolean
}

export const emptyFormatState = (): NoteFormatState => ({
  bold: false,
  italic: false,
  underline: false,
  heading: false,
  unordered: false,
  ordered: false,
  todo: false
})
