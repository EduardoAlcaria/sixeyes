import { useTorrents } from '@/hooks/useTorrents'
import { Card, CardContent } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

function fmtDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

export function CompletedPage() {
  const { completed } = useTorrents()

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
        <div className="rounded-xl border overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Title</TableHead>
                <TableHead>Size</TableHead>
                <TableHead>Completed</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {completed.map(c => (
                <TableRow key={c.id}>
                  <TableCell className="font-medium">{c.title ?? '—'}</TableCell>
                  <TableCell>{c.size}</TableCell>
                  <TableCell className="text-muted-foreground">{fmtDate(c.completedAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}
