import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { usePolling } from '@/hooks/usePolling'
import { insightApi } from '@/services/api'
import { bytes, cleanTitle, countryName, duration, flag, rate, speed, when } from '@/lib/format'
import { cn } from '@/lib/utils'
import type {
  ActivityDay, AppEvent, GameLibrary, Genre, HistoryEntry, MetricsSummary, NetRange, PeerMap, Torrent, Vitals,
} from '@/types'
import { Bar, Metric, Panel, Pill, SourceBadge, StageTrace, stages } from './primitives'
import { WorldMap } from './WorldMap'

const isDone = (t: Torrent) => t.progress >= 100 || t.status === 'Seeding'

// --- KPIs -------------------------------------------------------------------

export function KpiWidget({ torrents, summary, vitals, library }: {
  torrents: Torrent[]
  summary: MetricsSummary | null
  vitals: Vitals | null
  library: GameLibrary | null
}) {
  const down = torrents.filter(t => t.status === 'Downloading').reduce((s, t) => s + speed(t.downloadSpeed), 0)
  const downloading = torrents.filter(t => t.status === 'Downloading' && !isDone(t)).length
  const seeding = torrents.filter(t => t.status === 'Seeding').length
  const downloadDrive = vitals?.drives.find(d => torrents[0]?.savePath?.toUpperCase().startsWith(d.drive.toUpperCase()))
    ?? vitals?.drives[0]
  const ram = vitals?.ramTotal ? Math.round((vitals.ramUsed! / vitals.ramTotal) * 100) : null
  return (
    <Panel title="Resumo">
      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 xl:grid-cols-6">
        <Metric label="Download" value={down.toFixed(1)} unit="MB/s" tone="cyan" hint={summary ? `pico hoje ${summary.peakToday}` : undefined} />
        <Metric label="Baixando" value={downloading} hint={`${torrents.length} na fila`} tone="muted" />
        <Metric label="Semeando" value={seeding} hint={summary?.globalRatio != null ? `ratio ${summary.globalRatio}` : undefined} tone="muted" />
        <Metric label="Na estante" value={library?.games.length ?? '—'} hint={library ? `${library.games.filter(g => g.source === 'STEAM').length} Steam` : undefined} tone="muted" />
        <Metric label={`Livre ${downloadDrive?.drive ?? ''}`} value={downloadDrive ? Math.round(downloadDrive.free / 2 ** 30) : '—'} unit="GB"
          hint={summary?.diskFullDays != null ? `cheio em ~${summary.diskFullDays}d` : 'sem previsão'} tone={summary?.diskFullDays != null && summary.diskFullDays < 7 ? 'down' : 'muted'} />
        <Metric label="CPU / RAM" value={`${Math.round(vitals?.cpu ?? 0)}%`} unit={ram != null ? `/ ${ram}%` : undefined}
          hint={vitals ? `uptime ${duration(vitals.systemUptime)}` : undefined} tone="muted" />
      </div>
    </Panel>
  )
}

export function NetMetricsWidget({ summary, peers, vitals }: { summary: MetricsSummary | null; peers: PeerMap | null; vitals: Vitals | null }) {
  return (
    <Panel title="Métricas de rede">
      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 xl:grid-cols-6">
        <Metric label="Baixado hoje" value={bytes(summary?.downloadedToday)} tone="muted"
          hint={summary ? `média ${bytes(summary.avgDailyDown, 0)}/dia` : undefined} />
        <Metric label="Enviado hoje" value={bytes(summary?.uploadedToday)} tone="muted" />
        <Metric label="Peers" value={peers?.total ?? '—'} hint={peers ? `${Object.keys(peers.countries).length} países` : undefined} tone="muted" />
        <Metric label="Nós DHT" value={vitals?.dhtNodes ?? '—'} tone="muted" />
        <Metric label="Ratio global" value={summary?.globalRatio ?? '—'} tone="muted" />
        <Metric label="Total enviado" value={bytes(summary?.allTimeUploaded)} hint={`baixado ${bytes(summary?.allTimeDownloaded)}`} tone="muted" />
      </div>
    </Panel>
  )
}

// --- Throughput -------------------------------------------------------------

