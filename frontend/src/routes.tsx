import type { ComponentType } from 'react'
import { Navigate, type RouteObject } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { CrashPage } from './pages/CrashPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ProjectPage } from './pages/ProjectPage'
import { NewScenePage } from './pages/NewScenePage'
import { ProjectsPage } from './pages/ProjectsPage'
import { ScenePage } from './pages/ScenePage'

// The pages most visits never reach are fetched when first opened, which keeps the first load small. The router
// waits for a page's code before it switches to it.
const lazyPage = (load: () => Promise<{ default: ComponentType }>) => async () => ({ Component: (await load()).default })

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage />, errorElement: <CrashPage /> },
  { path: '/register', element: <RegisterPage />, errorElement: <CrashPage /> },
  {
    element: (
      <RequireAuth>
        <Layout />
      </RequireAuth>
    ),
    errorElement: <CrashPage />,
    children: [
      // A page that fails is shown inside the frame, so the header and its way out stay.
      {
        errorElement: <CrashPage />,
        children: [
          { path: '/', element: <Navigate to="/projects" replace /> },
          { path: '/projects', element: <ProjectsPage /> },
          { path: '/account', lazy: lazyPage(() => import('./pages/AccountPage').then((m) => ({ default: m.AccountPage }))) },
          { path: '/projects/:projectId', element: <ProjectPage /> },
          { path: '/projects/:projectId/call-sheet', lazy: lazyPage(() => import('./pages/CallSheetPage').then((m) => ({ default: m.CallSheetPage }))) },
          { path: '/projects/:projectId/scenes/new', element: <NewScenePage /> },
          { path: '/projects/:projectId/scenes/import', lazy: lazyPage(() => import('./pages/ImportScriptPage').then((m) => ({ default: m.ImportScriptPage }))) },
          { path: '/scenes/:sceneId', element: <ScenePage /> },
          { path: '/scenes/:sceneId/locations/new', lazy: lazyPage(() => import('./pages/NewLocationPage').then((m) => ({ default: m.NewLocationPage }))) },
          { path: '/scenes/:sceneId/compare', lazy: lazyPage(() => import('./pages/ComparePage').then((m) => ({ default: m.ComparePage }))) },
          { path: '/locations/:locationId', lazy: lazyPage(() => import('./pages/LocationPage').then((m) => ({ default: m.LocationPage }))) },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]
