import './badges.css'

export function StatusPill({ on, label }) {
  return (
    <span className={`status-pill ${on ? 'status-on' : 'status-off'}`}>
      <i className="status-dot" />
      {label !== undefined ? label : on ? 'ON' : 'OFF'}
    </span>
  )
}

export function ThreatLevelBadge({ level }) {
  const cls = {
    NORMAL: 'level-normal',
    SUSPICIOUS: 'level-suspicious',
    HIGH: 'level-high',
    CRITICAL: 'level-critical'
  }[level] || 'level-normal'
  return <span className={`threat-badge ${cls}`}>{level || 'NORMAL'}</span>
}

export function ConnectionBadge({ connected, label }) {
  return (
    <span className={`connection-badge ${connected ? 'connection-up' : 'connection-down'}`}>
      <i className={`connection-dot ${connected ? 'pulse' : ''}`} />
      {label !== undefined ? label : connected ? 'CONNECTED' : 'OFFLINE'}
    </span>
  )
}
