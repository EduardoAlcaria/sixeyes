import type { Torrent } from '../types'

interface Props {
  torrents: Torrent[]
}

function parseSpeed(s: string): number {
  const v = parseFloat(s.replace('MB/s', '').trim())
  return isNaN(v) ? 0 : v
}

function StatCard({ label, value, sub, accent }: { label: string; value: string; sub: string; accent: string }) {
  return (
    <div className="bg-slate-900 border border-slate-700/50 rounded-xl p-4">
      <p className="text-xs text-slate-500 mb-1 uppercase tracking-wide">{label}</p>
      <p className={`text-xl font-semibold font-mono ${accent}`}>{value}</p>
      <p className="text-xs text-slate-600 mt-1">{sub}</p>
    </div>
  )
}

export function StatsBar({ torrents }: Props) {
  const downloading = torrents.filter(t => t.status === 'Downloading')
  const seeding = torrents.filter(t => t.status === 'Seeding')
  const totalDown = downloading.reduce((s, t) => s + parseSpeed(t.downloadSpeed), 0)
  const totalUp = torrents.reduce((s, t) => s + parseSpeed(t.uploadSpeed), 0)

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <StatCard
        label="Download"
        value={`${totalDown.toFixed(2)} MB/s`}
        sub={`${downloading.length} active`}
        accent="text-sky-400"
      />
      <StatCard
        label="Upload"
        value={`${totalUp.toFixed(2)} MB/s`}
        sub={`${seeding.length} seeding`}
        accent="text-emerald-400"
      />
      <StatCard
        label="Total"
        value={`${torrents.length}`}
        sub="torrents tracked"
        accent="text-indigo-400"
      />
      <StatCard
        label="Completed"
        value={`${seeding.length + torrents.filter(t => t.progress >= 100).length}`}
        sub="finished downloads"
        accent="text-violet-400"
      />
    </div>
  )
}
