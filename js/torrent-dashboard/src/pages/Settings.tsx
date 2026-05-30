import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { useSettings } from '@/hooks/useSettings'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

// /system/disks already reports GB — format as-is, do not re-divide.
function gb(value: number): string {
  return `${value.toFixed(0)} GB`
}

export function SettingsPage() {
  const { settings, disks, saving, saved, save } = useSettings()
  const [path, setPath] = useState('')

  useEffect(() => {
    if (settings) setPath(settings.downloadPath)
  }, [settings])

  useEffect(() => {
    if (saved) toast.success('Settings saved')
  }, [saved])

  return (
    <div className="space-y-6 max-w-2xl">
      <h1 className="text-2xl font-bold tracking-tight">Settings</h1>

      <Card>
        <CardHeader>
          <CardTitle>Download location</CardTitle>
          <CardDescription>Where finished files are stored on the host.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={e => {
              e.preventDefault()
              save(path)
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="downloadPath">Download path</Label>
              <Input
                id="downloadPath"
                value={path}
                onChange={e => setPath(e.target.value)}
                placeholder="/app/downloads"
              />
            </div>

            {disks.length > 0 && (
              <div className="space-y-2">
                <Label htmlFor="disk">Pick a disk</Label>
                <select
                  id="disk"
                  className="flex h-9 w-full rounded-lg border border-input bg-transparent px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
                  value=""
                  onChange={e => e.target.value && setPath(e.target.value)}
                >
                  <option value="">Choose a mounted disk…</option>
                  {disks.map(d => (
                    <option key={d.path} value={d.path}>
                      {d.path} — {gb(d.available)} free of {gb(d.total)}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <Button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
