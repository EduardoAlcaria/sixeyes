import { MoreVertical, Pause, Play, Trash2, Users } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { Torrent, TorrentStatus } from '@/types'

interface Props {
  torrents: Torrent[]
  onPause: (id: number) => void
  onResume: (id: number) => void
  onRemove: (id: number) => void
}

const statusClass: Record<TorrentStatus, string> = {
  Downloading: 'bg-chart-1/15 text-chart-1',
  Seeding: 'bg-chart-4/15 text-chart-4',
  Paused: 'bg-secondary text-secondary-foreground',
  Stopped: 'bg-muted text-muted-foreground',
  Error: 'bg-destructive/10 text-destructive',
}

function StatusBadge({ status }: { status: TorrentStatus }) {
  return <Badge className={statusClass[status] ?? ''}>{status}</Badge>
}

function RowActions({ t, onPause, onResume, onRemove }: { t: Torrent } & Omit<Props, 'torrents'>) {
  const paused = t.status === 'Paused' || t.status === 'Stopped'
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button variant="ghost" size="icon" aria-label="Actions">
            <MoreVertical className="size-4" />
          </Button>
        }
      />
      <DropdownMenuContent align="end">
        {paused ? (
          <DropdownMenuItem onClick={() => onResume(t.id)}>
            <Play className="size-4" /> Resume
          </DropdownMenuItem>
        ) : (
          <DropdownMenuItem onClick={() => onPause(t.id)}>
            <Pause className="size-4" /> Pause
          </DropdownMenuItem>
        )}
        <DropdownMenuItem variant="destructive" onClick={() => onRemove(t.id)}>
          <Trash2 className="size-4" /> Remove
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export function TorrentTable({ torrents, onPause, onResume, onRemove }: Props) {
  if (torrents.length === 0) {
    return (
      <Card>
        <CardContent className="py-12 text-center text-muted-foreground">
          No torrents yet. Add a magnet link or a .torrent file to get started.
        </CardContent>
      </Card>
    )
  }

  return (
    <>
      {/* desktop / tablet table */}
      <div className="hidden md:block rounded-xl border overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead className="w-[180px]">Progress</TableHead>
              <TableHead className="text-right">↓ / ↑</TableHead>
              <TableHead className="text-right">Peers</TableHead>
              <TableHead>ETA</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-10" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {torrents.map(t => (
              <TableRow key={t.id}>
                <TableCell className="max-w-[280px] truncate font-medium">
                  {t.title ?? 'Fetching metadata…'}
                  <span className="block text-xs text-muted-foreground">{t.size}</span>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <Progress value={t.progress} className="h-2 flex-1" />
                    <span className="text-xs tabular-nums text-muted-foreground w-10 text-right">
                      {t.progress.toFixed(0)}%
                    </span>
                  </div>
                </TableCell>
                <TableCell className="text-right text-xs tabular-nums">
                  <span className="text-chart-1">{t.downloadSpeed}</span>
                  <span className="block text-chart-2">{t.uploadSpeed}</span>
                </TableCell>
                <TableCell className="text-right tabular-nums">{t.peers}</TableCell>
                <TableCell className="text-xs text-muted-foreground">{t.eta ?? '—'}</TableCell>
                <TableCell>
                  <StatusBadge status={t.status} />
                </TableCell>
                <TableCell>
                  <RowActions t={t} onPause={onPause} onResume={onResume} onRemove={onRemove} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* mobile cards */}
      <div className="md:hidden space-y-3">
        {torrents.map(t => (
          <Card key={t.id}>
            <CardContent className="p-4 space-y-3">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-medium truncate">{t.title ?? 'Fetching metadata…'}</p>
                  <p className="text-xs text-muted-foreground">{t.size}</p>
                </div>
                <RowActions t={t} onPause={onPause} onResume={onResume} onRemove={onRemove} />
              </div>
              <div className="flex items-center gap-2">
                <Progress value={t.progress} className="h-2 flex-1" />
                <span className="text-xs tabular-nums text-muted-foreground">
                  {t.progress.toFixed(0)}%
                </span>
              </div>
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span className="text-chart-1">↓ {t.downloadSpeed}</span>
                <span className="text-chart-2">↑ {t.uploadSpeed}</span>
                <span className="flex items-center gap-1">
                  <Users className="size-3" /> {t.peers}
                </span>
                <StatusBadge status={t.status} />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </>
  )
}
