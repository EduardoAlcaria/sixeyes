import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { usePolling } from '@/hooks/usePolling'
import { torrentApi } from '@/services/api'
import { between, bytes, cleanTitle, countryName, duration, flag, rate, when } from '@/lib/format'
import type { Torrent } from '@/types'
import { Bar, Cover, Fact, Panel, PieceMap, Pill, SourceBadge, StageTrace, stages } from './primitives'
import { TorrentActions } from './TorrentActions'
import type { TorrentHandlers } from './TorrentActions'

export function TorrentDetailDialog({ torrentId, onClose, handlers }: {
  torrentId: number | null
  onClose: () => void
  handlers: TorrentHandlers
}) {
  const { data } = usePolling(
    () => (torrentId ? torrentApi.details(torrentId) : Promise.resolve(null)),
    3000, [torrentId])

  const t: Torrent | undefined = data?.torrent
  const e = data?.engine
  const h = data?.history
  const c = data?.catalog

  return (
    <Dialog open={torrentId != null} onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto bg-popover p-5 sm:max-w-4xl">
        {!t ? (
          <p className="py-10 text-center text-sm text-muted-foreground">Carregando…</p>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex flex-wrap gap-4">
              <Cover className="aspect-[3/4] w-24 shrink-0 rounded-[calc(var(--radius)*0.6)]" seed={t.title ?? String(t.id)}
                sources={[c?.imageUrl]} />
              <div className="flex min-w-[220px] flex-1 flex-col gap-2">
                <DialogTitle className="text-lg font-extrabold">
                  <SourceBadge source="TORRENT" />{cleanTitle(t.title)}
                </DialogTitle>
                <p className="font-mono text-xs text-muted-foreground">
                  {t.size} · {t.progress.toFixed(1)}%{t.eta ? ` · ETA ${t.eta}` : ''} · {t.status}
                </p>
                <div className="max-w-sm"><StageTrace value={stages(t)} /></div>
                {t.installStatus === 'FAILED' && t.installMessage && (
                  <p className="rounded-md border border-bad/30 bg-bad/10 px-3 py-2 text-xs text-bad">{t.installMessage}</p>
                )}
                <TorrentActions torrent={t} handlers={{ ...handlers, open: undefined }} verbose />
              </div>
            </div>

            <Panel title="Download">
              <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4">
                <Fact label="Seeds / peers" value={`${t.seeds ?? '—'} / ${t.peers}`} />
                <Fact label="Availability" value={t.availability != null ? t.availability.toFixed(2) : '—'} />
                <Fact label="Download" value={rate(e?.downloadRate)} />
                <Fact label="Upload" value={rate(e?.uploadRate)} />
                <Fact label="Conexões" value={t.connections ?? '—'} />
                <Fact label="Nós DHT" value={e?.dhtNodes ?? '—'} />
                <Fact label="Desperdiçado" value={bytes(t.wasted)} />
                <Fact label="Ratio" value={t.ratio ?? '—'} />
                <Fact label="Baixado" value={bytes(t.totalDownloaded)} />
                <Fact label="Enviado" value={bytes(t.totalUploaded)} />
                <Fact label="Ativo há" value={duration(e?.activeSeconds)} />
                <Fact label="Semeando há" value={duration(e?.seedingSeconds)} />
                <Fact label="Peças" value={e?.numPieces ? `${e.numPieces} × ${bytes(e.pieceSize, 0)}` : '—'} />
                <Fact label="Vel. média" value={h?.speedSamples ? `${(h.speedSum! / h.speedSamples).toFixed(1)} MB/s` : '—'} />
                <Fact label="Pico" value={h?.peakSpeed ? `${h.peakSpeed.toFixed(1)} MB/s` : '—'} />
                <Fact label="Pasta" value={<span className="text-xs">{t.savePath ?? '—'}</span>} />
              </div>
            </Panel>

            <Panel title="Peças" note={e?.numPieces ? `${e.numPieces} peças · ${t.progress.toFixed(1)}%` : undefined}>
              <PieceMap pieces={e?.pieces ?? []} />
            </Panel>

            <div className="grid gap-3.5 md:grid-cols-2">
              <Panel title="Arquivos" note={e?.fileCount ? `${e.fileCount}` : undefined}>
                <div className="flex max-h-64 flex-col gap-2 overflow-y-auto pr-1">
                  {(e?.files ?? []).map(f => (
                    <div key={f.path} className="flex items-center gap-2.5 font-mono text-[0.75rem]">
                      <span className="min-w-0 flex-1 truncate" title={f.path}>{f.path.split(/[\\/]/).pop()}</span>
                      <span className="w-14 text-right text-faint">{bytes(f.size, 0)}</span>
                      <Bar className="w-24" value={f.progress * 100} tone={f.skipped ? 'faint' : f.progress >= 1 ? 'ok' : 'flow'} />
                      <span className="w-12 text-right text-muted-foreground">{f.skipped ? 'pulado' : `${Math.round(f.progress * 100)}%`}</span>
                    </div>
                  ))}
                  {!e?.files?.length && <p className="text-xs text-faint">Sem dados do engine agora.</p>}
                </div>
              </Panel>

              <Panel title="Trackers">
                <div className="flex flex-col">
                  {(e?.trackers ?? []).map(tr => (
                    <div key={tr.url} className="flex items-center justify-between gap-3 border-b border-dashed py-1.5 text-xs last:border-0">
                      <span className="min-w-0 truncate font-mono" title={tr.message || tr.url}>{tr.url.replace(/\/announce$/, '')}</span>
                      <span className="flex shrink-0 items-center gap-2">
                        {tr.seeds >= 0 && <span className="font-mono text-faint">{tr.seeds}S · {tr.leechers}L</span>}
                        {tr.working ? <Pill tone="ok">ok</Pill> : tr.updating ? <Pill tone="go">anunciando</Pill>
                          : tr.fails ? <Pill tone="bad">{tr.fails} falhas</Pill> : <Pill>—</Pill>}
                      </span>
                    </div>
                  ))}
                </div>
              </Panel>
            </div>

            <Panel title="Peers" note={e?.peerList?.length ? `top ${e.peerList.length} por velocidade` : undefined}>
              <div className="max-h-64 overflow-auto">
                <table className="w-full text-xs [&_td]:px-2 [&_th]:px-2">
                  <thead className="text-left text-[0.64rem] uppercase tracking-[0.05em] text-faint">
                    <tr><th className="py-1">País</th><th>Cliente</th><th className="text-right">↓</th><th className="text-right">↑</th><th className="text-right">Tem</th></tr>
                  </thead>
                  <tbody className="font-mono">
                    {(e?.peerList ?? []).map((p, i) => (
                      <tr key={`${p.ip}-${i}`} className="border-t">
                        <td className="py-1.5">{p.country ? `${flag(p.country)} ${countryName(p.country)}` : '—'}</td>
                        <td className="max-w-40 truncate">{p.client}{p.seed ? ' · seed' : ''}</td>
                        <td className="text-right">{rate(p.downSpeed)}</td>
                        <td className="text-right">{rate(p.upSpeed)}</td>
                        <td className="text-right">{Math.round(p.progress * 100)}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {!e?.peerList?.length && <p className="py-3 text-xs text-faint">Nenhum peer conectado.</p>}
              </div>
            </Panel>

            {h && (
              <Panel title="Linha do tempo">
                <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4">
                  <Fact label="Adicionado" value={when(h.addedAt)} />
                  <Fact label="Concluído" value={h.completedAt ? `${when(h.completedAt)} · ${duration(between(h.addedAt, h.completedAt))}` : '—'} />
                  <Fact label="Verificação" value={h.verifiedFiles != null ? `${h.verifiedFiles} ok · ${h.skippedFiles ?? 0} opc.` : '—'} />
                  <Fact label="Install · exit" value={h.installExitCode != null
                    ? `${duration(between(h.installStartedAt, h.installFinishedAt))} · ${h.installExitCode}` : (h.installStatus ?? '—')} />
                </div>
              </Panel>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
