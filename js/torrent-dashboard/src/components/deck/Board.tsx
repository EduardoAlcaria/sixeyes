import { useState } from 'react'
import type { ReactNode } from 'react'
import { ArrowDown, ArrowUp, GripVertical } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface Widget {
  id: string
  label: string
  /** Full row or half row on wide screens; everything is full width on phones. */
  wide?: boolean
  render: () => ReactNode
}

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

/**
 * The main screen: every widget in one grid. Order and visibility are the viewer's own
 * and survive reloads. Drag reorders on desktop; the arrows do the same on touch screens,
 * where HTML drag-and-drop does not fire.
 */
export function Board({ widgets }: { widgets: Widget[] }) {
  const [order, setOrder] = useState<string[]>(() => {
    const saved = load<string[]>(ORDER_KEY, [])
    const known = saved.filter(id => widgets.some(w => w.id === id))
    // Widgets added in a later version join the end instead of vanishing.
    return [...known, ...widgets.map(w => w.id).filter(id => !known.includes(id))]
  })
  const [hidden, setHidden] = useState<string[]>(() => load<string[]>(HIDDEN_KEY, []))
  const [dragging, setDragging] = useState<string | null>(null)
  const [over, setOver] = useState<string | null>(null)

  const commitOrder = (next: string[]) => {
    setOrder(next)
    save(ORDER_KEY, next)
  }

  /** Swap with the nearest visible neighbour; hidden widgets keep their slots. */
  const move = (id: string, delta: -1 | 1) => {
    const visible = order.filter(o => !hidden.includes(o))
    const neighbour = visible[visible.indexOf(id) + delta]
    if (!neighbour) return
    const next = [...order]
    const i = next.indexOf(id)
    const j = next.indexOf(neighbour)
    ;[next[i], next[j]] = [next[j], next[i]]
    commitOrder(next)
  }

  const drop = (target: string) => {
    if (!dragging || dragging === target) return
    const next = order.filter(id => id !== dragging)
    const at = next.indexOf(target)
    const from = order.indexOf(dragging)
    const to = order.indexOf(target)
    next.splice(from < to ? at + 1 : at, 0, dragging)
    commitOrder(next)
  }

  const toggle = (id: string) => {
    const next = hidden.includes(id) ? hidden.filter(h => h !== id) : [...hidden, id]
    setHidden(next)
    save(HIDDEN_KEY, next)
  }

  const byId = new Map(widgets.map(w => [w.id, w]))

  return (
    <div className="flex flex-col gap-3.5">
      <div className="flex flex-wrap items-center gap-1.5 rounded-[var(--radius)] border bg-card px-3.5 py-2.5">
        <span className="mr-1 text-[0.68rem] font-bold uppercase tracking-[0.06em] text-faint">Mostrar</span>
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

      <div className="grid grid-cols-1 items-start gap-3.5 lg:grid-cols-2">
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
              className={cn('group relative min-w-0', w.wide && 'lg:col-span-2',
                dragging === id && 'opacity-35',
                over === id && 'rounded-[var(--radius)] outline-2 outline-dashed outline-offset-4 outline-cyan')}>
              {/* Sits on the panel's top border so it never covers the panel's own header
                  actions. Always shown on touch screens, on hover on desktop. */}
              <div className="absolute -top-3 right-4 z-10 flex items-center gap-1 rounded-full border bg-popover px-1.5 py-0.5 shadow-sm transition-opacity lg:opacity-0 lg:group-hover:opacity-100 lg:focus-within:opacity-100">
                <button type="button" aria-label={`Subir ${w.label}`} disabled={index === 0}
                  onClick={() => move(id, -1)}
                  className="grid size-5 place-items-center rounded-full text-muted-foreground hover:text-foreground disabled:opacity-30">
                  <ArrowUp className="size-3.5" />
                </button>
                <button type="button" aria-label={`Descer ${w.label}`} disabled={index === visible.length - 1}
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