export function ThroughputWidget() {
  const [range, setRange] = useState<NetRange>('24h')
  const { data } = usePolling(() => insightApi.network(range), range === '24h' ? 15_000 : 60_000, [range])
  const points = useMemo(() => (data?.points ?? []).map(p => ({
    ...p,
    label: range === '24h'
      ? new Date(p.t).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
      : new Date(p.t).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }),
  })), [data, range])
  return (
    <Panel title="Throughput"
      note={data?.peakDown ? `pico ${data.peakDown} MB/s · ${when(data.peakAt)}` : 'sem tráfego ainda'}
      actions={(['24h', '7d', '30d'] as NetRange[]).map(r => (
        <button key={r} type="button" onClick={() => setRange(r)}
          className={cn('rounded-full border px-2 py-0.5 text-[0.66rem]', r === range ? 'border-primary bg-primary/20 text-foreground' : 'text-muted-foreground')}>
          {r}
        </button>
      ))}>
      <div className="h-36">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={points} margin={{ top: 4, right: 4, bottom: 0, left: -18 }}>
            <defs>
              <linearGradient id="dl" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stopColor="var(--cyan)" stopOpacity={0.4} />
                <stop offset="1" stopColor="var(--cyan)" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="var(--border)" strokeDasharray="2 4" vertical={false} />
            <XAxis dataKey="label" tick={{ fill: 'var(--faint)', fontSize: 10 }} tickLine={false} axisLine={false} minTickGap={36} />
            <YAxis tick={{ fill: 'var(--faint)', fontSize: 10 }} tickLine={false} axisLine={false} width={40} />
            <Tooltip contentStyle={{ background: 'var(--popover)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 12 }}
              formatter={(v, n) => [`${v ?? 0} MB/s`, n === 'down' ? 'Download' : 'Upload']} />
            <Area type="monotone" dataKey="down" stroke="var(--cyan)" strokeWidth={1.6} fill="url(#dl)" isAnimationActive={false} />
            <Area type="monotone" dataKey="up" stroke="var(--violet)" strokeWidth={1.2} fill="transparent" isAnimationActive={false} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <div className="mt-2 flex gap-4 font-mono text-[0.72rem] text-muted-foreground">
        <span><b className="text-cyan">↓</b> {bytes(data?.downBytes)}</span>
        <span><b className="text-violet">↑</b> {bytes(data?.upBytes)}</span>
      </div>
    </Panel>
  )
}

// --- Swarm ------------------------------------------------------------------

export function SwarmWidget({ torrents }: { torrents: Torrent[] }) {
  const active = torrents.filter(t => t.status === 'Downloading' || t.status === 'Seeding')
  const seeds = active.reduce((s, t) => s + (t.seeds ?? 0), 0)
  const peers = active.reduce((s, t) => s + t.peers, 0)
  const conns = active.reduce((s, t) => s + (t.connections ?? 0), 0)
  const avail = active.filter(t => t.availability != null)
  const worst = avail.length ? Math.min(...avail.map(t => t.availability!)) : null
  return (
    <Panel title="Saúde do swarm" note={worst != null ? `pior availability ${worst.toFixed(2)}` : undefined}>
      <div className="flex items-center gap-4">
        <svg viewBox="0 0 120 120" className="aspect-square w-full max-w-[160px] shrink-0">
          {[54, 36, 18].map(r => <circle key={r} cx="60" cy="60" r={r} fill="none" stroke="var(--border)" />)}
          <line x1="60" y1="6" x2="60" y2="114" stroke="var(--border)" />
          <line x1="6" y1="60" x2="114" y2="60" stroke="var(--border)" />
          <circle cx="60" cy="60" r="3" fill="var(--cyan)" />
          {active.map((t, i) => {
            // Healthier swarms sit closer to the centre: availability 3+ hugs it, under 1 drifts out.
            const a = t.availability ?? 0
            const dist = 54 - Math.min(a, 3) / 3 * 40
            const ang = (i / Math.max(active.length, 1)) * Math.PI * 2 - Math.PI / 2
            return (
              <circle key={t.id} cx={60 + Math.cos(ang) * dist} cy={60 + Math.sin(ang) * dist} r={3.2}
                fill={a < 1 ? 'var(--bad)' : t.status === 'Seeding' ? 'var(--violet)' : 'var(--cyan)'}>
                <title>{`${cleanTitle(t.title)} · availability ${a.toFixed(2)}`}</title>
              </circle>
            )
          })}
        </svg>
        <div className="min-w-0 flex-1 text-[0.82rem]">
          {[['Seeds', seeds, 'text-cyan'], ['Peers', peers, 'text-violet'], ['Conexões', conns, ''],
            ['Availability mín.', worst != null ? worst.toFixed(2) : '—', worst != null && worst < 1 ? 'text-bad' : 'text-ok']]
            .map(([k, v, c]) => (
              <div key={k as string} className="flex justify-between border-b border-dashed py-1.5 last:border-0">
                <span className="text-muted-foreground">{k}</span><b className={cn('font-mono', c as string)}>{v}</b>
              </div>
            ))}
          <p className="mt-2 text-[0.7rem] text-faint">Availability &lt; 1 = falta peça no swarm; o download pode travar.</p>
        </div>
      </div>
    </Panel>
  )
}

// --- Peer map ---------------------------------------------------------------

export function MapWidget({ peers, compact }: { peers: PeerMap | null; compact?: boolean }) {
  const top = Object.entries(peers?.countries ?? {}).sort((a, b) => b[1].peers - a[1].peers).slice(0, compact ? 5 : 8)
  return (
    <Panel title="Mapa de peers" note={peers ? `${peers.total} peers · ${Object.keys(peers.countries).length} países` : undefined}
      actions={compact && <Link to="/map" className="text-[0.7rem] text-cyan">abrir mapa →</Link>}>
      {peers && !peers.geoAvailable && (
        <p className="mb-2 rounded-md bg-warn/10 px-3 py-2 text-xs text-warn">Base GeoIP ausente — rode scripts/geoip-update.ps1.</p>
      )}
      <div className="grid items-center gap-4 md:grid-cols-[1fr_200px]">
        <WorldMap countries={peers?.countries ?? {}} className="h-auto w-full" />
        <div className="text-[0.8rem]">
          {top.map(([code, s]) => (
            <div key={code} className="flex justify-between border-b border-dashed py-1.5 last:border-0">
              <span className="truncate">{flag(code)} {countryName(code)}</span><b className="font-mono">{s.peers}</b>
            </div>
          ))}
          {!top.length && <p className="text-xs text-faint">Nenhum peer conectado agora.</p>}
        </div>
      </div>
      <p className="mt-2 text-right text-[0.62rem] text-faint">IP Geolocation by DB-IP</p>
    </Panel>
  )
}

// --- Recent history ---------------------------------------------------------

export function installLabel(h: HistoryEntry) {
  if (h.installStatus === 'INSTALLED') return <Pill tone="ok">Instalado</Pill>
  if (h.installStatus === 'FAILED') return <Pill tone="bad">Falhou</Pill>
  if (h.installStatus === 'VERIFYING' || h.installStatus === 'INSTALLING') return <Pill tone="go">Instalando</Pill>
  if (h.status === 'REMOVED') return <Pill>Removido</Pill>
  if (h.status === 'COMPLETED') return <Pill tone="ok">Baixado</Pill>
  if (h.status === 'ERROR') return <Pill tone="bad">Erro</Pill>
  return <Pill tone="go">Baixando</Pill>
}

export function RecentWidget({ history, onOpen }: { history: HistoryEntry[] | null; onOpen: (torrentId: number) => void }) {
  return (
    <Panel title="Downloads recentes" actions={<Link to="/history" className="text-[0.7rem] text-cyan">histórico completo →</Link>}>
      <div className="overflow-x-auto">
        <table className="w-full text-[0.82rem] [&_td]:px-2 [&_th]:px-2">
          <thead className="text-left text-[0.64rem] uppercase tracking-[0.05em] text-faint">
            <tr><th className="py-1.5">Jogo</th><th>Tam.</th><th>Verif.</th><th>Quando</th><th>Status</th></tr>
          </thead>
          <tbody>
            {(history ?? []).slice(0, 6).map(h => (
              <tr key={h.id} onClick={() => h.status !== 'REMOVED' && onOpen(h.torrentId)}
                className="cursor-pointer border-t hover:bg-panel2">
                <td className="max-w-56 truncate py-2"><SourceBadge source="TORRENT" />{cleanTitle(h.title)}</td>
                <td className="font-mono">{h.size ?? '—'}</td>
                <td className="font-mono">{h.verifiedFiles != null ? `${h.verifiedFiles}/${h.verifiedFiles + (h.skippedFiles ?? 0)}` : '—'}</td>
                <td className="font-mono">{when(h.completedAt ?? h.addedAt)}</td>
                <td>{installLabel(h)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!history?.length && <p className="py-4 text-center text-xs text-faint">Nada baixado ainda.</p>}
      </div>
    </Panel>
  )
}

// --- Disks ------------------------------------------------------------------

export function DisksWidget({ vitals }: { vitals: Vitals | null }) {
  return (
    <Panel title="Discos" note="espaço · leitura/escrita · saúde">
      <div className="flex flex-col">
        {(vitals?.drives ?? []).map(d => {
          const pct = d.total ? (d.used / d.total) * 100 : 0
          return (
            <div key={d.drive} className="flex flex-col gap-2 border-t py-2.5 first:border-0 first:pt-0">
              <div className="flex items-center gap-2 text-[0.86rem]">
                <b>{d.drive}\</b>
                <span className="truncate text-muted-foreground">{d.model || d.fs}{d.mediaType && d.mediaType !== 'Unspecified' ? ` · ${d.mediaType}` : ''}</span>
                <span className="ml-auto shrink-0 font-mono text-[0.76rem] text-muted-foreground">{bytes(d.free, 0)} livre / {bytes(d.total, 0)}</span>
              </div>
              <Bar value={pct} tone={pct > 90 ? 'hot' : 'flow'} />
              <div className="flex flex-wrap gap-x-4 gap-y-1 font-mono text-[0.72rem] text-muted-foreground">
                <span className="text-cyan">R <b className="text-foreground">{rate(d.readBps)}</b></span>
                <span className="text-violet">W <b className="text-foreground">{rate(d.writeBps)}</b></span>
                {d.temperature != null && <span>temp <b className="text-foreground">{d.temperature}°C</b></span>}
                {d.wear != null && <span>desgaste <b className="text-foreground">{d.wear}%</b></span>}
                {d.health && <span>saúde <b className={d.health === 'Healthy' ? 'text-ok' : 'text-warn'}>{d.health === 'Healthy' ? 'OK' : d.health}</b></span>}
              </div>
            </div>
          )
        })}
        {!vitals && <p className="text-xs text-faint">Carregando…</p>}
      </div>
    </Panel>
  )
}

// --- Install pipeline -------------------------------------------------------

export function PipelineWidget({ torrents, onOpen }: { torrents: Torrent[]; onOpen: (id: number) => void }) {
  const items = torrents.filter(t => !isDone(t) || ['REQUESTED', 'VERIFYING', 'INSTALLING', 'FAILED'].includes(t.installStatus))
  return (
    <Panel title="Pipeline de instalação" note="baixar → verificar → instalar">
      <div className="flex flex-col">
        {items.slice(0, 5).map(t => (
          <button type="button" key={t.id} onClick={() => onOpen(t.id)}
            className="flex flex-col gap-2 border-t py-3 text-left first:border-0 first:pt-0">
            <div className="flex items-center gap-2.5">
              <div className="min-w-0 flex-1">
                <div className="truncate text-[0.9rem] font-bold">{cleanTitle(t.title)}</div>
                <div className="font-mono text-[0.74rem] text-muted-foreground">
                  {isDone(t) ? (t.installMessage ?? 'Na fila de instalação') : `${t.progress.toFixed(1)}% · ${t.downloadSpeed}${t.eta ? ` · ETA ${t.eta}` : ''}`}
                </div>
              </div>
              {t.installStatus === 'FAILED' ? <Pill tone="bad">Falhou</Pill> : <Pill tone="go">{isDone(t) ? 'Instalação' : `${Math.floor(t.progress)}%`}</Pill>}
            </div>
            {!isDone(t) && <Bar value={t.progress} />}
            <StageTrace value={stages(t)} />
          </button>
        ))}
        {!items.length && <p className="text-xs text-faint">Nada em andamento.</p>}
      </div>
    </Panel>
  )
}

// --- Events -----------------------------------------------------------------

const DOT: Record<AppEvent['level'], string> = { INFO: 'bg-cyan', OK: 'bg-ok', WARN: 'bg-warn', ERROR: 'bg-bad' }

export function EventsWidget({ events }: { events: AppEvent[] | null }) {
  return (
    <Panel title="Eventos ao vivo">
      <div className="flex max-h-72 flex-col overflow-y-auto">
        {(events ?? []).map(e => (
          <div key={e.id} className="flex items-start gap-2.5 border-b py-2 text-[0.82rem] last:border-0">
            <span className="w-11 shrink-0 font-mono text-[0.7rem] text-faint">{when(e.at)}</span>
            <span className={cn('mt-1.5 size-1.5 shrink-0 rounded-full', DOT[e.level])} />
            <span className="min-w-0">{e.message}</span>
          </div>
        ))}
        {!events?.length && <p className="text-xs text-faint">Sem eventos ainda.</p>}
      </div>
    </Panel>
  )
}

// --- Vitals -----------------------------------------------------------------

export function VitalsWidget({ vitals }: { vitals: Vitals | null }) {
  const ramPct = vitals?.ramTotal ? (vitals.ramUsed! / vitals.ramTotal) * 100 : 0
  const rows: [string, number, string][] = [
    ['CPU', vitals?.cpu ?? 0, `${Math.round(vitals?.cpu ?? 0)}%${vitals?.cpuCount ? ` · ${vitals.cpuCount} threads` : ''}`],
    ['RAM', ramPct, `${bytes(vitals?.ramUsed, 1)} / ${bytes(vitals?.ramTotal, 0)}`],
  ]
  if (vitals?.gpu) rows.push(['GPU', vitals.gpu.usage, `${vitals.gpu.usage}% · ${vitals.gpu.temperature}°C`])
  return (
    <Panel title="Vitais do PC" note={vitals ? `ligado há ${duration(vitals.systemUptime)}` : undefined}>
      {rows.map(([k, v, label]) => (
        <div key={k} className="flex items-center gap-2.5 border-b border-dashed py-1.5 text-[0.8rem] last:border-0">
          <span className="w-12 text-muted-foreground">{k}</span>
          <Bar className="flex-1" value={v} tone={v > 90 ? 'hot' : 'flow'} />
          <b className="min-w-28 text-right font-mono text-[0.76rem] font-semibold">{label}</b>
        </div>
      ))}
      <div className="flex items-center gap-2.5 py-1.5 text-[0.8rem]">
        <span className="w-12 text-muted-foreground">Rede</span>
        <span className="flex-1 font-mono text-[0.76rem]"><span className="text-cyan">↓ {rate(vitals?.netRx)}</span> · <span className="text-violet">↑ {rate(vitals?.netTx)}</span></span>
      </div>
      <div className="mt-2 flex flex-wrap gap-1.5 font-mono text-[0.7rem] text-muted-foreground">
        <span className="rounded-md border bg-panel2 px-2 py-0.5">agent <b className="text-ok">{vitals ? duration(vitals.agentUptime) : '—'}</b></span>
        <span className="rounded-md border bg-panel2 px-2 py-0.5">controller <b className="text-ok">{vitals ? duration(vitals.controllerUptime) : '—'}</b></span>
        <span className="rounded-md border bg-panel2 px-2 py-0.5">DHT <b className="text-foreground">{vitals?.dhtNodes ?? '—'}</b></span>
      </div>
    </Panel>
  )
}

// --- Genres -----------------------------------------------------------------

const GENRE_COLORS = ['var(--primary)', 'var(--cyan)', 'var(--violet)', 'var(--pink)', 'var(--ok)', 'var(--warn)']

export function GenresWidget({ genres }: { genres: Genre[] | null }) {
  const top = (genres ?? []).slice(0, 6)
  const total = top.reduce((s, g) => s + g.games, 0)
  let offset = 25
  return (
    <Panel title="Gêneros da estante" note={total ? `${total} marcações` : undefined}>
      {total === 0 ? (
        <p className="text-xs text-faint">Sem gêneros conhecidos — aparecem para jogos do catálogo FitGirl/IGDB.</p>
      ) : (
        <div className="flex items-center gap-4">
          <svg viewBox="0 0 42 42" className="aspect-square w-full max-w-[150px] shrink-0">
            <circle cx="21" cy="21" r="15.915" fill="none" stroke="var(--border)" strokeWidth="6" />
            {top.map((g, i) => {
              const share = (g.games / total) * 100
              const el = (
                <circle key={g.name} cx="21" cy="21" r="15.915" fill="none" stroke={GENRE_COLORS[i]} strokeWidth="6"
                  strokeDasharray={`${share} ${100 - share}`} strokeDashoffset={offset} />
              )
              offset -= share
              return el
            })}
          </svg>
          <div className="min-w-0 flex-1 text-[0.8rem]">
            {top.map((g, i) => (
              <div key={g.name} className="flex justify-between gap-2 border-b border-dashed py-1 last:border-0">
                <span className="truncate"><i className="mr-1.5 inline-block size-2 rounded-sm" style={{ background: GENRE_COLORS[i] }} />{g.name}</span>
                <b className="font-mono">{Math.round((g.games / total) * 100)}%</b>
              </div>
            ))}
          </div>
        </div>
      )}
    </Panel>
  )
}

// --- Activity heatmap -------------------------------------------------------

export function HeatmapWidget({ activity }: { activity: ActivityDay[] | null }) {
  const days = activity ?? []
  const max = Math.max(1, ...days.map(d => d.downBytes))
  const lead = days.length ? new Date(days[0].date + 'T00:00').getDay() : 0
  const total = days.reduce((s, d) => s + d.downBytes, 0)
  const level = (b: number) => (b <= 0 ? 0 : Math.min(4, Math.ceil((b / max) * 4)))
  const shade = ['var(--background)', 'color-mix(in oklch, var(--cyan) 25%, var(--card))',
    'color-mix(in oklch, var(--cyan) 50%, var(--card))', 'color-mix(in oklch, var(--cyan) 75%, var(--card))', 'var(--cyan)']
  return (
    <Panel title="Atividade" note={`${bytes(total)} em ${days.length} dias`}>
      <div className="overflow-x-auto pb-1">
        <div className="grid w-max grid-flow-col gap-[3px]" style={{ gridTemplateRows: 'repeat(7, 12px)', gridAutoColumns: '12px' }}>
          {Array.from({ length: lead }).map((_, i) => <span key={`pad${i}`} />)}
          {days.map(d => (
            <span key={d.date} className="rounded-[2px]"
              style={{ background: level(d.downBytes) ? shade[level(d.downBytes)] : 'var(--muted)' }}
              title={`${new Date(d.date + 'T00:00').toLocaleDateString('pt-BR')} · ${bytes(d.downBytes)}`} />
          ))}
        </div>
      </div>
      <div className="mt-2 flex items-center justify-end gap-1 text-[0.66rem] text-faint">
        menos {shade.map(s => <i key={s} className="inline-block size-2.5 rounded-[2px] border" style={{ background: s }} />)} mais
      </div>
    </Panel>
  )
}

// --- Storage treemap --------------------------------------------------------

export function TreemapWidget({ library }: { library: GameLibrary | null }) {
  const games = (library?.games ?? []).filter(g => g.sizeBytes && g.sizeBytes > 0)
    .sort((a, b) => b.sizeBytes! - a.sizeBytes!)
  const total = games.reduce((s, g) => s + g.sizeBytes!, 0) + (library?.installFree ?? 0)
  return (
    <Panel title="Espaço por jogo" note={library ? `${bytes(games.reduce((s, g) => s + g.sizeBytes!, 0), 0)} em ${games.length} jogos` : undefined}>
      {library?.sizesComputing && <p className="mb-2 text-xs text-faint">Calculando tamanhos das pastas…</p>}
      <div className="flex flex-wrap gap-1">
        {games.map(g => {
          const share = g.sizeBytes! / Math.max(total, 1)
          return (
            <div key={g.key} title={`${g.name} · ${bytes(g.sizeBytes)}`}
              className="flex h-14 min-w-[84px] grow flex-col justify-between overflow-hidden rounded-md px-2 py-1.5 text-[0.7rem] text-white"
              style={{
                flexBasis: `calc(${(share * 100).toFixed(2)}% - 4px)`,
                background: g.source === 'STEAM' ? 'linear-gradient(150deg,#1b2838,#4b8bbe)' : 'linear-gradient(150deg, var(--primary), var(--violet))',
              }}>
              <b className="truncate font-bold">{g.name}</b>
              <span className="font-mono opacity-85">{bytes(g.sizeBytes, 0)}</span>
            </div>
          )
        })}
        {library?.installFree != null && (
          <div className="flex h-14 min-w-[84px] grow flex-col justify-between rounded-md border bg-panel2 px-2 py-1.5 text-[0.7rem] text-muted-foreground"
            style={{ flexBasis: `calc(${((library.installFree / Math.max(total, 1)) * 100).toFixed(2)}% - 4px)` }}>
            <b>Livre</b><span className="font-mono">{bytes(library.installFree, 0)}</span>
          </div>
        )}
      </div>
    </Panel>
  )
}
