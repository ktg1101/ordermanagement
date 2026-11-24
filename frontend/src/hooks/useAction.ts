import { useCallback, useState } from 'react'
import { asApiError } from '../api/client'
import type { ApiError } from '../api/client'

/**
 * Bọc một thao tác bất đồng bộ: theo dõi `busy` và lỗi gần nhất.
 * `run` không ném lỗi; trả về kết quả, hoặc `undefined` nếu thất bại (lỗi nằm ở `error`).
 */
export function useAction() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  const run = useCallback(async <T>(action: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true)
    setError(null)
    try {
      return await action()
    } catch (caught) {
      setError(asApiError(caught))
      return undefined
    } finally {
      setBusy(false)
    }
  }, [])

  const clearError = useCallback(() => setError(null), [])

  return { busy, error, run, clearError }
}
