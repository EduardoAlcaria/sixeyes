import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Clock, Download, ExternalLink, FolderOpen, HardDrive, Loader2, PackageCheck, Tag } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { FolderPicker, friendlyPath } from '@/components/torrents/FolderPicker'
import { catalogApi, settingsApi, torrentApi } from '@/services/api'
import type { CatalogGame, Settings } from '@/types'

/** "fg-optional-russian-voiceover" -> "Russian voiceover" */
function partLabel(part: string): string {
  const s = part.replace(/^fg-optional-?/i, '').replace(/[-_]+/g, ' ').trim()
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : part
}

interface Props {
  game: CatalogGame | null
  open: boolean
  onClose: () => void
}

export function GameDownloadDialog({ game, open, onClose }: Props) {
  const navigate = useNavigate()
  const [detail, setDetail] = useState<CatalogGame | null>(null)
  const [settings, setSettings] = useState<Settings | null>(null)
  const [downloadPath, setDownloadPath] = useState<string | null>(null)
  const [installPath, setInstallPath] = useState<string | null>(null)
  const [picker, setPicker] = useState<'download' | 'install' | null>(null)
  const [autoInstall, setAutoInstall] = useState(true)
  // Optional parts start unchecked: skipping them is the point, and each one selected
  // costs download time and disk as well as install time.
  const [wanted, setWanted] = useState<Set<string>>(new Set())
  const [adding, setAdding] = useState(false)
  // A dead cover host leaves a zero-height <img> and the title overlay floats loose.
  const [imgBroken, setImgBroken] = useState(false)

  useEffect(() => {
    if (!open || !game) {
      setDetail(null); setPicker(null); setImgBroken(false); setWanted(new Set())
      setDownloadPath(null); setInstallPath(null); setAutoInstall(true)
      return
    }
    let stale = false
    catalogApi.game(game.url)
      .then(d => { if (!stale) setDetail(d) })
      .catch(e => { if (!stale) { toast.error((e as Error).message); onClose() } })
    settingsApi.get().then(s => { if (!stale) setSettings(s) }).catch(() => undefined)
    return () => { stale = true }
  }, [open, game, onClose])

  const effectiveInstall = installPath ?? settings?.installPath ?? null
  const needsInstallFolder = autoInstall && !effectiveInstall

  const toggle = (part: string) =>
    setWanted(prev => {
      const next = new Set(prev)
      if (next.has(part)) next.delete(part)
      else next.add(part)
      return next
    })

  const download = async () => {
    if (!detail?.magnet) return
    setAdding(true)
    try {
      await torrentApi.add(detail.magnet, {
        downloadPath: downloadPath ?? undefined,
        installPath: effectiveInstall ?? undefined,
        autoInstall,
        skipParts: detail.optionalParts.filter(p => !wanted.has(p)),
      })
      toast.success(autoInstall
        ? `Downloading ${detail.title} - it installs itself when done`
        : `Download started: ${detail.title}`)
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
      <DialogContent className="sm:max-w-lg max-h-[92vh] overflow-y-auto overflow-x-hidden p-0 gap-0">
        {!detail ? (
          <div className="flex items-center justify-center py-20">
            <DialogTitle className="sr-only">Loading game</DialogTitle>
            <Loader2 className="size-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <>
            <div className="relative w-full min-w-0 bg-muted overflow-hidden rounded-t-xl">
              {detail.imageUrl && !imgBroken ? (
                <img
                  src={detail.imageUrl}
                  alt=""
                  className="w-full object-cover max-h-60"
                  onError={() => setImgBroken(true)}
                />
              ) : (
                <div className="w-full h-36 flex items-center justify-center">
                  <span className="text-5xl font-bold text-muted-foreground/30 select-none">
                    {detail.title.charAt(0).toUpperCase()}
                  </span>
                </div>
              )}
              <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/75 to-transparent pt-10 pb-3 px-4">
                <DialogTitle className="text-white font-semibold text-base leading-snug line-clamp-2 pr-6">
                  {detail.title}
                </DialogTitle>
                {detail.postedAt && (
                  <p className="text-[11px] text-white/70 mt-0.5">Repacked {detail.postedAt}</p>
                )}
              </div>
            </div>

            <div className="min-w-0 p-4 space-y-3">
              {detail.genres && (
                <div className="flex flex-wrap gap-1">
                  {detail.genres.split(',').map(g => g.trim()).filter(Boolean).slice(0, 6).map(g => (
                    <span key={g} className="rounded-full border px-2 py-0.5 text-[10px] text-muted-foreground">{g}</span>
                  ))}
                </div>
              )}

              {detail.summary && (
                <p className="text-xs text-muted-foreground leading-relaxed line-clamp-4">{detail.summary}</p>
              )}

              <div className="flex flex-wrap gap-2">
                {detail.repackSize && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-2.5 py-1 text-xs font-medium">
                    <Tag className="size-3 text-primary" /> Repack {detail.repackSize}
                  </span>
                )}
                {detail.originalSize && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-2.5 py-1 text-xs font-medium">
                    <HardDrive className="size-3 text-primary" /> Original {detail.originalSize}
                  </span>
                )}
                {detail.steamPrice && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-secondary-foreground">
                    Steam: {detail.steamPrice}
                  </span>
                )}
              </div>

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

              {detail.screenshots.length > 0 && (
                <div className="flex gap-2 overflow-x-auto pb-1">
                  {detail.screenshots.map(src => (
                    <img
                      key={src}
                      src={src}
                      alt=""
                      loading="lazy"
                      className="h-20 shrink-0 rounded-md object-cover"
                      onError={e => { e.currentTarget.style.display = 'none' }}
                    />
                  ))}
                </div>
              )}

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

              {detail.optionalParts.length > 0 && (
                <div className="border-t pt-2.5 space-y-2">
                  <p className="text-[11px] font-medium text-muted-foreground">Optional components</p>
                  <div className="space-y-1">
                    {detail.optionalParts.map(part => (
                      <label
                        key={part}
                        className="flex cursor-pointer items-center gap-2.5 rounded-lg border px-2.5 py-2 transition-colors hover:bg-muted/50"
                      >
                        <input
                          type="checkbox"
                          checked={wanted.has(part)}
                          onChange={() => toggle(part)}
                          className="size-3.5 shrink-0 accent-primary"
                        />
                        <span className="min-w-0 flex-1 truncate text-xs">{partLabel(part)}</span>
                      </label>
                    ))}
                  </div>
                  <p className="text-[11px] text-muted-foreground">
                    Unchecked parts are never downloaded, and the installer skips what is not there.
                  </p>
                </div>
              )}

              <PathRow
                label="Download to"
                value={friendlyPath(downloadPath ?? settings?.downloadPath ?? null)}
                open={picker === 'download'}
                onToggle={() => setPicker(p => (p === 'download' ? null : 'download'))}
              >
                <FolderPicker value={downloadPath} onChange={setDownloadPath} />
              </PathRow>

              <div className="border-t pt-2.5 space-y-2">
                <label className="flex cursor-pointer items-center gap-2 text-xs">
                  <input
                    type="checkbox"
                    checked={autoInstall}
                    onChange={e => setAutoInstall(e.target.checked)}
                    className="size-3.5 accent-primary"
                  />
                  <PackageCheck className="size-3.5 text-primary" />
                  Install automatically when the download finishes
                </label>
                {autoInstall && (
                  <PathRow
                    label="Install to"
                    value={friendlyPath(effectiveInstall, 'Not set - choose a folder')}
                    open={picker === 'install'}
                    onToggle={() => setPicker(p => (p === 'install' ? null : 'install'))}
                    bordered={false}
                  >
                    <FolderPicker value={installPath ?? settings?.installPath ?? null} onChange={setInstallPath} />
                  </PathRow>
                )}
              </div>

              <Button
                onClick={download}
                disabled={adding || !detail.magnet || needsInstallFolder}
                className="w-full gap-2"
              >
                {adding ? (
                  <><Loader2 className="size-4 animate-spin" /> Starting...</>
                ) : !detail.magnet ? (
                  'No magnet link for this repack'
                ) : needsInstallFolder ? (
                  'Choose an install folder first'
                ) : (
                  <><Download className="size-4" /> {autoInstall ? 'Download & install' : 'Download'}</>
                )}
              </Button>

              <a
                href={detail.url}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-center gap-1 text-[11px] text-muted-foreground hover:text-foreground"
              >
                View on FitGirl Repacks <ExternalLink className="size-3" />
              </a>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

function PathRow({ label, value, open, onToggle, bordered = true, children }: {
  label: string
  value: string
  open: boolean
  onToggle: () => void
  bordered?: boolean
  children: React.ReactNode
}) {
  return (
    <div className={`space-y-2 ${bordered ? 'border-t pt-2.5' : ''}`}>
      <button
        type="button"
        onClick={onToggle}
        className="flex w-full items-center gap-2 text-xs text-muted-foreground transition-colors hover:text-foreground"
      >
        <FolderOpen className="size-3.5 shrink-0" />
        <span className="flex-1 truncate text-left">
          {label} <span className="font-medium text-foreground">{value}</span>
        </span>
        <span className="shrink-0">{open ? 'Hide' : 'Change'}</span>
      </button>
      {open && children}
    </div>
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
