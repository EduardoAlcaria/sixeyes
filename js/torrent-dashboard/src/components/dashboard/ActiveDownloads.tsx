import { ArrowDown, ArrowUp, Clock, Inbox, Users } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import type { Torrent } from '@/types'

// Show what's actually moving: downloading first, then seeding, capped so the
// dashboard stays a glance, not the full Torrents page.
function rank(t: Torrent) {
  if (t.status === 'Downloading') return 0
  if (t.status === 'Seeding') return 1
  return 2
}

export function ActiveDownloads({ torrents }: { torrents: Torrent[] }) {
  const rows = [...torrents]
    .filter(t => t.status === 'Downloading' || t.status === 'Seeding')
    .sort((a, b) => rank(a) - rank(b) || b.progress - a.progress)
    .slice(0, 5)

  return (
    <Card>
      <CardHeader> 
        <CardTitle>Recent Downloads</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {rows.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-8 text-center text-muted-foreground">
            <Inbox className="size-6" />
            <p className="text-sm">No active transfers right now.</p>
          </div>
        ) : (
          rows.map(t => (
            <div key={t.id} className="space-y-1.5">
              <div className="flex items-center justify-between gap-2">
                <span className="truncate text-sm font-medium">
                  {t.title ?? 'Fetching metadata…'}
                </span>
                <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                  {t.progress.toFixed(0)}%
                </span>
              </div>
              <Progress value={t.progress} className="h-1.5" />
              <div className="flex items-center gap-3 text-xs text-muted-foreground tabular-nums">
                <span className="flex items-center gap-1 text-chart-1">
                  <ArrowDown className="size-3" /> {t.downloadSpeed}
                </span>
                <span className="flex items-center gap-1 text-chart-2">
                  <ArrowUp className="size-3" /> {t.uploadSpeed}
                </span>
                <span className="flex items-center gap-1">
                  <Users className="size-3" /> {t.peers}
                </span>
                {t.eta && (
                  <span className="ml-auto flex items-center gap-1">
                    <Clock className="size-3" /> {t.eta}
                  </span>
                )}
              </div>
            </div>
          ))
        )}
      </CardContent>
    </Card>
  )
}
