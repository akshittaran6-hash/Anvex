import { useState } from 'react'
import TopBar from '../components/TopBar.jsx'
import { LabProvider, useLab } from './lab-context.jsx'
import AdminLogin from './panels/AdminLogin.jsx'
import LabHome from './panels/LabHome.jsx'
import CreateSimulation from './panels/CreateSimulation.jsx'
import SimulationRunning from './panels/SimulationRunning.jsx'
import './lab.css'

const NAV = ['HOME', 'CREATE', 'TEMPLATES', 'SOURCES', 'HISTORY', 'SETTINGS']

function LabShell() {
  const { authed, simulation } = useLab()
  const [view, setView] = useState('HOME')
  const [runMode, setRunMode] = useState(false)

  if (!authed) {
    return <AdminLogin />
  }

  const effectiveView = simulation && (simulation.status === 'running' || simulation.status === 'completed') && view === 'CREATE' && runMode
    ? 'RUNNING'
    : view

  return (
    <div className="app-shell">
      <TopBar system="system1" connection={{ connected: true, label: 'CONNECTED TO ANVEX CORE' }} />
      <nav className="lab-nav">
        {NAV.map((item) => (
          <button
            key={item}
            type="button"
            className={`lab-nav-item ${view === item ? 'lab-nav-active' : ''}`}
            onClick={() => setView(item)}
          >
            {item}
          </button>
        ))}
        {simulation && simulation.status === 'running' && (
          <span className="lab-nav-live">● SIMULATION RUNNING</span>
        )}
      </nav>
      <main className="app-main lab-main">
        {effectiveView === 'HOME' && <LabHome onNavigate={setView} />}
        {effectiveView === 'CREATE' && (
          <CreateSimulation onStart={() => setRunMode(true)} />
        )}
        {effectiveView === 'RUNNING' && (
          <SimulationRunning onNewSimulation={() => setRunMode(false)} />
        )}
        {effectiveView === 'TEMPLATES' && <div className="placeholder">TEMPLATES panel pending.</div>}
        {effectiveView === 'SOURCES' && <div className="placeholder">SOURCES panel pending.</div>}
        {effectiveView === 'HISTORY' && <div className="placeholder">HISTORY panel pending.</div>}
        {effectiveView === 'SETTINGS' && <div className="placeholder">SETTINGS panel pending.</div>}
      </main>
      <footer className="lab-footer">
        <span>SYSTEM 1 | LAB CLIENT</span>
        <span className="lab-footer-dim">v1.0.0</span>
      </footer>
    </div>
  )
}

export default function LabApp() {
  return (
    <LabProvider>
      <LabShell />
    </LabProvider>
  )
}
