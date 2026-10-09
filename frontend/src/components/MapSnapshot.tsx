import { snapshotTiles } from '../lib/snapshot'

/**
 * A still of the street map round a venue, its point marked with a pin: what a scout pins to the board when there
 * is no photograph. Plain images of the map's own tiles, so it costs no map library. It fills its parent, which must
 * be positioned. Decoration: the venue's position is given in words elsewhere.
 */
export function MapSnapshot({ latitude, longitude }: { latitude: number; longitude: number }) {
  return (
    <div aria-hidden className="absolute inset-0 overflow-hidden bg-[#e3ebe6]">
      {snapshotTiles(latitude, longitude).map((tile) => (
        <img
          key={tile.url}
          src={tile.url}
          alt=""
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          className="absolute size-64 max-w-none grayscale-[35%]"
          style={{ left: `calc(50% + ${tile.left}px)`, top: `calc(50% + ${tile.top}px)` }}
        />
      ))}
      <span className="absolute top-1/2 left-1/2 size-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-[3px] border-ink bg-stop shadow-[var(--shadow-card)]" />
    </div>
  )
}
