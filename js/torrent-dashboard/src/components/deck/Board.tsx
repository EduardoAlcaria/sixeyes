import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ArrowDown, ArrowUp, Check, GripVertical, SlidersHorizontal, Trash2 } from 'lucide-react'
import { layoutApi } from '@/services/api'
import { cn } from '@/lib/utils'

export interface Widget {
  id: string
  label: string
  /** Full row or half row on wide screens; everything is full width on phones. */
  wide?: boolean
  render: () => ReactNode
}

interface Arrangement { order: string[]; hidden: string[] }
interface Layout extends Arrangement { presets: Record<string, Arrangement> }

const LAYOUT_KEY = 'sixeyes.board.layout'
const ORDER_KEY = 'sixeyes.board.order'
const HIDDEN_KEY = 'sixeyes.board.hidden'

function load<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

function save(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // storage blocked: the board still works for this visit
  }
}

/** Widgets added in a later version join the end instead of vanishing; removed ones drop out. */
function reconcile(order: string[], widgets: Widget[]): string[] {
  const known = order.filter(id => widgets.some(w => w.id === id))
  return [...known, ...widgets.map(w => w.id).filter(id => !known.includes(id))]
}

/**
 * The main screen: every widget in one grid. Order, visibility and named presets live on the
 * server, so every browser shows the same deck; localStorage is only the first paint.
 * Drag reorders on desktop; the arrows do the same on touch screens, where HTML
 * drag-and-drop does not fire.
 */
