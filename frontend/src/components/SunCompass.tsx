import { useState } from 'react'
import type { SolarDay, SunPosition } from '../api/types'
import { formatDate } from '../lib/format'

/** Start, middle and end of the scene's time: the arrows' colours, darkest last. */
const shades = ['#f59e0b', '#ea580c', '#9a3412']
const labels = ['Start', 'Middle', 'End']

/**
 * Where the sun is during the scene, drawn as a compass over the venue's map (north up, as the map is): one arrow
 * per moment pointing towards the sun, its length shrinking as the sun climbs, dashed while it is below the horizon.
 * The same positions are listed in words underneath, which is what screen readers and print get.
 */
export function SunCompass({ days }: { days: SolarDay[] }) {
  const withSun = days.filter((day) => (day.sunPath?.length ?? 0) > 0)
  const [selected, setSelected] = useState(0)
  if (withSun.length === 0) return null
  const day = withSun[Math.min(selected, withSun.length - 1)]
  const path = day.sunPath ?? []
  return (
    <figure className="space-y-2">
      <div aria-hidden className="pointer-events-none absolute top-2 right-2 z-[500] rounded-full bg-paper/90 p-1 shadow-md ring-1 ring-ink/20">
        <Compass path={path} />
      </div>
      <figcaption className="space-y-1 text-sm">
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-semibold text-ink">Sun during the scene</span>
          {withSun.length > 1 ? (
            <label className="flex items-center gap-1 text-muted">
              <span className="sr-only">Shoot day</span>
              <select
                value={Math.min(selected, withSun.length - 1)}
                onChange={(e) => setSelected(Number(e.target.value))}
                className="rounded-md border border-line bg-paper px-1 py-0.5 text-sm text-ink"
              >
                {withSun.map((d, i) => (
                  <option key={d.date} value={i}>
                    {formatDate(d.date)}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <span className="text-muted">{formatDate(day.date)}</span>
          )}
        </span>
        <ul className="space-y-0.5">
          {path.map((position, i) => (
            <li key={position.at} className="flex items-center gap-2 text-graphite">
              <span aria-hidden className="inline-block size-2.5 rounded-full" style={{ background: shades[i % shades.length] }} />
              {path.length > 1 && <span className="sr-only">{labels[i] ?? ''}: </span>}
              {position.text}
            </li>
          ))}
        </ul>
      </figcaption>
    </figure>
  )
}

function Compass({ path }: { path: SunPosition[] }) {
  const size = 112
  const c = size / 2
  const radius = 40
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="presentation">
      <circle cx={c} cy={c} r={radius + 6} fill="none" stroke="#1f2937" strokeOpacity={0.25} />
      {[
        ['N', 0],
        ['E', 90],
        ['S', 180],
        ['W', 270],
      ].map(([letter, angle]) => {
        const [x, y] = point(c, radius + 6 + 0, Number(angle))
        return (
          <text key={letter} x={x} y={y} textAnchor="middle" dominantBaseline="central" fontSize={10} fontWeight={700} fill="#1f2937"
            style={{ paintOrder: 'stroke', stroke: 'white', strokeWidth: 3 }}>
            {letter}
          </text>
        )
      })}
      {path.map((position, i) => {
        // A high sun is nearly overhead: a shorter arrow. Below the horizon: full length, dashed.
        const reach = radius - 10
        const length = position.elevation <= 0 ? reach : Math.max(12, reach * Math.cos((position.elevation * Math.PI) / 180))
        const [x, y] = point(c, length, position.azimuth)
        const colour = shades[i % shades.length]
        return (
          <g key={position.at}>
            <line x1={c} y1={c} x2={x} y2={y} stroke={colour} strokeWidth={3} strokeLinecap="round" strokeDasharray={position.elevation <= 0 ? '4 3' : undefined} />
            <circle cx={x} cy={y} r={5} fill={position.elevation <= 0 ? 'white' : colour} stroke={colour} strokeWidth={2} />
          </g>
        )
      })}
      <circle cx={c} cy={c} r={3} fill="#1f2937" />
    </svg>
  )
}

/** The point `length` from the centre in compass bearing `degrees` (0 = up/north, clockwise). */
function point(centre: number, length: number, degrees: number): [number, number] {
  const radians = (degrees * Math.PI) / 180
  return [centre + length * Math.sin(radians), centre - length * Math.cos(radians)]
}
