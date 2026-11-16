import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../api/http'

interface State<T> {
  key: string
  data?: T
  error?: string
}

/**
 * Minimal data loader: refetches whenever `key` changes, aborts stale
 * requests, and keeps the previous data visible while the next page loads.
 * Call `reload()` after a mutation.
 */
export function useQuery<T>(key: string, fetcher: (signal: AbortSignal) => Promise<T>) {
  const [version, setVersion] = useState(0)
  const fullKey = `${key}#${version}`
  const [state, setState] = useState<State<T>>({ key: '' })

  useEffect(() => {
    const ctrl = new AbortController()
    fetcher(ctrl.signal)
      .then((data) => setState({ key: fullKey, data }))
      .catch((err) => {
        if (!ctrl.signal.aborted) setState((s) => ({ key: fullKey, data: s.data, error: errorMessage(err) }))
      })
    return () => ctrl.abort()
    // `fullKey` captures every input of the request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fullKey])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  const settled = state.key === fullKey
  return {
    data: state.data,
    error: settled ? state.error : undefined,
    loading: !settled,
    reload,
  }
}
