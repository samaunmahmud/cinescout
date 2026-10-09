import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../api/client'
import { queryKeys } from '../api/queryKeys'
import type { Location, Video } from '../api/types'
import { useSession } from '../auth/context'
import { Clapperboard, CirclePlay } from 'lucide-react'
import { linkButton } from '../components/buttonStyles'
import { EmptyState, Section } from '../components/surfaces'
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
    <Section
      titleId="videos-heading"
      title="Videos"
      eyebrow="See it before the recce"
      icon={Clapperboard}
      description={`From YouTube, for “${query}”.`}
      actions={
        <a href={searchUrl(query)} target="_blank" rel="noopener noreferrer" className={linkButton('secondary')}>
          <CirclePlay aria-hidden className="size-4 text-stop-ink" />
          More on YouTube
          <span className="sr-only"> (opens in a new tab)</span>
        </a>
      }
    >

      {videos.isPending ? (
        <Spinner label="Looking for videos" />
      ) : notConfigured ? (
        <EmptyState icon={CirclePlay}>
          {sentence((videos.error as ApiError).detail ?? 'Videos are not available right now')} Use “More on YouTube” to search there.
        </EmptyState>
      ) : videos.isError ? (
        <ErrorAlert error={videos.error} onRetry={() => videos.refetch()} />
      ) : (
        <VideoGrid videos={videos.data.videos.filter((video) => isVideoId(video.id))} />
      )}
    </Section>
  )
}

function VideoGrid({ videos }: { videos: Video[] }) {
  const [playing, setPlaying] = useState<string | null>(null)
  if (videos.length === 0) {
    return <EmptyState icon={CirclePlay}>No videos of this venue found.</EmptyState>
  }
  return (
    <ul aria-label="Videos of the venue" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {videos.map((video) => (
        <li key={video.id} className="overflow-hidden board-card rounded-lg bg-paper transition hover:border-ink">
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
                  className="absolute top-1/2 left-1/2 flex size-12 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-night/70 text-white ring-2 ring-ink transition group-hover:scale-110 group-hover:bg-stop-ink"
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
              className="line-clamp-2 text-sm font-medium text-ink hover:text-cue-deep hover:underline"
            >
              {video.title}
              <span className="sr-only"> (opens YouTube in a new tab)</span>
            </a>
            <p className="text-xs text-muted">
              {video.channel}
              {video.publishedAt && ` · ${yearFormat.format(new Date(video.publishedAt))}`}
            </p>
          </div>
        </li>
      ))}
    </ul>
  )
}

/** The server's message as a sentence of its own, whether or not it came with a full stop. */
function sentence(text: string): string {
  const trimmed = text.trim()
  return /[.!?]$/.test(trimmed) ? trimmed : `${trimmed}.`
}
