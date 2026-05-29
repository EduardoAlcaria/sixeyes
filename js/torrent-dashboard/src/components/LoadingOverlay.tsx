interface Props {
  message?: string
}

export function LoadingOverlay({ message = 'Loading…' }: Props) {
  return (
    <div className="fixed inset-0 bg-slate-950/70 backdrop-blur-sm z-50 flex items-center justify-center">
      <div className="bg-slate-900 border border-slate-700 rounded-2xl px-8 py-6 flex flex-col items-center gap-4 shadow-2xl">
        <div className="relative w-10 h-10">
          <div className="absolute inset-0 rounded-full border-2 border-slate-700" />
          <div className="absolute inset-0 rounded-full border-2 border-transparent border-t-indigo-500 animate-spin" />
        </div>
        <p className="text-sm text-slate-300">{message}</p>
      </div>
    </div>
  )
}
