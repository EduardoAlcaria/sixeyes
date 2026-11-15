import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { catalogApi } from '../services/api'
import type { CatalogPage } from '../types'

export function useCatalog() {
  const [query, setQueryState] = useState('')
  const [page, setPage] = useState(0)
  const [results, setResults] = useState<CatalogPage | null>(null)
  // The query the current results answer; differs from `query` while debouncing.
  const [resultsFor, setResultsFor] = useState('')
  const [loading, setLoading] = useState(false)

  // Reset the page in the same update as the query: a separate effect fired one search
  // with the old page first, and whichever response landed last won.
  const setQuery = useCallback((q: string) => {
    setQueryState(q)
    setPage(0)
  }, [])

  useEffect(() => {
    let stale = false
    const delay = query.trim() ? 400 : 0
    const t = setTimeout(async () => {
      setLoading(true)
      try {
        const res = await catalogApi.search(query, page)
        if (!stale) { setResults(res); setResultsFor(query) }
      } catch (e) {
        if (!stale) toast.error((e as Error).message)
      } finally {
        if (!stale) setLoading(false)
      }
    }, delay)
    return () => { stale = true; clearTimeout(t) }
  }, [query, page])

  const refresh = useCallback(async () => {
    try {
      await catalogApi.refresh()
      toast.success('Syncing with FitGirl - new repacks appear in a moment')
    } catch (e) {
      toast.error((e as Error).message)
    }
  }, [])

  return { query, setQuery, page, setPage, results, loading, refresh, settled: resultsFor === query }
}
