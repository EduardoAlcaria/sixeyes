import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from 'recharts'
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

export function NetworkChart({ history }: { history: NetworkDataPoint[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Network activity</CardTitle>
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
            <YAxis tickLine={false} axisLine={false} width={32} fontSize={11} />
            <ChartTooltip content={<ChartTooltipContent />} />
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
    </Card>
  )
}
