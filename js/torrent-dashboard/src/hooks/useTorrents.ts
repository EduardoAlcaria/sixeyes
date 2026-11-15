import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { torrentApi } from '../services/api'
import type { CompletedTorrent, Torrent } from '../types'

const POLL_MS = 5_000

// Toast id = message, so a backend outage shows one toast instead of one per poll.
function showError(e: unknown) {
  const msg = e instanceof Error ? e.message : 'Request failed'
  toast.error(msg, { id: msg })
}

export function useTorrents() {
  const [torrents, setTorrents] = useState<Torrent[]>([])
  const [completed, setCompleted] = useState<CompletedTorrent[]>([])
  const [loading, setLoading] = useState(false)

  const fetchTorrents = useCallback(async () => {
    try {
      setTorrents(await torrentApi.getAll())
    } catch (e) {
      showError(e)
    }
  }, [])

  const fetchCompleted = useCallback(async () => {
    try {
      setCompleted(await torrentApi.getCompleted())
    } catch {
      // non-critical
    }
  }, [])

  useEffect(() => {
    fetchTorrents()
    fetchCompleted()
    const id = setInterval(() => { fetchTorrents(); fetchCompleted() }, POLL_MS)
    return () => clearInterval(id)
  }, [fetchTorrents, fetchCompleted])

  /** Runs an action, reports failure, and resolves to whether it succeeded. */
  const run = useCallback(async (action: () => Promise<unknown>): Promise<boolean> => {
    try {
      await action()
      return true
    } catch (e) {
      showError(e)
      return false
    }
  }, [])

  const addTorrent = useCallback(async (magnet: string, downloadPath?: string) => {
    setLoading(true)
    const ok = await run(() => torrentApi.add(magnet, { downloadPath }))
    if (ok) await fetchTorrents()
    setLoading(false)
    return ok
  }, [run, fetchTorrents])

  const addTorrentFile = useCallback(async (file: File, downloadPath?: string) => {
    setLoading(true)
    const ok = await run(() => torrentApi.addFile(file, downloadPath))
    if (ok) await fetchTorrents()
    setLoading(false)
    return ok
  }, [run, fetchTorrents])

  const replace = useCallback((updated: Torrent) =>
    setTorrents(prev => prev.map(t => (t.id === updated.id ? updated : t))), [])

  const pauseTorrent = useCallback((id: number) =>
    run(async () => replace(await torrentApi.pause(id))), [run, replace])

  const stopTorrent = useCallback((id: number) =>
    run(async () => replace(await torrentApi.stop(id))), [run, replace])

  const resumeTorrent = useCallback((id: number) =>
    run(async () => replace(await torrentApi.resume(id))), [run, replace])

  const installGame = useCallback(async (id: number) => {
    const ok = await run(() => torrentApi.install(id))
    await Promise.all([fetchTorrents(), fetchCompleted()])
    return ok
  }, [run, fetchTorrents, fetchCompleted])

  const cancelInstall = useCallback(async (id: number) => {
    const ok = await run(() => torrentApi.cancelInstall(id))
    await Promise.all([fetchTorrents(), fetchCompleted()])
    return ok
  }, [run, fetchTorrents, fetchCompleted])

  const removeTorrent = useCallback(async (id: number, deleteFiles = false) => {
    setTorrents(p => p.filter(t => t.id !== id)) // optimistic
    const ok = await run(() => torrentApi.remove(id, deleteFiles))
    if (!ok) await fetchTorrents() // restore from the server, not a stale snapshot
    return ok
  }, [run, fetchTorrents])

  return {
    torrents,
    completed,
    loading,
    addTorrent,
    addTorrentFile,
    pauseTorrent,
    stopTorrent,
    resumeTorrent,
    removeTorrent,
    installGame,
    cancelInstall,
  }
}

export type TorrentsState = ReturnType<typeof useTorrents>

/** One poll for the whole signed-in app: the shell provides it, pages read it. */
export const TorrentsContext = createContext<TorrentsState | null>(null)

export function useSharedTorrents(): TorrentsState {
  const ctx = useContext(TorrentsContext)
  if (!ctx) throw new Error('useSharedTorrents needs the DeckShell provider')
  return ctx
}
