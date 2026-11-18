import { useState } from 'react'
import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import type { GameSource, InstallStatus, Torrent } from '@/types'

export function Panel({ title, note, actions, className, children }: {
  title?: ReactNode
  note?: ReactNode
  actions?: ReactNode
  className?: string
  children: ReactNode
}) {
  return (
    <section className={cn('min-w-0 rounded-[var(--radius)] border bg-card p-4', className)}>
      {(title || actions) && (
        <header className="mb-3 flex items-center gap-2">
          {title && <h2 className="text-[0.74rem] font-bold uppercase tracking-[0.08em] text-muted-foreground">{title}</h2>}
          {note && <span className="text-[0.7rem] text-faint">{note}</span>}
          {actions && <div className="ml-auto flex items-center gap-1.5">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  )
}

export function Metric({ label, value, unit, hint, tone }: {
  label: string
  value: ReactNode
  unit?: string
  hint?: ReactNode
  tone?: 'up' | 'down' | 'muted' | 'cyan'
}) {
  return (
    <div className="flex min-w-0 flex-col gap-1 rounded-[calc(var(--radius)*0.6)] border bg-gradient-to-br from-panel2 to-card px-3 py-3">
      <span className="text-[0.62rem] uppercase tracking-[0.06em] text-faint">{label}</span>
      <span className={cn('font-mono text-[1.35rem] font-extrabold tabular-nums', tone === 'cyan' && 'text-cyan')}>
        {value}
        {unit && <small className="ml-1 text-[0.7rem] font-semibold text-muted-foreground">{unit}</small>}
      </span>
      {hint && (
        <span className={cn('truncate text-[0.68rem] font-bold',
          tone === 'up' ? 'text-ok' : tone === 'down' ? 'text-bad' : 'text-faint')}>
          {hint}
        </span>
      )}
    </div>
  )
}

const PILL = {
  go: 'bg-cyan/15 text-cyan',
  ok: 'bg-ok/15 text-ok',
  bad: 'bg-bad/20 text-bad',
  warn: 'bg-warn/15 text-warn',
  muted: 'bg-panel2 text-muted-foreground',
} as const

export function Pill({ tone = 'muted', children }: { tone?: keyof typeof PILL; children: ReactNode }) {
  return (
    <span className={cn('whitespace-nowrap rounded-full px-2.5 py-1 text-[0.64rem] font-bold uppercase tracking-[0.03em]', PILL[tone])}>
      {children}
    </span>
  )
}

export function Bar({ value, tone = 'flow', className, indeterminate }: {
  value: number
  tone?: 'flow' | 'ok' | 'bad' | 'hot' | 'faint'
  className?: string
  /** Work of unknown length: a block slides instead of filling. */
  indeterminate?: boolean
}) {
  const fill = {
    flow: 'bg-gradient-to-r from-primary to-cyan',
    ok: 'bg-ok',
    bad: 'bg-gradient-to-r from-bad to-pink',
    hot: 'bg-gradient-to-r from-warn to-bad',
    faint: 'bg-faint',
  }[tone]
  return (
    <div className={cn('relative h-[7px] overflow-hidden rounded-full border bg-background', className)}>
      {indeterminate
        ? <i className={cn('animate-slide absolute top-0 block h-full w-1/3 rounded-full', fill)} />
        : <i className={cn('block h-full rounded-full', fill)} style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />}
    </div>
  )
}

/** Circular progress; with no value it spins, for work whose length is unknown. */
export function Ring({ value, size = 44, tone = 'var(--cyan)', children }: {
  value: number | null
  size?: number
  tone?: string
  children?: ReactNode
}) {
  const r = 18
  const c = 2 * Math.PI * r
  return (
    <div className="relative grid shrink-0 place-items-center" style={{ width: size, height: size }}>
      <svg viewBox="0 0 44 44" className={cn('absolute inset-0 -rotate-90', value == null && 'animate-spin')}>
        <circle cx="22" cy="22" r={r} fill="none" stroke="var(--border)" strokeWidth="4" />
        <circle cx="22" cy="22" r={r} fill="none" stroke={tone} strokeWidth="4" strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - (value ?? 25) / 100)} />
      </svg>
      <span className="relative font-mono text-[0.62rem] font-bold">{children}</span>
    </div>
  )
}

export function SourceBadge({ source }: { source: GameSource | 'TORRENT' }) {
  const style = source === 'STEAM'
    ? 'bg-[#1b2838] text-[#66c0f4]'
    : source === 'LOCAL'
      ? 'bg-panel2 text-muted-foreground'
      : 'bg-primary/25 text-cyan'
  return (
    <span className={cn('mr-1.5 inline-block rounded-[5px] px-1.5 py-0.5 align-middle text-[0.55rem] font-extrabold tracking-[0.05em]', style)}>
      {source === 'TORRENT' ? 'TORRENT' : source}
    </span>
  )
}

type Stage = 'done' | 'now' | 'fail' | 'todo'

/** Where a torrent is in download -> verify -> install, as four ticks. */
export function stages(t: Pick<Torrent, 'progress' | 'status' | 'installStatus' | 'installMessage'>): [Stage, Stage, Stage, Stage] {
  const install: InstallStatus = t.installStatus ?? 'NONE'
  const downloaded = t.progress >= 100 || t.status === 'Seeding'
  const dl: Stage = downloaded ? 'done' : t.status === 'Error' ? 'fail' : 'now'
  if (!downloaded) return [dl, 'todo', 'todo', 'todo']
  switch (install) {
    case 'VERIFYING': return ['done', 'now', 'todo', 'todo']
    case 'INSTALLING': return ['done', 'done', 'now', 'todo']
    case 'INSTALLED': return ['done', 'done', 'done', 'done']
    case 'REQUESTED': return ['done', 'now', 'todo', 'todo']
    case 'FAILED':
      // Refusals and checksum failures happen before setup.exe ever runs.
      return /integrity|checksum|md5|refused|not a fitgirl/i.test(t.installMessage ?? '')
        ? ['done', 'fail', 'todo', 'todo']
        : ['done', 'done', 'fail', 'todo']
    default: return ['done', 'todo', 'todo', 'todo']
  }
}

export function StageTrace({ value }: { value: [Stage, Stage, Stage, Stage] }) {
  const labels = ['Download', 'Verify', 'Install', 'Done']
  return (
    <div className="flex gap-1.5">
      {value.map((s, i) => (
        <div key={labels[i]} className="flex flex-1 flex-col gap-1">
          <div className={cn('h-1 rounded-full',
            s === 'done' ? 'bg-ok' : s === 'now' ? 'animate-breathe bg-cyan shadow-[0_0_8px_var(--cyan)]' : s === 'fail' ? 'bg-bad' : 'bg-border')} />
          <span className={cn('text-[0.6rem] tracking-[0.02em]',
            s === 'now' ? 'text-cyan' : s === 'done' ? 'text-muted-foreground' : s === 'fail' ? 'text-bad' : 'text-faint')}>
            {labels[i]}
          </span>
        </div>
      ))}
    </div>
  )
}

/** Fixed-width map of which pieces are on disk; each cell is a run of pieces. */
export function PieceMap({ pieces, columns = 32 }: { pieces: number[]; columns?: number }) {
  if (!pieces.length) return <p className="text-xs text-faint">Waiting for metadata…</p>
  return (
    <div className="grid gap-[2px]" style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
      {pieces.map((p, i) => (
        <div key={i} className="aspect-square rounded-[1px]"
          style={{ background: p >= 1 ? 'var(--cyan)' : p > 0 ? `color-mix(in oklch, var(--primary) ${Math.round(40 + p * 60)}%, var(--border))` : 'var(--border)' }}
          title={`${Math.round(p * 100)}%`} />
      ))}
    </div>
  )
}

export function Stat({ k, v }: { k: string; v: ReactNode }) {
  return (
    <span className="rounded-[7px] border bg-panel2 px-2 py-0.5 font-mono text-[0.7rem] text-muted-foreground">
      {k} <b className="font-semibold text-foreground">{v}</b>
    </span>
  )
}

export function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0 rounded-[calc(var(--radius)*0.6)] border bg-panel2 px-3 py-2.5">
      <div className="text-[0.6rem] uppercase tracking-[0.06em] text-faint">{label}</div>
      <div className="mt-1 truncate font-mono text-[1rem] font-bold">{value}</div>
    </div>
  )
}

