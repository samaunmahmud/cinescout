import { Navigate, type RouteObject } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { AccountPage } from './pages/AccountPage'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { ImportScriptPage } from './pages/ImportScriptPage'
import { LocationPage } from './pages/LocationPage'
import { NewLocationPage } from './pages/NewLocationPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ProjectPage } from './pages/ProjectPage'
import { NewScenePage } from './pages/NewScenePage'
import { ProjectsPage } from './pages/ProjectsPage'
import { ScenePage } from './pages/ScenePage'

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: (
      <RequireAuth>
        <Layout />
      </RequireAuth>
    ),
    children: [
      { path: '/', element: <Navigate to="/projects" replace /> },
      { path: '/projects', element: <ProjectsPage /> },
      { path: '/account', element: <AccountPage /> },
      { path: '/projects/:projectId', element: <ProjectPage /> },
      { path: '/projects/:projectId/scenes/new', element: <NewScenePage /> },
      { path: '/projects/:projectId/scenes/import', element: <ImportScriptPage /> },
      { path: '/scenes/:sceneId', element: <ScenePage /> },
      { path: '/scenes/:sceneId/locations/new', element: <NewLocationPage /> },
      { path: '/locations/:locationId', element: <LocationPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
