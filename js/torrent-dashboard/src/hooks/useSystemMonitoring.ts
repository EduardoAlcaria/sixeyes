import { useCallback, useEffect, useState } from 'react'
import { systemApi } from '../services/api'
import type { NetworkDataPoint, SystemInfo } from '../types'

const POLL_MS = 5_000
const MAX_HISTORY = 20

const DEFAULT_SYSTEM: SystemInfo = {
  storage: { total: 0, used: 0, available: 0 },
  network: { downloadSpeed: 0, uploadSpeed: 0 },
}

export function useSystemMonitoring() {
  const [system, setSystem] = useState<SystemInfo>(DEFAULT_SYSTEM)
  const [history, setHistory] = useState<NetworkDataPoint[]>([])

  const fetchSystem = useCallback(async () => {
    try {
      const data = await systemApi.getInfo()
      setSystem(data)
      const time = new Date().toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
      setHistory(prev =>
        [...prev, { time, download: data.network.downloadSpeed, upload: data.network.uploadSpeed }].slice(-MAX_HISTORY)
      )
    } catch {
      // non-critical — keep previous values
    }
  }, [])

  useEffect(() => {
    fetchSystem()
    const id = setInterval(fetchSystem, POLL_MS)
    return () => clearInterval(id)
  }, [fetchSystem])

  return { system, history }
}
