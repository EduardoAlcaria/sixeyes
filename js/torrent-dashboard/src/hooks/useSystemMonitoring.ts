import { useEffect, useReducer } from 'react'
import { systemApi } from '../services/api'
import type { NetworkDataPoint, SystemInfo } from '../types'

const POLL_MS = 5_000
const MAX_HISTORY = 60

const DEFAULT_SYSTEM: SystemInfo = {
  storage: { total: 0, used: 0, available: 0 },
  network: { downloadSpeed: 0, uploadSpeed: 0 },
}

const HISTORY_KEY = 'sixeyes:netHistory'

function loadHistory(): NetworkDataPoint[] {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.slice(-MAX_HISTORY) : []
  } catch {
    return []
  }
}

// Module-level singleton store: history + system survive component remounts
// (navigating between pages) and a single poll loop is shared by all callers.
// History is also mirrored to localStorage so a full page reload keeps the graph.
let system: SystemInfo = DEFAULT_SYSTEM
let history: NetworkDataPoint[] = loadHistory()
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
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(history))
    } catch {
      // storage full / unavailable — graph still works in-memory
    }
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
