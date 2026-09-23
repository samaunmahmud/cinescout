import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="space-y-3 py-16 text-center">
      <h1 className="text-2xl font-semibold">Not found</h1>
      <p className="text-stone-400">This page does not exist, or it belongs to another account.</p>
      <Link to="/projects" className="font-semibold text-amber-400 hover:text-amber-300">
        Back to projects
      </Link>
    </div>
  )
}
