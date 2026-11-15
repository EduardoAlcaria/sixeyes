import { useCallback, useEffect, useState } from 'react'
import { settingsApi, systemApi } from '../services/api'
import type { DiskInfo, Settings } from '../types'

export function useSettings() {
  const [settings, setSettings] = useState<Settings | null>(null)
  const [disks, setDisks] = useState<DiskInfo[]>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const load = useCallback(async () => {
    try {
      const [s, d] = await Promise.all([settingsApi.get(), systemApi.getDisks()])
      setSettings(s)
      setDisks(d)
    } catch (e) {
      setError((e as Error).message)
    }
  }, [])

  useEffect(() => { load() }, [load])

  async function save(next: Settings) {
    setSaving(true)
    setError(null)
    try {
      setSettings(await settingsApi.update(next))
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setSaving(false)
    }
  }

  return { settings, disks, saving, saved, error, save }
}
