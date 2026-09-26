import { Link } from 'react-router'
import { linkButton } from '../components/buttonStyles'

export function NotFoundPage() {
  return (
    <div className="space-y-5 py-20 text-center">
      <p aria-hidden className="font-display text-[9rem] leading-none text-stone-800">404</p>
      <h1 className="font-display text-5xl leading-none text-stone-50">Not found</h1>
      <p className="text-stone-400">This page does not exist, or it belongs to another account.</p>
      <Link to="/projects" className={linkButton('secondary')}>
        Back to your projects
      </Link>
    </div>
  )
}