export function Board({ widgets }: { widgets: Widget[] }) {
  const [layout, setLayout] = useState<Layout>(() => {
    const saved = load<Partial<Layout>>(LAYOUT_KEY, {})
    // Older versions kept order and hidden under their own keys.
    const order = saved.order ?? load<string[]>(ORDER_KEY, [])
    return {
      order: reconcile(order, widgets),
      hidden: saved.hidden ?? load<string[]>(HIDDEN_KEY, []),
      presets: saved.presets ?? {},
    }
  })
  const [dragging, setDragging] = useState<string | null>(null)
  const [over, setOver] = useState<string | null>(null)
  const [menu, setMenu] = useState(false)
  const [presetName, setPresetName] = useState('')
  const menuRef = useRef<HTMLDivElement>(null)
  const synced = useRef(false)
  const timer = useRef<number | undefined>(undefined)
  const { order, hidden, presets } = layout

  // The server copy wins once it arrives; until then (or offline) the local copy is used.
  useEffect(() => {
    layoutApi.get().then(remote => {
      const r = remote as Partial<Layout>
      if (r.order) {
        setLayout({ order: reconcile(r.order, widgets), hidden: r.hidden ?? [], presets: r.presets ?? {} })
      }
    }).catch(() => undefined).finally(() => { synced.current = true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const commit = (next: Layout) => {
    setLayout(next)
    save(LAYOUT_KEY, next)
    if (!synced.current) return
    // Debounced: dragging fires many changes, the server only needs the last one.
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => layoutApi.save(next).catch(() => undefined), 500)
  }

  useEffect(() => {
    if (!menu) return
    const close = (e: MouseEvent) => { if (!menuRef.current?.contains(e.target as Node)) setMenu(false) }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [menu])

  /** Swap with the nearest visible neighbour; hidden widgets keep their slots. */
  const move = (id: string, delta: -1 | 1) => {
    const visible = order.filter(o => !hidden.includes(o))
    const neighbour = visible[visible.indexOf(id) + delta]
    if (!neighbour) return
    const next = [...order]
    const i = next.indexOf(id)
    const j = next.indexOf(neighbour)
    ;[next[i], next[j]] = [next[j], next[i]]
    commit({ ...layout, order: next })
  }

  const drop = (target: string) => {
    if (!dragging || dragging === target) return
    const next = order.filter(id => id !== dragging)
    const at = next.indexOf(target)
    const from = order.indexOf(dragging)
    const to = order.indexOf(target)
    next.splice(from < to ? at + 1 : at, 0, dragging)
    commit({ ...layout, order: next })
  }

  const toggle = (id: string) =>
    commit({ ...layout, hidden: hidden.includes(id) ? hidden.filter(h => h !== id) : [...hidden, id] })

  const savePreset = () => {
    const name = presetName.trim()
    if (!name) return
    commit({ ...layout, presets: { ...presets, [name]: { order, hidden } } })
    setPresetName('')
  }

  const applyPreset = (name: string) => {
    const p = presets[name]
    if (p) commit({ ...layout, order: reconcile(p.order, widgets), hidden: p.hidden })
  }

  const deletePreset = (name: string) => {
    const rest = { ...presets }
    delete rest[name]
    commit({ ...layout, presets: rest })
  }

  const byId = new Map(widgets.map(w => [w.id, w]))
  const shown = widgets.length - hidden.filter(h => byId.has(h)).length

  return (
    <div className="flex flex-col gap-3.5">
      <div ref={menuRef} className="relative self-start">
        <button type="button" onClick={() => setMenu(m => !m)} aria-expanded={menu}
          className="inline-flex items-center gap-2 rounded-full border bg-card px-3.5 py-1.5 text-[0.78rem] font-semibold">
          <SlidersHorizontal className="size-3.5" /> Widgets
          <span className="font-mono text-[0.7rem] text-muted-foreground">{shown}/{widgets.length}</span>
        </button>
        {menu && (
          <div className="absolute left-0 top-full z-30 mt-2 flex w-[min(92vw,26rem)] flex-col gap-3 rounded-[var(--radius)] border bg-popover p-3.5 shadow-lg">
            <div>
              <div className="mb-1.5 text-[0.68rem] font-bold uppercase tracking-[0.06em] text-faint">Show</div>
              <div className="flex flex-wrap gap-1.5">
                {order.map(id => byId.get(id)).filter(Boolean).map(w => (
                  <button key={w!.id} type="button" onClick={() => toggle(w!.id)}
                    className={cn('rounded-full border px-3 py-1 text-[0.74rem] transition-opacity',
                      hidden.includes(w!.id)
                        ? 'opacity-45 text-muted-foreground'
                        : 'border-primary bg-primary/20 text-foreground')}>
                    {w!.label}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <div className="mb-1.5 text-[0.68rem] font-bold uppercase tracking-[0.06em] text-faint">Layout presets</div>
              <div className="flex flex-col gap-1">
                {Object.keys(presets).map(name => (
                  <div key={name} className="flex items-center gap-2 rounded-lg border bg-background px-2.5 py-1.5 text-[0.78rem]">
                    <span className="min-w-0 flex-1 truncate">{name}</span>
                    <button type="button" onClick={() => applyPreset(name)} aria-label={`Apply ${name}`}
                      className="inline-flex items-center gap-1 text-cyan"><Check className="size-3.5" /> Apply</button>
                    <button type="button" onClick={() => deletePreset(name)} aria-label={`Delete ${name}`}
                      className="text-muted-foreground hover:text-bad"><Trash2 className="size-3.5" /></button>
                  </div>
                ))}
                {!Object.keys(presets).length && <p className="text-[0.72rem] text-faint">No presets yet. Arrange the deck, name it and save.</p>}
              </div>
              <form className="mt-2 flex gap-1.5" onSubmit={e => { e.preventDefault(); savePreset() }}>
                <input value={presetName} onChange={e => setPresetName(e.target.value)} placeholder="Preset name…" aria-label="Preset name"
                  className="min-w-0 flex-1 rounded-lg border bg-background px-2.5 py-1.5 text-[0.78rem] outline-none" />
                <button type="submit" disabled={!presetName.trim()}
                  className="rounded-lg bg-primary px-3 py-1.5 text-[0.76rem] font-bold text-primary-foreground disabled:opacity-40">Save current</button>
              </form>
            </div>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 gap-3.5 lg:grid-cols-2">
        {order.filter(id => !hidden.includes(id)).map((id, index, visible) => {
          const w = byId.get(id)
          if (!w) return null
          return (
            <div key={id}
              draggable
              onDragStart={e => { setDragging(id); e.dataTransfer.effectAllowed = 'move' }}
              onDragEnd={() => { setDragging(null); setOver(null) }}
              onDragOver={e => { e.preventDefault(); if (id !== dragging) setOver(id) }}
              onDragLeave={() => setOver(o => (o === id ? null : o))}
              onDrop={e => { e.preventDefault(); drop(id); setOver(null) }}
              className={cn('group relative min-w-0 [&>section]:h-full', w.wide && 'lg:col-span-2',
                dragging === id && 'opacity-35',
                over === id && 'rounded-[var(--radius)] outline-2 outline-dashed outline-offset-4 outline-cyan')}>
              {/* Sits on the panel's top border so it never covers the panel's own header
                  actions. Always shown on touch screens, on hover on desktop. */}
              <div className="absolute -top-3 right-4 z-10 flex items-center gap-1 rounded-full border bg-popover px-1.5 py-0.5 shadow-sm transition-opacity lg:opacity-0 lg:group-hover:opacity-100 lg:focus-within:opacity-100">
                <button type="button" aria-label={`Move ${w.label} up`} disabled={index === 0}
                  onClick={() => move(id, -1)}
                  className="grid size-5 place-items-center rounded-full text-muted-foreground hover:text-foreground disabled:opacity-30">
                  <ArrowUp className="size-3.5" />
                </button>
                <button type="button" aria-label={`Move ${w.label} down`} disabled={index === visible.length - 1}
                  onClick={() => move(id, 1)}
                  className="grid size-5 place-items-center rounded-full text-muted-foreground hover:text-foreground disabled:opacity-30">
                  <ArrowDown className="size-3.5" />
                </button>
                <GripVertical className="hidden size-4 cursor-grab text-faint md:block" aria-hidden />
              </div>
              {w.render()}
            </div>
          )
        })}
      </div>
    </div>
  )
}
