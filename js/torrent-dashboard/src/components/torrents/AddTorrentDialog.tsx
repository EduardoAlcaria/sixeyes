import { useRef, useState } from 'react'
import { FolderOpen, LinkIcon, Plus, Upload } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { FolderPicker, friendlyPath } from '@/components/torrents/FolderPicker'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

interface Props {
  onAddMagnet: (magnet: string, downloadPath?: string) => Promise<void> | void
  onAddFile: (file: File, downloadPath?: string) => Promise<void> | void
  loading: boolean
}

export function AddTorrentDialog({ onAddMagnet, onAddFile, loading }: Props) {
  const [open, setOpen] = useState(false)
  const [magnet, setMagnet] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [path, setPath] = useState<string | null>(null)
  const [showPicker, setShowPicker] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  async function submitMagnet(e: React.FormEvent) {
    e.preventDefault()
    if (!magnet.trim().startsWith('magnet:')) {
      toast.error('Must start with "magnet:"')
      return
    }
    await onAddMagnet(magnet.trim(), path ?? undefined)
    setMagnet('')
    setOpen(false)
  }

  async function submitFile(e: React.FormEvent) {
    e.preventDefault()
    if (!file) {
      toast.error('Choose a .torrent file first')
      return
    }
    await onAddFile(file, path ?? undefined)
    setFile(null)
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button />}>
        <Plus className="size-4" /> Add torrent
      </DialogTrigger>

      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle className="text-base">Add a torrent</DialogTitle>
        </DialogHeader>

        <Tabs defaultValue="magnet" className="gap-3">
          <TabsList className="h-8 w-full">
            <TabsTrigger value="magnet" className="flex-1 text-xs">
              <LinkIcon className="size-3" /> Magnet
            </TabsTrigger>
            <TabsTrigger value="file" className="flex-1 text-xs">
              <Upload className="size-3" /> File
            </TabsTrigger>
          </TabsList>

          <TabsContent value="magnet">
            <form onSubmit={submitMagnet} className="flex gap-2">
              <div className="relative flex-1">
                <LinkIcon className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
                <Input
                  autoFocus
                  placeholder="magnet:?xt=urn:btih:…"
                  value={magnet}
                  onChange={e => setMagnet(e.target.value)}
                  className="h-8 pl-8 text-sm"
                />
              </div>
              <Button type="submit" size="sm" disabled={loading || !magnet.trim()}>
                {loading ? 'Adding…' : 'Add'}
              </Button>
            </form>
          </TabsContent>

          <TabsContent value="file">
            <form onSubmit={submitFile} className="space-y-2">
              <button
                type="button"
                onClick={() => inputRef.current?.click()}
                className="flex w-full items-center justify-center gap-2 rounded-lg border border-dashed py-5 text-xs text-muted-foreground transition-colors hover:bg-muted/50 hover:border-primary/40"
              >
                <Upload className="size-4" />
                {file ? (
                  <span className="truncate font-medium text-foreground">{file.name}</span>
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
              <Button type="submit" size="sm" className="w-full" disabled={loading || !file}>
                {loading ? 'Uploading…' : 'Add file'}
              </Button>
            </form>
          </TabsContent>
        </Tabs>

        {/* Folder picker */}
        <div className="border-t pt-2.5 space-y-2">
          <button
            type="button"
            onClick={() => setShowPicker(s => !s)}
            className="flex w-full items-center gap-2 text-xs text-muted-foreground transition-colors hover:text-foreground"
          >
            <FolderOpen className="size-3.5 shrink-0" />
            <span className="flex-1 truncate text-left">
              Save to <span className="font-medium text-foreground">{friendlyPath(path)}</span>
            </span>
            <span className="shrink-0">{showPicker ? 'Hide' : 'Change'}</span>
          </button>
          {showPicker && <FolderPicker value={path} onChange={setPath} />}
        </div>
      </DialogContent>
    </Dialog>
  )
}
