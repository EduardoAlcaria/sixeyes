import { useMemo, useState } from 'react'
import { Play, Search } from 'lucide-react'
import { toast } from 'sonner'
import { GameDetailDialog } from '@/components/deck/GameDetailDialog'
import { Cover, Metric, SourceBadge, steamArt } from '@/components/deck/primitives'
import { usePolling } from '@/hooks/usePolling'
import { insightApi } from '@/services/api'
import { bytes, minutes, when } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Game, GameSource } from '@/types'

type Filter = 'all' | GameSource | 'never'
type Sort = 'recent' | 'name' | 'size' | 'played'

export function ShelfPage() {
  const { data, refresh } = usePolling(insightApi.games, 30_000)
  const [filter, setFilter] = useState<Filter>('all')
  const [sort, setSort] = useState<Sort>('recent')
  const [q, setQ] = useState('')
  const [open, setOpen] = useState<Game | null>(null)
  const [launching, setLaunching] = useState<string | null>(null)
  const games = data?.games ?? []

  const shown = useMemo(() => games
    .filter(g => !q.trim() || g.name.toLowerCase().includes(q.trim().toLowerCase()))
    .filter(g => filter === 'all' || (filter === 'never' ? g.playtimeMinutes === 0 : g.source === filter))
    .sort((a, b) => {
      switch (sort) {
        case 'name': return a.name.localeCompare(b.name)
        case 'size': return (b.sizeBytes ?? 0) - (a.sizeBytes ?? 0)
        case 'played': return b.playtimeMinutes - a.playtimeMinutes
        default: return (b.lastPlayed ?? '').localeCompare(a.lastPlayed ?? '')
      }
    }), [games, filter, sort, q])

  const launch = async (g: Game) => {
    setLaunching(g.key)
    try {
      await insightApi.launch(g.key)
      toast.success(`Abrindo ${g.name}`)
      setTimeout(refresh, 20_000)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Não abriu')
    } finally {
      setLaunching(null)
    }
  }

  const totalMinutes = games.reduce((s, g) => s + g.playtimeMinutes, 0)
  const week = games.reduce((s, g) => s + g.last7DaysHours.reduce((a, b) => a + b, 0), 0)

  return (
    <>
      <div className="flex flex-wrap items-baseline gap-3">
        <h1 className="text-2xl font-extrabold">Shelf</h1>
        <span className="text-sm text-muted-foreground">tudo que está instalado, de qualquer fonte</span>
      </div>

      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
        <Metric label="Jogos" value={games.length} tone="muted"
          hint={`${games.filter(g => g.source === 'STEAM').length} Steam · ${games.filter(g => g.source !== 'STEAM').length} outros`} />
        <Metric label="Tempo total" value={minutes(totalMinutes)} tone="muted" />
        <Metric label="Últimos 7 dias" value={`${week.toFixed(1)}h`} tone="muted" />
        <Metric label="Espaço usado" value={bytes(games.reduce((s, g) => s + (g.sizeBytes ?? 0), 0), 0)} tone="muted"
          hint={data?.installFree != null ? `${bytes(data.installFree, 0)} livre em ${data.installRoot}` : undefined} />
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        <label className="flex min-w-[180px] items-center gap-2 rounded-full border bg-card px-3 py-1 text-muted-foreground">
          <Search className="size-3.5" />
          <input value={q} onChange={e => setQ(e.target.value)} placeholder="Buscar jogo…" aria-label="Buscar jogo"
            className="w-full bg-transparent text-[0.76rem] text-foreground outline-none" />
        </label>
        {([['all', 'Tudo'], ['TORRENT', 'FitGirl'], ['STEAM', 'Steam'], ['LOCAL', 'Pasta local'], ['never', 'Nunca jogados']] as [Filter, string][]).map(([f, label]) => (
          <button key={f} type="button" onClick={() => setFilter(f)}
            className={cn('rounded-full border px-3 py-1 text-[0.76rem]', filter === f ? 'border-primary bg-primary/20 text-foreground' : 'text-muted-foreground')}>
            {label}
          </button>
        ))}
        <select value={sort} onChange={e => setSort(e.target.value as Sort)} aria-label="Ordenar"
          className="ml-auto rounded-full border bg-card px-3 py-1 text-[0.76rem] text-muted-foreground">
          <option value="recent">Jogados recentemente</option>
          <option value="played">Mais jogados</option>
          <option value="size">Maiores</option>
          <option value="name">Nome</option>
        </select>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
        {shown.map(g => (
          <div key={g.key} className="overflow-hidden rounded-[calc(var(--radius)*0.6)] border bg-card">
            <button type="button" onClick={() => setOpen(g)} className="block w-full text-left" aria-label={`Detalhes de ${g.name}`}>
              <Cover className="aspect-[3/4] w-full" seed={g.name}
                sources={g.source === 'STEAM' ? steamArt(g.key.slice(6)) : [g.coverUrl]}>
                <span className="absolute left-2 top-2"><SourceBadge source={g.source} /></span>
                <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 to-transparent px-2.5 pb-2 pt-6 text-[0.8rem] font-bold text-white">
                  {g.name}
                </span>
              </Cover>
            </button>
            <div className="flex items-center justify-between gap-2 px-2.5 py-2">
              <div className="min-w-0 font-mono text-[0.68rem] text-faint">
                <div>{bytes(g.sizeBytes, 0)}</div>
                <div>{g.playtimeMinutes ? minutes(g.playtimeMinutes) : 'nunca'} · {when(g.lastPlayed)}</div>
              </div>
              <button type="button" disabled={launching === g.key} onClick={() => launch(g)} aria-label={`Jogar ${g.name}`}
                className={cn('grid size-8 shrink-0 place-items-center rounded-full disabled:opacity-50',
                  g.source === 'STEAM' ? 'bg-[#66c0f4] text-[#0b1b2b]' : 'bg-ok text-[#06231a]')}>
                <Play className="size-3.5" />
              </button>
            </div>
          </div>
        ))}
      </div>
      {!shown.length && (
        <p className="py-10 text-center text-sm text-faint">
          {data ? 'Nada instalado aqui ainda.' : 'Carregando…'}
        </p>
      )}

      <GameDetailDialog game={open} onClose={() => setOpen(null)} onLaunch={launch} launching={launching === open?.key} />
    </>
  )
}
