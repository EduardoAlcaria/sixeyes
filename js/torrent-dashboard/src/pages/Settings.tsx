import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { useSettings } from '@/hooks/useSettings'
import { authApi, type Device } from '@/services/api'
import { FolderPicker } from '@/components/torrents/FolderPicker'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

// /system/disks already reports GB - format as-is, do not re-divide.
function gb(value: number): string {
  return `${value.toFixed(0)} GB`
}

export function SettingsPage() {
  const { settings, disks, saving, saved, error, save } = useSettings()
  const [downloadPath, setDownloadPath] = useState('')
  const [installPath, setInstallPath] = useState('')

  useEffect(() => {
    if (settings) {
      setDownloadPath(settings.downloadPath)
      setInstallPath(settings.installPath ?? '')
    }
  }, [settings])

  useEffect(() => {
    if (saved) toast.success('Settings saved')
  }, [saved])

  useEffect(() => {
    if (error) toast.error(error)
  }, [error])

  return (
    <div className="space-y-6 max-w-2xl">
      <h1 className="text-2xl font-bold tracking-tight">Settings</h1>

      <Card>
        <CardHeader>
          <CardTitle>Folders</CardTitle>
          <CardDescription>
            Defaults for new downloads. The download dialog can override both per game.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={e => {
              e.preventDefault()
              save({ downloadPath: downloadPath.trim(), installPath: installPath.trim() || null })
            }}
            className="space-y-5"
          >
            <PathField
              id="downloadPath"
              label="Download folder"
              hint="Where repacks are downloaded to."
              value={downloadPath}
              onChange={setDownloadPath}
              placeholder="D:\Downloads"
            />

            {disks.length > 0 && (
              <div className="space-y-2">
                <Label htmlFor="disk">Free space</Label>
                <ul id="disk" className="grid gap-1 text-xs text-muted-foreground sm:grid-cols-2">
                  {disks.map(d => (
                    <li key={d.path} className="rounded-md border px-2.5 py-1.5">
                      <span className="font-medium text-foreground">{d.device}</span> {gb(d.available)} free of {gb(d.total)}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <PathField
              id="installPath"
              label="Install folder"
              hint="Games install into a sub-folder here. Leave empty to choose per game."
              value={installPath}
              onChange={setInstallPath}
              placeholder="D:\Games"
            />

            <Button type="submit" disabled={saving || !downloadPath.trim()}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <DevicesCard />
    </div>
  )
}

function PathField({ id, label, hint, value, onChange, placeholder }: {
  id: string
  label: string
  hint: string
  value: string
  onChange: (v: string) => void
  placeholder: string
}) {
  const [browsing, setBrowsing] = useState(false)
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <div className="flex gap-2">
        <Input id={id} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} />
        <Button type="button" variant="outline" onClick={() => setBrowsing(b => !b)}>
          {browsing ? 'Done' : 'Browse'}
        </Button>
      </div>
      {browsing && <FolderPicker value={value || null} onChange={p => p && onChange(p)} />}
      <p className="text-xs text-muted-foreground">{hint}</p>
    </div>
  )
}

/**
 * Approve or remove the browsers allowed to sign in. A correct password on an
 * unrecognised device is not enough - it lands here as PENDING until approved from a
 * device that already is.
 */
function DevicesCard() {
  const [devices, setDevices] = useState<Device[]>([])
  const [busy, setBusy] = useState<number | null>(null)

  const refresh = useCallback(() => {
    authApi.devices().then(setDevices).catch(() => undefined)
  }, [])

  useEffect(refresh, [refresh])

  async function act(d: Device, action: 'approve' | 'remove') {
    if (action === 'remove' && d.current
        && !window.confirm('This is the browser you are using now. Removing it signs you out here. Continue?')) {
      return
    }
    setBusy(d.id)
    try {
      if (action === 'approve') {
        await authApi.approveDevice(d.id)
        toast.success('Device approved')
      } else {
        const { revokedSessions } = await authApi.removeDevice(d.id)
        toast.success(`Device removed - ${revokedSessions} session(s) signed out`)
      }
      refresh()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed')
    } finally {
      setBusy(null)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Devices</CardTitle>
        <CardDescription>
          Browsers allowed to sign in. Removing one signs out its sessions immediately.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {devices.length === 0 && (
          <p className="text-sm text-muted-foreground">No devices recorded yet.</p>
        )}
        {devices.map(d => (
          <div key={d.id} className="flex items-center justify-between gap-3 rounded-lg border p-3">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">
                {d.label}
                {d.current && <span className="ml-2 text-xs font-normal text-primary">(this device)</span>}
              </p>
              <p className="text-xs text-muted-foreground">
                {d.status === 'TRUSTED' ? 'Approved' : 'Awaiting approval'}
                {d.lastSeenAt && ` · last seen ${new Date(d.lastSeenAt).toLocaleString()}`}
              </p>
            </div>
            <div className="flex shrink-0 gap-2">
              {d.status === 'PENDING' && (
                <Button size="sm" disabled={busy === d.id} onClick={() => act(d, 'approve')}>
                  Approve
                </Button>
              )}
              <Button
                size="sm"
                variant="outline"
                disabled={busy === d.id}
                onClick={() => act(d, 'remove')}
              >
                Remove
              </Button>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
