import type { Torrent, TorrentStatus } from '../types'

interface Props {
  torrent: Torrent
  onPause: (id: number) => void
  onResume: (id: number) => void
  onRemove: (id: number) => void
}

const STATUS_STYLES: Record<TorrentStatus, { badge: string; bar: string; dot: string }> = {
  Downloading: { badge: 'text-sky-400 bg-sky-400/10 border-sky-400/20', bar: 'bg-sky-500', dot: 'bg-sky-400' },
  Seeding:     { badge: 'text-emerald-400 bg-emerald-400/10 border-emerald-400/20', bar: 'bg-emerald-500', dot: 'bg-emerald-400' },
  Paused:      { badge: 'text-amber-400 bg-amber-400/10 border-amber-400/20', bar: 'bg-amber-500', dot: 'bg-amber-400' },
  Stopped:     { badge: 'text-slate-400 bg-slate-400/10 border-slate-400/20', bar: 'bg-slate-500', dot: 'bg-slate-400' },
  Error:       { badge: 'text-red-400 bg-red-400/10 border-red-400/20', bar: 'bg-red-500', dot: 'bg-red-400' },
}

export function TorrentCard({ torrent, onPause, onResume, onRemove }: Props) {
  const styles = STATUS_STYLES[torrent.status] ?? STATUS_STYLES.Stopped
  const canControl = torrent.status === 'Downloading' || torrent.status === 'Seeding' || torrent.status === 'Paused'
  const isPaused = torrent.status === 'Paused'

  return (
    <div className="group relative bg-slate-900 border border-slate-700/50 rounded-xl p-4 hover:border-slate-600/70 transition-all duration-200">

      {/* Header */}
      <div className="flex items-start justify-between gap-2 mb-3">
        <div className="flex items-center gap-2 min-w-0">
          <span className={`w-2 h-2 rounded-full flex-shrink-0 ${styles.dot}`} />
          <h3 className="text-slate-100 font-medium text-sm truncate leading-tight">
            {torrent.title ?? 'Fetching metadata…'}
          </h3>
        </div>
        <div className="flex items-center gap-1 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
          {canControl && (
            <button
              onClick={() => (isPaused ? onResume(torrent.id) : onPause(torrent.id))}
              className="w-7 h-7 flex items-center justify-center rounded-lg text-slate-400 hover:text-amber-400 hover:bg-amber-400/10 transition-colors"
              title={isPaused ? 'Resume' : 'Pause'}
            >
              {isPaused ? (
                <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M8 5v14l11-7z" />
                </svg>
              ) : (
                <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
                </svg>
              )}
            </button>
          )}
          <button
            onClick={() => onRemove(torrent.id)}
            className="w-7 h-7 flex items-center justify-center rounded-lg text-slate-400 hover:text-red-400 hover:bg-red-400/10 transition-colors"
            title="Remove"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>

      {/* Status + size */}
      <div className="flex items-center gap-2 mb-3">
        <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium border ${styles.badge}`}>
          {torrent.status}
        </span>
        {torrent.size && torrent.size !== '0 B' && (
          <span className="text-slate-500 text-xs font-mono">{torrent.size}</span>
        )}
      </div>

      {/* Progress */}
      <div className="mb-3">
        <div className="flex justify-between text-xs mb-1.5">
          <span className="text-slate-500">Progress</span>
          <span className="text-slate-300 font-mono">{torrent.progress.toFixed(1)}%</span>
        </div>
        <div className="h-1.5 bg-slate-800 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${styles.bar}`}
            style={{ width: `${Math.min(torrent.progress, 100)}%` }}
          />
        </div>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-3 gap-2 text-xs">
        <div>
          <p className="text-slate-500 mb-0.5">Download</p>
          <p className="text-sky-400 font-mono font-medium">{torrent.downloadSpeed}</p>
        </div>
        <div>
          <p className="text-slate-500 mb-0.5">Upload</p>
          <p className="text-emerald-400 font-mono font-medium">{torrent.uploadSpeed}</p>
        </div>
        <div>
          <p className="text-slate-500 mb-0.5">Peers</p>
          <p className="text-slate-300 font-mono font-medium">{torrent.peers}</p>
        </div>
      </div>

      {torrent.eta && (
        <p className="text-slate-500 text-xs mt-2 font-mono">ETA: {torrent.eta}</p>
      )}
    </div>
  )
}
