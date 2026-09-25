import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './theme/global.css'
import LabApp from './lab/LabApp.jsx'
import DashboardApp from './dashboard/DashboardApp.jsx'

function route() {
  const path = window.location.pathname.replace(/\/+$/, '')
  if (path.endsWith('/lab')) return 'lab'
  return 'dashboard'
}

const view = route()
const root = createRoot(document.getElementById('root'))
root.render(
  <StrictMode>
    {view === 'lab' ? <LabApp /> : <DashboardApp />}
  </StrictMode>
)