/** Deterministic gradient for a title when there is no cover art. */
export function coverGradient(seed: string): string {
  const palette = ['var(--cyan)', 'var(--primary)', 'var(--violet)', 'var(--pink)', 'var(--ok)', 'var(--warn)']
  let h = 0
  for (const c of seed) h = (h * 31 + c.charCodeAt(0)) >>> 0
  // >>> not >>: a signed shift of a hash past 2^31 goes negative and indexes nothing.
  return `linear-gradient(150deg, ${palette[h % palette.length]}, ${palette[(h >>> 3) % palette.length]})`
}

/**
 * Cover art that degrades gracefully: tries each URL in turn (Steam keeps art at several
 * addresses, and newer apps lack the old ones), then falls back to a gradient.
 */
export function Cover({ sources, seed, className, children }: {
  sources: (string | null | undefined)[]
  seed: string
  className?: string
  children?: ReactNode
}) {
  const urls = sources.filter((s): s is string => !!s)
  const [i, setI] = useState(0)
  return (
    <div className={cn('relative overflow-hidden', className)} style={{ background: coverGradient(seed) }}>
      {i < urls.length && (
        <img key={urls[i]} src={urls[i]} alt="" loading="lazy" referrerPolicy="no-referrer"
          onError={() => setI(n => n + 1)} className="absolute inset-0 size-full object-cover" />
      )}
      {children}
    </div>
  )
}

/** Every place Steam may hold a game's art, best first. Our own relay leads: it works where the CDN is blocked. */
export function steamArt(appId: string | null | undefined, portrait = true): string[] {
  if (!appId) return []
  const cf = `https://cdn.cloudflare.steamstatic.com/steam/apps/${appId}`
  const ak = `https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/${appId}`
  const own = `/api/v1/games/art/steam/${appId}?portrait=${portrait}`
  return portrait
    ? [own, `${cf}/library_600x900.jpg`, `${ak}/library_600x900.jpg`, `${ak}/library_600x900_2x.jpg`, `${cf}/header.jpg`, `${ak}/header.jpg`]
    : [own, `${cf}/header.jpg`, `${ak}/header.jpg`, `${cf}/capsule_231x87.jpg`]
}
