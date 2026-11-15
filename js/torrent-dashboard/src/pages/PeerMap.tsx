import { useState } from 'react'
import { Metric, Panel } from '@/components/deck/primitives'
import { WorldMap } from '@/components/deck/WorldMap'
import { usePolling } from '@/hooks/usePolling'
import { useSharedTorrents } from '@/hooks/useTorrents'
import { insightApi } from '@/services/api'
import { cleanTitle, countryName, flag, rate } from '@/lib/format'

export function PeerMapPage() {
  const { torrents } = useSharedTorrents()
  const [torrentId, setTorrentId] = useState<number | null>(null)
  const { data } = usePolling(() => insightApi.peers(torrentId ?? undefined), 8_000, [torrentId])
  const rows = Object.entries(data?.countries ?? {}).sort((a, b) => b[1].peers - a[1].peers)
  const fastest = [...rows].sort((a, b) => b[1].downSpeed - a[1].downSpeed)[0]
  const active = torrents.filter(t => t.status === 'Downloading' || t.status === 'Seeding')

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-extrabold">Peer Map</h1>
        <span className="text-sm text-muted-foreground">de onde vem o swarm</span>
        <select value={torrentId ?? ''} onChange={e => setTorrentId(e.target.value ? Number(e.target.value) : null)}
          aria-label="Filtrar por torrent"
          className="ml-auto max-w-72 rounded-lg border bg-card px-3 py-1.5 text-[0.8rem]">
          <option value="">Todos os torrents</option>
          {active.map(t => <option key={t.id} value={t.id}>{cleanTitle(t.title)}</option>)}
        </select>
      </div>

      {data && !data.geoAvailable && (
        <p className="rounded-md border border-warn/30 bg-warn/10 px-3 py-2 text-sm text-warn">
          Base GeoIP ausente. Rode <code className="font-mono">scripts/geoip-update.ps1</code> uma vez.
        </p>
      )}

      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
        <Metric label="Peers" value={data?.total ?? '—'} tone="muted" hint={data?.unknown ? `${data.unknown} sem país` : undefined} />
        <Metric label="Países" value={rows.length} tone="muted" />
        <Metric label="Maior swarm" value={rows[0] ? `${flag(rows[0][0])} ${rows[0][1].peers}` : '—'} tone="muted"
          hint={rows[0] ? countryName(rows[0][0]) : undefined} />
        <Metric label="Mais rápido" value={fastest ? `${flag(fastest[0])} ${rate(fastest[1].downSpeed)}` : '—'} tone="muted"
          hint={fastest ? countryName(fastest[0]) : undefined} />
      </div>

      <Panel>
        <WorldMap countries={data?.countries ?? {}} className="h-auto w-full" />
        <p className="mt-1 text-right text-[0.62rem] text-faint">IP Geolocation by DB-IP</p>
      </Panel>

      <Panel title="Por país">
        <div className="overflow-x-auto">
          <table className="w-full text-[0.84rem] [&_td]:px-2 [&_th]:px-2">
            <thead className="text-left text-[0.64rem] uppercase tracking-[0.05em] text-faint">
              <tr><th className="py-1.5">País</th><th className="text-right">Peers</th><th className="text-right">Download</th><th className="text-right">Upload</th></tr>
            </thead>
            <tbody className="font-mono">
              {rows.map(([code, s]) => (
                <tr key={code} className="border-t">
                  <td className="py-2 font-sans">{flag(code)} {countryName(code)}</td>
                  <td className="text-right">{s.peers}</td>
                  <td className="text-right text-cyan">{rate(s.downSpeed)}</td>
                  <td className="text-right text-violet">{rate(s.upSpeed)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {!rows.length && <p className="py-6 text-center text-xs text-faint">Nenhum peer conectado agora.</p>}
        </div>
      </Panel>
    </>
  )
}
