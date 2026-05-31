import { CheckCircle2, Download, Loader2, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { useTorrents } from '@/hooks/useTorrents'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { InstallStatus } from '@/types'

function fmtDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

function InstallCell({ status, onInstall }: { status: InstallStatus; onInstall: () => void }) {
  switch (status) {
    case 'INSTALLING':
      return (
        <Badge variant="secondary" className="gap-1">
          <Loader2 className="size-3 animate-spin" /> Installing…
        </Badge>
      )
    case 'INSTALLED':
      return (
        <Badge className="gap-1 bg-chart-4/15 text-chart-4">
          <CheckCircle2 className="size-3" /> Installed
        </Badge>
      )
    case 'FAILED':
      return (
        <Button variant="outline" size="sm" onClick={onInstall} className="gap-1 text-destructive">
          <XCircle className="size-3" /> Failed — retry
        </Button>
      )
    case 'REQUESTED':
      return (
        <Badge variant="secondary" className="gap-1">
          <Loader2 className="size-3 animate-spin" /> Queued
        </Badge>
      )
    default:
      return (
        <Button variant="outline" size="sm" onClick={onInstall} className="gap-1">
          <Download className="size-3" /> Install
        </Button>
      )
  }
}

export function CompletedPage() {
  const { completed, installGame } = useTorrents()

  const install = async (id: number, title: string | null) => {
    await installGame(id)
    toast.success(`Install queued: ${title ?? 'game'}`)
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">
        Completed <span className="text-muted-foreground font-normal">({completed.length})</span>
      </h1>

      {completed.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            No completed downloads yet.
          </CardContent>
        </Card>
      ) : (
        <div className="border-y overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Title</TableHead>
                <TableHead>Size</TableHead>
                <TableHead>Completed</TableHead>
                <TableHead className="text-right">Install</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {completed.map(c => (
                <TableRow key={c.id}>
                  <TableCell className="font-medium">{c.title ?? '—'}</TableCell>
                  <TableCell>{c.size}</TableCell>
                  <TableCell className="text-muted-foreground">{fmtDate(c.completedAt)}</TableCell>
                  <TableCell className="text-right">
                    <InstallCell status={c.installStatus} onInstall={() => install(c.id, c.title)} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}
