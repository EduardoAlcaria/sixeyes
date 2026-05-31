import { useMemo, useState } from 'react'
import {
  ArrowDown,
  ArrowUp,
  ChevronsUpDown,
  Download,
  MoreVertical,
  Pause,
  Play,
  Square,
  Trash2,
  Users,
  XCircle,
} from 'lucide-react'
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
  onStop: (id: number) => void
  onResume: (id: number) => void
  onRemove: (id: number, deleteFiles: boolean) => void
  onInstall: (id: number) => void
  onCancelInstall: (id: number) => void
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

// --- sorting ---------------------------------------------------------------
type SortKey = 'title' | 'progress' | 'peers' | 'status'
type SortDir = 'asc' | 'desc'

function sortValue(t: Torrent, key: SortKey): string | number {
  switch (key) {
    case 'title': return (t.title ?? '').toLowerCase()
    case 'progress': return t.progress
    case 'peers': return t.peers
    case 'status': return t.status
  }
}

function RowActions({ t, onPause, onStop, onResume, onRemove, onInstall, onCancelInstall }: RowProps) {
  const halted = t.status === 'Paused' || t.status === 'Stopped'
  const queued = t.installStatus === 'REQUESTED' || t.installStatus === 'INSTALLING'
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
          {halted ? (
            <DropdownMenuItem onClick={() => onResume(t.id)}>
              <Play className="size-4" /> Resume
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem onClick={() => onPause(t.id)}>
              <Pause className="size-4" /> Pause
            </DropdownMenuItem>
          )}
          {t.status !== 'Stopped' && (
            <DropdownMenuItem onClick={() => onStop(t.id)}>
              <Square className="size-4" /> Stop
            </DropdownMenuItem>
          )}
          {queued ? (
            <DropdownMenuItem onClick={() => onCancelInstall(t.id)}>
              <XCircle className="size-4" /> Cancel install
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem onClick={() => onInstall(t.id)}>
              <Download className="size-4" /> Install
            </DropdownMenuItem>
          )}
          <DropdownMenuSeparator />
          <DropdownMenuItem
            variant="destructive"
            onClick={() => {
              setWipeDisk(false)
              // Defer past the menu's focus-restore, else the dialog reopens shut.
              setTimeout(() => setConfirmOpen(true), 0)
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

export function TorrentTable(props: Props) {
  const { torrents, onPause, onStop, onResume, onRemove, onInstall, onCancelInstall } = props
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({ key: 'progress', dir: 'desc' })

  const sorted = useMemo(() => {
    const rows = [...torrents]
    rows.sort((a, b) => {
      const av = sortValue(a, sort.key)
      const bv = sortValue(b, sort.key)
      const cmp = av < bv ? -1 : av > bv ? 1 : 0
      return sort.dir === 'asc' ? cmp : -cmp
    })
    return rows
  }, [torrents, sort])

  const toggleSort = (key: SortKey) =>
    setSort(s => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' }))

  const SortHead = ({ label, k, className }: { label: string; k: SortKey; className?: string }) => {
    const active = sort.key === k
    const Icon = !active ? ChevronsUpDown : sort.dir === 'asc' ? ArrowUp : ArrowDown
    return (
      <TableHead className={className}>
        <button
          type="button"
          onClick={() => toggleSort(k)}
          className="inline-flex items-center gap-1 hover:text-foreground transition-colors"
        >
          {label}
          <Icon className={`size-3.5 ${active ? 'text-foreground' : 'text-muted-foreground/50'}`} />
        </button>
      </TableHead>
    )
  }

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
              <SortHead label="Title" k="title" />
              <SortHead label="Progress" k="progress" className="w-[180px]" />
              <TableHead className="text-right">↓ / ↑</TableHead>
              <SortHead label="Peers" k="peers" className="[&>button]:justify-end text-right" />
              <TableHead>ETA</TableHead>
              <SortHead label="Status" k="status" />
              <TableHead className="w-10" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.map(t => (
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
                    onStop={onStop}
                    onResume={onResume}
                    onRemove={onRemove}
                    onInstall={onInstall}
                    onCancelInstall={onCancelInstall}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* mobile cards */}
      <div className="md:hidden space-y-3">
        {sorted.map(t => (
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
                  onStop={onStop}
                  onResume={onResume}
                  onRemove={onRemove}
                  onInstall={onInstall}
                  onCancelInstall={onCancelInstall}
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
