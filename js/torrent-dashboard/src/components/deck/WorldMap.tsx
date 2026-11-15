import { useMemo } from 'react'
import { geoCentroid, geoEqualEarth, geoPath } from 'd3-geo'
import type { Feature, FeatureCollection, Geometry } from 'geojson'
import { feature } from 'topojson-client'
import type { GeometryCollection, Topology } from 'topojson-specification'
import isoCountries from 'i18n-iso-countries'
import atlas from 'world-atlas/countries-110m.json'
import { countryName, flag } from '@/lib/format'

const WIDTH = 960
const HEIGHT = 470

// Projected once at module load: the coastline never changes.
const topo = atlas as unknown as Topology<{ countries: GeometryCollection }>
const world = feature(topo, topo.objects.countries) as FeatureCollection<Geometry>
const projection = geoEqualEarth().fitSize([WIDTH, HEIGHT], world)
const path = geoPath(projection)
const shapes = world.features.map((f: Feature<Geometry>) => ({
  id: String(f.id ?? ''),
  d: path(f) ?? '',
  centroid: projection(geoCentroid(f)),
}))

export interface CountryStat {
  peers: number
  downSpeed?: number
}

/** Countries shaded by peer count, with a sized bubble on each one that has peers. */
export function WorldMap({ countries, className }: { countries: Record<string, CountryStat>; className?: string }) {
  const byNumeric = useMemo(() => {
    const out = new Map<string, { code: string; peers: number }>()
    for (const [code, stat] of Object.entries(countries)) {
      const numeric = isoCountries.alpha2ToNumeric(code)
      if (numeric) out.set(numeric, { code, peers: stat.peers })
    }
    return out
  }, [countries])

  const max = Math.max(1, ...[...byNumeric.values()].map(v => v.peers))

  return (
    <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className={className} role="img" aria-label="Mapa de peers por país">
      <g>
        {shapes.map(s => {
          const hit = byNumeric.get(s.id)
          const share = hit ? Math.sqrt(hit.peers / max) : 0
          return (
            <path key={s.id || s.d.slice(0, 12)} d={s.d} strokeWidth={0.5}
              style={{
                fill: hit ? `color-mix(in oklch, var(--cyan) ${Math.round(18 + share * 62)}%, var(--panel2))` : 'var(--panel2)',
                stroke: 'var(--border)',
              }}>
              {hit && <title>{`${flag(hit.code)} ${countryName(hit.code)} · ${hit.peers} peers`}</title>}
            </path>
          )
        })}
      </g>
      <g>
        {shapes.map(s => {
          const hit = byNumeric.get(s.id)
          if (!hit || !s.centroid) return null
          const r = 3 + Math.sqrt(hit.peers / max) * 11
          return (
            <circle key={`c${s.id}`} cx={s.centroid[0]} cy={s.centroid[1]} r={r}
              style={{ fill: 'var(--cyan)', fillOpacity: 0.85, stroke: 'var(--background)', strokeWidth: 1.2 }}>
              <title>{`${flag(hit.code)} ${countryName(hit.code)} · ${hit.peers} peers`}</title>
            </circle>
          )
        })}
      </g>
    </svg>
  )
}
