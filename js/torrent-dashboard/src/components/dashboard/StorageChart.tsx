import { HardDrive } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import type { SystemInfo } from '@/types'

// Java already reports storage in GB — format as-is, do not re-divide.
function gb(value: number): string {
  return `${value.toFixed(1)} GB`
}

export function StorageChart({ system }: { system: SystemInfo }) {
  const { total, used, available, device } = system.storage
  const pct = total > 0 ? Math.round((used / total) * 100) : 0
  const label = device && device !== 'downloads' ? device : 'Downloads'

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <HardDrive className="size-4 text-muted-foreground" /> Storage
          <Badge variant="secondary" className="ml-auto font-mono">{label}</Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-end justify-between">
          <span className="text-3xl font-bold tabular-nums">{pct}%</span>
          <span className="text-sm text-muted-foreground">
            {gb(used)} / {gb(total)}
          </span>
        </div>
        <Progress value={pct} className="h-2" />
        <p className="text-sm text-muted-foreground">
          {gb(available)} free on {label}
        </p>
      </CardContent>
    </Card>
  )
}
