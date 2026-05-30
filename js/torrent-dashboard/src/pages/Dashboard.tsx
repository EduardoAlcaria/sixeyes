import { useTorrents } from '@/hooks/useTorrents'
import { useSystemMonitoring } from '@/hooks/useSystemMonitoring'
import { StatCards } from '@/components/dashboard/StatCards'
import { NetworkChart } from '@/components/dashboard/NetworkChart'
import { StorageChart } from '@/components/dashboard/StorageChart'

export function DashboardPage() {
  const { torrents } = useTorrents()
  const { system, history } = useSystemMonitoring()
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
      <StatCards torrents={torrents} system={system} />
      <div className="grid gap-6 lg:grid-cols-2">
        <NetworkChart history={history} />
        <StorageChart system={system} />
      </div>
    </div>
  )
}
