import { Link } from 'react-router'
import { linkButton } from '../components/buttonStyles'

export function NotFoundPage() {
  return (
    <div className="animate-fade-in space-y-6 py-20 text-center">
      <p aria-hidden className="font-marquee text-[8rem] leading-none text-stone-800">Cut!</p>
      <h1 className="gold-leaf font-display text-6xl leading-none">Not found</h1>
      <p className="font-serif text-lg text-stone-400 italic">This scene is not in the script: the page does not exist, or it belongs to another account.</p>
      <Link to="/projects" className={linkButton('secondary')}>
        Back to your projects
      </Link>
    </div>
  )
}
