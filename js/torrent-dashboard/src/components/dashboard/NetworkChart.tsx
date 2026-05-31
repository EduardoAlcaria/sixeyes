import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import { ArrowDown, ArrowUp } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart'
import type { NetworkDataPoint } from '@/types'

const config = {
  download: { label: 'Download', color: 'var(--chart-1)' },
  upload: { label: 'Upload', color: 'var(--chart-2)' },
} satisfies ChartConfig

const fmt = (v: number) => `${v.toFixed(2)} MB/s`

function peak(history: NetworkDataPoint[], key: 'download' | 'upload') {
  return history.reduce((m, p) => Math.max(m, p[key]), 0)
}
function avg(history: NetworkDataPoint[], key: 'download' | 'upload') {
  if (history.length === 0) return 0
  return history.reduce((s, p) => s + p[key], 0) / history.length
}

function Live({ icon: Icon, value, className }: {
  icon: React.ComponentType<{ className?: string }>
  value: number
  className: string
}) {
  return (
    <span className={`flex items-center gap-1 text-sm font-semibold tabular-nums ${className}`}>
      <Icon className="size-3.5" /> {fmt(value)}
    </span>
  )
}

export function NetworkChart({ history }: { history: NetworkDataPoint[] }) {
  const last = history[history.length - 1]
  const down = last?.download ?? 0
  const up = last?.upload ?? 0

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between space-y-0">
        <div className="space-y-1">
          <CardTitle>Network activity</CardTitle>
          <p className="text-xs text-muted-foreground">Last {Math.max(history.length, 1) * 5}s</p>
        </div>
        <div className="flex flex-col items-end gap-0.5">
          <Live icon={ArrowDown} value={down} className="text-chart-1" />
          <Live icon={ArrowUp} value={up} className="text-chart-2" />
        </div>
      </CardHeader>
      <CardContent>
        <ChartContainer config={config} className="h-[220px] w-full">
          <AreaChart data={history} margin={{ left: 4, right: 8, top: 8 }}>
            <defs>
              <linearGradient id="fillDownload" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-download)" stopOpacity={0.6} />
                <stop offset="95%" stopColor="var(--color-download)" stopOpacity={0.05} />
              </linearGradient>
              <linearGradient id="fillUpload" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-upload)" stopOpacity={0.6} />
                <stop offset="95%" stopColor="var(--color-upload)" stopOpacity={0.05} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} strokeDasharray="3 3" />
            <XAxis dataKey="time" tickLine={false} axisLine={false} minTickGap={28} fontSize={11} />
            <YAxis
              tickLine={false}
              axisLine={false}
              width={48}
              fontSize={11}
              tickFormatter={(v: number) => `${v.toFixed(1)}`}
              unit=" MB/s"
            />
            <ChartTooltip
              content={<ChartTooltipContent formatter={(v) => fmt(Number(v))} />}
            />
            <Area
              dataKey="download"
              type="monotone"
              stroke="var(--color-download)"
              fill="url(#fillDownload)"
              strokeWidth={2}
            />
            <Area
              dataKey="upload"
              type="monotone"
              stroke="var(--color-upload)"
              fill="url(#fillUpload)"
              strokeWidth={2}
            />
          </AreaChart>
        </ChartContainer>
      </CardContent>
      <div className="grid grid-cols-2 gap-px border-t bg-border text-center">
        <div className="bg-card py-2">
          <p className="text-[10px] uppercase tracking-wide text-muted-foreground">Peak ↓ / ↑</p>
          <p className="text-xs font-medium tabular-nums">
            <span className="text-chart-1">{fmt(peak(history, 'download'))}</span>
            {' · '}
            <span className="text-chart-2">{fmt(peak(history, 'upload'))}</span>
          </p>
        </div>
        <div className="bg-card py-2">
          <p className="text-[10px] uppercase tracking-wide text-muted-foreground">Avg ↓ / ↑</p>
          <p className="text-xs font-medium tabular-nums">
            <span className="text-chart-1">{fmt(avg(history, 'download'))}</span>
            {' · '}
            <span className="text-chart-2">{fmt(avg(history, 'upload'))}</span>
          </p>
        </div>
      </div>
    </Card>
  )
}
