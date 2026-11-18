import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Download, Play, Search } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { AddTorrentDialog } from '@/components/torrents/AddTorrentDialog'
import { Bar, Cover, Metric, Panel, Pill, Ring, SourceBadge, Stat, StageTrace, stages, steamArt } from '@/components/deck/primitives'
import { GameDetailDialog } from '@/components/deck/GameDetailDialog'
import { TorrentActions } from '@/components/deck/TorrentActions'
import { TorrentDetailDialog } from '@/components/deck/TorrentDetailDialog'
import { usePolling } from '@/hooks/usePolling'
import { useSharedTorrents } from '@/hooks/useTorrents'
import { useTorrentHandlers } from '@/hooks/useTorrentHandlers'
import { insightApi } from '@/services/api'
import { bytes, cleanTitle, minutes, speed } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Game, Torrent } from '@/types'

type Filter = 'all' | 'downloading' | 'seeding' | 'paused' | 'installed' | 'steam'
type Sort = 'progress' | 'name' | 'speed' | 'added'

const FILTERS: [Filter, string][] = [
  ['all', 'All'], ['downloading', 'Downloading'], ['seeding', 'Seeding'], ['paused', 'Paused'],
  ['installed', 'Installed'], ['steam', 'Steam'],
]

/** Last ~2 minutes of each torrent's speed, kept client-side for the card sparklines. */
function useSpeedHistory(torrents: Torrent[]) {
  const store = useRef(new Map<number, number[]>())
  useEffect(() => {
    for (const t of torrents) {
      const v = t.status === 'Seeding' ? speed(t.uploadSpeed) : speed(t.downloadSpeed)
      const list = store.current.get(t.id) ?? []
      list.push(v)
      if (list.length > 24) list.shift()
      store.current.set(t.id, list)
    }
  }, [torrents])
  return store.current
}

