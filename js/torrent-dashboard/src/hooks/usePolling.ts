import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Fetches now and every `ms` while mounted. Keeps the last good value on failure, so a
 * blip in the backend leaves the panel showing data instead of blanking it.
 */
export function usePolling<T>(fetcher: () => Promise<T>, ms: number, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  const refresh = useCallback(async () => {
    try {
      setData(await fetcherRef.current())
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao carregar')
    }
  }, [])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, ms)
    return () => clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ms, refresh, ...deps])

  return { data, error, refresh }
}
