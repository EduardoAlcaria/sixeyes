import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'

interface Props {
  total: number
  used: number
  available: number
}

export function StorageChart({ total, used, available }: Props) {
  const usedPct = total > 0 ? ((used / total) * 100).toFixed(1) : '0.0'

  const data = [
    { name: 'Used', value: used, color: '#f43f5e' },
    { name: 'Available', value: available, color: '#10b981' },
  ]

  return (
    <div className="bg-slate-900 border border-slate-700/50 rounded-xl p-4">
      <h3 className="text-sm font-semibold text-slate-200 mb-3">Storage</h3>

      <div className="flex items-center gap-4">
        <div className="w-24 h-24 flex-shrink-0">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie data={data} cx="50%" cy="50%" innerRadius={28} outerRadius={44} paddingAngle={3} dataKey="value" strokeWidth={0}>
                {data.map((entry, i) => (
                  <Cell key={i} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: 8, fontSize: 11 }}
                formatter={(v: number) => [`${v.toFixed(1)} GB`]}
              />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="flex-1 space-y-2.5">
          <div className="text-center">
            <p className="text-2xl font-bold text-slate-200 font-mono">{usedPct}%</p>
            <p className="text-xs text-slate-500">used of {total.toFixed(1)} GB</p>
          </div>
          <div className="space-y-1.5">
            <div className="flex justify-between text-xs">
              <span className="flex items-center gap-1.5 text-slate-400">
                <span className="w-2 h-2 rounded-full bg-rose-500" />Used
              </span>
              <span className="text-slate-300 font-mono">{used.toFixed(1)} GB</span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="flex items-center gap-1.5 text-slate-400">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />Free
              </span>
              <span className="text-slate-300 font-mono">{available.toFixed(1)} GB</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
