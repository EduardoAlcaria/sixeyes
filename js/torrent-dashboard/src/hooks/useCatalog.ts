import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { catalogApi } from '../services/api'
import type { CatalogPage } from '../types'

export function useCatalog() {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [results, setResults] = useState<CatalogPage | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const delay = query.trim() ? 400 : 0
    const t = setTimeout(async () => {
      setLoading(true)
      try {
        setResults(await catalogApi.search(query, page))
      } catch (e) {
        toast.error((e as Error).message)
      } finally {
        setLoading(false)
      }
    }, delay)
    return () => clearTimeout(t)
  }, [query, page])

  // reset to page 0 when query changes
  useEffect(() => { setPage(0) }, [query])

  const refresh = useCallback(async () => {
    try {
      await catalogApi.refresh()
      toast.success('Catalog crawl started — results appear as games are indexed')
    } catch (e) {
      toast.error((e as Error).message)
    }
  }, [])

  return { query, setQuery, page, setPage, results, loading, refresh }
}
