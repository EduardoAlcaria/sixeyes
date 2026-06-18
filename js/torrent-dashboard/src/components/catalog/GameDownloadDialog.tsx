import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Clock, Download, FolderOpen, Loader2, Tag } from 'lucide-react'
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
      <DialogContent className="sm:max-w-lg max-h-[92vh] overflow-y-auto p-0 gap-0">
        {!detail ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="size-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <>
            {/* Cover image or placeholder */}
            <div className="relative w-full bg-muted overflow-hidden rounded-t-xl">
              {detail.imageUrl ? (
                <img
                  src={detail.imageUrl}
                  alt={detail.title}
                  className="w-full object-cover max-h-60"
                />
              ) : (
                <div className="w-full h-36 flex items-center justify-center">
                  <span className="text-5xl font-bold text-muted-foreground/30 select-none">
                    {detail.title.charAt(0).toUpperCase()}
                  </span>
                </div>
              )}
              {/* gradient title overlay */}
              <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent pt-8 pb-3 px-4">
                <h2 className="text-white font-semibold text-base leading-snug line-clamp-2 pr-6">
                  {detail.title}
                </h2>
              </div>
            </div>

            <div className="p-4 space-y-3">
              {/* Summary */}
              {detail.summary && (
                <p className="text-xs text-muted-foreground leading-relaxed line-clamp-4">
                  {detail.summary}
                </p>
              )}

              {/* Size + Price */}
              {(detail.repackSize || detail.steamPrice) && (
                <div className="flex flex-wrap gap-2">
                  {detail.repackSize && (
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-2.5 py-1 text-xs font-medium">
                      <Tag className="size-3 text-primary" />
                      {detail.repackSize}
                    </span>
                  )}
                  {detail.steamPrice && (
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-secondary-foreground">
                      Steam: {detail.steamPrice}
                    </span>
                  )}
                </div>
              )}

              {/* HLTB */}
              {hasHltb && (
                <div className="rounded-lg border bg-muted/40 px-3 py-2.5">
                  <p className="text-[11px] font-medium text-muted-foreground flex items-center gap-1 mb-2">
                    <Clock className="size-3" /> How Long to Beat
                  </p>
                  <div className="grid grid-cols-3 gap-2 text-center">
                    {detail.hltbMain != null && <HltbCell label="Main Story" hours={detail.hltbMain} />}
                    {detail.hltbRushed != null && <HltbCell label="Rushed" hours={detail.hltbRushed} />}
                    {detail.hltbCompletionist != null && <HltbCell label="100%" hours={detail.hltbCompletionist} />}
                  </div>
                </div>
              )}

              {/* Dev / Pub */}
              {(detail.developers || detail.publishers) && (
                <div className="text-[11px] text-muted-foreground space-y-0.5">
                  {detail.developers && (
                    <p><span className="font-semibold text-foreground/70">Dev</span> · {detail.developers}</p>
                  )}
                  {detail.publishers && (
                    <p><span className="font-semibold text-foreground/70">Pub</span> · {detail.publishers}</p>
                  )}
                </div>
              )}

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

              {/* Download button */}
              <Button
                onClick={download}
                disabled={adding || !detail.magnet}
                className="w-full gap-2"
              >
                {adding ? (
                  <><Loader2 className="size-4 animate-spin" /> Starting…</>
                ) : detail.magnet ? (
                  <><Download className="size-4" /> Download</>
                ) : (
                  'No magnet link yet'
                )}
              </Button>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

function HltbCell({ label, hours }: { label: string; hours: number }) {
  return (
    <div className="space-y-0.5">
      <p className="text-[10px] text-muted-foreground">{label}</p>
      <p className="text-sm font-bold tabular-nums">{hours}h</p>
    </div>
  )
}
