import { Link } from 'react-router'
import { linkButton } from '../components/buttonStyles'
import { usePageTitle } from '../lib/usePageTitle'

export function NotFoundPage() {
  usePageTitle('Not found')
  return (
    <div className="animate-fade-in space-y-6 py-20 text-center">
      <p aria-hidden className="font-display font-bold text-[8rem] leading-none text-ink">Cut!</p>
      <h1 className="font-bold font-display text-6xl leading-none">Not found</h1>
      <p className="text-lg text-muted">This scene is not in the script: the page does not exist, or it belongs to another account.</p>
      <Link to="/projects" className={linkButton('secondary')}>
        Back to your projects
      </Link>
    </div>
  )
}
