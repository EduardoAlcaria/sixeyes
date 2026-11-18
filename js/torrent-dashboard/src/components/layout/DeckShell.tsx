import { useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  Clock, Globe, Joystick, LayoutGrid, LibraryBig, List, LogOut, Moon, Plus, Search, Settings, Sun,
} from 'lucide-react'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import { speed } from '@/lib/format'
import { useTheme } from '@/providers/ThemeProvider'
import { useSystemMonitoring } from '@/hooks/useSystemMonitoring'
import { TorrentsContext, useTorrents } from '@/hooks/useTorrents'

const NAV = [
  { to: '/', label: 'Overview', icon: LayoutGrid, end: true },
  { to: '/library', label: 'Library', icon: List },
  { to: '/catalog', label: 'Catalog', icon: LibraryBig },
  { to: '/shelf', label: 'Shelf', icon: Joystick },
  { to: '/map', label: 'Peer Map', icon: Globe },
  { to: '/history', label: 'History', icon: Clock },
]

function CommandPalette({ open, onClose, onAddMagnet }: {
  open: boolean
  onClose: () => void
  onAddMagnet: (magnet: string) => Promise<boolean>
}) {
  const [q, setQ] = useState('')
  const [index, setIndex] = useState(0)
  const navigate = useNavigate()
  const input = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (open) {
      setQ('')
      setIndex(0)
      setTimeout(() => input.current?.focus(), 0)
    }
  }, [open])

  const actions = useMemo(() => {
    const text = q.trim()
    if (text.startsWith('magnet:')) {
      return [{ label: 'Adicionar este magnet', icon: Plus, run: async () => {
        if (await onAddMagnet(text)) toast.success('Download adicionado')
      } }]
    }
    const go = [...NAV, { to: '/settings', label: 'Settings', icon: Settings }]
      .filter(n => !text || n.label.toLowerCase().includes(text.toLowerCase()))
      .map(n => ({ label: `Ir para ${n.label}`, icon: n.icon, run: async () => navigate(n.to) }))
    return text
      ? [{ label: `Search "${text}" in the catalog`, icon: Search, run: async () => navigate(`/catalog?q=${encodeURIComponent(text)}`) }, ...go]
      : go
  }, [q, navigate, onAddMagnet])

  if (!open) return null

  const run = async (i: number) => {
    const a = actions[i]
    if (!a) return
    onClose()
    await a.run()
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-start justify-center bg-black/60 pt-[13vh] backdrop-blur-sm" onClick={onClose}>
      <div className="w-[min(560px,92vw)] overflow-hidden rounded-2xl border bg-popover shadow-2xl" onClick={e => e.stopPropagation()}>
        <input ref={input} value={q} onChange={e => { setQ(e.target.value); setIndex(0) }}
          onKeyDown={e => {
            if (e.key === 'ArrowDown') { e.preventDefault(); setIndex(i => Math.min(i + 1, actions.length - 1)) }
            if (e.key === 'ArrowUp') { e.preventDefault(); setIndex(i => Math.max(i - 1, 0)) }
            if (e.key === 'Enter') { e.preventDefault(); run(index) }
            if (e.key === 'Escape') onClose()
          }}
          placeholder="Colar magnet, buscar jogo, ou ir para…" aria-label="Comando"
          className="w-full border-b bg-transparent px-4 py-3.5 text-base outline-none" />
        <div className="max-h-80 overflow-y-auto py-1">
          {actions.map((a, i) => (
            <button key={a.label} type="button" onMouseEnter={() => setIndex(i)} onClick={() => run(i)}
              className={cn('flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm',
                i === index ? 'bg-card text-foreground' : 'text-muted-foreground')}>
              <a.icon className="size-4" /> {a.label}
              {i === index && <kbd className="ml-auto font-mono text-[0.7rem] text-faint">enter</kbd>}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

export function DeckShell({ onLogout }: { onLogout: () => void }) {
  const torrentsState = useTorrents()
  const { torrents } = torrentsState
  const { system, online } = useSystemMonitoring()
  const { theme, toggle } = useTheme()
  const [palette, setPalette] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPalette(p => !p)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const seeds = torrents.reduce((s, t) => s + (t.seeds ?? 0), 0)
  const peers = torrents.reduce((s, t) => s + t.peers, 0)
  const down = torrents.filter(t => t.status === 'Downloading').reduce((s, t) => s + speed(t.downloadSpeed), 0)

  return (
    <TorrentsContext.Provider value={torrentsState}>
      <div className="grid min-h-dvh grid-cols-1 bg-[radial-gradient(1100px_560px_at_82%_-12%,color-mix(in_oklch,var(--violet)_13%,transparent),transparent_60%),radial-gradient(820px_460px_at_-6%_112%,color-mix(in_oklch,var(--cyan)_9%,transparent),transparent_55%)] md:grid-cols-[74px_1fr]">
        <nav className="sticky top-0 hidden h-dvh flex-col items-center gap-1.5 border-r bg-sidebar py-4 md:flex">
          <div className="mb-3 grid size-10 place-items-center rounded-xl bg-gradient-to-br from-primary to-violet text-lg font-extrabold text-white shadow-[0_0_22px_color-mix(in_oklch,var(--violet)_50%,transparent)]">6</div>
          {NAV.map(n => (
            <NavLink key={n.to} to={n.to} end={n.end} title={n.label} aria-label={n.label}
              className={({ isActive }) => cn('group relative grid h-11 w-12 place-items-center rounded-xl border border-transparent text-faint hover:bg-card hover:text-foreground',
                isActive && 'border-border bg-card text-cyan before:absolute before:-left-2 before:top-3 before:bottom-3 before:w-[3px] before:rounded before:bg-cyan before:shadow-[0_0_12px_var(--cyan)]')}>
              <n.icon className="size-5" />
              <span className="pointer-events-none absolute left-14 z-30 whitespace-nowrap rounded-md border bg-popover px-2 py-1 text-xs text-foreground opacity-0 transition-opacity group-hover:opacity-100">{n.label}</span>
            </NavLink>
          ))}
          <div className="flex-1" />
          <NavLink to="/settings" title="Settings" aria-label="Settings"
            className={({ isActive }) => cn('grid h-11 w-12 place-items-center rounded-xl text-faint hover:bg-card hover:text-foreground', isActive && 'bg-card text-cyan')}>
            <Settings className="size-5" />
          </NavLink>
        </nav>

        <div className="min-w-0">
          <header className="sticky top-0 z-20 flex flex-wrap items-center gap-x-5 gap-y-2 border-b bg-sidebar/70 px-4 py-3 backdrop-blur md:px-6">
            <div className="flex items-center gap-2 text-[0.74rem] font-semibold text-muted-foreground" role="status">
              <span className={cn('size-2.5 rounded-full', online === false ? 'bg-bad' : online ? 'animate-pulse bg-ok' : 'bg-faint')} />
              {online === false ? 'ENGINE OFFLINE' : online ? 'ONLINE' : 'CONNECTING'}
            </div>
            <div className="flex flex-col"><span className="text-[0.6rem] uppercase tracking-[0.08em] text-faint">Download</span><b className="font-mono text-cyan">{down.toFixed(1)} MB/s</b></div>
            <div className="flex flex-col"><span className="text-[0.6rem] uppercase tracking-[0.08em] text-faint">Upload</span><b className="font-mono text-violet">{system.network.uploadSpeed.toFixed(1)} MB/s</b></div>
            <div className="hidden flex-col sm:flex"><span className="text-[0.6rem] uppercase tracking-[0.08em] text-faint">Swarm</span><b className="font-mono">{seeds} ⇵ {peers}</b></div>
            <button type="button" onClick={() => setPalette(true)}
              className="ml-auto hidden items-center gap-2 rounded-lg border bg-card px-3 py-1.5 text-[0.8rem] text-muted-foreground md:flex">
              <Search className="size-3.5" /> Search or paste a magnet <kbd className="rounded border bg-panel2 px-1.5 font-mono text-[0.68rem]">Ctrl K</kbd>
            </button>
            <button type="button" onClick={() => setPalette(true)} aria-label="Search" className="ml-auto grid size-9 place-items-center rounded-lg border bg-card md:hidden">
              <Search className="size-4" />
            </button>
            <button type="button" onClick={toggle} aria-label="Alternar tema" className="grid size-9 place-items-center rounded-lg border bg-card text-muted-foreground">
              {theme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
            </button>
            <button type="button" onClick={onLogout} aria-label="Sign out" className="grid size-9 place-items-center rounded-lg border bg-card text-muted-foreground">
              <LogOut className="size-4" />
            </button>
          </header>

          <div className="flex gap-1.5 overflow-x-auto px-4 pt-3 md:hidden">
            {[...NAV, { to: '/settings', label: 'Settings', icon: Settings, end: false }].map(n => (
              <NavLink key={n.to} to={n.to} end={n.end}
                className={({ isActive }) => cn('whitespace-nowrap rounded-full border px-3.5 py-1.5 text-[0.8rem]',
                  isActive ? 'border-transparent bg-primary text-white' : 'bg-card text-muted-foreground')}>
                {n.label}
              </NavLink>
            ))}
          </div>

          <main className="flex min-w-0 flex-col gap-4 px-4 pb-10 pt-4 md:px-6">
            <Outlet />
          </main>
        </div>
      </div>
      <CommandPalette open={palette} onClose={() => setPalette(false)} onAddMagnet={torrentsState.addTorrent} />
    </TorrentsContext.Provider>
  )
}
