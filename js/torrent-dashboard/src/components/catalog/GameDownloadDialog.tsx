import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FolderOpen, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FolderPicker, friendlyPath } from '@/components/torrents/FolderPicker'
import { catalogApi, torrentApi } from '@/services/api'
import type { CatalogGame } from '@/types'

interface Props {
  game: CatalogGame | null
  open: boolean
  onClose: () => void
}

export function GameDownloadDialog({ game, open, onClose }: Props) {
  const navigate = useNavigate()
  const [detail, setDetail] = useState<CatalogGame | null>(null)
  const [path, setPath] = useState<string | null>(null)
  const [showPicker, setShowPicker] = useState(false)
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    if (!open || !game) { setDetail(null); setShowPicker(false); return }
    if (game.magnet) { setDetail(game); return }
    catalogApi.game(game.url)
      .then(setDetail)
      .catch(e => { toast.error((e as Error).message); onClose() })
  }, [open, game, onClose])

  const download = async () => {
    if (!detail?.magnet) return
    setAdding(true)
    try {
      await torrentApi.add(detail.magnet, path ?? undefined)
      toast.success(`Download started: ${detail.title}`)
      onClose()
      navigate('/torrents')
    } catch (e) {
      toast.error((e as Error).message)
    } finally {
      setAdding(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle className="text-base line-clamp-2">{game?.title ?? 'Loading…'}</DialogTitle>
        </DialogHeader>

        {!detail ? (
          <div className="flex items-center justify-center py-10">
            <Loader2 className="size-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="space-y-3">
            {detail.imageUrl && (
              <img
                src={detail.imageUrl}
                alt={detail.title}
                className="w-full rounded-lg object-cover max-h-44"
              />
            )}

            {detail.repackSize && (
              <p className="text-sm text-muted-foreground">
                Repack size: <span className="font-medium text-foreground">{detail.repackSize}</span>
              </p>
            )}

            <div className="border-t pt-2.5">
              <button
                type="button"
                onClick={() => setShowPicker(s => !s)}
                className="flex w-full items-center gap-2 text-xs text-muted-foreground transition-colors hover:text-foreground"
              >
                <FolderOpen className="size-3.5 shrink-0" />
                <span className="flex-1 truncate text-left">
                  Save to{' '}
                  <span className="font-medium text-foreground">{friendlyPath(path)}</span>
                </span>
                <span>{showPicker ? 'Hide' : 'Change'}</span>
              </button>
              {showPicker && (
                <div className="mt-2">
                  <FolderPicker value={path} onChange={setPath} />
                </div>
              )}
            </div>

            <Button
              onClick={download}
              disabled={adding || !detail.magnet}
              className="w-full"
            >
              {adding ? (
                <><Loader2 className="size-4 animate-spin mr-1.5" /> Starting…</>
              ) : 'Download'}
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
