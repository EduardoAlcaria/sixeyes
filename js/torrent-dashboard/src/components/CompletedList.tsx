import type { CompletedTorrent } from '../types'

interface Props {
  items: CompletedTorrent[]
}

export function CompletedList({ items }: Props) {
  return (
    <div className="bg-slate-900 border border-slate-700/50 rounded-xl p-4">
      <h3 className="text-sm font-semibold text-slate-200 mb-3">Completed</h3>

      {items.length === 0 ? (
        <p className="text-slate-600 text-xs text-center py-4">No completed downloads yet</p>
      ) : (
        <div className="space-y-2 max-h-56 overflow-y-auto pr-1 custom-scrollbar">
          {items.map(t => (
            <div key={t.id} className="flex items-start gap-2.5 p-2.5 rounded-lg bg-slate-800/60 hover:bg-slate-800 transition-colors">
              <div className="w-5 h-5 rounded-full bg-emerald-500/10 flex items-center justify-center flex-shrink-0 mt-0.5">
                <svg className="w-3 h-3 text-emerald-400" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-xs text-slate-200 font-medium truncate">{t.title ?? 'Unknown'}</p>
                <p className="text-xs text-slate-500 mt-0.5 font-mono">{t.size} · {t.completedAt}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
