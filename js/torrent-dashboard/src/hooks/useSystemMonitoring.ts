import { useEffect, useReducer } from 'react'
import { systemApi } from '../services/api'
import type { NetworkDataPoint, SystemInfo } from '../types'

const POLL_MS = 5_000
const MAX_HISTORY = 60

const DEFAULT_SYSTEM: SystemInfo = {
  storage: { total: 0, used: 0, available: 0 },
  network: { downloadSpeed: 0, uploadSpeed: 0 },
}

// Module-level singleton store: history + system survive component remounts
// (navigating between pages) and a single poll loop is shared by all callers,
// so the network graph keeps building instead of resetting on every mount.
let system: SystemInfo = DEFAULT_SYSTEM
let history: NetworkDataPoint[] = []
let started = false
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach(fn => fn())
}

async function poll() {
  try {
    const data = await systemApi.getInfo()
    system = data
    const time = new Date().toLocaleTimeString('en-US', {
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
    history = [
      ...history,
      { time, download: data.network.downloadSpeed, upload: data.network.uploadSpeed },
    ].slice(-MAX_HISTORY)
    emit()
  } catch {
    // non-critical — keep previous values
  }
}

function ensureStarted() {
  if (started) return
  started = true
  poll()
  setInterval(poll, POLL_MS)
}

export function useSystemMonitoring() {
  const [, force] = useReducer((x: number) => x + 1, 0)
  useEffect(() => {
    listeners.add(force)
    ensureStarted()
    return () => {
      listeners.delete(force)
    }
  }, [])
  return { system, history }
}
