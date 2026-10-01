import './top-bar.css'
import { ConnectionBadge } from './Badges.jsx'

const NAV = {
  system2: ['LIVE', 'RUNS', 'COMPARE']
}

const PHASES = ['CONFIGURE', 'SIMULATE', 'SEND', 'VALIDATE', 'LEARN']

export default function TopBar({ system, activeTab, onTabChange, connection, version = 'v1.0.0' }) {
  const nav = NAV[system] || []
  return (
    <header className="top-bar">
      <div className="top-bar-brand">
        <span className="brand-mark">A N V E X</span>
        {system === 'system1' && <span className="brand-sub">LAB CLIENT</span>}
      </div>
      <span className="top-bar-tagline">
        {system === 'system1' ? 'ATTACK SIMULATION INTERFACE · SYSTEM 1' : 'OBSERVE · PROTECT · VERIFY'}
      </span>
      <nav className="top-bar-nav">
        {system === 'system1'
          ? PHASES.map((phase) => (
              <span key={phase} className="top-nav-phase">{phase}</span>
            ))
          : nav.map((tab) => (
              <button
                key={tab}
                type="button"
                className={`top-nav-item ${activeTab === tab ? 'top-nav-active' : ''}`}
                onClick={() => onTabChange && onTabChange(tab)}
              >
                {tab}
              </button>
            ))}
      </nav>
      <div className="top-bar-right">
        {connection && <ConnectionBadge connected={connection.connected} label={connection.label} />}
        {system === 'system2' && <span className="admin-badge">ADMIN</span>}
        <span className="top-bar-version">{version}</span>
      </div>
    </header>
  )
}
