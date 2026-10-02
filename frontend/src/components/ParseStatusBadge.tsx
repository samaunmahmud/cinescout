import type { ParseStatus } from '../api/types'
import { Badge } from './ui'

const labels: Record<ParseStatus, { text: string; tone: 'neutral' | 'cue' | 'green' | 'red' }> = {
  PENDING: { text: 'Not analysed', tone: 'neutral' },
  PARSED: { text: 'Requirements ready', tone: 'green' },
  FAILED: { text: 'Analysis failed', tone: 'red' },
}

export function ParseStatusBadge({ status }: { status: ParseStatus }) {
  const { text, tone } = labels[status]
  return <Badge tone={tone}>{text}</Badge>
}
