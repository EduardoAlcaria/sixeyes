import { useCallback, useEffect, useRef, useState } from 'react'
import { torrentApi } from '../services/api'
import type { CompletedTorrent, Torrent } from '../types'

const POLL_MS = 5_000

export function useTorrents() {
  const [torrents, setTorrents] = useState<Torrent[]>([])
  const [completed, setCompleted] = useState<CompletedTorrent[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const errorTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const showError = useCallback((msg: string) => {
    setError(msg)
    if (errorTimer.current) clearTimeout(errorTimer.current)
    errorTimer.current = setTimeout(() => setError(null), 5_000)
  }, [])

  const fetchTorrents = useCallback(async () => {
    try {
      const data = await torrentApi.getAll()
      setTorrents(data)
    } catch (e) {
      showError((e as Error).message)
    }
  }, [showError])

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

  const addTorrent = useCallback(async (magnet: string, downloadPath?: string) => {
    setLoading(true)
    try {
      await torrentApi.add(magnet, downloadPath)
      await fetchTorrents()
      setError(null)
    } catch (e) {
      showError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [fetchTorrents, showError])

  const addTorrentFile = useCallback(async (file: File, downloadPath?: string) => {
    setLoading(true)
    try {
      await torrentApi.addFile(file, downloadPath)
      await fetchTorrents()
      setError(null)
    } catch (e) {
      showError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [fetchTorrents, showError])

  const pauseTorrent = useCallback(async (id: number) => {
    try {
      const updated = await torrentApi.pause(id)
      setTorrents(prev => prev.map(t => (t.id === id ? updated : t)))
    } catch (e) {
      showError((e as Error).message)
    }
  }, [showError])

  const resumeTorrent = useCallback(async (id: number) => {
    try {
      const updated = await torrentApi.resume(id)
      setTorrents(prev => prev.map(t => (t.id === id ? updated : t)))
    } catch (e) {
      showError((e as Error).message)
    }
  }, [showError])

  const installGame = useCallback(async (id: number) => {
    try {
      await torrentApi.install(id)
      await fetchCompleted()
    } catch (e) {
      showError((e as Error).message)
    }
  }, [fetchCompleted, showError])

  const removeTorrent = useCallback(async (id: number, deleteFiles = false) => {
    let prev: Torrent[] = []
    setTorrents(p => { prev = p; return p.filter(t => t.id !== id) }) // optimistic
    try {
      await torrentApi.remove(id, deleteFiles)
    } catch (e) {
      setTorrents(prev) // rollback
      showError((e as Error).message)
    }
  }, [showError])

  return {
    torrents,
    completed,
    loading,
    error,
    addTorrent,
    addTorrentFile,
    pauseTorrent,
    resumeTorrent,
    removeTorrent,
    installGame,
  }
}
