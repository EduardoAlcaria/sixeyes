import { useState } from 'react'
import { RefreshCw, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { GameCard, GameCardSkeleton } from '@/components/catalog/GameCard'
import { GameDownloadDialog } from '@/components/catalog/GameDownloadDialog'
import { useCatalog } from '@/hooks/useCatalog'
import type { CatalogGame } from '@/types'

export function CatalogPage() {
  const { query, setQuery, page, setPage, results, loading, refresh } = useCatalog()
  const [selected, setSelected] = useState<CatalogGame | null>(null)

  const isEmpty = !loading && results !== null && results.content.length === 0

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-bold tracking-tight shrink-0">Catalog</h1>
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            autoFocus
            placeholder="Search FitGirl repacks…"
            value={query}
            onChange={e => setQuery(e.target.value)}
            className="pl-9"
          />
        </div>
        <Button variant="outline" size="icon" onClick={refresh} aria-label="Refresh catalog">
          <RefreshCw className="size-4" />
        </Button>
      </div>

      {isEmpty && (
        <p className="text-center text-muted-foreground py-16 text-sm">
          {query.trim()
            ? `No results for "${query}"`
            : <>Catalog is empty — click <button type="button" onClick={refresh} className="underline underline-offset-2 hover:text-foreground">refresh</button> to index.</>
          }
        </p>
      )}

      {loading && (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
          {Array.from({ length: 12 }).map((_, i) => (
            <GameCardSkeleton key={i} />
          ))}
        </div>
      )}

      {!loading && results && results.content.length > 0 && (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
            {results.content.map(game => (
              <GameCard key={game.id} game={game} onClick={() => setSelected(game)} />
            ))}
          </div>

          {results.totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
              >
                Previous
              </Button>
              <span className="text-sm text-muted-foreground">
                {page + 1} / {results.totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= results.totalPages - 1}
                onClick={() => setPage(p => p + 1)}
              >
                Next
              </Button>
            </div>
          )}
        </>
      )}

      <GameDownloadDialog
        game={selected}
        open={selected !== null}
        onClose={() => setSelected(null)}
      />
    </div>
  )
}
