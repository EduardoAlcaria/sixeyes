import { useState } from 'react'

interface Props {
  onAdd: (magnet: string) => void
  loading: boolean
}

export function AddTorrentForm({ onAdd, loading }: Props) {
  const [open, setOpen] = useState(false)
  const [magnet, setMagnet] = useState('')

  const submit = () => {
    const val = magnet.trim()
    if (!val) return
    onAdd(val)
    setMagnet('')
    setOpen(false)
  }

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="w-full h-full min-h-[140px] flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-slate-700 hover:border-indigo-500/50 hover:bg-indigo-500/5 transition-all duration-200 group"
      >
        <div className="w-10 h-10 rounded-full bg-slate-800 group-hover:bg-indigo-500/10 flex items-center justify-center transition-colors">
          <svg className="w-5 h-5 text-slate-500 group-hover:text-indigo-400 transition-colors" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
        </div>
        <span className="text-sm text-slate-500 group-hover:text-slate-400 transition-colors">Add Torrent</span>
      </button>
    )
  }

  return (
    <div className="bg-slate-900 border border-slate-700/50 rounded-xl p-4 flex flex-col gap-3">
      <p className="text-sm font-medium text-slate-300">Add new torrent</p>
      <textarea
        value={magnet}
        onChange={e => setMagnet(e.target.value)}
        onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit() } }}
        placeholder="magnet:?xt=urn:btih:..."
        autoFocus
        disabled={loading}
        rows={3}
        className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 placeholder-slate-600 font-mono resize-none focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500/50 transition-colors disabled:opacity-50"
      />
      <div className="flex gap-2">
        <button
          onClick={submit}
          disabled={loading || !magnet.trim()}
          className="flex-1 py-2 px-4 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
        >
          {loading ? 'Adding…' : 'Add Torrent'}
        </button>
        <button
          onClick={() => { setOpen(false); setMagnet('') }}
          disabled={loading}
          className="py-2 px-3 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors text-sm"
        >
          Cancel
        </button>
      </div>
    </div>
  )
}
