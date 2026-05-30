import { useRef, useState } from 'react'
import { Plus, Upload } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

interface Props {
  onAddMagnet: (magnet: string) => Promise<void> | void
  onAddFile: (file: File) => Promise<void> | void
  loading: boolean
}

export function AddTorrentDialog({ onAddMagnet, onAddFile, loading }: Props) {
  const [open, setOpen] = useState(false)
  const [magnet, setMagnet] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  async function submitMagnet(e: React.FormEvent) {
    e.preventDefault()
    if (!magnet.startsWith('magnet:')) {
      toast.error('Enter a valid magnet link (must start with "magnet:")')
      return
    }
    await onAddMagnet(magnet.trim())
    toast.success('Magnet added')
    setMagnet('')
    setOpen(false)
  }

  async function submitFile(e: React.FormEvent) {
    e.preventDefault()
    if (!file) {
      toast.error('Choose a .torrent file first')
      return
    }
    await onAddFile(file)
    toast.success(`Added ${file.name}`)
    setFile(null)
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        render={
          <Button>
            <Plus className="size-4" /> Add torrent
          </Button>
        }
      />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a torrent</DialogTitle>
          <DialogDescription>Paste a magnet link or upload a .torrent file.</DialogDescription>
        </DialogHeader>
        <Tabs defaultValue="magnet">
          <TabsList className="w-full">
            <TabsTrigger value="magnet" className="flex-1">
              Magnet
            </TabsTrigger>
            <TabsTrigger value="file" className="flex-1">
              File
            </TabsTrigger>
          </TabsList>

          <TabsContent value="magnet">
            <form onSubmit={submitMagnet} className="space-y-4 pt-2">
              <div className="space-y-2">
                <Label htmlFor="magnet">Magnet link</Label>
                <Input
                  id="magnet"
                  placeholder="magnet:?xt=urn:btih:…"
                  value={magnet}
                  onChange={e => setMagnet(e.target.value)}
                />
              </div>
              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? 'Adding…' : 'Add magnet'}
              </Button>
            </form>
          </TabsContent>

          <TabsContent value="file">
            <form onSubmit={submitFile} className="space-y-4 pt-2">
              <button
                type="button"
                onClick={() => inputRef.current?.click()}
                className="flex w-full flex-col items-center gap-2 rounded-xl border border-dashed py-8 text-sm text-muted-foreground transition-colors hover:bg-muted/50"
              >
                <Upload className="size-6" />
                {file ? (
                  <span className="font-medium text-foreground">{file.name}</span>
                ) : (
                  <span>Click to choose a .torrent file</span>
                )}
              </button>
              <input
                ref={inputRef}
                type="file"
                accept=".torrent"
                className="hidden"
                onChange={e => setFile(e.target.files?.[0] ?? null)}
              />
              <Button type="submit" className="w-full" disabled={loading || !file}>
                {loading ? 'Uploading…' : 'Add file'}
              </Button>
            </form>
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}
