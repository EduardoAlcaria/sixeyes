import { useMemo } from 'react'
import type { TorrentHandlers } from '@/components/deck/TorrentActions'
import { useSharedTorrents } from './useTorrents'

/** The shared torrent actions in the shape the deck's buttons take. */
export function useTorrentHandlers(open?: (id: number) => void): TorrentHandlers {
  const t = useSharedTorrents()
  return useMemo(() => ({
    pause: t.pauseTorrent,
    stop: t.stopTorrent,
    resume: t.resumeTorrent,
    remove: t.removeTorrent,
    install: t.installGame,
    cancelInstall: t.cancelInstall,
    open,
  }), [t.pauseTorrent, t.stopTorrent, t.resumeTorrent, t.removeTorrent, t.installGame, t.cancelInstall, open])
}
