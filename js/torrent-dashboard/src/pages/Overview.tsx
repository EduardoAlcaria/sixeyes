import { useState } from 'react'
import { Board } from '@/components/deck/Board'
import type { Widget } from '@/components/deck/Board'
import { TorrentDetailDialog } from '@/components/deck/TorrentDetailDialog'
import {
  DisksWidget, EventsWidget, GameStatsWidget, GenresWidget, HeatmapWidget, KpiWidget, MapWidget, NetMetricsWidget,
  PipelineWidget, PlayHeatmapWidget, RecentWidget, SwarmWidget, ThroughputWidget, TreemapWidget, VitalsWidget,
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
  const owned = usePolling(insightApi.steamOwned, 300_000).data
  const genres = usePolling(insightApi.genres, 300_000).data
  const playDays = usePolling(() => insightApi.playtime(364), 120_000).data

  const widgets: Widget[] = [
    { id: 'kpi', label: 'Summary', wide: true, render: () => <KpiWidget torrents={torrents} summary={summary} vitals={vitals} library={library} /> },
    { id: 'net', label: 'Metrics', wide: true, render: () => <NetMetricsWidget summary={summary} peers={peers} vitals={vitals} /> },
    { id: 'throughput', label: 'Network', render: () => <ThroughputWidget /> },
    { id: 'swarm', label: 'Swarm', render: () => <SwarmWidget torrents={torrents} /> },
    { id: 'map', label: 'Map', wide: true, render: () => <MapWidget peers={peers} compact /> },
    { id: 'recent', label: 'Recent', wide: true, render: () => <RecentWidget history={history} onOpen={setOpenId} /> },
    { id: 'disks', label: 'Disks', render: () => <DisksWidget vitals={vitals} /> },
    { id: 'pipeline', label: 'Pipeline', render: () => <PipelineWidget torrents={torrents} onOpen={setOpenId} /> },
    { id: 'events', label: 'Events', render: () => <EventsWidget events={events} /> },
    { id: 'vitals', label: 'Vitals', render: () => <VitalsWidget vitals={vitals} /> },
    { id: 'genres', label: 'Genres', wide: true, render: () => <GenresWidget genres={genres} /> },
    { id: 'play', label: 'Play activity', wide: true, render: () => <PlayHeatmapWidget days={playDays} /> },
    { id: 'gamestats', label: 'Game stats', wide: true, render: () => <GameStatsWidget library={library} owned={owned} /> },
    { id: 'heat', label: 'Traffic', wide: true, render: () => <HeatmapWidget activity={activity} /> },
    { id: 'tree', label: 'Space', wide: true, render: () => <TreemapWidget library={library} /> },
  ]

  return (
    <>
      <div className="flex flex-wrap items-baseline gap-3">
        <h1 className="text-2xl font-extrabold">Command Deck</h1>
        <span className="text-sm text-muted-foreground">drag to reorder · toggle widgets under "Show"</span>
      </div>
      <Board widgets={widgets} />
      <TorrentDetailDialog torrentId={openId} onClose={() => setOpenId(null)} handlers={handlers} />
    </>
  )
}
