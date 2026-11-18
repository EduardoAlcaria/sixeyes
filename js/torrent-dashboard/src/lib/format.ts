const UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']

export function bytes(n: number | null | undefined, digits = 1): string {
  if (n == null || !Number.isFinite(n)) return '—'
  let v = Math.abs(n)
  let i = 0
  while (v >= 1024 && i < UNITS.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(i === 0 ? 0 : digits)} ${UNITS[i]}`
}

/** Bytes per second as MB/s, the unit the rest of the app speaks. */
export function rate(bps: number | null | undefined): string {
  if (bps == null) return '—'
  const mb = bps / 1024 / 1024
  return mb >= 100 ? `${mb.toFixed(0)} MB/s` : mb >= 1 ? `${mb.toFixed(1)} MB/s` : `${(bps / 1024).toFixed(0)} KB/s`
}

/** "12.40 MB/s" -> 12.4 */
export function speed(s: string | null | undefined): number {
  const v = parseFloat((s ?? '').replace('MB/s', ''))
  return Number.isFinite(v) ? v : 0
}

export function duration(seconds: number | null | undefined): string {
  if (seconds == null || seconds < 0) return '—'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (d) return `${d}d ${h}h`
  if (h) return `${h}h${String(m).padStart(2, '0')}`
  if (m) return `${m}m`
  return `${Math.floor(seconds)}s`
}

export function minutes(min: number | null | undefined): string {
  if (!min) return '0h'
  const h = Math.floor(min / 60)
  return h >= 1 ? `${h}h${min % 60 ? String(min % 60).padStart(2, '0') : ''}` : `${min}m`
}

/** ISO local date-time -> "14:32", "yesterday", "3 dias", "12/10". */
export function when(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const now = new Date()
  const days = Math.floor((new Date(now.toDateString()).getTime() - new Date(d.toDateString()).getTime()) / 86400000)
  if (days <= 0) return d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
  if (days === 1) return 'yesterday'
  if (days < 7) return `${days}d ago`
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit' })
}

export function between(a: string | null | undefined, b: string | null | undefined): number | null {
  if (!a || !b) return null
  return (new Date(b).getTime() - new Date(a).getTime()) / 1000
}

const regionNames = typeof Intl !== 'undefined' && 'DisplayNames' in Intl
  ? new Intl.DisplayNames(['en'], { type: 'region' })
  : null

export function countryName(code: string): string {
  try {
    return regionNames?.of(code) ?? code
  } catch {
    return code
  }
}

export function flag(code: string): string {
  if (!/^[A-Z]{2}$/.test(code)) return ''
  return String.fromCodePoint(...[...code].map(c => 0x1f1e6 + c.charCodeAt(0) - 65))
}

/** "Hades II [FitGirl Repack]" -> "Hades II" */
export function cleanTitle(t: string | null | undefined): string {
  if (!t) return 'Sem nome'
  return t.replace(/\s*[[(][^\])]*repack[^\])]*[\])]/i, '').replace(/,?\s*v\d[\w.-]*.*$/i, '').trim()
}