function Sparkline({ values, tone }: { values: number[]; tone: string }) {
  if (values.length < 2) return <div className="h-7" />
  const max = Math.max(...values, 0.01)
  const pts = values.map((v, i) => `${(i / (values.length - 1)) * 100},${28 - (v / max) * 24}`).join(' ')
  return (
    <svg viewBox="0 0 100 30" preserveAspectRatio="none" className="h-7 w-full">
      <polyline points={pts} fill="none" stroke={tone} strokeWidth={1.8} vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

export function LibraryPage() {
  const shared = useSharedTorrents()
  const { torrents } = shared
  const [openId, setOpenId] = useState<number | null>(null)
  const handlers = useTorrentHandlers(useCallback((id: number) => setOpenId(id), []))
  const history = useSpeedHistory(torrents)
  const [filter, setFilter] = useState<Filter>('all')
  const [sort, setSort] = useState<Sort>('progress')
  const [q, setQ] = useState('')
  const games = usePolling(insightApi.games, 60_000).data
  const ownedAll = usePolling(insightApi.steamOwned, 60_000).data
  const owned = useMemo(() => {
    const text = q.trim().toLowerCase()
    return (ownedAll ?? []).filter(g => !text || g.name.toLowerCase().includes(text))
      .sort((a, b) => sort === 'name' ? a.name.localeCompare(b.name) : b.playtimeMinutes - a.playtimeMinutes)
  }, [ownedAll, q, sort])
  const [openGame, setOpenGame] = useState<Game | null>(null)
  const [launching, setLaunching] = useState<string | null>(null)
  const steam = useMemo(() => {
    const text = q.trim().toLowerCase()
    return (games?.games ?? [])
      .filter(g => g.source === 'STEAM' && (!text || g.name.toLowerCase().includes(text)))
      .sort((a, b) => sort === 'name' ? a.name.localeCompare(b.name)
        : sort === 'added' ? (b.lastPlayed ?? '').localeCompare(a.lastPlayed ?? '')
        : b.playtimeMinutes - a.playtimeMinutes)
  }, [games, q, sort])

  const done = (t: Torrent) => t.progress >= 100 || t.status === 'Seeding'
  const shown = useMemo(() => {
    const text = q.trim().toLowerCase()
    return torrents
      .filter(t => !text || (t.title ?? '').toLowerCase().includes(text))
      .filter(t => {
        switch (filter) {
          case 'downloading': return t.status === 'Downloading' && !done(t)
          case 'seeding': return t.status === 'Seeding'
          case 'paused': return t.status === 'Paused' || t.status === 'Stopped'
          case 'installed': return t.installStatus === 'INSTALLED'
          case 'steam': return false
          default: return true
        }
      })
      .sort((a, b) => {
        switch (sort) {
          case 'name': return cleanTitle(a.title).localeCompare(cleanTitle(b.title))
          case 'speed': return speed(b.downloadSpeed) - speed(a.downloadSpeed)
          case 'added': return b.id - a.id
          default: return a.progress - b.progress
        }
      })
  }, [torrents, filter, sort, q])

  const downloading = torrents.filter(t => t.status === 'Downloading' && !done(t))
  const totalDown = torrents.reduce((s, t) => s + (t.totalDownloaded ?? 0), 0)
  const totalUp = torrents.reduce((s, t) => s + (t.totalUploaded ?? 0), 0)

  const installSteam = async (appId: string, name: string) => {
    try {
      await insightApi.steamInstall(appId)
      toast.success(`Steam is opening the install for ${name}`)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Could not start the install')
    }
  }

  const launch = async (g: Game) => {
    setLaunching(g.key)
    try {
      await insightApi.launch(g.key)
      toast.success(`Opening ${g.name}`)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Could not open')
    } finally {
      setLaunching(null)
    }
  }

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-extrabold">Library</h1>
        <span className="text-sm text-muted-foreground">{torrents.length} torrents · {steam.length} Steam</span>
        <div className="ml-auto">
          <AddTorrentDialog onAddMagnet={shared.addTorrent} onAddFile={shared.addTorrentFile} loading={shared.loading} />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
        <Metric label="Downloading" value={downloading.length} tone="up"
          hint={`↓ ${downloading.reduce((s, t) => s + speed(t.downloadSpeed), 0).toFixed(1)} MB/s`} />
        <Metric label="Seeding" value={torrents.filter(t => t.status === 'Seeding').length} tone="muted"
          hint={`↑ ${torrents.reduce((s, t) => s + speed(t.uploadSpeed), 0).toFixed(1)} MB/s`} />
        <Metric label="Paused" value={torrents.filter(t => t.status === 'Paused' || t.status === 'Stopped').length} tone="muted" />
        <Metric label="Downloaded (total)" value={bytes(totalDown)} tone="muted" />
        <Metric label="Queue ratio" value={totalDown ? (totalUp / totalDown).toFixed(2) : '—'} tone="muted" hint={`uploaded ${bytes(totalUp)}`} />
      </div>

      <div className="flex flex-wrap items-center gap-2.5 rounded-[var(--radius)] border bg-card p-3">
        <label className="flex min-w-[180px] flex-1 items-center gap-2 rounded-lg border bg-background px-3 py-2 text-muted-foreground">
          <Search className="size-4" />
          <input value={q} onChange={e => setQ(e.target.value)} placeholder="Filter torrents and games…" aria-label="Filtrar"
            className="w-full bg-transparent text-foreground outline-none" />
        </label>
        <div className="flex flex-wrap gap-1.5">
          {FILTERS.map(([f, label]) => (
            <button key={f} type="button" onClick={() => setFilter(f)}
              className={cn('rounded-full border px-3 py-1 text-[0.76rem]', filter === f ? 'border-primary bg-primary/20 text-foreground' : 'text-muted-foreground')}>
              {label}
            </button>
          ))}
        </div>
        <select value={sort} onChange={e => setSort(e.target.value as Sort)} aria-label="Sort"
          className="rounded-full border bg-card px-3 py-1 text-[0.76rem] text-muted-foreground">
          <option value="progress">Sort: progress</option>
          <option value="speed">Sort: speed</option>
          <option value="name">Sort: name</option>
          <option value="added">Sort: newest</option>
        </select>
      </div>

      {filter !== 'steam' && (
        <div className="grid gap-3.5 lg:grid-cols-2">
          {shown.map(t => {
            const verifying = t.installStatus === 'VERIFYING'
            const installing = t.installStatus === 'INSTALLING'
            // While the repack is being verified or installed, that work replaces the download bar.
            const busy = verifying || installing
            return (
            <Panel key={t.id} className="flex flex-col gap-2.5">
              <div className="flex items-center gap-3">
                <Cover className="aspect-[2/3] w-12 shrink-0 rounded-[8px]" seed={t.title ?? String(t.id)} sources={[t.coverUrl]} />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[0.92rem] font-bold"><SourceBadge source="TORRENT" />{cleanTitle(t.title)}</div>
                  <div className="truncate font-mono text-[0.74rem] text-muted-foreground">
                    {busy
                      ? `${verifying ? 'Verifying files' : 'Installing'}${t.installProgress != null ? ` · ${t.installProgress}%` : ''}${installing && t.installEta ? ` · ${t.installEta} left` : ''}`
                      : `${t.size} · ${t.progress.toFixed(1)}%${t.eta ? ` · ETA ${t.eta}` : ''}${t.errorMessage ? ` · ${t.errorMessage}` : ''}`}
                  </div>
                </div>
                {busy ? <Ring value={verifying ? t.installProgress ?? 0 : t.installProgress}>{t.installProgress != null ? `${t.installProgress}%` : ''}</Ring>
                  : t.status === 'Error' ? <Pill tone="bad">Error</Pill>
                  : t.installStatus === 'INSTALLED' ? <Pill tone="ok">Installed</Pill>
                  : t.installStatus === 'FAILED' ? <Pill tone="bad">Install failed</Pill>
                  : t.status === 'Seeding' ? <Pill tone="ok">Seeding</Pill>
                  : t.status === 'Downloading' ? <Ring value={t.progress}>{Math.floor(t.progress)}%</Ring>
                  : <Pill>{t.status === 'Paused' ? 'Paused' : 'Stopped'}</Pill>}
              </div>
              {busy
                ? <Bar value={t.installProgress ?? 0} indeterminate={installing && t.installProgress == null} tone="flow" />
                : <Bar value={t.progress} tone={t.status === 'Error' ? 'bad' : done(t) ? 'ok' : t.status === 'Downloading' ? 'flow' : 'faint'} />}
              <StageTrace value={stages(t)} />
              {t.installStatus === 'FAILED' && t.installMessage && (
                <p className="rounded-md border border-bad/30 bg-bad/10 px-3 py-1.5 text-[0.76rem] text-bad">{t.installMessage}</p>
              )}
              <div className="flex flex-wrap gap-1.5">
                <Stat k="↓" v={t.downloadSpeed} />
                <Stat k="↑" v={t.uploadSpeed} />
                <Stat k="seeds" v={t.seeds ?? '—'} />
                <Stat k="peers" v={t.peers} />
                <Stat k="avail" v={t.availability != null ? t.availability.toFixed(2) : '—'} />
                <Stat k="ratio" v={t.ratio ?? '—'} />
              </div>
              <Sparkline values={history.get(t.id) ?? []} tone={t.status === 'Seeding' ? 'var(--violet)' : 'var(--cyan)'} />
              <div className="flex justify-end"><TorrentActions torrent={t} handlers={handlers} /></div>
            </Panel>
            )
          })}
          {!shown.length && <p className="py-10 text-center text-sm text-faint lg:col-span-2">Nothing here.</p>}
        </div>
      )}

      {(filter === 'all' || filter === 'steam') && steam.length > 0 && (
        <>
          {filter === 'all' && <h2 className="mt-2 text-[0.74rem] font-bold uppercase tracking-[0.08em] text-muted-foreground">Steam</h2>}
          <div className="grid gap-3.5 lg:grid-cols-2">
            {steam.map(g => (
              <Panel key={g.key} className="flex items-center gap-3">
                <button type="button" onClick={() => setOpenGame(g)} aria-label={`Details of ${g.name}`}
                  className="flex min-w-0 flex-1 items-center gap-3 text-left">
                <Cover className="size-11 shrink-0 rounded-[10px]" seed={g.name} sources={steamArt(g.key.slice(6), false)} />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[0.92rem] font-bold"><SourceBadge source="STEAM" />{g.name}</div>
                  <div className="font-mono text-[0.74rem] text-muted-foreground">{bytes(g.sizeBytes)} · {minutes(g.playtimeMinutes)} played</div>
                </div>
                </button>
                <button type="button" onClick={() => launch(g)}
                  className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-[#66c0f4] px-3 text-xs font-bold text-[#0b1b2b]">
                  <Play className="size-3.5" /> Play
                </button>
              </Panel>
            ))}
          </div>
        </>
      )}

      <GameDetailDialog game={openGame} onClose={() => setOpenGame(null)} onLaunch={launch} launching={launching === openGame?.key} />
      {filter === 'steam' && owned.length > 0 && (
        <>
          <h2 className="mt-2 text-[0.74rem] font-bold uppercase tracking-[0.08em] text-muted-foreground">
            In your Steam library · not installed ({owned.length})
          </h2>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
            {owned.map(g => (
              <div key={g.appId} className="overflow-hidden rounded-[calc(var(--radius)*0.6)] border bg-card">
                <Cover className="aspect-[2/3] w-full" seed={g.name} sources={steamArt(g.appId)}>
                  <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 to-transparent px-2.5 pb-2 pt-6 text-[0.8rem] font-bold text-white">
                    {g.name}
                  </span>
                </Cover>
                <div className="flex items-center justify-between gap-2 px-2.5 py-2">
                  <span className="font-mono text-[0.68rem] text-faint">{g.playtimeMinutes ? minutes(g.playtimeMinutes) : 'never played'}</span>
                  <div className="flex shrink-0 gap-1.5">
                    <button type="button" onClick={() => installSteam(g.appId, g.name)} aria-label={`Install ${g.name} with Steam`}
                      title="Install with Steam (opens Steam's install dialog on the PC)"
                      className="grid size-8 place-items-center rounded-full bg-[#66c0f4] text-[#0b1b2b]">
                      <Download className="size-3.5" />
                    </button>
                    <Link to={`/catalog?q=${encodeURIComponent(g.name)}`} aria-label={`Find ${g.name} in the catalog`}
                      title="Find a repack in the catalog"
                      className="grid size-8 place-items-center rounded-full bg-primary text-primary-foreground">
                      <Search className="size-3.5" />
                    </Link>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <TorrentDetailDialog torrentId={openId} onClose={() => setOpenId(null)} handlers={handlers} />
    </>
  )
}
