import { useCallback, useMemo, useState } from 'react'
import { Search } from 'lucide-react'
import { Metric, Panel, SourceBadge } from '@/components/deck/primitives'
import { TorrentDetailDialog } from '@/components/deck/TorrentDetailDialog'
import { installLabel } from '@/components/deck/widgets'
import { usePolling } from '@/hooks/usePolling'
import { useTorrentHandlers } from '@/hooks/useTorrentHandlers'
import { insightApi } from '@/services/api'
import { between, bytes, cleanTitle, duration, when } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { HistoryEntry } from '@/types'

type Filter = 'all' | 'installed' | 'failed' | 'downloading' | 'removed'

const MATCH: Record<Filter, (h: HistoryEntry) => boolean> = {
  all: () => true,
  installed: h => h.installStatus === 'INSTALLED',
  failed: h => h.installStatus === 'FAILED' || h.status === 'ERROR',
  downloading: h => h.status === 'DOWNLOADING' || h.status === 'PAUSED',
  removed: h => h.status === 'REMOVED',
}

export function HistoryPage() {
  const { data } = usePolling(insightApi.history, 15_000)
  const [filter, setFilter] = useState<Filter>('all')
  const [q, setQ] = useState('')
  const [openId, setOpenId] = useState<number | null>(null)
  const handlers = useTorrentHandlers(useCallback((id: number) => setOpenId(id), []))
  const all = data ?? []

  const shown = useMemo(() => all
    .filter(MATCH[filter])
    .filter(h => !q.trim() || (h.title ?? '').toLowerCase().includes(q.trim().toLowerCase())), [all, filter, q])

  const finished = all.filter(h => h.completedAt)
  const avgDl = finished.length
    ? finished.reduce((s, h) => s + (between(h.addedAt, h.completedAt) ?? 0), 0) / finished.length : null
  const installs = all.filter(h => h.installStatus === 'INSTALLED' || h.installStatus === 'FAILED')
  const okRate = installs.length ? Math.round(installs.filter(h => h.installStatus === 'INSTALLED').length / installs.length * 100) : null

  return (
    <>
      <div className="flex flex-wrap items-baseline gap-3">
        <h1 className="text-2xl font-extrabold">Histórico</h1>
        <span className="text-sm text-muted-foreground">todo download e instalação, inclusive os removidos</span>
      </div>

      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
        <Metric label="Downloads" value={all.length} tone="muted" hint={`${finished.length} concluídos`} />
        <Metric label="Tempo médio" value={duration(avgDl)} tone="muted" hint="do início ao 100%" />
        <Metric label="Instalações OK" value={okRate != null ? `${okRate}%` : '—'} tone={okRate != null && okRate < 80 ? 'down' : 'up'}
          hint={`${installs.length} tentativas`} />
        <Metric label="Baixado no total" value={bytes(all.reduce((s, h) => s + (h.totalDownloaded ?? 0), 0))} tone="muted" />
      </div>

      <div className="flex flex-wrap items-center gap-2.5 rounded-[var(--radius)] border bg-card p-3">
        <label className="flex min-w-[180px] flex-1 items-center gap-2 rounded-lg border bg-background px-3 py-2 text-muted-foreground">
          <Search className="size-4" />
          <input value={q} onChange={e => setQ(e.target.value)} placeholder="Buscar no histórico…" aria-label="Buscar no histórico"
            className="w-full bg-transparent text-foreground outline-none" />
        </label>
        {([['all', 'Tudo'], ['installed', 'Instalados'], ['failed', 'Falhas'], ['downloading', 'Em andamento'], ['removed', 'Removidos']] as [Filter, string][]).map(([f, label]) => (
          <button key={f} type="button" onClick={() => setFilter(f)}
            className={cn('rounded-full border px-3 py-1 text-[0.76rem]', filter === f ? 'border-primary bg-primary/20 text-foreground' : 'text-muted-foreground')}>
            {label}
          </button>
        ))}
      </div>

      <Panel>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[860px] text-[0.82rem] [&_td]:px-2 [&_th]:px-2">
            <thead className="text-left text-[0.64rem] uppercase tracking-[0.05em] text-faint">
              <tr>
                <th className="py-1.5">Jogo</th><th>Tamanho</th><th className="text-right">Seeds máx</th>
                <th className="text-right">Vel. média</th><th className="text-right">Pico</th><th>Duração</th>
                <th>Verif.</th><th>Install · exit</th><th className="text-right">Ratio</th><th>Quando</th><th>Status</th>
              </tr>
            </thead>
            <tbody>
              {shown.map(h => {
                const ratio = h.totalDownloaded ? (h.totalUploaded ?? 0) / h.totalDownloaded : null
                return (
                  <tr key={h.id} onClick={() => h.status !== 'REMOVED' && setOpenId(h.torrentId)}
                    className={cn('border-t', h.status !== 'REMOVED' && 'cursor-pointer hover:bg-panel2')}>
                    <td className="max-w-64 truncate py-2"><SourceBadge source="TORRENT" />{cleanTitle(h.title)}</td>
                    <td className="font-mono">{h.size ?? '—'}</td>
                    <td className="text-right font-mono">{h.maxSeeds ?? '—'}</td>
                    <td className="text-right font-mono">{h.speedSamples ? `${(h.speedSum! / h.speedSamples).toFixed(1)} MB/s` : '—'}</td>
                    <td className="text-right font-mono">{h.peakSpeed ? `${h.peakSpeed.toFixed(1)}` : '—'}</td>
                    <td className="font-mono">{h.completedAt ? duration(between(h.addedAt, h.completedAt)) : '—'}</td>
                    <td className="font-mono">{h.verifiedFiles != null ? `${h.verifiedFiles}/${h.verifiedFiles + (h.skippedFiles ?? 0)}` : '—'}</td>
                    <td className={cn('font-mono', h.installExitCode != null && h.installExitCode !== 0 && 'text-bad')}>
                      {h.installExitCode != null
                        ? `${duration(between(h.installStartedAt, h.installFinishedAt))} · ${h.installExitCode}`
                        : h.installStatus === 'FAILED' ? 'não rodou' : '—'}
                    </td>
                    <td className="text-right font-mono">{ratio != null ? ratio.toFixed(2) : '—'}</td>
                    <td className="font-mono">{when(h.completedAt ?? h.addedAt)}</td>
                    <td>{installLabel(h)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {!shown.length && <p className="py-8 text-center text-xs text-faint">{data ? 'Nada aqui.' : 'Carregando…'}</p>}
        </div>
      </Panel>

      <TorrentDetailDialog torrentId={openId} onClose={() => setOpenId(null)} handlers={handlers} />
    </>
  )
}
