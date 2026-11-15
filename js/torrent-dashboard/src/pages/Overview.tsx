import { useState } from 'react'
import { Board } from '@/components/deck/Board'
import type { Widget } from '@/components/deck/Board'
import { TorrentDetailDialog } from '@/components/deck/TorrentDetailDialog'
import {
  DisksWidget, EventsWidget, GenresWidget, HeatmapWidget, KpiWidget, MapWidget, NetMetricsWidget,
  PipelineWidget, RecentWidget, SwarmWidget, ThroughputWidget, TreemapWidget, VitalsWidget,
} from '@/components/deck/widgets'
import { usePolling } from '@/hooks/usePolling'
import { useSharedTorrents } from '@/hooks/useTorrents'
import { useTorrentHandlers } from '@/hooks/useTorrentHandlers'
import { insightApi } from '@/services/api'

export function OverviewPage() {
  const { torrents } = useSharedTorrents()
  const handlers = useTorrentHandlers()
  const [openId, setOpenId] = useState<number | null>(null)

  const summary = usePolling(insightApi.summary, 15_000).data
  const vitals = usePolling(insightApi.vitals, 3_000).data
  const events = usePolling(() => insightApi.events(40), 5_000).data
  const peers = usePolling(() => insightApi.peers(), 10_000).data
  const history = usePolling(insightApi.history, 15_000).data
  const activity = usePolling(() => insightApi.activity(364), 300_000).data
  const library = usePolling(insightApi.games, 60_000).data
  const genres = usePolling(insightApi.genres, 300_000).data

  const widgets: Widget[] = [
    { id: 'kpi', label: 'Resumo', wide: true, render: () => <KpiWidget torrents={torrents} summary={summary} vitals={vitals} library={library} /> },
    { id: 'net', label: 'Métricas', wide: true, render: () => <NetMetricsWidget summary={summary} peers={peers} vitals={vitals} /> },
    { id: 'throughput', label: 'Rede', render: () => <ThroughputWidget /> },
    { id: 'swarm', label: 'Swarm', render: () => <SwarmWidget torrents={torrents} /> },
    { id: 'map', label: 'Mapa', wide: true, render: () => <MapWidget peers={peers} compact /> },
    { id: 'recent', label: 'Recentes', wide: true, render: () => <RecentWidget history={history} onOpen={setOpenId} /> },
    { id: 'disks', label: 'Discos', render: () => <DisksWidget vitals={vitals} /> },
    { id: 'pipeline', label: 'Pipeline', render: () => <PipelineWidget torrents={torrents} onOpen={setOpenId} /> },
    { id: 'events', label: 'Eventos', render: () => <EventsWidget events={events} /> },
    { id: 'vitals', label: 'Vitais', render: () => <VitalsWidget vitals={vitals} /> },
    { id: 'genres', label: 'Gêneros', render: () => <GenresWidget genres={genres} /> },
    { id: 'heat', label: 'Atividade', wide: true, render: () => <HeatmapWidget activity={activity} /> },
    { id: 'tree', label: 'Espaço', wide: true, render: () => <TreemapWidget library={library} /> },
  ]

  return (
    <>
      <div className="flex flex-wrap items-baseline gap-3">
        <h1 className="text-2xl font-extrabold">Command Deck</h1>
        <span className="text-sm text-muted-foreground">arraste pra reordenar · ligue/desligue em "Mostrar"</span>
      </div>
      <Board widgets={widgets} />
      <TorrentDetailDialog torrentId={openId} onClose={() => setOpenId(null)} handlers={handlers} />
    </>
  )
}
