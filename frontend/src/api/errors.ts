import { ApiError } from './client'

/** The message to show for a failed request; field-level validation errors are shown next to their fields. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.fieldErrors.length > 0) return 'Please correct the highlighted fields.'
    return error.detail ?? error.title
  }
  return 'Something went wrong. Please try again.'
}

/** Validation messages by field name, for a failed form submission. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  return Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.message]))
}
