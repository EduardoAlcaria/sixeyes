import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Clock, FolderOpen, Loader2, Tag } from 'lucide-react'
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

  const hasHltb = detail && (detail.hltbMain || detail.hltbRushed || detail.hltbCompletionist)

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogContent className="sm:max-w-md max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-base line-clamp-2 pr-6">{game?.title ?? 'Loading…'}</DialogTitle>
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
                className="w-full rounded-lg object-cover max-h-52"
              />
            )}

            {detail.summary && (
              <p className="text-xs text-muted-foreground leading-relaxed line-clamp-4">
                {detail.summary}
              </p>
            )}

            <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
              {detail.repackSize && (
                <span className="flex items-center gap-1">
                  <Tag className="size-3" />
                  <span className="font-medium text-foreground">{detail.repackSize}</span>
                </span>
              )}
              {detail.steamPrice && (
                <span className="flex items-center gap-1">
                  Steam:
                  <span className="font-medium text-foreground">{detail.steamPrice}</span>
                </span>
              )}
            </div>

            {hasHltb && (
              <div className="rounded-md border px-3 py-2 space-y-1">
                <p className="text-xs font-medium flex items-center gap-1 text-muted-foreground mb-1.5">
                  <Clock className="size-3" /> How Long to Beat
                </p>
                <div className="grid grid-cols-3 gap-1 text-center">
                  {detail.hltbMain != null && (
                    <HltbCell label="Main" hours={detail.hltbMain} />
                  )}
                  {detail.hltbRushed != null && (
                    <HltbCell label="Rushed" hours={detail.hltbRushed} />
                  )}
                  {detail.hltbCompletionist != null && (
                    <HltbCell label="100%" hours={detail.hltbCompletionist} />
                  )}
                </div>
              </div>
            )}

            {(detail.developers || detail.publishers) && (
              <div className="text-xs text-muted-foreground space-y-0.5">
                {detail.developers && (
                  <p><span className="font-medium text-foreground">Dev:</span> {detail.developers}</p>
                )}
                {detail.publishers && (
                  <p><span className="font-medium text-foreground">Pub:</span> {detail.publishers}</p>
                )}
              </div>
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
              {adding
                ? <><Loader2 className="size-4 animate-spin mr-1.5" /> Starting…</>
                : detail.magnet ? 'Download' : 'No magnet link'
              }
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

function HltbCell({ label, hours }: { label: string; hours: number }) {
  return (
    <div className="space-y-0.5">
      <p className="text-[10px] text-muted-foreground">{label}</p>
      <p className="text-sm font-semibold">{hours}h</p>
    </div>
  )
}
