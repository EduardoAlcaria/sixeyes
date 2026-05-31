import { useState } from 'react'
import { Download, MoreVertical, Pause, Play, Trash2, Users } from 'lucide-react'
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
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import type { Torrent, TorrentStatus } from '@/types'

interface Props {
  torrents: Torrent[]
  onPause: (id: number) => void
  onResume: (id: number) => void
  onRemove: (id: number, deleteFiles: boolean) => void
  onInstall: (id: number) => void
}

type RowProps = { t: Torrent } & Omit<Props, 'torrents'>

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

// 3-dot menu: pause/resume, queue host install, or delete. Delete opens a
// confirm dialog that also offers to wipe the downloaded files from disk.
function RowActions({ t, onPause, onResume, onRemove, onInstall }: RowProps) {
  const paused = t.status === 'Paused' || t.status === 'Stopped'
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [wipeDisk, setWipeDisk] = useState(false)

  return (
    <>
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
          <DropdownMenuItem onClick={() => onInstall(t.id)}>
            <Download className="size-4" /> Install
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            variant="destructive"
            onClick={() => {
              setWipeDisk(false)
              setConfirmOpen(true)
            }}
          >
            <Trash2 className="size-4" /> Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Delete torrent?</DialogTitle>
            <DialogDescription>
              Remove “{t.title ?? 'this torrent'}” from SixEyes. This stops the transfer and
              clears it from the dashboard.
            </DialogDescription>
          </DialogHeader>
          <label className="flex items-start gap-3 rounded-lg border p-3 text-sm cursor-pointer hover:bg-accent">
            <input
              type="checkbox"
              className="mt-0.5 size-4 accent-destructive"
              checked={wipeDisk}
              onChange={e => setWipeDisk(e.target.checked)}
            />
            <span>
              <span className="font-medium">Also delete downloaded files from disk</span>
              <span className="block text-xs text-muted-foreground">
                Permanently erases the data on the host. Cannot be undone.
              </span>
            </span>
          </label>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                onRemove(t.id, wipeDisk)
                setConfirmOpen(false)
              }}
            >
              {wipeDisk ? 'Delete + wipe files' : 'Delete'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

export function TorrentTable({ torrents, onPause, onResume, onRemove, onInstall }: Props) {
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
      <div className="hidden md:block border-y overflow-hidden">
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
                  <RowActions
                    t={t}
                    onPause={onPause}
                    onResume={onResume}
                    onRemove={onRemove}
                    onInstall={onInstall}
                  />
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
                <RowActions
                  t={t}
                  onPause={onPause}
                  onResume={onResume}
                  onRemove={onRemove}
                  onInstall={onInstall}
                />
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
