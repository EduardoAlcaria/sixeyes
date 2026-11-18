import { useState } from 'react'
import { Download, Pause, Play, Search, Square, Trash2, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { Torrent } from '@/types'

export interface TorrentHandlers {
  pause: (id: number) => unknown
  stop: (id: number) => unknown
  resume: (id: number) => unknown
  remove: (id: number, deleteFiles: boolean) => unknown
  install: (id: number) => unknown
  cancelInstall: (id: number) => unknown
  open?: (id: number) => void
}

function Btn({ onClick, label, icon, tone, verbose }: {
  onClick: () => void
  label: string
  icon: React.ReactNode
  tone?: 'cyan' | 'bad' | 'ok'
  verbose?: boolean
}) {
  return (
    <button type="button" title={label} aria-label={label}
      onClick={e => { e.stopPropagation(); onClick() }}
      className={cn('inline-flex h-8 items-center justify-center gap-1.5 rounded-lg border bg-panel2 text-xs font-semibold text-muted-foreground hover:text-foreground',
        verbose ? 'px-3' : 'w-8',
        tone === 'cyan' && 'text-cyan', tone === 'bad' && 'hover:text-bad', tone === 'ok' && 'text-ok')}>
      {icon}{verbose && label}
    </button>
  )
}

export function TorrentActions({ torrent: t, handlers, verbose }: {
  torrent: Torrent
  handlers: TorrentHandlers
  verbose?: boolean
}) {
  const [confirming, setConfirming] = useState(false)
  const done = t.progress >= 100 || t.status === 'Seeding'
  const running = t.status === 'Downloading' || t.status === 'Seeding'
  const installBusy = t.installStatus === 'VERIFYING' || t.installStatus === 'INSTALLING'
  const canInstall = done && t.installable && !installBusy && t.installStatus !== 'INSTALLED' && t.installStatus !== 'REQUESTED'

  if (confirming) {
    return (
      <div className="flex flex-wrap items-center gap-1.5 text-xs" onClick={e => e.stopPropagation()}>
        <span className="text-muted-foreground">Remove?</span>
        <Btn verbose label="Torrent only" icon={<Trash2 className="size-3.5" />}
          onClick={() => { handlers.remove(t.id, false); setConfirming(false) }} />
        <Btn verbose tone="bad" label="Apagar arquivos" icon={<Trash2 className="size-3.5" />}
          onClick={() => { handlers.remove(t.id, true); setConfirming(false) }} />
        <Btn label="Cancel" icon={<X className="size-3.5" />} onClick={() => setConfirming(false)} />
      </div>
    )
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {handlers.open && (
        <Btn verbose tone="cyan" label="Details" icon={<Search className="size-3.5" />} onClick={() => handlers.open!(t.id)} />
      )}
      {canInstall && <Btn verbose={verbose} tone="ok" label="Install" icon={<Download className="size-3.5" />} onClick={() => handlers.install(t.id)} />}
      {t.installStatus === 'REQUESTED' && (
        <Btn verbose={verbose} label="Cancel install" icon={<X className="size-3.5" />} onClick={() => handlers.cancelInstall(t.id)} />
      )}
      {running
        ? <Btn verbose={verbose} label="Pause" icon={<Pause className="size-3.5" />} onClick={() => handlers.pause(t.id)} />
        : <Btn verbose={verbose} label="Retomar" icon={<Play className="size-3.5" />} onClick={() => handlers.resume(t.id)} />}
      {running && <Btn verbose={verbose} label="Stop" icon={<Square className="size-3.5" />} onClick={() => handlers.stop(t.id)} />}
      <Btn verbose={verbose} tone="bad" label="Remove" icon={<Trash2 className="size-3.5" />} onClick={() => setConfirming(true)} />
    </div>
  )
}
