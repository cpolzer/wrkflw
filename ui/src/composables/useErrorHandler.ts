import { ref } from 'vue'
import { ApiError } from '@/api/client'

export interface ToastMessage {
  id: number
  message: string
  type: 'error' | 'warning' | 'info'
}

const toasts = ref<ToastMessage[]>([])
let nextId = 1

export function useErrorHandler() {
  function handleError(error: unknown, context?: string): string {
    let message: string

    if (error instanceof ApiError) {
      switch (error.status) {
        case 403:
          message = context ?? 'You do not have permission to perform this action.'
          break
        case 404:
          message = 'The requested item was not found.'
          break
        case 409:
          message = 'This action is no longer available — it may have been completed by someone else.'
          break
        case 422:
          message = 'The request contained invalid data. Please check your input.'
          break
        default:
          message = 'Something went wrong. Please try again.'
      }
    } else if (error instanceof Error && error.name === 'AbortError') {
      message = 'Cannot reach the server. Please check your connection.'
    } else {
      message = 'An unexpected error occurred.'
    }

    showError(message)
    return message
  }

  function showError(message: string): void {
    const id = nextId++
    toasts.value.push({ id, message, type: 'error' })
    setTimeout(() => dismiss(id), 5000)
  }

  function dismiss(id: number): void {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  return { toasts, handleError, showError, dismiss }
}
