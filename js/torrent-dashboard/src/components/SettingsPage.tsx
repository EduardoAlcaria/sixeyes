import { useState } from 'react'
import { useSettings } from '../hooks/useSettings'
import type { DiskInfo } from '../types'

function formatGb(gb: number): string {
  if (gb >= 1024) return `${(gb / 1024).toFixed(1)} TB`
  return `${gb.toFixed(1)} GB`
}

function DiskRow({ disk, selected, onSelect }: { disk: DiskInfo; selected: boolean; onSelect: () => void }) {
  const usedPct = disk.total > 0 ? (disk.used / disk.total) * 100 : 0
  const barColor = usedPct > 90 ? 'bg-red-500' : usedPct > 70 ? 'bg-amber-500' : 'bg-indigo-500'

  return (
    <button
      onClick={onSelect}
      className={`w-full text-left p-4 rounded-xl border transition-all duration-150 ${
        selected
          ? 'border-indigo-500 bg-indigo-500/10'
          : 'border-slate-700/50 bg-slate-800/50 hover:border-slate-600'
      }`}
    >
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <svg className={`w-4 h-4 ${selected ? 'text-indigo-400' : 'text-slate-500'}`} fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2" />
          </svg>
          <span className={`text-sm font-mono font-medium ${selected ? 'text-indigo-300' : 'text-slate-300'}`}>
            {disk.device}
          </span>
          <span className="text-xs text-slate-600 font-mono">({disk.path})</span>
        </div>
        {selected && (
          <span className="text-xs text-indigo-400 bg-indigo-400/10 border border-indigo-400/20 rounded-md px-2 py-0.5">
            Selected
          </span>
        )}
      </div>

      <div className="h-1.5 bg-slate-700 rounded-full overflow-hidden mb-2">
        <div className={`h-full rounded-full ${barColor}`} style={{ width: `${Math.min(usedPct, 100)}%` }} />
      </div>

      <div className="flex justify-between text-xs text-slate-500">
        <span>{formatGb(disk.used)} used</span>
        <span className="text-slate-400 font-medium">{formatGb(disk.available)} free</span>
        <span>{formatGb(disk.total)} total</span>
      </div>
    </button>
  )
}

export function SettingsPage() {
  const { settings, disks, loading, saving, error, saved, save } = useSettings()
  const [selectedDrive, setSelectedDrive] = useState<string | null>(null)
  const [subdir, setSubdir] = useState('')

  const baseDrive = selectedDrive ?? (settings?.downloadPath ? deriveDrive(settings.downloadPath, disks) : null)
  const currentPath = buildPath(baseDrive, subdir) || settings?.downloadPath || ''

  return (
    <div className="max-w-2xl mx-auto px-6 py-8 space-y-6">
      <div>
        <h2 className="text-base font-semibold text-slate-100">Settings</h2>
        <p className="text-sm text-slate-500 mt-1">Choose where torrents are saved.</p>
      </div>

      {error && (
        <div className="text-sm text-red-400 bg-red-400/10 border border-red-400/20 rounded-xl px-4 py-3">
          {error}
        </div>
      )}

      <div className="bg-slate-900 border border-slate-700/50 rounded-2xl p-5 space-y-4">
        <h3 className="text-sm font-semibold text-slate-300">Download Location</h3>

        {loading ? (
          <div className="space-y-3">
            {[1, 2].map(i => (
              <div key={i} className="h-20 bg-slate-800 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : disks.length === 0 ? (
          <p className="text-sm text-slate-500 text-center py-6">
            No disks available. Make sure the Python engine is running.
          </p>
        ) : (
          <div className="space-y-2">
            {disks.map(disk => (
              <DiskRow
                key={disk.path}
                disk={disk}
                selected={baseDrive === disk.path}
                onSelect={() => { setSelectedDrive(disk.path); setSubdir('') }}
              />
            ))}
          </div>
        )}

        {baseDrive && (
          <div className="pt-1 space-y-1">
            <label className="text-xs text-slate-400">Subdirectory <span className="text-slate-600">(optional)</span></label>
            <input
              type="text"
              value={subdir}
              onChange={e => setSubdir(e.target.value)}
              placeholder="downloads"
              className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500/50 transition-colors"
            />
            <p className="text-xs text-slate-600">
              Full path: <span className="font-mono text-slate-500">{currentPath}</span>
            </p>
          </div>
        )}

        <button
          onClick={() => save(currentPath)}
          disabled={saving || !currentPath || currentPath === settings?.downloadPath}
          className="w-full py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
        >
          {saving ? 'Saving…' : saved ? 'Saved!' : 'Save'}
        </button>
      </div>
    </div>
  )
}

function deriveDrive(downloadPath: string, disks: DiskInfo[]): string | null {
  return disks.find(d => downloadPath.startsWith(d.path))?.path ?? disks[0]?.path ?? null
}

function buildPath(drive: string | null, subdir: string): string {
  if (!drive) return ''
  const trimmed = subdir.trim().replace(/^\/+/, '')
  return trimmed ? `${drive}/${trimmed}` : drive
}
