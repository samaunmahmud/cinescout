import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../api/client'
import { queryKeys } from '../api/queryKeys'
import type { Location, Video } from '../api/types'
import { useSession } from '../auth/context'
import { ErrorAlert, Spinner } from '../components/ui'
import { embedUrl, isVideoId, searchUrl, thumbnailUrl, watchUrl } from '../lib/video'

const yearFormat = new Intl.DateTimeFormat(undefined, { year: 'numeric' })

/** Videos of the venue from YouTube, to see the place before a recce. */
export function VideosSection({ location }: { location: Location }) {
  const { api } = useSession()
  const videos = useQuery({
    queryKey: queryKeys.videos(location.id),
    queryFn: () => api.locations.videos(location.id),
    // The server caches the search for a week; asking again within a visit gains nothing.
    staleTime: Infinity,
    retry: false,
  })
  // Without a server-side search, the venue's name and address make the best query there is.
  const fallbackQuery = [location.name, location.address].filter(Boolean).join(' ')
  const query = videos.data?.query ?? fallbackQuery
  const notConfigured = videos.error instanceof ApiError && videos.error.status === 503

  return (
    <section aria-labelledby="videos-heading" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 id="videos-heading" className="text-lg font-semibold">
            Videos
          </h2>
          <p className="text-sm text-stone-400">See the place before the recce. From YouTube, for “{query}”.</p>
        </div>
        <a href={searchUrl(query)} target="_blank" rel="noopener noreferrer" className="text-sm text-amber-300 underline hover:text-amber-200">
          More on YouTube
          <span className="sr-only"> (opens in a new tab)</span>
        </a>
      </div>

      {videos.isPending ? (
        <Spinner label="Looking for videos" />
      ) : notConfigured ? (
        <p className="rounded-lg border border-dashed border-stone-800 px-6 py-8 text-center text-stone-400">
          {(videos.error as ApiError).detail ?? 'Videos are not available right now.'} Use “More on YouTube” to search there.
        </p>
      ) : videos.isError ? (
        <ErrorAlert error={videos.error} onRetry={() => videos.refetch()} />
      ) : (
        <VideoGrid videos={videos.data.videos.filter((video) => isVideoId(video.id))} />
      )}
    </section>
  )
}

function VideoGrid({ videos }: { videos: Video[] }) {
  const [playing, setPlaying] = useState<string | null>(null)
  if (videos.length === 0) {
    return <p className="rounded-lg border border-dashed border-stone-800 px-6 py-8 text-center text-stone-400">No videos of this venue found.</p>
  }
  return (
    <ul aria-label="Videos of the venue" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {videos.map((video) => (
        <li key={video.id} className="overflow-hidden rounded-lg border border-stone-800 bg-stone-900/60">
          <div className="relative aspect-video bg-black">
            {playing === video.id ? (
              <iframe
                src={embedUrl(video.id)}
                title={video.title}
                className="absolute inset-0 size-full"
                allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                referrerPolicy="strict-origin-when-cross-origin"
                allowFullScreen
              />
            ) : (
              <button type="button" onClick={() => setPlaying(video.id)} className="group absolute inset-0 size-full" aria-label={`Play “${video.title}”`}>
                <img src={thumbnailUrl(video.id)} alt="" loading="lazy" className="size-full object-cover transition group-hover:opacity-80" />
                <span
                  aria-hidden
                  className="absolute top-1/2 left-1/2 flex size-12 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-black/70 text-white ring-2 ring-white/70 transition group-hover:scale-110 group-hover:bg-red-600"
                >
                  ▶
                </span>
              </button>
            )}
          </div>
          <div className="space-y-1 p-3">
            <a
              href={watchUrl(video.id)}
              target="_blank"
              rel="noopener noreferrer"
              className="line-clamp-2 text-sm font-medium text-stone-100 hover:text-amber-300 hover:underline"
            >
              {video.title}
              <span className="sr-only"> (opens YouTube in a new tab)</span>
            </a>
            <p className="text-xs text-stone-400">
              {video.channel}
              {video.publishedAt && ` · ${yearFormat.format(new Date(video.publishedAt))}`}
            </p>
          </div>
        </li>
      ))}
    </ul>
  )
}
