import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { NetworkDataPoint } from '../types'

interface Props {
  history: NetworkDataPoint[]
  downloadSpeed: number
  uploadSpeed: number
}

export function NetworkChart({ history, downloadSpeed, uploadSpeed }: Props) {
  return (
    <div className="bg-slate-900 border border-slate-700/50 rounded-xl p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-slate-200">Network Speed</h3>
        <div className="flex gap-3 text-xs font-mono">
          <span className="text-sky-400">↓ {downloadSpeed.toFixed(2)} MB/s</span>
          <span className="text-emerald-400">↑ {uploadSpeed.toFixed(2)} MB/s</span>
        </div>
      </div>

      <div className="h-36">
        {history.length === 0 ? (
          <div className="h-full flex items-center justify-center text-slate-600 text-xs">
            Waiting for data…
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={history} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id="dlGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#38bdf8" stopOpacity={0.25} />
                  <stop offset="95%" stopColor="#38bdf8" stopOpacity={0} />
                </linearGradient>
                <linearGradient id="ulGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#34d399" stopOpacity={0.25} />
                  <stop offset="95%" stopColor="#34d399" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
              <XAxis dataKey="time" stroke="#475569" tick={{ fontSize: 9, fill: '#475569' }} />
              <YAxis stroke="#475569" tick={{ fontSize: 9, fill: '#475569' }} unit=" MB/s" width={52} />
              <Tooltip
                contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: 8, fontSize: 11 }}
                labelStyle={{ color: '#94a3b8' }}
              />
              <Area type="monotone" dataKey="download" stroke="#38bdf8" strokeWidth={2} fill="url(#dlGrad)" dot={false} name="Download" />
              <Area type="monotone" dataKey="upload" stroke="#34d399" strokeWidth={2} fill="url(#ulGrad)" dot={false} name="Upload" />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}
