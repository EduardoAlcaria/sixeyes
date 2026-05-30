import { ArrowDownToLine, CheckCircle2, Loader2, Upload } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { SystemInfo, Torrent } from '@/types'

function fmtSpeed(mbps: number): string {
  return `${mbps.toFixed(2)} MB/s`
}

function Stat({
  label,
  value,
  hint,
  icon: Icon,
}: {
  label: string
  value: string | number
  hint?: string
  icon: React.ComponentType<{ className?: string }>
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <Icon className="size-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold tabular-nums">{value}</div>
        {hint && <p className="text-xs text-muted-foreground mt-1">{hint}</p>}
      </CardContent>
    </Card>
  )
}

export function StatCards({ torrents, system }: { torrents: Torrent[]; system: SystemInfo }) {
  const active = torrents.filter(t => t.status === 'Downloading').length
  const seeding = torrents.filter(t => t.status === 'Seeding').length
  const completed = torrents.filter(t => t.status === 'Seeding' || t.progress >= 100).length

  return (
    <div className="grid gap-4 grid-cols-2 lg:grid-cols-4">
      <Stat label="Active" value={active} hint="Downloading now" icon={Loader2} />
      <Stat label="Seeding" value={seeding} hint="Sharing back" icon={Upload} />
      <Stat label="Completed" value={completed} hint="Finished" icon={CheckCircle2} />
      <Stat
        label="Network"
        value={fmtSpeed(system.network.downloadSpeed)}
        hint={`↑ ${fmtSpeed(system.network.uploadSpeed)}`}
        icon={ArrowDownToLine}
      />
    </div>
  )
}
